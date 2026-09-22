const { generateText, parseJsonLoose, isConfigured } = require('../_lib');
module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed.' });
  const { topic, tone } = req.body || {};
  if (!topic) return res.status(400).json({ error: 'Topik wajib diisi.' });
  if (!isConfigured()) return res.status(200).json({ hook: `Tau gak kenapa kebanyakan orang gagal di "${topic}"?`, introduction: 'Di video ini gua bakal breakdown step-by-step.', mainPoints: ['Poin pertama.', 'Poin kedua.', 'Poin ketiga.'], cta: 'Follow buat konten kayak gini tiap minggu.' });
  try {
    const raw = await generateText(`Kamu adalah penulis skrip video profesional. Tone: ${tone || 'casual'}. Balas HANYA JSON, tanpa markdown.`,
      `Buatkan skrip video pendek untuk topik: "${topic}". Format JSON: {"hook": string, "introduction": string, "mainPoints": string[], "cta": string}`, 1536);
    res.status(200).json(parseJsonLoose(raw));
  } catch (err) { res.status(502).json({ error: 'AI gagal membuat skrip: ' + err.message }); }
};
