const {
  OPENROUTER_URL,
  getModel,
  buildOpenRouterMessages,
  validateAttachments,
  systemPrompt,
  buildFallbackCandidates,
  fetchGeminiDirect,
  pipeGeminiAsOpenAiStream,
} = require('../lib/vercel-shared');

const MAX_MESSAGES_PER_REQUEST = 60;

module.exports = async (req, res) => {
  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST');
    return res.status(405).json({ error: 'Method not allowed.' });
  }

  const OPENROUTER_API_KEY = process.env.OPENROUTER_API_KEY || '';
  const GEMINI_API_KEY = process.env.GEMINI_API_KEY || '';
  if (!OPENROUTER_API_KEY && !GEMINI_API_KEY) {
    return res.status(500).json({
      error: 'Server belum dikonfigurasi: tambahkan OPENROUTER_API_KEY dan/atau GEMINI_API_KEY di Vercel → Project Settings → Environment Variables, lalu redeploy.',
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

  const candidates = buildFallbackCandidates(modelConfig, messages);
  const orMessages = [{ role: 'system', content: systemPrompt() }, ...buildOpenRouterMessages(messages)];

  const controller = new AbortController();
  req.on('close', () => controller.abort());

  let upstream = null;
  let usedModel = candidates[0];
  let lastStatus = 502;
  let lastDetail = '';

  for (let i = 0; i < candidates.length; i++) {
    const candidate = candidates[i];
    let attempt;
    try {
      if (candidate.provider === 'google') {
        if (!GEMINI_API_KEY) { lastStatus = 500; lastDetail = 'GEMINI_API_KEY belum diisi.'; continue; }
        attempt = await fetchGeminiDirect(candidate, messages, reasoningEffort, controller.signal, GEMINI_API_KEY);
      } else {
        if (!OPENROUTER_API_KEY) { lastStatus = 500; lastDetail = 'OPENROUTER_API_KEY belum diisi.'; continue; }
        const payload = {
          model: candidate.id,
          messages: orMessages,
          stream: true,
          max_tokens: candidate.maxOutputTokens,
        };
        if (reasoningEffort && reasoningEffort !== 'none') payload.reasoning = { effort: reasoningEffort };
        attempt = await fetch(OPENROUTER_URL, {
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
      }
    } catch (err) {
      if (err.name === 'AbortError') return res.end();
      lastStatus = 502;
      lastDetail = 'Tidak bisa menghubungi provider AI.';
      continue;
    }

    if (attempt.ok) {
      upstream = attempt;
      usedModel = candidate;
      break;
    }

    lastStatus = attempt.status;
    try {
      const errJson = await attempt.json();
      lastDetail = (errJson && errJson.error && errJson.error.message) || '';
    } catch (_) {
      /* respons error bukan JSON */
    }
    const isRetryable = attempt.status === 429 || attempt.status === 404 || attempt.status === 403;
    if (!isRetryable) break;
  }

  if (!upstream) {
    const friendly = {
      401: 'API key tidak valid atau ditolak.',
      402: 'Kredit OpenRouter tidak cukup untuk model ini.',
      403: 'API key ditolak (cek GEMINI_API_KEY jika sedang mencoba model Gemini).',
      404: 'Model tidak ditemukan atau sedang tidak tersedia.',
      429:
        candidates.length > 1
          ? 'Semua model gratis sedang penuh, coba lagi sebentar lagi.'
          : 'Rate limit tercapai, coba lagi sebentar lagi.',
      500: lastDetail || 'Server belum dikonfigurasi untuk model ini.',
    };
    const msg = friendly[lastStatus] || (lastDetail || `Provider mengembalikan error (status ${lastStatus}).`);
    const status = lastStatus >= 400 && lastStatus < 600 ? lastStatus : 502;
    return res.status(status).json({ error: lastDetail && friendly[lastStatus] && lastStatus !== 500 ? `${msg} ${lastDetail}` : msg });
  }

  if (usedModel.id !== candidates[0].id) {
    res.setHeader('X-Roum-Model-Used', usedModel.id);
  }
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  if (typeof res.flushHeaders === 'function') res.flushHeaders();

  if (usedModel.provider === 'google') {
    return pipeGeminiAsOpenAiStream(upstream, res);
  }

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
