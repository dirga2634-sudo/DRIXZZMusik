# Roum AI

AI chat web app bertema *dark futuristic* dengan logo "RM", memakai **OpenRouter API** sebagai gateway model AI. Backend Express menjadi proxy sehingga API key tidak pernah menyentuh browser.

- Frontend: HTML5 + CSS3 + JavaScript vanilla (tanpa build step)
- Backend: Node.js + Express (proxy `/api/chat`, streaming SSE)
- Markdown, syntax highlighting & sanitizer dimuat dari CDN: `marked`, `highlight.js`, `DOMPurify`
- Riwayat chat disimpan di **localStorage** browser (bukan di server)

---

## 1. Model yang dipakai

Roum AI **tidak dikunci ke satu model** — tersedia beberapa pilihan yang bisa diganti kapan saja lewat menu **Settings → Model**:

| Model | Kelebihan |
|---|---|
| **GLM 5.2 (Free)** (`z-ai/glm-5.2:free`) — default | **Gratis**, kualitas terbaik untuk coding + obrolan biasa di antara model gratis OpenRouter. Teks saja, tidak bisa gambar/video. |
| **Nemotron 3 Super (Free)** (`nvidia/nemotron-3-super-120b-a12b:free`) | **Gratis**, model NVIDIA lebih besar, konteks 1M token, kuat di reasoning/coding/agentic. Teks saja. |
| **Nemotron Nano Omni** (`nvidia/...:free`) | **Gratis**, satu-satunya pilihan gratis yang bisa menganalisis gambar (dan video di luar Vercel). Kualitas coding/obrolan di bawah dua di atas. |
| **GLM-5.3 Flash** (`z-ai/glm-5.3-flash`) | Sebelumnya sempat tampil sebagai preview gratis "Ox Alpha". Konteks 1M token, gambar & video, reasoning. Berbayar tapi sangat murah per token. |
| **Claude Sonnet 5** (`anthropic/claude-sonnet-5`) | Reasoning & analisis file/gambar paling kuat, effort reasoning bisa diatur. Tidak mendukung video. Harga menengah. |
| **Gemini 3 Pro** (`google/gemini-3-pro-preview`) | Paling kuat untuk video, audio, gambar, dan dokumen sekaligus. Harga menengah. |

Defaultnya sengaja diset ke model **gratis** supaya Roum AI langsung bisa dipakai walau saldo OpenRouter $0. Begitu ada saldo, ganti ke GLM-5.3 Flash/Claude/Gemini di Settings buat kualitas yang lebih baik.

⚠️ **Model gratis di OpenRouter itu daftarnya berubah-ubah** (bisa ditarik atau diganti provider tanpa pemberitahuan — persis seperti yang terjadi pada Ox Alpha). Kalau `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free` suatu saat error "model tidak ditemukan", cek daftar model gratis terbaru di [openrouter.ai/models](https://openrouter.ai/models) (filter "Free"), lalu ganti `DEFAULT_MODEL` di `.env` atau edit objek `MODELS` di `server.js`.

---

## 2. Cara install Node.js

1. Buka [nodejs.org](https://nodejs.org/), unduh versi **LTS** (minimal Node.js 18, disarankan versi LTS terbaru).
2. Install seperti aplikasi biasa (Windows/Mac) atau lewat package manager (Linux, mis. `sudo apt install nodejs npm`).
3. Cek berhasil dengan:
   ```
   node -v
   npm -v
   ```

## 3. Cara install dependency

Buka terminal di folder `roum-ai/`, lalu jalankan:

```
npm install
```

Perintah ini akan memasang `express`, `cors`, `dotenv`, dan `express-rate-limit` sesuai `package.json`.

## 4. Cara membuat API key OpenRouter

1. Buka [openrouter.ai](https://openrouter.ai/) dan buat akun (bisa login dengan Google/GitHub).
2. Masuk ke halaman **[openrouter.ai/keys](https://openrouter.ai/keys)**.
3. Klik **Create Key**, beri nama bebas (misalnya "Roum AI"), lalu salin key yang muncul (formatnya `sk-or-v1-...`).
   > Key hanya ditampilkan sekali — simpan baik-baik.
4. Model default Roum AI (Nemotron Nano Omni) **gratis** dan tidak butuh saldo sama sekali. Model lain (GLM-5.3 Flash, Claude Sonnet 5, Gemini 3 Pro) berbayar per-token — kalau nanti mau coba, isi saldo/credit dulu di halaman **Credits** (harga bervariasi, cek [openrouter.ai/models](https://openrouter.ai/models)).

## 5. Cara memasukkan API key ke .env

1. Di folder `roum-ai/`, salin `.env.example` menjadi `.env`:
   ```
   cp .env.example .env
   ```
   (Windows CMD: `copy .env.example .env`)
2. Buka `.env`, isi baris berikut dengan key asli kamu:
   ```
   OPENROUTER_API_KEY=sk-or-v1-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   ```
3. Simpan file. **Jangan pernah** commit atau membagikan file `.env` ini — sudah otomatis diabaikan oleh `.gitignore`.

> ⚠️ **Catatan keamanan:** kamu sempat menempelkan sebuah API key OpenRouter langsung di chat ini. Karena sudah tercatat di riwayat percakapan, sebaiknya anggap key itu terekspos — buka [openrouter.ai/keys](https://openrouter.ai/keys), hapus/regenerate key lama, lalu pakai key **baru** di `.env`. Ke depannya, key cukup ditaruh langsung di file `.env`, tidak perlu dikirim lewat chat manapun.

## 6. Cara menjalankan Roum AI

```
npm start
```

Lalu buka **http://localhost:3000** di browser. Untuk mode development dengan auto-restart saat file berubah, dan kalau `nodemon` sudah ter-install lewat `npm install`:

```
npm run dev
```

Titik hijau di sebelah nama model (pojok kiri atas area chat) menandakan server sudah terhubung dengan API key yang valid. Titik merah = `OPENROUTER_API_KEY` belum/tidak terbaca.

## 7. Cara membuka website dari HP

**Opsi A — satu jaringan WiFi (untuk uji coba lokal):**
1. Pastikan laptop/PC dan HP terhubung ke WiFi yang sama.
2. Cari alamat IP lokal laptop:
   - Windows: `ipconfig` → lihat "IPv4 Address" (contoh `192.168.1.5`)
   - Mac/Linux: `ifconfig` atau `ip addr` → cari alamat `192.168.x.x`
3. Jalankan server (`npm start`), lalu di HP buka browser ke `http://192.168.1.5:3000` (ganti dengan IP laptop kamu).

**Opsi B — bisa diakses dari mana saja:** deploy ke hosting (lihat bagian 8), lalu buka URL publiknya dari HP.

## 8. Cara deploy ke hosting

Roum AI punya **dua backend siap pakai** tergantung platform tujuan — keduanya memakai frontend yang sama persis di `public/`:

- `server.js` → untuk hosting yang menjalankan Node.js sungguhan (proses hidup terus): **Render, Railway, Fly.io, VPS, atau lokal/Termux**.
- folder `api/` → untuk **Vercel** (serverless functions, bukan proses yang hidup terus).

**Netlify sengaja tidak didukung** untuk versi ini: Netlify hanya cocok untuk file statis, dan format serverless function-nya tidak mendukung *streaming* respons AI dengan baik (jawaban akan muncul sekaligus di akhir, bukan mengetik langsung) — jadi pengalaman chat-nya jelek. Kalau targetnya "gratis + gampang + full-Node", **Render** lebih pas dari Netlify.

### Opsi A — Render / Railway / Fly.io / VPS (pakai `server.js`)

1. Push folder project ini ke repository GitHub (pastikan `.env` **tidak** ikut ter-commit — cek `.gitignore`).
2. Di dashboard platform pilihan, buat *Web Service* baru dan hubungkan ke repo tersebut.
3. Atur:
   - **Build command:** `npm install`
   - **Start command:** `npm start`
4. Di bagian **Environment Variables** platform tersebut, tambahkan `OPENROUTER_API_KEY` (dan opsional `SITE_URL`, `DEFAULT_MODEL`, dll — lihat `.env.example`) — isi lewat dashboard, bukan lewat file `.env`.
5. Deploy. Platform akan memberi URL publik (misalnya `https://roum-ai.onrender.com`).

Render punya tier gratis asli untuk Node.js (tanpa kartu kredit) — instance-nya "tidur" kalau 15 menit tidak dipakai lalu bangun lagi ~30–60 detik pas diakses lagi. Cukup untuk pemakaian pribadi.

### Opsi B — Vercel (pakai folder `api/`)

1. Push project ini ke GitHub.
2. Di [vercel.com](https://vercel.com), **Add New → Project**, import repo tersebut. Framework Preset pilih **Other** (biar folder `public/` otomatis jadi root situs statis, dan folder `api/` otomatis jadi serverless functions — tidak perlu konfigurasi build apa pun).
3. Di **Environment Variables**, tambahkan `OPENROUTER_API_KEY` (isi dengan key OpenRouter kamu).
4. Deploy. Vercel kasih URL publik (`https://nama-project.vercel.app`).

**Batasan khusus versi Vercel** (tidak berlaku di Opsi A):
- **Video dimatikan total.** Vercel Functions punya batas keras request 4.5MB — tidak cukup untuk file video sama sekali, jadi tombol upload video otomatis dinonaktifkan di deployment ini.
- **Gambar dibatasi 2MB** (lebih kecil dari 10MB di Opsi A), supaya sisa ruang di bawah batas 4.5MB itu cukup untuk teks percakapan.
- Function dibatasi durasi 60 detik (`vercel.json`) — jawaban dengan reasoning "High" yang sangat panjang berisiko terpotong di paket gratis Vercel.
- Tidak ada rate limiting bawaan (beda dari Opsi A yang pakai `express-rate-limit`) — Vercel Functions tidak punya proses yang hidup terus untuk menyimpan hitungannya.

Kalau butuh upload video, pakai Opsi A (Render dkk), bukan Vercel.

---

## Batasan yang perlu diketahui

- **Riwayat chat** tersimpan di `localStorage` browser masing-masing perangkat — tidak sinkron antar perangkat/browser, dan bisa hilang jika cache browser dibersihkan.
- Lampiran gambar/video ikut tersimpan di localStorage sebagai base64. Browser membatasi localStorage (biasanya beberapa MB), jadi di percakapan yang sangat panjang dengan banyak lampiran besar, lampiran lama bisa otomatis "dilepas" dari riwayat tersimpan (teks tetap aman) — akan muncul notifikasi kalau ini terjadi.
- Video hanya bisa dianalisis oleh model yang mendukungnya (GLM-5.3 Flash, Gemini 3 Pro) — dukungan video di OpenRouter memang bergantung pada model/provider, bukan fitur universal.
- Batas ukuran file default: gambar 10MB, video 30MB (bisa diubah di `server.js` & `public/app.js`, cari `MAX_IMAGE_BYTES`/`MAX_VIDEO_BYTES`).

## Struktur project

```
roum-ai/
├── public/
│   ├── index.html      # struktur halaman
│   ├── style.css        # desain dark/glassmorphism, mobile-first
│   ├── app.js            # semua logic frontend (chat, streaming, settings, dll)
│   └── assets/
│       └── favicon.svg   # logo RM
├── server.js             # backend Express untuk Render/Railway/VPS/lokal (Opsi A)
├── api/                  # backend serverless untuk Vercel (Opsi B)
│   ├── chat.js
│   ├── models.js
│   └── health.js
├── lib/
│   └── vercel-shared.js  # katalog model & validasi khusus untuk folder api/
├── vercel.json           # durasi maksimum function chat di Vercel
├── package.json
├── .env.example
└── .gitignore
```
