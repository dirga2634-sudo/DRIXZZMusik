const { generateText, parseJsonLoose, isConfigured } = require('../_lib');
module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed.' });
  const { topic } = req.body || {};
  if (!topic) return res.status(400).json({ error: 'Topik wajib diisi.' });
  if (!isConfigured()) return res.status(200).json({ hashtags: ['#fyp', '#viral', '#contentcreator', '#shorts', '#trending'] });
  try {
    const raw = await generateText('Kamu adalah asisten hashtag untuk kreator video sosial media. Balas HANYA JSON array of string, tanpa markdown.',
      `Buatkan 15 hashtag relevan untuk video dengan topik: "${topic}". Format: ["#tag1", "#tag2", ...]`, 512);
    res.status(200).json({ hashtags: parseJsonLoose(raw) });
  } catch (err) { res.status(502).json({ error: 'AI gagal membuat hashtag: ' + err.message }); }
};
