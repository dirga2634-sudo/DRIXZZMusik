const path = require('path');
const fs = require('fs');
const db = require('../lib/db');
const { sendJson, readJsonBody } = require('../lib/router');
const processor = require('../video/processor');
const { EXPORTS_DIR } = require('../lib/pipeline');

function register(router) {
  router.get('/api/projects/:id/clips', async (req, res) => {
    const project = db.findById('projects', req.params.id);
    if (!project || project.userId !== req.user.id) return sendJson(res, 404, { error: 'Project tidak ditemukan.' });
    const clips = db.findAllBy('clips', (c) => c.projectId === req.params.id).sort((a, b) => (b.viralScore || 0) - (a.viralScore || 0));
    sendJson(res, 200, { clips });
  });

  router.patch('/api/clips/:id', async (req, res) => {
    const clip = db.findById('clips', req.params.id);
    if (!clip) return sendJson(res, 404, { error: 'Clip tidak ditemukan.' });
    const body = await readJsonBody(req);
    const patch = {};
    if (typeof body.title === 'string') patch.title = body.title.slice(0, 150);
    if (typeof body.aspectRatio === 'string') patch.aspectRatio = body.aspectRatio;
    const updated = await db.update('clips', req.params.id, patch);
    sendJson(res, 200, { clip: updated });
  });

  router.delete('/api/clips/:id', async (req, res) => {
    const clip = db.findById('clips', req.params.id);
    if (!clip) return sendJson(res, 404, { error: 'Clip tidak ditemukan.' });
    await db.remove('clips', req.params.id);
    sendJson(res, 200, { ok: true });
  });

  // Export/render beneran: potong dari video sumber + reframe sesuai aspect ratio.
  // Kalau clip ini clip demo (tanpa sourceVideoPath asli di disk), kembalikan
  // pesan jelas — bukan pura-pura render sukses.
  router.post('/api/clips/:id/export', async (req, res) => {
    const clip = db.findById('clips', req.params.id);
    if (!clip) return sendJson(res, 404, { error: 'Clip tidak ditemukan.' });
    const body = await readJsonBody(req);
    const aspectRatio = body.aspectRatio || clip.aspectRatio || '9:16';

    if (!clip.sourceVideoPath || !fs.existsSync(clip.sourceVideoPath)) {
      return sendJson(res, 200, {
        demo: true,
        message: 'Ini clip contoh (Demo Mode) — tidak ada file video sumber asli untuk di-render. Upload video sungguhan lewat "Create" untuk export beneran.',
      });
    }

    const outDir = path.join(EXPORTS_DIR, clip.projectId, 'renders');
    const outPath = path.join(outDir, `${clip.id}_${aspectRatio.replace(':', 'x')}.mp4`);
    const rawPath = path.join(outDir, `${clip.id}_raw.mp4`);

    await processor.cutClip(clip.sourceVideoPath, clip.startSec, clip.endSec, rawPath);
    if (aspectRatio === '9:16') await processor.reframeToVertical(rawPath, outPath);
    else if (aspectRatio === '1:1') await processor.reframeToSquare(rawPath, outPath);
    else await fs.promises.copyFile(rawPath, outPath);

    const exportRecord = { id: db.id('export'), clipId: clip.id, projectId: clip.projectId, aspectRatio, path: `/exports/${clip.projectId}/renders/${path.basename(outPath)}`, createdAt: db.now() };
    await db.insert('exports', exportRecord);
    sendJson(res, 200, { demo: false, export: exportRecord });
  });
}

module.exports = { register };
