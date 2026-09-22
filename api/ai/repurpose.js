const { generateText, parseJsonLoose, isConfigured } = require('../_lib');
module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed.' });
  const { topic } = req.body || {};
  if (!topic) return res.status(400).json({ error: 'Topik wajib diisi.' });
  if (!isConfigured()) return res.status(200).json({ quoteCards: ['"Setiap kreator hebat mulai dari satu video pertama."'], youtubeDescription: 'Contoh deskripsi — isi AI key untuk hasil asli.', socialPosts: ['Contoh post — isi AI key untuk hasil asli.'] });
  try {
    const raw = await generateText('Kamu adalah asisten content repurposing. Balas HANYA JSON, tanpa markdown.',
      `Dari video dengan topik: "${topic}", buatkan 3 quote card, 1 deskripsi YouTube, 3 post Threads/X. Format: {"quoteCards": string[], "youtubeDescription": string, "socialPosts": string[]}`, 1536);
    res.status(200).json(parseJsonLoose(raw));
  } catch (err) { res.status(502).json({ error: 'AI gagal repurpose: ' + err.message }); }
};
