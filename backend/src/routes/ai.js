const ai = require('../ai/provider');
const { sendJson, readJsonBody } = require('../lib/router');

function register(router) {
  router.post('/api/ai/titles', async (req, res) => {
    const { topic } = await readJsonBody(req);
    if (!topic) return sendJson(res, 400, { error: 'Topik/transcript wajib diisi.' });
    if (!ai.isConfigured()) return sendJson(res, 200, demoTitles());
    try {
      const raw = await ai.generateText(
        'Kamu adalah asisten SEO & judul viral untuk kreator video. Balas HANYA dengan JSON, tanpa markdown.',
        `Berdasarkan topik/isi video berikut, buatkan:\n- 10 judul YouTube\n- 10 judul Shorts/TikTok\n- 5 caption Instagram\n- 3 deskripsi video (2-3 kalimat)\n\nTopik/isi: "${topic}"\n\nBalas format JSON: {"youtubeTitles": string[], "shortsTitles": string[], "instagramCaptions": string[], "descriptions": string[]}`,
        { maxTokens: 2048 }
      );
      sendJson(res, 200, JSON.parse(raw.replace(/```json|```/g, '').trim()));
    } catch (err) {
      sendJson(res, 502, { error: 'AI gagal membuat judul: ' + err.message });
    }
  });

  router.post('/api/ai/hashtags', async (req, res) => {
    const { topic } = await readJsonBody(req);
    if (!topic) return sendJson(res, 400, { error: 'Topik wajib diisi.' });
    if (!ai.isConfigured()) return sendJson(res, 200, { hashtags: ['#fyp', '#viral', '#contentcreator', '#shorts', '#trending'] });
    try {
      const raw = await ai.generateText(
        'Kamu adalah asisten hashtag untuk kreator video sosial media. Balas HANYA dengan JSON array of string, tanpa markdown, tanpa penjelasan.',
        `Buatkan 15 hashtag relevan (campuran hashtag besar & niche) untuk video dengan topik: "${topic}". Format: ["#tag1", "#tag2", ...]`,
        { maxTokens: 512 }
      );
      const hashtags = JSON.parse(raw.replace(/```json|```/g, '').trim());
      sendJson(res, 200, { hashtags });
    } catch (err) {
      sendJson(res, 502, { error: 'AI gagal membuat hashtag: ' + err.message });
    }
  });

  router.post('/api/ai/script', async (req, res) => {
    const { topic, tone } = await readJsonBody(req);
    if (!topic) return sendJson(res, 400, { error: 'Topik wajib diisi.' });
    if (!ai.isConfigured()) return sendJson(res, 200, demoScript(topic));
    try {
      const raw = await ai.generateText(
        `Kamu adalah penulis skrip video profesional. Gaya tone: ${tone || 'casual'}. Balas HANYA dengan JSON, tanpa markdown.`,
        `Buatkan skrip video pendek untuk topik: "${topic}". Format JSON: {"hook": string, "introduction": string, "mainPoints": string[], "cta": string}`,
        { maxTokens: 1536 }
      );
      sendJson(res, 200, JSON.parse(raw.replace(/```json|```/g, '').trim()));
    } catch (err) {
      sendJson(res, 502, { error: 'AI gagal membuat skrip: ' + err.message });
    }
  });

  router.post('/api/ai/repurpose', async (req, res) => {
    const { topic } = await readJsonBody(req);
    if (!topic) return sendJson(res, 400, { error: 'Topik/transcript wajib diisi.' });
    if (!ai.isConfigured()) return sendJson(res, 200, { quoteCards: ['"Setiap kreator hebat mulai dari satu video pertama."'], youtubeDescription: 'Deskripsi contoh — isi AI key untuk hasil asli.', socialPosts: ['Contoh post — isi AI key untuk hasil asli.'] });
    try {
      const raw = await ai.generateText(
        'Kamu adalah asisten content repurposing. Balas HANYA JSON, tanpa markdown.',
        `Dari video dengan topik/isi berikut, buatkan: 3 quote card (kutipan singkat menarik), 1 deskripsi YouTube (SEO friendly), dan 3 post untuk Threads/X. Topik/isi: "${topic}"\n\nFormat: {"quoteCards": string[], "youtubeDescription": string, "socialPosts": string[]}`,
        { maxTokens: 1536 }
      );
      sendJson(res, 200, JSON.parse(raw.replace(/```json|```/g, '').trim()));
    } catch (err) {
      sendJson(res, 502, { error: 'AI gagal repurpose content: ' + err.message });
    }
  });

  router.get('/api/ai/status', async (req, res) => {
    sendJson(res, 200, { configured: ai.isConfigured() });
  });
}

function demoTitles() {
  return {
    youtubeTitles: ['5 Kesalahan Fatal Kreator Pemula (No. 3 Sering Diabaikan)', 'Cara Saya Dapat 1 Juta Views dalam 30 Hari', 'Rahasia Algoritma yang Jarang Dibahas'],
    shortsTitles: ['Ini yang bikin video lo gak viral 👀', 'Trik 5 detik pertama yang wajib dicoba', 'POV: Lo baru tau ini'],
    instagramCaptions: ['Yang belum tau, wajib nonton sampe abis ✨', 'Save dulu, praktekin nanti 📌'],
    descriptions: ['Di video ini kita bahas tuntas strategi yang jarang diomongin kreator lain.'],
  };
}
function demoScript(topic) {
  return {
    hook: `Tau gak kenapa kebanyakan orang gagal di "${topic || 'topik ini'}"?`,
    introduction: 'Di video ini gua bakal breakdown step-by-step tanpa basa-basi.',
    mainPoints: ['Poin pertama yang paling sering dilewatkan orang.', 'Poin kedua yang bikin hasil beda drastis.', 'Poin ketiga sebagai penutup yang kuat.'],
    cta: 'Kalau ini ngebantu, follow buat konten kayak gini tiap minggu.',
  };
}

module.exports = { register };
