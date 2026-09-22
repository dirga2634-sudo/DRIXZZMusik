const db = require('../lib/db');
const { sendJson, readJsonBody } = require('../lib/router');
const { buildDemoData } = require('../data/seedDemo');

function register(router) {
  router.get('/api/templates', async (req, res) => {
    let templates = db.readDb().templates;
    if (templates.length === 0) templates = buildDemoData(req.user.id).templates;
    sendJson(res, 200, { templates });
  });

  router.get('/api/brand-kit', async (req, res) => {
    let kit = db.findAllBy('brandKits', (b) => b.userId === req.user.id)[0];
    if (!kit) kit = buildDemoData(req.user.id).brandKit;
    sendJson(res, 200, { brandKit: kit });
  });

  router.patch('/api/brand-kit', async (req, res) => {
    const body = await readJsonBody(req);
    let kit = db.findAllBy('brandKits', (b) => b.userId === req.user.id)[0];
    if (!kit) {
      kit = { ...buildDemoData(req.user.id).brandKit, id: db.id('brand') };
      await db.insert('brandKits', kit);
    }
    const patch = {};
    ['name', 'colors', 'fontHeading', 'fontBody', 'captionStyle'].forEach((k) => { if (body[k] !== undefined) patch[k] = body[k]; });
    const updated = await db.update('brandKits', kit.id, patch);
    sendJson(res, 200, { brandKit: updated });
  });

  router.get('/api/analytics', async (req, res) => {
    const myProjects = db.findAllBy('projects', (p) => p.userId === req.user.id);
    const myProjectIds = new Set(myProjects.map((p) => p.id));
    const myClips = db.findAllBy('clips', (c) => myProjectIds.has(c.projectId));
    const myExports = db.findAllBy('exports', (e) => myProjectIds.has(e.projectId));

    if (myClips.length === 0) {
      return sendJson(res, 200, { ...buildDemoData(req.user.id).analytics, isDemo: true, note: 'Data contoh — analitik asli (views, engagement) butuh koneksi resmi ke platform (YouTube/TikTok API) yang belum dikonfigurasi.' });
    }
    const avgLen = myClips.reduce((s, c) => s + (c.duration || 0), 0) / myClips.length;
    const best = [...myClips].sort((a, b) => (b.viralScore || 0) - (a.viralScore || 0))[0];
    sendJson(res, 200, {
      totalViews: null, clipsCreated: myClips.length, avgClipLength: Math.round(avgLen),
      exportCount: myExports.length, bestPerforming: best ? best.id : null,
      viewsByDay: null, isDemo: false,
      note: 'Jumlah views butuh koneksi resmi ke platform (YouTube/TikTok API) — belum dikonfigurasi, jadi hanya metrik internal (jumlah clip/export) yang ditampilkan.',
    });
  });
}

module.exports = { register };
