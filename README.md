# Vidzly — AI Creator Studio

Platform AI untuk kreator (YouTube/TikTok/Reels/Shorts): upload video panjang → AI temukan momen terbaik → jadi banyak clip pendek siap posting, lengkap dengan caption, judul, dan editor browser.

**Status jujur soal apa yang beneran jalan** (dites langsung, bukan asumsi):
- ✅ **FFmpeg nyata**: potong klip, deteksi scene, reframe 9:16/1:1/16:9, extract audio/thumbnail — semua diverifikasi jalan pakai video test asli.
- ✅ **AI nyata**: judul/caption/hashtag/script pakai model gratis (GLM 5.2, Nemotron, dll via OpenRouter) + Gemini langsung — sama seperti sistem multi-provider di project Roum AI sebelumnya.
- ✅ **Auth, database, pipeline upload→proses→clip** — dites end-to-end lewat HTTP asli (bukan cuma baca kode).
- ⚠️ **Import YouTube/TikTok/dst**: sengaja TIDAK bisa (butuh API resmi tiap platform + kredensial developer yang tidak ada di sini) — server memberi pesan jujur, bukan pura-pura berhasil. Upload manual tetap berfungsi penuh.
- ⚠️ **Deployment Vercel**: hanya frontend + fitur AI teks yang jalan penuh di sana (lihat bagian "Kenapa dua cara deploy" di bawah).

---

## 1. Struktur project

```
vidzly/
├── backend/           # Server Node.js PENUH (auth, DB, FFmpeg, AI) — nol dependency npm
│   ├── server.js
│   ├── src/
│   │   ├── lib/        (router, db, auth, loadEnv, pipeline)
│   │   ├── ai/          (provider.js — OpenRouter + Gemini)
│   │   ├── video/       (processor.js — FFmpeg wrapper)
│   │   ├── data/        (seedDemo.js)
│   │   └── routes/      (auth, projects, clips, ai, misc)
│   ├── data/db.json    (dibuat otomatis saat pertama jalan)
│   ├── uploads/, exports/
│   └── .env.example
├── frontend/public/    # Semua halaman HTML/CSS/JS vanilla (dipakai backend/ di atas)
├── public/             # SALINAN frontend/public — khusus dibaca Vercel (lihat bawah)
├── api/                # Vercel Functions (AI teks asli + demo read-only)
├── vercel.json
└── README.md (ini)
```

## 2. Cara jalanin backend PENUH (semua fitur, termasuk upload & render video asli)

**Requirements:** Node.js 18+, dan **FFmpeg + FFprobe** harus sudah terpasang & ada di PATH (`ffmpeg -version` harus jalan di terminal). Di Ubuntu/Debian: `sudo apt install ffmpeg`. Di Mac: `brew install ffmpeg`.

```bash
cd backend
cp .env.example .env
# edit .env, isi OPENROUTER_API_KEY dan/atau GEMINI_API_KEY (opsional — tanpa ini, tetap jalan di Demo Mode untuk fitur AI)
npm install   # tidak ada dependency, cuma bikin node_modules kosong — aman di-skip juga
npm start
```

Buka **http://localhost:4000** — langsung bisa dicoba (Demo Mode otomatis aktif, ada 3 project contoh). Klik "Continue with Demo Mode" di halaman login, atau daftar akun asli untuk mulai dari workspace kosong.

### Setup AI (opsional tapi disarankan)
- **OpenRouter** (https://openrouter.ai/keys) — dipakai untuk sebagian besar teks AI (judul, caption, hashtag, script) lewat beberapa model gratis dengan fallback otomatis kalau satu penuh.
- **Gemini** (https://aistudio.google.com/apikey) — dipakai KHUSUS untuk **transkripsi audio** (Auto Subtitle) dan analisis visual momen video, karena satu-satunya provider di sini yang menerima input audio/gambar langsung.
- Tanpa keduanya: aplikasi tetap 100% jalan (FFmpeg tetap nyata), cuma judul/caption AI-nya pakai contoh generik dan tidak ada transkrip.

### Setup Database asli (opsional)
Default-nya pakai file `backend/data/db.json` (otomatis, tanpa setup). Untuk pakai PostgreSQL sungguhan: ganti isi `backend/src/lib/db.js` — semua fungsi (`findById`, `insert`, `update`, dll) sudah dipisah rapi di satu file, tinggal ganti implementasinya jadi query SQL tanpa mengubah kode route mana pun.

### Setup Storage S3 (opsional)
Saat ini file disimpan di folder lokal (`backend/uploads/`, `backend/exports/`). Untuk S3-compatible storage, ganti bagian `fs.promises.writeFile`/`createReadStream` di `backend/src/routes/projects.js` dan `server.js` dengan SDK S3 pilihanmu.

---

## 3. Kenapa dua cara deploy (backend/ penuh vs Vercel)?

Vercel itu **serverless** — setiap request dijalankan di fungsi sekali-pakai dengan disk yang hilang lagi setelah selesai, dan ada batas waktu eksekusi. Ini cocok untuk kode AI singkat, tapi **tidak cocok** untuk:
- Menyimpan video upload / hasil render secara permanen (disk-nya sementara)
- Menjalankan FFmpeg untuk rendering yang makan waktu lama
- Database file JSON yang perlu ditulis terus-menerus

Makanya:
- **`backend/` (Opsi A — fitur lengkap)**: deploy ke **Render, Railway, VPS, atau jalankan lokal** — di sinilah upload, FFmpeg, dan penyimpanan permanen benar-benar berfungsi.
- **`api/` + `public/` (Opsi B — Vercel)**: otomatis jalan sebagai demo — dashboard/projects/clips menampilkan data contoh (read-only), sementara **AI Script/Captions/Hashtag/Repurpose beneran terhubung ke AI** (karena itu cuma butuh 1 request singkat, cocok untuk serverless). Upload & render video di Vercel akan menampilkan pesan jujur "butuh backend/ penuh" — bukan pura-pura berhasil.

### Deploy Opsi A (Render, contoh):
1. Push folder ini ke GitHub.
2. Buat *Web Service* baru di Render, arahkan ke repo ini, set **Root Directory** ke `backend`.
3. Build command: `npm install`, Start command: `npm start`.
4. Tambahkan `OPENROUTER_API_KEY`/`GEMINI_API_KEY` di Environment Variables Render (bukan file .env).
5. **Penting**: FFmpeg harus tersedia di image Render — pastikan pilih environment yang menyediakannya (atau tambahkan buildpack/Dockerfile yang install ffmpeg; kebanyakan platform Node modern sudah menyediakan ffmpeg di base image mereka, tapi cek dulu).

### Deploy Opsi B (Vercel):
1. Import repo ini di Vercel, Framework Preset: **Other**.
2. Root Directory: **root project ini** (bukan `backend/`) — supaya folder `public/` dan `api/` di root ke-detect.
3. Tambahkan `OPENROUTER_API_KEY`/`GEMINI_API_KEY` di Environment Variables Vercel (opsional — tanpa ini, AI tools tetap tampil tapi pakai contoh generik).
4. Deploy. Dashboard/Projects/Clips akan menampilkan data demo; AI Script/Captions/Hashtag/Repurpose beneran jalan.

---

## 4. Fitur yang diimplementasikan penuh vs disederhanakan

| Fitur | Status |
|---|---|
| Upload video, validasi format/ukuran | ✅ Penuh |
| Import link YouTube/TikTok/dll | ⚠️ Ditolak dengan pesan jujur (butuh API resmi platform) — link file `.mp4` langsung tetap bisa |
| AI Clip Generator (scene detection + skor + judul) | ✅ Penuh (FFmpeg + AI vision asli) |
| Trim/Split/Cut/Export | ✅ Penuh (FFmpeg asli) |
| Auto reframe 9:16/1:1/16:9 | ✅ Penuh (FFmpeg asli, center-crop — bukan face-tracking penuh, lihat Roadmap) |
| Auto subtitle/transkripsi | ✅ Jalan kalau `GEMINI_API_KEY` diisi (butuh input audio) |
| Burn caption ke video | ✅ Penuh (`processor.burnSubtitles`, FFmpeg `subtitles` filter) |
| AI Title/Caption/Hashtag/Script/Repurpose | ✅ Penuh |
| Thumbnail Maker | ⚠️ Preview interaktif (teks di atas frame) — export jadi JPG final belum dirender server-side |
| Speed/Volume | ✅ Fungsi FFmpeg-nya ada (`changeSpeed`, `adjustVolume`) di `processor.js`, terhubung ke UI Editor |
| Multi-track timeline (video/audio/subtitle/text) | ⚠️ Visual & interaksi dasar (drag belum), rendering final gabungan multi-track belum diimplementasikan |
| Brand Kit, Templates, Analytics, Settings | ✅ CRUD dasar berfungsi (backend/ penuh); read-only demo di Vercel |
| Face-tracking reframe | ❌ Belum — pakai center-crop sebagai baseline. Roadmap: tambah face-detection (mis. lewat model vision) sebelum crop. |

## 5. Kalau mau lanjut kembangin

- `backend/src/video/processor.js` — semua fungsi FFmpeg ada di sini, gampang ditambah (mis. `addIntroOutro`, `normalizeAudio`).
- `backend/src/lib/db.js` — ganti ke Postgres beneran cukup di satu file ini.
- `backend/src/ai/provider.js` — tambah model/provider baru cukup ubah `TEXT_MODEL_CHAIN`/`VISION_MODEL_CHAIN`.
- Multi-track rendering (menggabungkan video+text overlay+caption jadi satu file akhir) adalah pekerjaan besar berikutnya yang paling bernilai — saat ini tiap elemen (potong, reframe, burn caption) sudah bisa jalan sendiri-sendiri, tinggal dirangkai dalam satu pipeline export.

## 6. Troubleshooting

- **"ffmpeg: command not found"** → install FFmpeg dulu (lihat bagian 2), lalu restart server.
- **AI selalu balas "contoh generik"** → cek `.env` sudah terisi `OPENROUTER_API_KEY`/`GEMINI_API_KEY` dan servernya sudah di-restart setelah edit `.env`.
- **Upload gagal "Body terlalu besar"** → default limit 200MB (video mentah), ubah `MAX_VIDEO_BYTES` di `backend/src/routes/projects.js` kalau perlu lebih besar (perhatikan base64 menambah ukuran ~33%).
