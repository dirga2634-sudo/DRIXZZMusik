const { generateText, parseJsonLoose, isConfigured } = require('../_lib');
module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed.' });
  const { topic } = req.body || {};
  if (!topic) return res.status(400).json({ error: 'Topik wajib diisi.' });
  if (!isConfigured()) return res.status(200).json({ youtubeTitles: ['5 Kesalahan Fatal Kreator Pemula'], shortsTitles: ['Ini yang bikin video lo gak viral 👀'], instagramCaptions: ['Yang belum tau, wajib nonton sampe abis ✨'], descriptions: ['Contoh deskripsi — isi AI key untuk hasil asli.'] });
  try {
    const raw = await generateText('Kamu adalah asisten SEO & judul viral untuk kreator video. Balas HANYA dengan JSON, tanpa markdown.',
      `Berdasarkan topik/isi video berikut, buatkan:\n- 10 judul YouTube\n- 10 judul Shorts/TikTok\n- 5 caption Instagram\n- 3 deskripsi video\n\nTopik: "${topic}"\n\nBalas format JSON: {"youtubeTitles": string[], "shortsTitles": string[], "instagramCaptions": string[], "descriptions": string[]}`, 2048);
    res.status(200).json(parseJsonLoose(raw));
  } catch (err) { res.status(502).json({ error: 'AI gagal membuat judul: ' + err.message }); }
};
