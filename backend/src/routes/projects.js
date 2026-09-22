const path = require('path');
const fs = require('fs');
const db = require('../lib/db');
const { sendJson, readJsonBody } = require('../lib/router');
const { runPipeline, UPLOADS_DIR } = require('../lib/pipeline');
const processor = require('../video/processor');

const MAX_VIDEO_BYTES = 200 * 1024 * 1024; // 200MB mentah
const ALLOWED_VIDEO_MIME = ['video/mp4', 'video/quicktime', 'video/webm', 'video/x-matroska'];
const EXT_BY_MIME = { 'video/mp4': 'mp4', 'video/quicktime': 'mov', 'video/webm': 'webm', 'video/x-matroska': 'mkv' };

// Job progress in-memory (cukup untuk 1 instance server; untuk multi-instance sungguhan, pindah ke DB/Redis)
const jobProgress = new Map();

function register(router) {
  router.get('/api/projects', async (req, res) => {
    const projects = db.findAllBy('projects', (p) => p.userId === req.user.id).sort((a, b) => new Date(b.updatedAt || b.createdAt) - new Date(a.updatedAt || a.createdAt));
    sendJson(res, 200, { projects });
  });

  router.get('/api/projects/:id', async (req, res) => {
    const project = db.findById('projects', req.params.id);
    if (!project || project.userId !== req.user.id) return sendJson(res, 404, { error: 'Project tidak ditemukan.' });
    sendJson(res, 200, { project });
  });

  router.patch('/api/projects/:id', async (req, res) => {
    const project = db.findById('projects', req.params.id);
    if (!project || project.userId !== req.user.id) return sendJson(res, 404, { error: 'Project tidak ditemukan.' });
    const body = await readJsonBody(req);
    const patch = {};
    if (typeof body.name === 'string' && body.name.trim()) patch.name = body.name.trim().slice(0, 120);
    const updated = await db.update('projects', req.params.id, patch);
    sendJson(res, 200, { project: updated });
  });

  router.delete('/api/projects/:id', async (req, res) => {
    const project = db.findById('projects', req.params.id);
    if (!project || project.userId !== req.user.id) return sendJson(res, 404, { error: 'Project tidak ditemukan.' });
    await db.remove('projects', req.params.id);
    await db.mutate((d) => { d.clips = d.clips.filter((c) => c.projectId !== req.params.id); });
    sendJson(res, 200, { ok: true });
  });

  // Upload video (base64 di body JSON — sederhana, tanpa perlu parser multipart tambahan).
  router.post('/api/projects/upload', async (req, res) => {
    let body;
    try { body = await readJsonBody(req, MAX_VIDEO_BYTES); } catch (err) { return sendJson(res, err.statusCode || 400, { error: err.message }); }
    const { name, mime, dataBase64 } = body;
    if (!dataBase64 || !mime) return sendJson(res, 400, { error: 'File video wajib disertakan.' });
    if (!ALLOWED_VIDEO_MIME.includes(mime)) return sendJson(res, 400, { error: `Format "${mime}" tidak didukung. Gunakan MP4, MOV, atau WebM.` });
    const buffer = Buffer.from(dataBase64, 'base64');
    if (buffer.length > MAX_VIDEO_BYTES) return sendJson(res, 413, { error: `Ukuran file melebihi batas ${Math.floor(MAX_VIDEO_BYTES / 1024 / 1024)}MB.` });

    const projectId = db.id('proj');
    const ext = EXT_BY_MIME[mime] || 'mp4';
    const videoPath = path.join(UPLOADS_DIR, `${projectId}.${ext}`);
    await fs.promises.mkdir(UPLOADS_DIR, { recursive: true });
    await fs.promises.writeFile(videoPath, buffer);

    let duration = 0;
    try { duration = (await processor.getMetadata(videoPath)).duration; } catch (_) { /* biarkan 0, tetap lanjut */ }

    const project = {
      id: projectId, userId: req.user.id, name: name || 'Untitled Project',
      sourceType: 'upload', videoPath, durationSec: Math.round(duration),
      status: 'uploaded', thumbnail: null, createdAt: db.now(), updatedAt: db.now(),
    };
    await db.insert('projects', project);
    sendJson(res, 201, { project });
  });

  // Import dari URL — HANYA validasi format URL + placeholder metadata yang jujur.
  // Tidak melakukan scraping/bypass — mengambil video asli dari YouTube/TikTok/dll
  // butuh API resmi & kredensial platform yang belum dikonfigurasi di server ini.
  router.post('/api/projects/import-url', async (req, res) => {
    const body = await readJsonBody(req);
    const { url } = body;
    if (!url || typeof url !== 'string') return sendJson(res, 400, { error: 'URL wajib diisi.' });
    let parsed;
    try { parsed = new URL(url); } catch (_) { return sendJson(res, 400, { error: 'URL tidak valid.' }); }
    if (!['http:', 'https:'].includes(parsed.protocol)) return sendJson(res, 400, { error: 'URL harus dimulai dengan http:// atau https://' });

    const host = parsed.hostname.replace(/^www\./, '');
    const knownPlatforms = { 'youtube.com': 'YouTube', 'youtu.be': 'YouTube', 'tiktok.com': 'TikTok', 'instagram.com': 'Instagram', 'twitch.tv': 'Twitch', 'x.com': 'X', 'twitter.com': 'X' };
    const platform = knownPlatforms[host] || null;
    const isDirectFile = /\.(mp4|mov|webm)(\?.*)?$/i.test(parsed.pathname);

    if (!platform && !isDirectFile) {
      return sendJson(res, 400, { error: `Platform "${host}" belum didukung. Yang didukung: YouTube, TikTok, Instagram, Twitch, X, atau link file video langsung (.mp4/.mov/.webm).` });
    }
    if (platform && !isDirectFile) {
      // Jujur: tanpa API resmi platform (butuh kredensial developer per-platform), import
      // video asli dari sini tidak bisa dilakukan sungguhan — beri tahu jelas, bukan pura-pura sukses.
      return sendJson(res, 501, {
        error: `Import otomatis dari ${platform} butuh API resmi ${platform} (kredensial developer) yang belum dikonfigurasi di server ini. Untuk sekarang, unduh videonya lewat aplikasi resmi ${platform} lalu upload manual lewat "Upload Video".`,
        platform, notImplemented: true,
      });
    }
    // Link file video langsung — ini bisa benar-benar diproses.
    sendJson(res, 200, { valid: true, platform: 'Direct file', title: parsed.pathname.split('/').pop(), previewNote: 'Klik Import untuk mengunduh & memproses file ini.' });
  });

  router.post('/api/projects/:id/process', async (req, res) => {
    const project = db.findById('projects', req.params.id);
    if (!project || project.userId !== req.user.id) return sendJson(res, 404, { error: 'Project tidak ditemukan.' });
    if (!project.videoPath || !fs.existsSync(project.videoPath)) return sendJson(res, 400, { error: 'File video project ini tidak ditemukan di server.' });

    await db.update('projects', project.id, { status: 'processing' });
    jobProgress.set(project.id, 'queued');
    sendJson(res, 202, { status: 'processing' });

    // Proses di background — client poll GET /api/projects/:id/process-status
    runPipeline(project, project.videoPath, (stage) => jobProgress.set(project.id, stage))
      .then(async (result) => {
        await db.mutate((d) => { d.clips.push(...result.clips); if (result.transcriptSegments.length) d.transcripts.push({ id: db.id('tr'), projectId: project.id, language: 'auto', segments: result.transcriptSegments }); });
        await db.update('projects', project.id, { status: 'ready', thumbnail: result.clips[0]?.thumbnail || null });
        jobProgress.set(project.id, 'done');
      })
      .catch(async (err) => {
        console.error('Pipeline gagal:', err);
        await db.update('projects', project.id, { status: 'failed' });
        jobProgress.set(project.id, 'failed:' + err.message);
      });
  });

  router.get('/api/projects/:id/process-status', async (req, res) => {
    const project = db.findById('projects', req.params.id);
    if (!project || project.userId !== req.user.id) return sendJson(res, 404, { error: 'Project tidak ditemukan.' });
    const stage = jobProgress.get(req.params.id) || (project.status === 'ready' ? 'done' : 'queued');
    sendJson(res, 200, { stage, projectStatus: project.status });
  });
}

module.exports = { register };
