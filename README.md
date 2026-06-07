# Drizzx Musik Backend API

Backend API untuk Drizzx Musik Android App.

## Endpoints

- `GET /api/trending` - Trending musik
- `GET /api/search?q=query` - Cari lagu
- `GET /api/stream?id=videoId` - Dapatkan stream URL

## Deploy ke Vercel

1. Fork/upload repo ini ke GitHub
2. Buka vercel.com → New Project → import repo ini
3. Deploy (tidak perlu setting apapun)
4. Salin URL Vercel yang diberikan (contoh: https://drizzx-api.vercel.app)
5. Update BASE_URL di MusicApi.java APK dengan URL tersebut
