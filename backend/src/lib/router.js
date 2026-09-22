/**
 * Router HTTP super ringan (pengganti Express) — biar backend nol-dependency
 * dan bisa dites langsung tanpa npm install. Mendukung path param (:id) dan
 * body JSON otomatis.
 */
function pathToRegex(routePath) {
  const paramNames = [];
  const pattern = routePath
    .replace(/\/:[a-zA-Z0-9_]+/g, (match) => {
      paramNames.push(match.slice(2));
      return '/([^/]+)';
    })
    .replace(/\//g, '\\/');
  return { regex: new RegExp(`^${pattern}$`), paramNames };
}

function createRouter() {
  const routes = [];
  const middlewares = [];

  function add(method, routePath, handler) {
    const { regex, paramNames } = pathToRegex(routePath);
    routes.push({ method, regex, paramNames, handler });
  }

  function use(mw) { middlewares.push(mw); }

  async function handle(req, res) {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    req.query = Object.fromEntries(url.searchParams);
    req.pathname = url.pathname;

    for (const mw of middlewares) {
      const shouldContinue = await mw(req, res);
      if (shouldContinue === false) return; // middleware sudah kirim respons sendiri (mis. auth gagal)
    }

    for (const route of routes) {
      if (route.method !== req.method) continue;
      const match = route.regex.exec(req.pathname);
      if (!match) continue;
      req.params = {};
      route.paramNames.forEach((name, i) => { req.params[name] = decodeURIComponent(match[i + 1]); });
      try {
        await route.handler(req, res);
      } catch (err) {
        console.error('Route error:', err);
        if (!res.headersSent) sendJson(res, 500, { error: err.message || 'Terjadi kesalahan server.' });
      }
      return;
    }
    sendJson(res, 404, { error: 'Endpoint tidak ditemukan.' });
  }

  return { get: (p, h) => add('GET', p, h), post: (p, h) => add('POST', p, h), patch: (p, h) => add('PATCH', p, h), delete: (p, h) => add('DELETE', p, h), use, handle };
}

function sendJson(res, status, data) {
  const body = JSON.stringify(data);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) });
  res.end(body);
}

function readJsonBody(req, maxBytes = 60 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > maxBytes) { reject(Object.assign(new Error('Body terlalu besar.'), { statusCode: 413 })); req.destroy(); return; }
      chunks.push(chunk);
    });
    req.on('end', () => {
      if (chunks.length === 0) return resolve({});
      try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8'))); }
      catch (_) { reject(Object.assign(new Error('JSON body tidak valid.'), { statusCode: 400 })); }
    });
    req.on('error', reject);
  });
}

module.exports = { createRouter, sendJson, readJsonBody };
