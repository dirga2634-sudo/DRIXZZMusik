module.exports = async (req, res) => {
  const { url } = req.body || {};
  if (!url) return res.status(400).json({ error: 'URL wajib diisi.' });
  res.status(501).json({ error: 'Import video butuh backend/ penuh — tidak tersedia di deployment Vercel demo ini. Lihat README untuk menjalankan backend/ sungguhan.' });
};
