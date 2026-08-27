const {
  OPENROUTER_URL,
  getModel,
  buildOpenRouterMessages,
  validateAttachments,
  systemPrompt,
} = require('../lib/vercel-shared');

const MAX_MESSAGES_PER_REQUEST = 60;

module.exports = async (req, res) => {
  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST');
    return res.status(405).json({ error: 'Method not allowed.' });
  }

  const OPENROUTER_API_KEY = process.env.OPENROUTER_API_KEY || '';
  if (!OPENROUTER_API_KEY) {
    return res.status(500).json({
      error: 'Server belum dikonfigurasi: tambahkan OPENROUTER_API_KEY di Vercel → Project Settings → Environment Variables, lalu redeploy.',
    });
  }

  const { messages, model, reasoningEffort } = req.body || {};
  if (!Array.isArray(messages) || messages.length === 0) {
    return res.status(400).json({ error: 'Pesan tidak valid atau kosong.' });
  }
  if (messages.length > MAX_MESSAGES_PER_REQUEST) {
    return res.status(400).json({ error: 'Percakapan ini terlalu panjang untuk satu permintaan.' });
  }

  const modelConfig = getModel(model);
  const attachmentError = validateAttachments(messages, modelConfig);
  if (attachmentError) {
    return res.status(400).json({ error: attachmentError });
  }

  const payload = {
    model: modelConfig.id,
    messages: [{ role: 'system', content: systemPrompt() }, ...buildOpenRouterMessages(messages)],
    stream: true,
    max_tokens: modelConfig.maxOutputTokens,
  };
  if (reasoningEffort && reasoningEffort !== 'none') {
    payload.reasoning = { effort: reasoningEffort };
  }

  const controller = new AbortController();
  req.on('close', () => controller.abort());

  let upstream;
  try {
    upstream = await fetch(OPENROUTER_URL, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${OPENROUTER_API_KEY}`,
        'Content-Type': 'application/json',
        'HTTP-Referer': process.env.SITE_URL || 'https://vercel.com',
        'X-Title': 'Roum AI',
      },
      body: JSON.stringify(payload),
      signal: controller.signal,
    });
  } catch (err) {
    if (err.name === 'AbortError') return res.end();
    return res.status(502).json({ error: 'Tidak bisa menghubungi OpenRouter.' });
  }

  if (!upstream.ok) {
    let detail = '';
    try {
      const errJson = await upstream.json();
      detail = (errJson && errJson.error && errJson.error.message) || '';
    } catch (_) {
      /* respons error bukan JSON */
    }
    const friendly = {
      401: 'API key OpenRouter tidak valid atau ditolak.',
      402: 'Kredit OpenRouter tidak cukup untuk model ini.',
      404: 'Model tidak ditemukan atau sedang tidak tersedia.',
      429: 'Rate limit OpenRouter tercapai, coba lagi sebentar lagi.',
    };
    const msg = friendly[upstream.status] || `OpenRouter mengembalikan error (status ${upstream.status}).`;
    const status = upstream.status >= 400 && upstream.status < 600 ? upstream.status : 502;
    return res.status(status).json({ error: detail ? `${msg} ${detail}` : msg });
  }

  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  if (typeof res.flushHeaders === 'function') res.flushHeaders();

  const reader = upstream.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop();
      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed.startsWith('data:')) continue;
        res.write(`data: ${trimmed.slice(5).trim()}\n\n`);
      }
    }
  } catch (_) {
    // koneksi terputus di tengah jalan
  } finally {
    res.end();
  }
};
