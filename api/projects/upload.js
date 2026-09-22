module.exports = async (req, res) => {
  res.status(501).json({ error: 'Upload video butuh backend/ penuh dengan disk persisten + FFmpeg — tidak tersedia di deployment Vercel demo ini. Jalankan backend/ di Render/Railway/VPS (lihat README), atau coba lokal lewat "npm start" di folder backend/.' });
};
