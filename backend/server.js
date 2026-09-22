/**
 * Vidzly AI Creator Studio — backend server.
 * Node.js murni (tanpa Express) supaya nol-dependency & bisa dites langsung.
 */
require('./src/lib/loadEnv').loadEnv();
const http = require('http');
const path = require('path');
const fs = require('fs');
const { createRouter, sendJson } = require('./src/lib/router');
const authRoutes = require('./src/routes/auth');
const projectRoutes = require('./src/routes/projects');
const clipRoutes = require('./src/routes/clips');
const aiRoutes = require('./src/routes/ai');
const miscRoutes = require('./src/routes/misc');
const ai = require('./src/ai/provider');

const PORT = Number(process.env.PORT) || 4000;
const FRONTEND_DIR = path.join(__dirname, '..', 'frontend', 'public');
const EXPORTS_DIR = path.join(__dirname, 'exports');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
  '.mp4': 'video/mp4', '.mp3': 'audio/mpeg', '.ico': 'image/x-icon', '.woff2': 'font/woff2',
};

function serveStatic(rootDir, urlPath, res) {
  const safePath = path.normalize(urlPath).replace(/^(\.\.[/\\])+/, '');
  const filePath = path.join(rootDir, safePath);
  if (!filePath.startsWith(rootDir)) { sendJson(res, 403, { error: 'Forbidden' }); return true; }
  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) return false;
  const ext = path.extname(filePath).toLowerCase();
  res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
  fs.createReadStream(filePath).pipe(res);
  return true;
}

const router = createRouter();
router.use(authRoutes.authMiddleware);
authRoutes.register(router);
projectRoutes.register(router);
clipRoutes.register(router);
aiRoutes.register(router);
miscRoutes.register(router);

router.get('/api/health', async (req, res) => {
  sendJson(res, 200, { ok: true, aiConfigured: ai.isConfigured(), demoUser: req.user.isDemo });
});

const server = http.createServer(async (req, res) => {
  try {
    // File hasil export/render (thumbnail, video hasil potong) disajikan sebagai static juga.
    if (req.method === 'GET' && req.url.startsWith('/exports/')) {
      const served = serveStatic(EXPORTS_DIR, req.url.replace('/exports', ''), res);
      if (served) return;
    }
    if (req.url.startsWith('/api/')) {
      await router.handle(req, res);
      return;
    }
    // Static frontend; fallback ke index.html untuk path tanpa ekstensi (biar refresh di halaman mana pun tetap kebuka).
    const urlPath = req.url.split('?')[0];
    const hasExt = path.extname(urlPath) !== '';
    const served = serveStatic(FRONTEND_DIR, urlPath === '/' ? '/index.html' : urlPath, res);
    if (served) return;
    if (!hasExt) {
      const fallbackServed = serveStatic(FRONTEND_DIR, '/index.html', res);
      if (fallbackServed) return;
    }
    sendJson(res, 404, { error: 'Not found' });
  } catch (err) {
    console.error('Server error:', err);
    if (!res.headersSent) sendJson(res, 500, { error: 'Internal server error' });
  }
});

server.listen(PORT, () => {
  console.log(`\n🎬 Vidzly AI Creator Studio jalan di http://localhost:${PORT}`);
  console.log(`   AI provider dikonfigurasi: ${ai.isConfigured() ? 'YA' : 'TIDAK (Demo Mode aktif untuk fitur AI)'}\n`);
});

module.exports = server;
