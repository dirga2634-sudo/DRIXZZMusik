const {
  OPENROUTER_URL,
  GEMINI_BASE_URL,
  getModel,
  buildOpenRouterMessages,
  validateAttachments,
  systemPrompt,
  buildFallbackCandidates,
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

  const activeControllers = new Set();
  let clientClosed = false;
  req.on('close', () => {
    clientClosed = true;
    for (const c of activeControllers) c.abort();
  });
  function trackController() {
    const controller = new AbortController();
    activeControllers.add(controller);
    return { controller, release: () => activeControllers.delete(controller) };
  }

  // === FASE 1: kumpulkan draf jawaban dari beberapa model SEKALIGUS (paralel) ===
  // Lihat catatan lebih lengkap di server.js — intinya: bukan lomba cepat-cepatan,
  // tapi sengaja MENUNGGU beberapa model selesai supaya ada beberapa draf untuk
  // digabung di Fase 2. Sengaja OpenRouter-only untuk fase ini.
  const DRAFT_COUNT = 3;
  const draftCandidates = candidates.filter((c) => c.provider !== 'google').slice(0, DRAFT_COUNT);

  async function getDraftText(candidate) {
    const { controller, release } = trackController();
    try {
      if (!OPENROUTER_API_KEY) throw new Error('OPENROUTER_API_KEY belum diisi.');
      const payload = { model: candidate.id, messages: orMessages, stream: false, max_tokens: candidate.maxOutputTokens };
      const response = await fetch(OPENROUTER_URL, {
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
      if (!response.ok) throw new Error(`status ${response.status}`);
      const json = await response.json();
      const text = json && json.choices && json.choices[0] && json.choices[0].message && json.choices[0].message.content;
      if (!text) throw new Error('respons draf kosong');
      return { candidate, text };
    } finally {
      release();
    }
  }

  const draftSettled = await Promise.allSettled(draftCandidates.map(getDraftText));
  if (clientClosed) return res.end();
  const successfulDrafts = draftSettled.filter((r) => r.status === 'fulfilled').map((r) => r.value);

  if (successfulDrafts.length === 0) {
    return res.status(502).json({ error: 'Semua draf jawaban gagal didapat (provider sedang bermasalah), coba lagi sebentar lagi.' });
  }

  // === FASE 2: sintesis semua draf jadi SATU jawaban final (ini yang di-stream) ===
  const lastUserMsg = [...messages].reverse().find((m) => m.role !== 'assistant');
  const lastUserText = (lastUserMsg && lastUserMsg.content) || '';
  const draftsBlock = successfulDrafts.map((d, i) => `--- Draf ${i + 1} ---\n${d.text}`).join('\n\n');
  const synthesisSystemPrompt =
    systemPrompt() +
    ' Untuk pesan ini secara khusus, kamu diberi beberapa draf jawaban independen dari beberapa sistem AI berbeda untuk pertanyaan yang sama. ' +
    'Gabungkan bagian terbaik dari tiap draf, perbaiki kesalahan yang kamu lihat, selesaikan kalau ada yang saling bertentangan, dan tulis SATU jawaban ' +
    'final yang logis, jelas, dan enak dibaca. Tulis langsung sebagai jawabanmu sendiri — jangan sebut kata "draf", "beberapa AI", atau proses penggabungan ini sama sekali.';
  const synthesisUserText = `Pertanyaan pengguna:\n${lastUserText}\n\n${draftsBlock}`;

  function attemptSynthesis(candidate) {
    const { controller, release } = trackController();
    let promise;
    if (candidate.provider === 'google') {
      const genConfig = { maxOutputTokens: candidate.maxOutputTokens };
      if (reasoningEffort && reasoningEffort !== 'none') genConfig.thinkingConfig = { includeThoughts: true };
      promise = !GEMINI_API_KEY
        ? Promise.reject(Object.assign(new Error('GEMINI_API_KEY belum diisi.'), { roumStatus: 500 }))
        : fetch(`${GEMINI_BASE_URL}/${candidate.geminiModel}:streamGenerateContent?alt=sse`, {
            method: 'POST',
            headers: { 'x-goog-api-key': GEMINI_API_KEY, 'Content-Type': 'application/json' },
            body: JSON.stringify({
              contents: [{ role: 'user', parts: [{ text: synthesisUserText }] }],
              systemInstruction: { parts: [{ text: synthesisSystemPrompt }] },
              generationConfig: genConfig,
            }),
            signal: controller.signal,
          });
    } else if (!OPENROUTER_API_KEY) {
      promise = Promise.reject(Object.assign(new Error('OPENROUTER_API_KEY belum diisi.'), { roumStatus: 500 }));
    } else {
      const orPayload = {
        model: candidate.id,
        messages: [
          { role: 'system', content: synthesisSystemPrompt },
          { role: 'user', content: synthesisUserText },
        ],
        stream: true,
        max_tokens: candidate.maxOutputTokens,
      };
      if (reasoningEffort && reasoningEffort !== 'none') orPayload.reasoning = { effort: reasoningEffort };
      promise = fetch(OPENROUTER_URL, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${OPENROUTER_API_KEY}`,
          'Content-Type': 'application/json',
          'HTTP-Referer': process.env.SITE_URL || 'https://vercel.com',
          'X-Title': 'Roum AI',
        },
        body: JSON.stringify(orPayload),
        signal: controller.signal,
      });
    }
    promise.then(release, release);
    return { candidate, controller, promise };
  }

  async function raceGroup(group) {
    if (group.length === 0) return { winner: null, failures: [] };
    const attempts = group.map((candidate) => attemptSynthesis(candidate));
    const failures = [];
    let pending = attempts.length;

    return new Promise((resolve) => {
      for (const { candidate, controller, promise } of attempts) {
        promise
          .then(async (response) => {
            if (response.ok) {
              for (const other of attempts) {
                if (other.candidate !== candidate) other.controller.abort();
              }
              resolve({ winner: { candidate, response }, failures });
            } else {
              let detail = '';
              try { const j = await response.json(); detail = (j && j.error && j.error.message) || ''; } catch (_) {}
              failures.push({ status: response.status, detail });
              pending--;
              if (pending === 0) resolve({ winner: null, failures });
            }
          })
          .catch((err) => {
            if (err.name === 'AbortError') { pending--; if (pending === 0) resolve({ winner: null, failures }); return; }
            failures.push({ status: err.roumStatus || 502, detail: err.message });
            pending--;
            if (pending === 0) resolve({ winner: null, failures });
          });
      }
    });
  }

  const OPENROUTER_RACE_CAP = 4;
  const openRouterCandidates = candidates.filter((c) => c.provider !== 'google');
  const googleCandidates = candidates.filter((c) => c.provider === 'google');
  const raceGroupCandidates = [...openRouterCandidates.slice(0, OPENROUTER_RACE_CAP), ...googleCandidates];
  const remainderCandidates = openRouterCandidates.slice(OPENROUTER_RACE_CAP);

  let upstream = null;
  let usedModel = candidates[0];
  let lastStatus = 502;
  let lastDetail = '';

  const raceResult = await raceGroup(raceGroupCandidates);
  if (clientClosed) return res.end();

  if (raceResult.winner) {
    upstream = raceResult.winner.response;
    usedModel = raceResult.winner.candidate;
  } else {
    if (raceResult.failures.length) {
      const last = raceResult.failures[raceResult.failures.length - 1];
      lastStatus = last.status;
      lastDetail = last.detail;
    }
    for (const candidate of remainderCandidates) {
      if (clientClosed) return res.end();
      let attempt;
      try {
        attempt = await attemptSynthesis(candidate).promise;
      } catch (err) {
        if (err.name === 'AbortError') return res.end();
        lastStatus = err.roumStatus || 502;
        lastDetail = err.message || 'Tidak bisa menghubungi provider AI.';
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
  }

  if (!upstream) {
    const friendly = {
      401: 'API key tidak valid atau ditolak.',
      402: 'Kredit OpenRouter tidak cukup untuk model ini.',
      403: 'API key ditolak (cek GEMINI_API_KEY jika sedang mencoba model Gemini).',
      404: 'Model tidak ditemukan atau sedang tidak tersedia.',
      429: 'Semua model sedang penuh, coba lagi sebentar lagi.',
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
