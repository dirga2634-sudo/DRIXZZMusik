const { isConfigured } = require('./_lib');
module.exports = async (req, res) => {
  res.status(200).json({ ok: true, aiConfigured: isConfigured(), deployment: 'vercel-demo', note: 'Deployment Vercel ini fokus fitur AI teks (title/caption/hashtag/script) + demo data. Upload & rendering video asli butuh backend/ penuh (Render/Railway/VPS) — lihat README.' });
};
