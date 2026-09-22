module.exports = async (req, res) => {
  res.status(200).json({ demo: true, message: 'Ini clip contoh (Demo Mode di Vercel) — export/render video asli butuh backend/ penuh dengan FFmpeg. Jalankan backend/ di Render/Railway/VPS atau lokal, lihat README.' });
};
