module.exports = (req, res) => {
  res.status(200).json({ ok: true, configured: Boolean(process.env.OPENROUTER_API_KEY) });
};
