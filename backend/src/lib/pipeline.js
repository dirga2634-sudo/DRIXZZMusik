/**
 * Pipeline AI Clip Generator — inti fitur utama Vidzly.
 * Upload → extract audio → transcribe → scene detection → analisis visual AI →
 * generate clip candidates dengan judul & viral score dari AI asli.
 *
 * Kalau AI provider belum dikonfigurasi, otomatis jatuh ke mode heuristik
 * (potong rata berdasarkan scene/silence tanpa judul AI) — bukan gagal total,
 * tapi juga tidak pura-pura pakai AI padahal tidak.
 */
const path = require('path');
const fs = require('fs');
const processor = require('../video/processor');
const ai = require('../ai/provider');
const db = require('./db');

const EXPORTS_DIR = path.join(__dirname, '..', '..', 'exports');
const UPLOADS_DIR = path.join(__dirname, '..', '..', 'uploads');

function fileToBase64(filePath) {
  return fs.readFileSync(filePath).toString('base64');
}

async function runPipeline(project, videoPath, onProgress) {
  const report = (stage) => { onProgress && onProgress(stage); };

  report('extracting_audio');
  const audioPath = path.join(EXPORTS_DIR, project.id, 'audio.mp3');
  await processor.extractAudio(videoPath, audioPath);

  report('transcribing');
  let transcriptSegments = [];
  if (ai.isConfigured()) {
    try {
      const audioB64 = fileToBase64(audioPath);
      transcriptSegments = await ai.transcribeAudio(audioB64, 'audio/mpeg');
    } catch (err) {
      console.warn('Transkripsi gagal, lanjut tanpa transcript:', err.message);
    }
  }

  report('finding_highlights');
  const meta = await processor.getMetadata(videoPath);
  let scenes = await processor.detectScenes(videoPath, 0.25);
  scenes = [0, ...scenes, meta.duration].filter((v, i, arr) => arr.indexOf(v) === i).sort((a, b) => a - b);

  // Bangun kandidat rentang klip dari titik-titik scene (target 15-60 detik per klip).
  const candidateRanges = [];
  for (let i = 0; i < scenes.length - 1; i++) {
    let start = scenes[i];
    let end = scenes[i + 1];
    if (end - start < 8) continue; // kependekan, skip
    if (end - start > 60) end = start + 60; // kepanjangan, potong maks 60s
    candidateRanges.push({ start, end });
    if (candidateRanges.length >= 6) break;
  }
  if (candidateRanges.length === 0) {
    // Video tanpa scene-change terdeteksi (mis. talking-head statis) — bagi rata saja.
    const chunk = Math.min(45, meta.duration / 3 || meta.duration);
    for (let t = 0; t + 10 < meta.duration && candidateRanges.length < 4; t += chunk) {
      candidateRanges.push({ start: t, end: Math.min(meta.duration, t + chunk) });
    }
  }

  report('generating_clips');
  const clipsDir = path.join(EXPORTS_DIR, project.id, 'thumbs');
  const thumbPaths = [];
  for (const range of candidateRanges) {
    const mid = (range.start + range.end) / 2;
    const thumbPath = path.join(clipsDir, `t_${Math.round(mid)}.jpg`);
    await processor.extractThumbnail(videoPath, mid, thumbPath);
    thumbPaths.push({ range, thumbPath });
  }

  let aiJudgments = null;
  if (ai.isConfigured() && thumbPaths.length > 0) {
    try {
      const images = thumbPaths.map((t) => fileToBase64(t.thumbPath));
      const transcriptHint = transcriptSegments.length
        ? `Transkrip video ini (potongan): ${transcriptSegments.slice(0, 8).map((s) => s.text).join(' ')}`
        : 'Tidak ada transkrip tersedia.';
      const prompt = `Kamu diberi ${images.length} thumbnail yang diambil dari titik tengah ${images.length} segmen berbeda dari satu video (gambar 1 = segmen 1, dst, berurutan). ${transcriptHint}\n\n` +
        `Untuk SETIAP gambar/segmen secara berurutan, nilai seberapa menarik segmen itu untuk dijadikan clip pendek viral (skor 0-100), buat judul pendek yang catchy (bahasa Indonesia, maks 60 karakter), dan kategori (pilih salah satu: best, educational, funny, emotional, trending).\n` +
        `Balas HANYA dengan JSON array (tanpa markdown), satu object per gambar, urut sesuai urutan gambar: [{"score": number, "title": string, "category": string}]`;
      const raw = await ai.analyzeVisualMoments(prompt, images);
      const cleaned = raw.replace(/```json|```/g, '').trim();
      aiJudgments = JSON.parse(cleaned);
    } catch (err) {
      console.warn('Analisis visual AI gagal, pakai judul default:', err.message);
    }
  }

  const clips = [];
  for (let i = 0; i < candidateRanges.length; i++) {
    const range = candidateRanges[i];
    const judgment = aiJudgments && aiJudgments[i] ? aiJudgments[i] : null;
    const clipId = db.id('clip');
    clips.push({
      id: clipId,
      projectId: project.id,
      title: judgment?.title || `Clip ${i + 1} (${Math.round(range.start)}s–${Math.round(range.end)}s)`,
      duration: Math.round(range.end - range.start),
      startSec: range.start,
      endSec: range.end,
      viralScore: judgment?.score ?? Math.max(40, 90 - i * 8),
      category: judgment?.category || 'best',
      transcriptPreview: transcriptSegments.filter((s) => s.start >= range.start && s.start <= range.end).map((s) => s.text).join(' ').slice(0, 160) || null,
      thumbnail: `/exports/${project.id}/thumbs/t_${Math.round((range.start + range.end) / 2)}.jpg`,
      aspectRatio: '9:16',
      sourceVideoPath: videoPath,
      aiGenerated: Boolean(judgment),
      createdAt: db.now(),
    });
  }

  report('done');
  return { clips, transcriptSegments, usedAi: Boolean(aiJudgments) };
}

module.exports = { runPipeline, EXPORTS_DIR, UPLOADS_DIR };
