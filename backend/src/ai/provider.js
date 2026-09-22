/**
 * AI Provider abstraction untuk Vidzly.
 * Sama filosofinya dengan Roum AI: OpenRouter (banyak model gratis, race+fallback
 * biar tetap andal) + Gemini langsung (kuota independen, dukung input gambar
 * untuk analisis visual momen video). API key HANYA pernah dipakai di server ini.
 */
const OPENROUTER_URL = 'https://openrouter.ai/api/v1/chat/completions';
const GEMINI_BASE_URL = 'https://generativelanguage.googleapis.com/v1beta/models';

const OPENROUTER_API_KEY = process.env.OPENROUTER_API_KEY || '';
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || '';

const TEXT_MODEL_CHAIN = [
  'z-ai/glm-5.2:free',
  'nvidia/nemotron-3-ultra-550b-a55b:free',
  'minimax/minimax-m3:free',
];
const VISION_MODEL_CHAIN = [
  'minimax/minimax-m3:free',
  'thinkingmachines/inkling:free',
  'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',
];

function isConfigured() {
  return Boolean(OPENROUTER_API_KEY || GEMINI_API_KEY);
}

/** Tembak beberapa model OpenRouter SEKALIGUS, pakai yang pertama sukses. */
async function raceOpenRouter(modelIds, messages, { maxTokens = 2048, signal } = {}) {
  if (!OPENROUTER_API_KEY) throw new Error('OPENROUTER_API_KEY belum diisi.');
  const controllers = modelIds.map(() => new AbortController());
  const failures = [];
  let pending = modelIds.length;

  return new Promise((resolve, reject) => {
    modelIds.forEach((modelId, i) => {
      fetch(OPENROUTER_URL, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${OPENROUTER_API_KEY}`,
          'Content-Type': 'application/json',
          'X-Title': 'Vidzly AI Creator Studio',
        },
        body: JSON.stringify({ model: modelId, messages, max_tokens: maxTokens, stream: false }),
        signal: controllers[i].signal,
      })
        .then(async (res) => {
          if (!res.ok) throw new Error(`status ${res.status}`);
          const json = await res.json();
          const text = json && json.choices && json.choices[0] && json.choices[0].message && json.choices[0].message.content;
          if (!text) throw new Error('respons kosong');
          controllers.forEach((c, j) => { if (j !== i) c.abort(); });
          resolve({ text, model: modelId });
        })
        .catch((err) => {
          if (err.name === 'AbortError') { pending--; if (pending === 0) reject(new Error('Semua model AI gagal: ' + failures.join('; '))); return; }
          failures.push(`${modelId}: ${err.message}`);
          pending--;
          if (pending === 0) reject(new Error('Semua model AI gagal: ' + failures.join('; ')));
        });
      if (signal) signal.addEventListener('abort', () => controllers[i].abort());
    });
  });
}

async function callGeminiVision(promptText, imageBase64List, { maxTokens = 2048 } = {}) {
  if (!GEMINI_API_KEY) throw new Error('GEMINI_API_KEY belum diisi.');
  const parts = [{ text: promptText }, ...imageBase64List.map((b64) => ({ inlineData: { mimeType: 'image/jpeg', data: b64 } }))];
  const res = await fetch(`${GEMINI_BASE_URL}/gemini-flash-latest:generateContent`, {
    method: 'POST',
    headers: { 'x-goog-api-key': GEMINI_API_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify({ contents: [{ role: 'user', parts }], generationConfig: { maxOutputTokens: maxTokens } }),
  });
  if (!res.ok) {
    const errText = await res.text().catch(() => '');
    throw new Error(`Gemini vision gagal (${res.status}): ${errText.slice(0, 300)}`);
  }
  const json = await res.json();
  const text = json && json.candidates && json.candidates[0] && json.candidates[0].content && json.candidates[0].content.parts
    ?.map((p) => p.text).filter(Boolean).join('') || '';
  if (!text) throw new Error('Gemini vision: respons kosong.');
  return text;
}

async function callGeminiAudio(promptText, audioBase64, mimeType = 'audio/mpeg') {
  if (!GEMINI_API_KEY) throw new Error('GEMINI_API_KEY belum diisi.');
  const res = await fetch(`${GEMINI_BASE_URL}/gemini-flash-latest:generateContent`, {
    method: 'POST',
    headers: { 'x-goog-api-key': GEMINI_API_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      contents: [{ role: 'user', parts: [{ text: promptText }, { inlineData: { mimeType, data: audioBase64 } }] }],
      generationConfig: { maxOutputTokens: 8192 },
    }),
  });
  if (!res.ok) {
    const errText = await res.text().catch(() => '');
    throw new Error(`Gemini audio gagal (${res.status}): ${errText.slice(0, 300)}`);
  }
  const json = await res.json();
  const text = json && json.candidates && json.candidates[0] && json.candidates[0].content && json.candidates[0].content.parts
    ?.map((p) => p.text).filter(Boolean).join('') || '';
  return text;
}

/** Generate teks umum (judul, caption, hashtag, script) — coba OpenRouter dulu, fallback Gemini. */
async function generateText(systemPrompt, userPrompt, opts = {}) {
  const messages = [{ role: 'system', content: systemPrompt }, { role: 'user', content: userPrompt }];
  if (OPENROUTER_API_KEY) {
    try {
      const result = await raceOpenRouter(TEXT_MODEL_CHAIN, messages, opts);
      return result.text;
    } catch (err) {
      if (!GEMINI_API_KEY) throw err;
      // lanjut coba Gemini di bawah
    }
  }
  if (GEMINI_API_KEY) {
    const res = await fetch(`${GEMINI_BASE_URL}/gemini-flash-latest:generateContent`, {
      method: 'POST',
      headers: { 'x-goog-api-key': GEMINI_API_KEY, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [{ role: 'user', parts: [{ text: userPrompt }] }],
        systemInstruction: { parts: [{ text: systemPrompt }] },
        generationConfig: { maxOutputTokens: opts.maxTokens || 2048 },
      }),
    });
    if (!res.ok) throw new Error(`Gemini gagal (${res.status})`);
    const json = await res.json();
    return json.candidates?.[0]?.content?.parts?.map((p) => p.text).filter(Boolean).join('') || '';
  }
  throw new Error('Tidak ada AI provider yang dikonfigurasi (isi OPENROUTER_API_KEY atau GEMINI_API_KEY).');
}

/** Analisis beberapa thumbnail (base64 JPG) untuk menilai "momen menarik" — dipakai AI Clip Generator. */
async function analyzeVisualMoments(promptText, imageBase64List) {
  if (GEMINI_API_KEY) {
    try { return await callGeminiVision(promptText, imageBase64List); } catch (err) { if (!OPENROUTER_API_KEY) throw err; }
  }
  if (OPENROUTER_API_KEY) {
    const content = [{ type: 'text', text: promptText }, ...imageBase64List.map((b64) => ({ type: 'image_url', image_url: { url: `data:image/jpeg;base64,${b64}` } }))];
    const result = await raceOpenRouter(VISION_MODEL_CHAIN, [{ role: 'user', content }], { maxTokens: 2048 });
    return result.text;
  }
  throw new Error('Tidak ada AI provider bervisi yang dikonfigurasi.');
}

/** Transkripsi audio jadi teks (dengan timestamp per segmen) — dipakai fitur Auto Subtitle. */
async function transcribeAudio(audioBase64, mimeType) {
  if (!GEMINI_API_KEY) throw new Error('Transkripsi butuh GEMINI_API_KEY (satu-satunya provider di sini yang mendukung input audio).');
  const prompt = 'Transcribe this audio. Return ONLY a JSON array of segments, each like {"start": seconds, "end": seconds, "text": "..."}. No markdown, no explanation, just the raw JSON array.';
  const raw = await callGeminiAudio(prompt, audioBase64, mimeType);
  const cleaned = raw.replace(/```json|```/g, '').trim();
  try {
    return JSON.parse(cleaned);
  } catch (_) {
    return [{ start: 0, end: 0, text: raw.trim() }];
  }
}

module.exports = { isConfigured, generateText, analyzeVisualMoments, transcribeAudio, TEXT_MODEL_CHAIN, VISION_MODEL_CHAIN };
