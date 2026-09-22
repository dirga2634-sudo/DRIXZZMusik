module.exports = async (req, res) => {
  res.status(501).json({ error: 'Pemrosesan video (FFmpeg) butuh backend/ penuh — tidak tersedia di deployment Vercel demo ini. Lihat README.' });
};
