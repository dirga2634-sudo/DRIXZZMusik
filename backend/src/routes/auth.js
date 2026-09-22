const db = require('../lib/db');
const { hashPassword, verifyPassword, signToken, verifyToken } = require('../lib/auth');
const { sendJson, readJsonBody } = require('../lib/router');
const { buildDemoData } = require('../data/seedDemo');

const DEMO_USER_ID = 'demo_user';

/** Middleware: selalu ada req.user — pakai token asli kalau ada & valid, else fallback ke akun Demo. */
function authMiddleware(req, res) {
  const authHeader = req.headers.authorization || '';
  const token = authHeader.startsWith('Bearer ') ? authHeader.slice(7) : null;
  const payload = token ? verifyToken(token) : null;
  if (payload && payload.userId) {
    const user = db.findById('users', payload.userId);
    if (user) { req.user = { id: user.id, name: user.name, email: user.email, isDemo: false }; return true; }
  }
  req.user = { id: DEMO_USER_ID, name: 'Creator', email: 'demo@vidzly.app', isDemo: true };
  return true;
}

function ensureDemoSeeded() {
  const dbData = db.readDb();
  if (dbData.projects.some((p) => p.userId === DEMO_USER_ID)) return;
  const demo = buildDemoData(DEMO_USER_ID);
  db.mutate((d) => {
    d.projects.push(...demo.projects);
    d.clips.push(...demo.clips);
    d.transcripts.push(...demo.transcripts);
    d.templates.push(...demo.templates);
    d.brandKits.push(demo.brandKit);
  });
}
ensureDemoSeeded();

function register(router) {
  router.post('/api/auth/register', async (req, res) => {
    const body = await readJsonBody(req);
    const { name, email, password } = body;
    if (!name || !email || !password) return sendJson(res, 400, { error: 'Nama, email, dan password wajib diisi.' });
    if (password.length < 6) return sendJson(res, 400, { error: 'Password minimal 6 karakter.' });
    const existing = db.findAllBy('users', (u) => u.email.toLowerCase() === email.toLowerCase());
    if (existing.length) return sendJson(res, 409, { error: 'Email sudah terdaftar.' });
    const user = { id: db.id('user'), name, email, passwordHash: hashPassword(password), createdAt: db.now() };
    await db.insert('users', user);
    const demo = buildDemoData(user.id);
    await db.mutate((d) => { d.brandKits.push(demo.brandKit); }); // user baru mulai dengan brand kit default, tapi workspace kosong (bukan demo project) supaya "empty state" beneran kepakai
    const token = signToken({ userId: user.id });
    sendJson(res, 201, { token, user: { id: user.id, name: user.name, email: user.email } });
  });

  router.post('/api/auth/login', async (req, res) => {
    const body = await readJsonBody(req);
    const { email, password } = body;
    const user = db.findAllBy('users', (u) => u.email.toLowerCase() === String(email || '').toLowerCase())[0];
    if (!user || !verifyPassword(password || '', user.passwordHash)) return sendJson(res, 401, { error: 'Email atau password salah.' });
    const token = signToken({ userId: user.id });
    sendJson(res, 200, { token, user: { id: user.id, name: user.name, email: user.email } });
  });

  router.post('/api/auth/demo', async (req, res) => {
    const token = signToken({ userId: DEMO_USER_ID });
    sendJson(res, 200, { token, user: { id: DEMO_USER_ID, name: 'Creator', email: 'demo@vidzly.app', isDemo: true } });
  });

  router.get('/api/auth/me', async (req, res) => {
    sendJson(res, 200, { user: req.user });
  });
}

module.exports = { register, authMiddleware, DEMO_USER_ID };
