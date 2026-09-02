/**
 * Konfigurasi & helper khusus deployment Vercel — dipakai oleh file-file di /api.
 * Sengaja DIPISAH dari server.js (bukan di-share) karena Vercel Functions punya
 * batasan berbeda dari Node/Express biasa:
 *   - Request body maksimal 4.5MB (keras, tidak bisa dinaikkan lewat konfigurasi)
 *   - Tidak ada proses yang terus hidup (setiap request = eksekusi baru)
 * Makanya di sini video dimatikan total dan limit gambar diperkecil jauh
 * dibanding server.js. Lihat README bagian "Deploy ke Vercel".
 */

const OPENROUTER_URL = 'https://openrouter.ai/api/v1/chat/completions';
const ALLOWED_IMAGE_MIME = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];

// Base64 menambah ~33% ukuran; limit dijaga jauh di bawah 4.5MB supaya masih
// ada ruang untuk teks histori percakapan + struktur JSON di request yang sama.
const MAX_IMAGE_BYTES = 2 * 1024 * 1024; // 2MB mentah (~2.7MB setelah base64)

const VIDEO_DISABLED_REASON =
  'Video dimatikan di deployment Vercel ini: batas request Vercel Functions (4.5MB) tidak cukup untuk file video. Pakai deployment Node.js biasa (Render/VPS/Termux lokal) untuk fitur video.';

const MODELS = [
  {
    id: 'auto:free',
    label: 'Roum AI Pro',
    tag: 'Model utama',
    description: 'Model utama Roum AI — cepat, cerdas, dan selalu berusaha memberi jawaban terbaik untuk pertanyaan, coding, maupun analisis gambar.',
    supportsImage: true,
    supportsVideo: false,
    maxOutputTokens: 8192,
  },
  {
    id: 'z-ai/glm-5.2:free',
    label: 'GLM 5.2 (Free)',
    tag: 'Gratis · Coding & chat',
    description: 'Model gratis dengan kualitas terbaik untuk coding sekaligus obrolan biasa di antara model gratis OpenRouter. Teks saja, tidak bisa menganalisis gambar/video.',
    supportsImage: false,
    supportsVideo: false,
    maxOutputTokens: 8192,
  },
  {
    id: 'nvidia/nemotron-3-ultra-550b-a55b:free',
    label: 'Nemotron 3 Ultra (Free)',
    tag: 'Gratis · Paling populer',
    description: 'Model gratis paling banyak dipakai di OpenRouter — frontier reasoning NVIDIA, konteks 1M token. Teks saja.',
    supportsImage: false,
    supportsVideo: false,
    maxOutputTokens: 16384,
  },
  {
    id: 'minimax/minimax-m3:free',
    label: 'MiniMax M3 (Free)',
    tag: 'Gratis · Gambar',
    description: 'Model multimodal gratis dari MiniMax, konteks ~1M token, salah satu model gratis paling populer di OpenRouter. (Video dimatikan di deployment Vercel ini karena limit ukuran request — modelnya sendiri sebenarnya mendukung video.)',
    supportsImage: true,
    supportsVideo: false,
    maxOutputTokens: 16384,
  },
  {
    id: 'thinkingmachines/inkling:free',
    label: 'Inkling (Free)',
    tag: 'Gratis · Gambar',
    description: 'Model multimodal gratis dari Thinking Machines Lab, memahami gambar & audio, konteks ~1M token.',
    supportsImage: true,
    supportsVideo: false,
    maxOutputTokens: 16384,
  },
  {
    id: 'nvidia/nemotron-3-super-120b-a12b:free',
    label: 'Nemotron 3 Super (Free)',
    tag: 'Gratis · Reasoning besar',
    description: 'Model gratis NVIDIA yang lebih besar dari Nemotron Nano Omni, konteks 262K token, kuat di reasoning/coding/agentic. Teks saja.',
    supportsImage: false,
    supportsVideo: false,
    maxOutputTokens: 16384,
  },
  {
    id: 'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',
    label: 'Nemotron Nano Omni',
    tag: 'Gratis · Gambar',
    description: 'Model open-source NVIDIA yang benar-benar gratis, kualitas coding/obrolan di bawah GLM 5.2 tapi mendukung analisis gambar. (Video tetap dimatikan di deployment Vercel ini karena limit ukuran request.)',
    supportsImage: true,
    supportsVideo: false,
    maxOutputTokens: 8192,
  },
  {
    id: 'z-ai/glm-5.3-flash',
    label: 'GLM-5.3 Flash',
    tag: 'Murah & cepat',
    description: 'Sebelumnya dikenal sebagai "Ox Alpha" — kini resmi dirilis Z.ai, konteks 1M token, berbayar tapi sangat murah. (Video dimatikan di deployment Vercel ini — modelnya sendiri sebenarnya mendukung video.)',
    supportsImage: true,
    supportsVideo: false,
    maxOutputTokens: 131072,
  },
  {
    id: 'anthropic/claude-sonnet-5',
    label: 'Claude Sonnet 5',
    tag: 'Reasoning kuat',
    description: 'Reasoning dan analisis gambar/file terbaik untuk coding & pekerjaan kompleks. Konteks 1M token.',
    supportsImage: true,
    supportsVideo: false,
    maxOutputTokens: 64000,
  },
  {
    id: 'google/gemini-3-pro-preview',
    label: 'Gemini 3 Pro',
    tag: 'Multimodal terkuat',
    description: 'Paling kuat untuk analisis gambar & dokumen sekaligus. Konteks 1M token.',
    supportsImage: true,
    supportsVideo: false,
    maxOutputTokens: 65536,
  },
  {
    id: 'google-direct/gemini-flash-latest',
    label: 'Gemini Flash (Free · langsung Google)',
    tag: 'Gratis · Independen',
    description: 'Gratis lewat kuota API Google AI Studio kamu sendiri — tidak lewat OpenRouter, tetap jalan walau semua model gratis OpenRouter penuh. Butuh GEMINI_API_KEY sendiri di Environment Variables Vercel.',
    supportsImage: true,
    supportsVideo: false,
    maxOutputTokens: 16384,
    provider: 'google',
    geminiModel: 'gemini-flash-latest',
  },
];

const GEMINI_BASE_URL = 'https://generativelanguage.googleapis.com/v1beta/models';
const GEMINI_DIRECT_ID = 'google-direct/gemini-flash-latest';

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
    }
    return { role, parts: parts.length ? parts : [{ text: '' }] };
  });
}

function fetchGeminiDirect(candidate, clientMessages, reasoningEffort, signal, apiKey) {
  const body = {
    contents: buildGeminiContents(clientMessages),
    systemInstruction: { parts: [{ text: systemPrompt() }] },
    generationConfig: { maxOutputTokens: candidate.maxOutputTokens },
  };
  if (reasoningEffort && reasoningEffort !== 'none') {
    body.generationConfig.thinkingConfig = { includeThoughts: true };
  }
  return fetch(`${GEMINI_BASE_URL}/${candidate.geminiModel}:streamGenerateContent?alt=sse`, {
    method: 'POST',
    headers: { 'x-goog-api-key': apiKey, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  });
}

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
    /* koneksi terputus */
  } finally {
    res.write('data: [DONE]\n\n');
    res.end();
  }
}

const DEFAULT_MODEL_ID = 'auto:free';

// Model gratis di OpenRouter dipakai banyak orang sekaligus, jadi lebih sering
// kena rate limit. Kalau model yang dipilih ada di daftar ini, server otomatis
// coba model gratis lain di sini sebelum menyerah. Model berbayar tidak di-fallback.
const FREE_FALLBACK_CHAIN = [
  'z-ai/glm-5.2:free',
  'nvidia/nemotron-3-ultra-550b-a55b:free',
  'minimax/minimax-m3:free',
  'nvidia/nemotron-3-super-120b-a12b:free',
  'thinkingmachines/inkling:free',
  'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',
];

function buildFallbackCandidates(modelConfig, messages) {
  const hasImage = messages.some((m) => (m.attachments || []).some((a) => a.type === 'image'));
  const chain = process.env.GEMINI_API_KEY ? [...FREE_FALLBACK_CHAIN, GEMINI_DIRECT_ID] : FREE_FALLBACK_CHAIN;
  if (modelConfig.id === 'auto:free') {
    const candidates = chain.map((id) => getModel(id)).filter((m) => m && (!hasImage || m.supportsImage));
    return candidates.length ? candidates : [getModel(chain[0])];
  }
  if (!chain.includes(modelConfig.id)) return [modelConfig];
  const ordered = [modelConfig.id, ...chain.filter((id) => id !== modelConfig.id)];
  const candidates = ordered.map((id) => getModel(id)).filter((m) => m && (!hasImage || m.supportsImage));
  return candidates.length ? candidates : [modelConfig];
}

function getModel(id) {
  return MODELS.find((m) => m.id === id) || MODELS.find((m) => m.id === DEFAULT_MODEL_ID);
}

function buildOpenRouterMessages(clientMessages) {
  return clientMessages.map((m) => {
    const role = m.role === 'assistant' ? 'assistant' : 'user';
    const attachments = Array.isArray(m.attachments) ? m.attachments : [];
    if (attachments.length === 0) return { role, content: m.content || '' };
    const parts = [];
    if (m.content) parts.push({ type: 'text', text: m.content });
    for (const att of attachments) {
      if (att.type === 'image') parts.push({ type: 'image_url', image_url: { url: att.dataUrl } });
      // video sudah ditolak lebih dulu oleh validateAttachments — tidak pernah sampai sini.
    }
    return { role, content: parts };
  });
}

function validateAttachments(messages, modelConfig) {
  for (const m of messages) {
    const attachments = Array.isArray(m.attachments) ? m.attachments : [];
    for (const att of attachments) {
      if (!att || typeof att.dataUrl !== 'string' || !att.dataUrl.startsWith('data:')) {
        return 'Format lampiran tidak valid.';
      }
      if (att.type === 'video') return VIDEO_DISABLED_REASON;
      if (!modelConfig.supportsImage) return `Model "${modelConfig.label}" tidak mendukung input gambar.`;
      if (att.mime && !ALLOWED_IMAGE_MIME.includes(att.mime)) return `Format gambar "${att.mime}" tidak didukung.`;
      const approxBytes = Math.floor((att.dataUrl.length - att.dataUrl.indexOf(',')) * 0.75);
      if (approxBytes > MAX_IMAGE_BYTES) {
        return `File "${att.name || ''}" terlalu besar (maks ${Math.floor(MAX_IMAGE_BYTES / (1024 * 1024))}MB di deployment Vercel).`;
      }
    }
  }
  return null;
}

function systemPrompt() {
  return (
    'You are Roum AI, a helpful assistant built for this app — that is your one and only identity. ' +
    'If asked who made you, what company is behind you, what model or AI you are, or anything about your origin/architecture, ' +
    'always simply say you are Roum AI. Never mention OpenRouter, or any underlying AI provider, company, or model name ' +
    '(e.g. Google, NVIDIA, Anthropic, Z.ai, MiniMax, GLM, Gemini, Nemotron, Claude, GPT) — those are internal implementation ' +
    'details the user does not see. Format answers with Markdown, always use fenced code blocks with a language tag for code, ' +
    'and be concise but thorough.'
  );
}

module.exports = {
  OPENROUTER_URL,
  MODELS,
  DEFAULT_MODEL_ID,
  MAX_IMAGE_BYTES,
  VIDEO_DISABLED_REASON,
  getModel,
  buildOpenRouterMessages,
  validateAttachments,
  systemPrompt,
  buildFallbackCandidates,
  fetchGeminiDirect,
  pipeGeminiAsOpenAiStream,
};
