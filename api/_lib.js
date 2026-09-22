/**
 * Helper bersama untuk Vercel Functions Vidzly — AI provider (sama seperti
 * backend/src/ai/provider.js) + data demo statis (untuk endpoint read-only
 * yang tetap harus tampil walau tanpa server Node persisten).
 */
const OPENROUTER_URL = 'https://openrouter.ai/api/v1/chat/completions';
const GEMINI_BASE_URL = 'https://generativelanguage.googleapis.com/v1beta/models';
const TEXT_MODEL_CHAIN = ['z-ai/glm-5.2:free', 'nvidia/nemotron-3-ultra-550b-a55b:free', 'minimax/minimax-m3:free'];

function isConfigured() {
  return Boolean(process.env.OPENROUTER_API_KEY || process.env.GEMINI_API_KEY);
}

async function raceOpenRouter(modelIds, messages, maxTokens = 2048) {
  const key = process.env.OPENROUTER_API_KEY;
  if (!key) throw new Error('OPENROUTER_API_KEY belum diisi.');
  const controllers = modelIds.map(() => new AbortController());
  const failures = [];
  let pending = modelIds.length;
  return new Promise((resolve, reject) => {
    modelIds.forEach((modelId, i) => {
      fetch(OPENROUTER_URL, {
        method: 'POST',
        headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json', 'X-Title': 'Vidzly AI Creator Studio' },
        body: JSON.stringify({ model: modelId, messages, max_tokens: maxTokens, stream: false }),
        signal: controllers[i].signal,
      })
        .then(async (res) => {
          if (!res.ok) throw new Error(`status ${res.status}`);
          const json = await res.json();
          const text = json?.choices?.[0]?.message?.content;
          if (!text) throw new Error('respons kosong');
          controllers.forEach((c, j) => { if (j !== i) c.abort(); });
          resolve(text);
        })
        .catch((err) => {
          if (err.name === 'AbortError') { pending--; if (pending === 0) reject(new Error(failures.join('; '))); return; }
          failures.push(`${modelId}: ${err.message}`);
          pending--;
          if (pending === 0) reject(new Error(failures.join('; ')));
        });
    });
  });
}

async function generateText(systemPrompt, userPrompt, maxTokens = 2048) {
  if (process.env.OPENROUTER_API_KEY) {
    try { return await raceOpenRouter(TEXT_MODEL_CHAIN, [{ role: 'system', content: systemPrompt }, { role: 'user', content: userPrompt }], maxTokens); }
    catch (err) { if (!process.env.GEMINI_API_KEY) throw err; }
  }
  if (process.env.GEMINI_API_KEY) {
    const res = await fetch(`${GEMINI_BASE_URL}/gemini-flash-latest:generateContent`, {
      method: 'POST',
      headers: { 'x-goog-api-key': process.env.GEMINI_API_KEY, 'Content-Type': 'application/json' },
      body: JSON.stringify({ contents: [{ role: 'user', parts: [{ text: userPrompt }] }], systemInstruction: { parts: [{ text: systemPrompt }] }, generationConfig: { maxOutputTokens: maxTokens } }),
    });
    if (!res.ok) throw new Error(`Gemini gagal (${res.status})`);
    const json = await res.json();
    return json.candidates?.[0]?.content?.parts?.map((p) => p.text).filter(Boolean).join('') || '';
  }
  throw new Error('Tidak ada AI provider dikonfigurasi (isi OPENROUTER_API_KEY atau GEMINI_API_KEY di Environment Variables Vercel).');
}

function parseJsonLoose(raw) {
  return JSON.parse(String(raw).replace(/```json|```/g, '').trim());
}

const DEMO = {
  projects: [
    { id: 'demo_proj_podcast', name: 'Podcast Ep. 42 — Growth Marketing', sourceType: 'upload', durationSec: 2415, status: 'ready', thumbnail: '/assets/demo/podcast-thumb.svg', createdAt: '2026-09-01T00:00:00.000Z', updatedAt: '2026-09-01T00:00:00.000Z' },
    { id: 'demo_proj_gaming', name: 'Ranked Valorant — Clutch Round', sourceType: 'url', durationSec: 1830, status: 'ready', thumbnail: '/assets/demo/gaming-thumb.svg', createdAt: '2026-09-01T00:00:00.000Z', updatedAt: '2026-09-01T00:00:00.000Z' },
  ],
  clips: {
    demo_proj_podcast: [
      { id: 'demo_clip_1', title: '"Ini yang bikin 90% startup gagal scaling"', duration: 42, viralScore: 94, category: 'best', transcriptPreview: '...jadi kesalahan paling umum itu scaling sebelum product-market fit beneran ketemu...', thumbnail: '/assets/demo/clip1.svg', aspectRatio: '9:16' },
      { id: 'demo_clip_2', title: 'Rahasia CAC turun 60% dalam 3 bulan', duration: 58, viralScore: 88, category: 'educational', transcriptPreview: '...kita ubah funnel-nya total, hasilnya CAC kita turun signifikan...', thumbnail: '/assets/demo/clip2.svg', aspectRatio: '9:16' },
    ],
    demo_proj_gaming: [
      { id: 'demo_clip_4', title: 'CLUTCH 1v4 di round terakhir!!', duration: 35, viralScore: 97, category: 'trending', transcriptPreview: '[teriakan] GAK NYANGKA GUA — round paling gila musim ini...', thumbnail: '/assets/demo/clip4.svg', aspectRatio: '9:16' },
    ],
  },
  templates: [
    { id: 'tpl_podcast', name: 'Podcast', description: 'Layout waveform + judul besar, cocok buat clip ngobrol.', category: 'podcast' },
    { id: 'tpl_gaming', name: 'Gaming', description: 'Caption bold + efek kill-feed, cocok buat highlight game.', category: 'gaming' },
    { id: 'tpl_education', name: 'Education', description: 'Caption jelas + poin-poin, cocok buat konten edukasi.', category: 'education' },
  ],
  analytics: { totalViews: 284500, clipsCreated: 47, avgClipLength: 36, exportCount: 31, viewsByDay: [12000,15400,9800,22100,31000,28700,41200], isDemo: true, note: 'Data contoh (deployment Vercel ini read-only demo) — untuk data & upload video asli, jalankan backend/ penuh (lihat README).' },
  brandKit: { name: 'Brand Kit Utama', colors: ['#7C5CFF','#22D3EE','#0B0B12'], fontHeading: 'Space Grotesk', captionStyle: 'bold' },
};

module.exports = { isConfigured, generateText, parseJsonLoose, DEMO };
