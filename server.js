/**
 * Roum AI — backend proxy Express.
 * Semua panggilan ke OpenRouter terjadi DI SINI (server-side), sehingga
 * OPENROUTER_API_KEY tidak pernah menyentuh browser/frontend.
 */

require('dotenv').config();
const path = require('path');
const express = require('express');
const cors = require('cors');
const rateLimit = require('express-rate-limit');

const app = express();

const PORT = Number(process.env.PORT) || 3000;
const OPENROUTER_API_KEY = process.env.OPENROUTER_API_KEY || '';
const OPENROUTER_URL = 'https://openrouter.ai/api/v1/chat/completions';
const SITE_URL = process.env.SITE_URL || `http://localhost:${PORT}`;

// Gemini langsung ke API Google (BUKAN lewat OpenRouter) — pakai kuota gratis
// Google AI Studio sendiri, independen dari OpenRouter. Beda auth (header
// x-goog-api-key, bukan Bearer token) dan beda format request/response total,
// makanya ditangani terpisah dari fungsi-fungsi khusus OpenRouter di bawah.
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || '';
const GEMINI_BASE_URL = 'https://generativelanguage.googleapis.com/v1beta/models';

const SYSTEM_PROMPT =
  'You are Roum AI, a helpful assistant built for this app — that is your one and only identity. ' +
  'If asked who made you, what company is behind you, what model or AI you are, or anything about your origin/architecture, ' +
  'always simply say you are Roum AI. Never mention OpenRouter, or any underlying AI provider, company, or model name ' +
  '(e.g. Google, NVIDIA, Anthropic, Z.ai, MiniMax, GLM, Gemini, Nemotron, Claude, GPT) — those are internal implementation ' +
  'details the user does not see. Format answers with Markdown, always use fenced code blocks with a language tag for code, ' +
  'and be concise but thorough.';

/**
 * Katalog model yang diizinkan (allowlist). Frontend hanya boleh memilih salah
 * satu key di bawah ini — server MENOLAK model string sembarangan dari client
 * supaya tidak disalahgunakan untuk memanggil model lain yang tidak diinginkan.
 * Untuk menambah/mengganti model, cukup edit objek ini (pakai slug resmi dari
 * https://openrouter.ai/models).
 */
const MODELS = {
  'auto:free': {
    id: 'auto:free',
    label: 'Roum AI Pro',
    tag: 'Model utama',
    description: 'Model utama Roum AI — cepat, cerdas, dan selalu berusaha memberi jawaban terbaik untuk pertanyaan, coding, maupun analisis gambar.',
    supportsImage: true,
    supportsVideo: true,
    maxOutputTokens: 8192,
  },
  'z-ai/glm-5.2:free': {
    id: 'z-ai/glm-5.2:free',
    label: 'GLM 5.2 (Free)',
    tag: 'Gratis · Coding & chat',
    description:
      'Model gratis dengan kualitas terbaik untuk coding sekaligus obrolan biasa di antara model gratis OpenRouter — gaya jawabannya natural, bukan cuma bisa coding. Teks saja, tidak bisa menganalisis gambar/video (pakai Nemotron Nano Omni atau MiniMax M3 untuk itu).',
    supportsImage: false,
    supportsVideo: false,
    maxOutputTokens: 8192,
  },
  'nvidia/nemotron-3-ultra-550b-a55b:free': {
    id: 'nvidia/nemotron-3-ultra-550b-a55b:free',
    label: 'Nemotron 3 Ultra (Free)',
    tag: 'Gratis · Paling populer',
    description:
      'Model gratis paling banyak dipakai di OpenRouter — frontier reasoning NVIDIA (550B parameter, MoE), konteks 1M token, sangat kuat untuk agentic coding & riset. Teks saja.',
    supportsImage: false,
    supportsVideo: false,
    maxOutputTokens: 16384,
  },
  'minimax/minimax-m3:free': {
    id: 'minimax/minimax-m3:free',
    label: 'MiniMax M3 (Free)',
    tag: 'Gratis · Gambar & video',
    description:
      'Model multimodal gratis dari MiniMax — mendukung teks, gambar, DAN video, konteks raksasa ~1M token, kuat untuk tugas agentic/coding jangka panjang. Salah satu model gratis paling populer di OpenRouter.',
    supportsImage: true,
    supportsVideo: true,
    maxOutputTokens: 16384,
  },
  'thinkingmachines/inkling:free': {
    id: 'thinkingmachines/inkling:free',
    label: 'Inkling (Free)',
    tag: 'Gratis · Gambar & audio',
    description:
      'Model multimodal gratis dari Thinking Machines Lab — memahami gambar & audio secara native, konteks ~1M token, untuk reasoning/coding/agentic umum.',
    supportsImage: true,
    supportsVideo: false,
    maxOutputTokens: 16384,
  },
  'nvidia/nemotron-3-super-120b-a12b:free': {
    id: 'nvidia/nemotron-3-super-120b-a12b:free',
    label: 'Nemotron 3 Super (Free)',
    tag: 'Gratis · Reasoning besar',
    description:
      'Model gratis NVIDIA yang lebih besar dari Nemotron Nano Omni — konteks 262K token, kuat di reasoning/coding/agentic (arsitektur hybrid Mamba-Transformer MoE). Teks saja, tidak bisa menganalisis gambar/video.',
    supportsImage: false,
    supportsVideo: false,
    maxOutputTokens: 16384,
  },
  'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free': {
    id: 'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',
    label: 'Nemotron Nano Omni',
    tag: 'Gratis · Gambar & video',
    description:
      'Model open-source NVIDIA yang benar-benar gratis — mendukung teks, gambar, video, dan reasoning. Kualitas coding/obrolannya di bawah GLM 5.2, tapi bisa menganalisis file visual.',
    supportsImage: true,
    supportsVideo: true,
    maxOutputTokens: 8192,
  },
  'z-ai/glm-5.3-flash': {
    id: 'z-ai/glm-5.3-flash',
    label: 'GLM-5.3 Flash',
    tag: 'Murah & cepat',
    description:
      'Sebelumnya dikenal sebagai model preview "Ox Alpha" — kini resmi dirilis Z.ai. Konteks 1M token, reasoning, mendukung gambar & video. Berbayar tapi sangat murah per token.',
    supportsImage: true,
    supportsVideo: true,
    maxOutputTokens: 131072,
  },
  'anthropic/claude-sonnet-5': {
    id: 'anthropic/claude-sonnet-5',
    label: 'Claude Sonnet 5',
    tag: 'Reasoning kuat',
    description:
      'Reasoning dan analisis gambar/file terbaik untuk coding & pekerjaan kompleks. Konteks 1M token, effort reasoning bisa diatur.',
    supportsImage: true,
    supportsVideo: false,
    maxOutputTokens: 64000,
  },
  'google/gemini-3-pro-preview': {
    id: 'google/gemini-3-pro-preview',
    label: 'Gemini 3 Pro',
    tag: 'Multimodal terkuat',
    description:
      'Paling kuat untuk memahami video, audio, gambar, dan dokumen sekaligus. Konteks 1M token.',
    supportsImage: true,
    supportsVideo: true,
    maxOutputTokens: 65536,
  },
  'google-direct/gemini-flash-latest': {
    id: 'google-direct/gemini-flash-latest',
    label: 'Gemini Flash (Free · langsung Google)',
    tag: 'Gratis · Independen',
    description:
      'Gratis lewat kuota API Google AI Studio kamu sendiri — TIDAK lewat OpenRouter sama sekali, jadi tetap jalan walau semua model gratis OpenRouter penuh. Kuat untuk gambar. Butuh GEMINI_API_KEY sendiri di .env.',
    supportsImage: true,
    supportsVideo: false,
    maxOutputTokens: 16384,
    provider: 'google',
    geminiModel: 'gemini-flash-latest',
  },
};

const DEFAULT_MODEL_ID =
  process.env.DEFAULT_MODEL && MODELS[process.env.DEFAULT_MODEL]
    ? process.env.DEFAULT_MODEL
    : 'auto:free';

/**
 * Model gratis di OpenRouter dipakai BANYAK orang sekaligus (karena $0), jadi
 * lebih sering kena rate limit/penuh dibanding model berbayar. Kalau model
 * yang dipilih ada di daftar ini dan kena 429/404, server otomatis coba model
 * gratis lain di daftar ini sebelum menyerah — user tidak perlu gonta-ganti
 * model manual. Model berbayar TIDAK pernah di-fallback (supaya tidak diam-diam
 * mengganti pilihan user ke model lain yang bisa kena biaya berbeda).
 */
const FREE_FALLBACK_CHAIN = [
  'z-ai/glm-5.2:free',
  'nvidia/nemotron-3-ultra-550b-a55b:free',
  'minimax/minimax-m3:free',
  'nvidia/nemotron-3-super-120b-a12b:free',
  'thinkingmachines/inkling:free',
  'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',
  // Gemini-direct cuma masuk rantai "Auto" kalau GEMINI_API_KEY memang diisi —
  // ditambahkan secara dinamis di buildFallbackCandidates, bukan statis di sini,
  // karena beda provider/auth dari yang lain.
];
const GEMINI_DIRECT_ID = 'google-direct/gemini-flash-latest';

function buildFallbackCandidates(modelConfig, messages) {
  const hasImage = messages.some((m) => (m.attachments || []).some((a) => a.type === 'image'));
  const hasVideo = messages.some((m) => (m.attachments || []).some((a) => a.type === 'video'));
  const chain = GEMINI_API_KEY ? [...FREE_FALLBACK_CHAIN, GEMINI_DIRECT_ID] : FREE_FALLBACK_CHAIN;

  // "Auto" bukan slug asli — selalu diganti seluruh rantai gratis, urutan tetap.
  if (modelConfig.id === 'auto:free') {
    const candidates = chain.map((id) => MODELS[id]).filter(
      (m) => m && (!hasImage || m.supportsImage) && (!hasVideo || m.supportsVideo)
    );
    return candidates.length ? candidates : [MODELS[chain[0]]];
  }

  if (!chain.includes(modelConfig.id)) return [modelConfig];
  const ordered = [modelConfig.id, ...chain.filter((id) => id !== modelConfig.id)];
  const candidates = ordered
    .map((id) => MODELS[id])
    .filter((m) => m && (!hasImage || m.supportsImage) && (!hasVideo || m.supportsVideo));
  return candidates.length ? candidates : [modelConfig];
}

/**
 * --- Gemini-direct helpers ---
 * API native Google format-nya beda total dari OpenRouter (contents/parts,
 * bukan messages/content; header x-goog-api-key, bukan Bearer token; role
 * "model" bukan "assistant"). Fungsi-fungsi ini menerjemahkan request KE
 * format Gemini, dan menerjemahkan respons stream-nya KEMBALI ke bentuk
 * OpenAI-style delta yang sama dipakai OpenRouter — supaya kode streaming
 * & frontend tidak perlu tahu bedanya sama sekali.
 */
function buildGeminiContents(clientMessages) {
  return clientMessages.map((m) => {
    const role = m.role === 'assistant' ? 'model' : 'user';
    const parts = [];
    if (m.content) parts.push({ text: m.content });
    for (const att of m.attachments || []) {
      if (att.type === 'image' && typeof att.dataUrl === 'string') {
        const match = att.dataUrl.match(/^data:([^;]+);base64,([\s\S]*)$/);
        if (match) parts.push({ inlineData: { mimeType: match[1], data: match[2] } });
      }
      // Video sengaja tidak dikirim lewat jalur ini — lihat validateAttachments (supportsVideo: false untuk provider google-direct).
    }
    return { role, parts: parts.length ? parts : [{ text: '' }] };
  });
}

function fetchGeminiDirect(candidate, clientMessages, reasoningEffort, signal) {
  const body = {
    contents: buildGeminiContents(clientMessages),
    systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
    generationConfig: { maxOutputTokens: candidate.maxOutputTokens },
  };
  if (reasoningEffort && reasoningEffort !== 'none') {
    body.generationConfig.thinkingConfig = { includeThoughts: true };
  }
  return fetch(`${GEMINI_BASE_URL}/${candidate.geminiModel}:streamGenerateContent?alt=sse`, {
    method: 'POST',
    headers: { 'x-goog-api-key': GEMINI_API_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  });
}

/** Baca stream SSE ala-Gemini dari `upstream`, tulis ulang ke `res` dalam bentuk delta OpenAI-style. */
async function pipeGeminiAsOpenAiStream(upstream, res) {
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
        const data = trimmed.slice(5).trim();
        if (!data) continue;
        let json;
        try { json = JSON.parse(data); } catch (_) { continue; }
        const parts = (json.candidates && json.candidates[0] && json.candidates[0].content && json.candidates[0].content.parts) || [];
        for (const part of parts) {
          if (!part.text) continue;
          const delta = part.thought ? { reasoning: part.text } : { content: part.text };
          res.write(`data: ${JSON.stringify({ choices: [{ delta }] })}\n\n`);
        }
      }
    }
  } catch (_) {
    // koneksi terputus di tengah jalan
  } finally {
    res.write('data: [DONE]\n\n');
    res.end();
  }
}

const MAX_MESSAGES_PER_REQUEST = 60;
const MAX_IMAGE_BYTES = 10 * 1024 * 1024; // 10MB
const MAX_VIDEO_BYTES = 30 * 1024 * 1024; // 30MB
const ALLOWED_IMAGE_MIME = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];
const ALLOWED_VIDEO_MIME = ['video/mp4', 'video/webm', 'video/quicktime'];

// ---------- Middleware ----------

app.use(
  cors({
    origin: process.env.ALLOWED_ORIGIN ? process.env.ALLOWED_ORIGIN.split(',') : true,
  })
);

// Body cukup besar untuk menampung gambar/video ter-encode base64
// (video 30MB mentah ≈ 40MB base64, ditambah histori percakapan).
app.use(express.json({ limit: '55mb' }));

// Header keamanan dasar (tanpa perlu dependency tambahan seperti helmet).
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  next();
});

app.use(express.static(path.join(__dirname, 'public')));

// ---------- Helper ----------

/** Ubah pesan dari frontend menjadi format "messages" ala OpenAI/OpenRouter. */
function buildOpenRouterMessages(clientMessages) {
  return clientMessages.map((m) => {
    // Hanya 'user'/'assistant' yang diteruskan — mencegah client menyusupkan
    // role 'system' tambahan untuk menimpa system prompt Roum AI.
    const role = m.role === 'assistant' ? 'assistant' : 'user';
    const attachments = Array.isArray(m.attachments) ? m.attachments : [];
    if (attachments.length === 0) {
      return { role, content: m.content || '' };
    }
    const parts = [];
    if (m.content) parts.push({ type: 'text', text: m.content });
    for (const att of attachments) {
      if (att.type === 'image') {
        parts.push({ type: 'image_url', image_url: { url: att.dataUrl } });
      } else if (att.type === 'video') {
        parts.push({ type: 'video_url', video_url: { url: att.dataUrl } });
      }
    }
    return { role, content: parts };
  });
}

/** Validasi lampiran terhadap model yang dipilih + batas ukuran/MIME. Return string error atau null. */
function validateAttachments(messages, modelConfig) {
  for (const m of messages) {
    const attachments = Array.isArray(m.attachments) ? m.attachments : [];
    for (const att of attachments) {
      if (!att || typeof att.dataUrl !== 'string' || !att.dataUrl.startsWith('data:')) {
        return 'Format lampiran tidak valid.';
      }
      if (att.type === 'video' && !modelConfig.supportsVideo) {
        return `Model "${modelConfig.label}" tidak mendukung input video. Pilih GLM-5.3 Flash atau Gemini 3 Pro, atau hapus lampiran video.`;
      }
      if (att.type === 'image' && !modelConfig.supportsImage) {
        return `Model "${modelConfig.label}" tidak mendukung input gambar.`;
      }
      if (att.type === 'image' && att.mime && !ALLOWED_IMAGE_MIME.includes(att.mime)) {
        return `Format gambar "${att.mime}" tidak didukung.`;
      }
      if (att.type === 'video' && att.mime && !ALLOWED_VIDEO_MIME.includes(att.mime)) {
        return `Format video "${att.mime}" tidak didukung.`;
      }
      const approxBytes = Math.floor((att.dataUrl.length - att.dataUrl.indexOf(',')) * 0.75);
      const limit = att.type === 'video' ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
      if (approxBytes > limit) {
        return `File "${att.name || ''}" terlalu besar (maks ${Math.floor(limit / (1024 * 1024))}MB).`;
      }
    }
  }
  return null;
}

// ---------- Routes ----------

app.get('/api/models', (req, res) => {
  // Sengaja HANYA mengekspos "Roum AI Pro" ke frontend — model-model asli di
  // balik layar (GLM, Nemotron, Gemini, dst — masih lengkap di objek MODELS di
  // atas dan tetap dipakai oleh buildFallbackCandidates) tidak ditampilkan ke user.
  res.json({
    models: [MODELS[DEFAULT_MODEL_ID]],
    default: DEFAULT_MODEL_ID,
    maxImageBytes: MAX_IMAGE_BYTES,
    maxVideoBytes: MAX_VIDEO_BYTES,
  });
});

app.get('/api/health', (req, res) => {
  res.json({
    ok: true,
    configured: Boolean(OPENROUTER_API_KEY) || Boolean(GEMINI_API_KEY),
    openrouter: Boolean(OPENROUTER_API_KEY),
    gemini: Boolean(GEMINI_API_KEY),
  });
});

const chatLimiter = rateLimit({
  windowMs: 5 * 60 * 1000,
  max: Number(process.env.RATE_LIMIT_MAX) || 40,
  standardHeaders: true,
  legacyHeaders: false,
  handler: (req, res) => {
    res.status(429).json({ error: 'Terlalu banyak permintaan. Coba lagi dalam beberapa menit.' });
  },
});

app.post('/api/chat', chatLimiter, async (req, res) => {
  if (!OPENROUTER_API_KEY && !GEMINI_API_KEY) {
    return res.status(500).json({
      error: 'Server belum dikonfigurasi: isi minimal salah satu dari OPENROUTER_API_KEY atau GEMINI_API_KEY di file .env, lalu restart server.',
    });
  }

  const { messages, model, reasoningEffort } = req.body || {};

  if (!Array.isArray(messages) || messages.length === 0) {
    return res.status(400).json({ error: 'Pesan tidak valid atau kosong.' });
  }
  if (messages.length > MAX_MESSAGES_PER_REQUEST) {
    return res.status(400).json({ error: 'Percakapan ini terlalu panjang untuk satu permintaan.' });
  }

  const modelConfig = MODELS[model] || MODELS[DEFAULT_MODEL_ID];

  const attachmentError = validateAttachments(messages, modelConfig);
  if (attachmentError) {
    return res.status(400).json({ error: attachmentError });
  }

  const candidates = buildFallbackCandidates(modelConfig, messages);
  const orMessages = [
    { role: 'system', content: SYSTEM_PROMPT },
    ...buildOpenRouterMessages(messages),
  ];

  // Semua controller yang lagi aktif (baik yang lagi race paralel maupun yang
  // sequential belakangan) dicatat di sini, supaya kalau CLIENT memutus koneksi
  // (tutup tab / klik Stop), semuanya langsung ikut berhenti sekaligus.
  const activeControllers = new Set();
  let clientClosed = false;
  req.on('close', () => {
    clientClosed = true;
    for (const c of activeControllers) c.abort();
  });

  /** Tembak SATU kandidat (OpenRouter atau Gemini-direct, tergantung provider-nya). */
  function attemptCandidate(candidate) {
    const controller = new AbortController();
    activeControllers.add(controller);
    const cleanup = () => activeControllers.delete(controller);

    let promise;
    if (candidate.provider === 'google') {
      promise = !GEMINI_API_KEY
        ? Promise.reject(Object.assign(new Error('GEMINI_API_KEY belum diisi.'), { roumStatus: 500 }))
        : fetchGeminiDirect(candidate, messages, reasoningEffort, controller.signal);
    } else if (!OPENROUTER_API_KEY) {
      promise = Promise.reject(Object.assign(new Error('OPENROUTER_API_KEY belum diisi.'), { roumStatus: 500 }));
    } else {
      const payload = {
        model: candidate.id,
        messages: orMessages,
        stream: true,
        max_tokens: candidate.maxOutputTokens,
      };
      if (reasoningEffort && reasoningEffort !== 'none') payload.reasoning = { effort: reasoningEffort };
      promise = fetch(OPENROUTER_URL, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${OPENROUTER_API_KEY}`,
          'Content-Type': 'application/json',
          'HTTP-Referer': SITE_URL,
          'X-Title': 'Roum AI',
        },
        body: JSON.stringify(payload),
        signal: controller.signal,
      });
    }
    promise.then(cleanup, cleanup);
    return { candidate, controller, promise };
  }

  /**
   * Tembak SEMUA kandidat di `group` SEKALIGUS (paralel, bukan gantian) — pakai
   * siapa pun yang pertama merespons sukses, kandidat lain di grup yang sama
   * langsung di-abort supaya tidak terus jalan sia-sia.
   */
  async function raceGroup(group) {
    if (group.length === 0) return { winner: null, failures: [] };
    const attempts = group.map((candidate) => attemptCandidate(candidate));
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

  const RACE_GROUP_SIZE = candidates.length; // tembak SEMUA kandidat yang cocok sekaligus (termasuk yang bisa gambar/video), bukan cuma sebagian
  let upstream = null;
  let usedModel = candidates[0];
  let lastStatus = 502;
  let lastDetail = '';

  const raceResult = await raceGroup(candidates.slice(0, RACE_GROUP_SIZE));
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
    // Grup pertama gagal semua — sisanya dicoba satu-satu sebagai cadangan terakhir.
    for (const candidate of candidates.slice(RACE_GROUP_SIZE)) {
      if (clientClosed) return res.end();
      let attempt;
      try {
        attempt = await attemptCandidate(candidate).promise;
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
        /* respons error bukan JSON, abaikan */
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

  // Beri tahu frontend model mana yang benar-benar merespons (bisa beda dari yang
  // diminta kalau terjadi fallback), supaya bisa ditampilkan sebagai notifikasi kecil.
  // Header ini cuma dikirim kalau BENERAN terjadi fallback (model pertama gagal),
  // bukan setiap kali "Auto" berhasil di percobaan pertama — supaya notifikasi di
  // frontend tidak muncul salah waktu.
  if (usedModel.id !== candidates[0].id) {
    res.setHeader('X-Roum-Model-Used', usedModel.id);
  }

  // Mulai stream Server-Sent Events ke frontend.
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  if (typeof res.flushHeaders === 'function') res.flushHeaders();

  // Gemini punya format chunk sendiri — diterjemahkan ke bentuk delta OpenAI-style
  // dulu sebelum dikirim ke frontend, supaya app.js tidak perlu tahu bedanya.
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
        const data = trimmed.slice(5).trim();
        res.write(`data: ${data}\n\n`);
      }
    }
  } catch (_) {
    // koneksi terputus di tengah jalan — biarkan, akan di-handle finally
  } finally {
    res.end();
  }
});

app.use((req, res) => {
  res.status(404).json({ error: 'Endpoint tidak ditemukan.' });
});

// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  if (err && err.type === 'entity.too.large') {
    return res.status(413).json({ error: 'Ukuran pesan/lampiran terlalu besar.' });
  }
  console.error(err);
  res.status(500).json({ error: 'Terjadi kesalahan pada server.' });
});

app.listen(PORT, () => {
  console.log(`\n🚀 Roum AI jalan di http://localhost:${PORT}\n`);
  if (!OPENROUTER_API_KEY) {
    console.warn('⚠️  OPENROUTER_API_KEY belum diisi di .env — /api/chat akan gagal sampai key diisi.\n');
  }
});
