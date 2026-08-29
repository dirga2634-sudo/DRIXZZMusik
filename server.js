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

/**
 * Katalog model yang diizinkan (allowlist). Frontend hanya boleh memilih salah
 * satu key di bawah ini — server MENOLAK model string sembarangan dari client
 * supaya tidak disalahgunakan untuk memanggil model lain yang tidak diinginkan.
 * Untuk menambah/mengganti model, cukup edit objek ini (pakai slug resmi dari
 * https://openrouter.ai/models).
 */
const MODELS = {
  'z-ai/glm-5.2:free': {
    id: 'z-ai/glm-5.2:free',
    label: 'GLM 5.2 (Free)',
    tag: 'Gratis · Coding & chat',
    description:
      'Model gratis dengan kualitas terbaik untuk coding sekaligus obrolan biasa di antara model gratis OpenRouter — gaya jawabannya natural, bukan cuma bisa coding. Teks saja, tidak bisa menganalisis gambar/video (pakai Nemotron Nano Omni untuk itu).',
    supportsImage: false,
    supportsVideo: false,
    maxOutputTokens: 8192,
  },
  'nvidia/nemotron-3-super-120b-a12b:free': {
    id: 'nvidia/nemotron-3-super-120b-a12b:free',
    label: 'Nemotron 3 Super (Free)',
    tag: 'Gratis · Reasoning besar',
    description:
      'Model gratis NVIDIA yang lebih besar dari Nemotron Nano Omni — konteks 1M token, kuat di reasoning/coding/agentic. Teks saja, tidak bisa menganalisis gambar/video.',
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
};

const DEFAULT_MODEL_ID =
  process.env.DEFAULT_MODEL && MODELS[process.env.DEFAULT_MODEL]
    ? process.env.DEFAULT_MODEL
    : 'z-ai/glm-5.2:free';

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
  'nvidia/nemotron-3-super-120b-a12b:free',
  'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',
];

function buildFallbackCandidates(modelConfig, messages) {
  if (!FREE_FALLBACK_CHAIN.includes(modelConfig.id)) return [modelConfig];
  const hasImage = messages.some((m) => (m.attachments || []).some((a) => a.type === 'image'));
  const hasVideo = messages.some((m) => (m.attachments || []).some((a) => a.type === 'video'));
  const ordered = [modelConfig.id, ...FREE_FALLBACK_CHAIN.filter((id) => id !== modelConfig.id)];
  const candidates = ordered
    .map((id) => MODELS[id])
    .filter((m) => m && (!hasImage || m.supportsImage) && (!hasVideo || m.supportsVideo));
  return candidates.length ? candidates : [modelConfig];
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
  res.json({
    models: Object.values(MODELS),
    default: DEFAULT_MODEL_ID,
    maxImageBytes: MAX_IMAGE_BYTES,
    maxVideoBytes: MAX_VIDEO_BYTES,
  });
});

app.get('/api/health', (req, res) => {
  res.json({ ok: true, configured: Boolean(OPENROUTER_API_KEY) });
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
  if (!OPENROUTER_API_KEY) {
    return res.status(500).json({
      error: 'Server belum dikonfigurasi: OPENROUTER_API_KEY belum diisi di file .env. Isi dulu lalu restart server.',
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
    {
      role: 'system',
      content:
        'You are Roum AI, a sharp and friendly assistant. Format answers with Markdown, always use fenced code blocks with a language tag for code, and be concise but thorough.',
    },
    ...buildOpenRouterMessages(messages),
  ];

  const controller = new AbortController();
  req.on('close', () => controller.abort());

  let upstream = null;
  let usedModel = candidates[0];
  let lastStatus = 502;
  let lastDetail = '';

  for (let i = 0; i < candidates.length; i++) {
    const candidate = candidates[i];
    const payload = {
      model: candidate.id,
      messages: orMessages,
      stream: true,
      max_tokens: candidate.maxOutputTokens,
    };
    if (reasoningEffort && reasoningEffort !== 'none') {
      payload.reasoning = { effort: reasoningEffort };
    }

    let attempt;
    try {
      attempt = await fetch(OPENROUTER_URL, {
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
    } catch (err) {
      if (err.name === 'AbortError') return res.end();
      lastStatus = 502;
      lastDetail = 'Tidak bisa menghubungi OpenRouter. Periksa koneksi internet server.';
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
    // 429 (rate limit) / 404 (model lagi tidak tersedia) → layak dicoba model gratis berikutnya.
    const isRetryable = attempt.status === 429 || attempt.status === 404;
    if (!isRetryable) break; // error lain (401/402/dst) tidak akan hilang dengan ganti model, langsung berhenti
  }

  if (!upstream) {
    const friendly = {
      401: 'API key OpenRouter tidak valid atau ditolak.',
      402: 'Kredit OpenRouter tidak cukup untuk model ini.',
      404: 'Model tidak ditemukan atau sedang tidak tersedia.',
      429:
        candidates.length > 1
          ? 'Semua model gratis sedang penuh, coba lagi sebentar lagi.'
          : 'Rate limit OpenRouter tercapai, coba lagi sebentar lagi.',
    };
    const msg = friendly[lastStatus] || (lastDetail || `OpenRouter mengembalikan error (status ${lastStatus}).`);
    const status = lastStatus >= 400 && lastStatus < 600 ? lastStatus : 502;
    return res.status(status).json({ error: lastDetail && friendly[lastStatus] ? `${msg} ${lastDetail}` : msg });
  }

  // Beri tahu frontend model mana yang benar-benar merespons (bisa beda dari yang
  // diminta kalau terjadi fallback), supaya bisa ditampilkan sebagai notifikasi kecil.
  res.setHeader('X-Roum-Model-Used', usedModel.id);

  // Mulai stream Server-Sent Events ke frontend, teruskan chunk apa adanya.
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
