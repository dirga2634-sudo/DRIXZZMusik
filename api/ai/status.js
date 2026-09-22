const { isConfigured } = require('../_lib');
module.exports = async (req, res) => { res.status(200).json({ configured: isConfigured() }); };
