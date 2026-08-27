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
    id: 'stealth/ox-alpha',
    label: 'Ox Alpha',
    tag: 'Gratis · Preview',
    description: 'Model preview gratis dari provider anonim, konteks 1M token, reasoning. Bisa berubah/hilang sewaktu-waktu.',
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
];

const DEFAULT_MODEL_ID = 'stealth/ox-alpha';

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
  return 'You are Roum AI, a sharp and friendly assistant. Format answers with Markdown, always use fenced code blocks with a language tag for code, and be concise but thorough.';
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
};
