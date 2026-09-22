module.exports = async (req, res) => {
  // Deployment Vercel ini tidak menyimpan akun asli (lihat catatan di README) — selalu tampil sebagai Demo.
  res.status(200).json({ user: { id: 'demo_user', name: 'Creator', email: 'demo@vidzly.app', isDemo: true } });
};
