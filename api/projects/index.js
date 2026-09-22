const { DEMO } = require('../_lib');
module.exports = async (req, res) => {
  if (req.method === 'GET') return res.status(200).json({ projects: DEMO.projects });
  return res.status(501).json({ error: 'Upload & buat project baru butuh backend/ penuh (server Node dengan disk persisten + FFmpeg) — tidak tersedia di deployment Vercel demo ini. Jalankan backend/ di Render/Railway/VPS, lihat README.' });
};
