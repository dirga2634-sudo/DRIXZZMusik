module.exports = (req, res) => {
  const hasOpenRouter = Boolean(process.env.OPENROUTER_API_KEY);
  const hasGemini = Boolean(process.env.GEMINI_API_KEY);
  res.status(200).json({ ok: true, configured: hasOpenRouter || hasGemini, openrouter: hasOpenRouter, gemini: hasGemini });
};
