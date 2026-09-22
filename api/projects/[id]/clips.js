const { DEMO } = require('../../_lib');
module.exports = async (req, res) => {
  const { id } = req.query;
  res.status(200).json({ clips: DEMO.clips[id] || [] });
};
