const { DEMO } = require('./_lib');
module.exports = async (req, res) => {
  if (req.method === 'GET') return res.status(200).json({ brandKit: DEMO.brandKit });
  res.status(200).json({ brandKit: { ...DEMO.brandKit, ...(req.body || {}) }, note: 'Perubahan tidak tersimpan permanen di deployment demo ini (tanpa database persisten).' });
};
