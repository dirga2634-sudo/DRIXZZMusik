const { DEMO } = require('./_lib');
module.exports = async (req, res) => { res.status(200).json({ templates: DEMO.templates }); };
