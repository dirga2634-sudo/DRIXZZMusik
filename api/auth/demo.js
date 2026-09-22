const crypto = require('crypto');
module.exports = async (req, res) => {
  const payload = Buffer.from(JSON.stringify({ userId: 'demo_user', exp: Math.floor(Date.now()/1000) + 604800 })).toString('base64url');
  const sig = crypto.createHmac('sha256', process.env.SESSION_SECRET || 'vidzly-dev-secret').update(payload).digest('base64url');
  res.status(200).json({ token: `${payload}.${sig}`, user: { id: 'demo_user', name: 'Creator', email: 'demo@vidzly.app', isDemo: true } });
};
