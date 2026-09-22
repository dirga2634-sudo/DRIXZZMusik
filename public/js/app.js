/**
 * Vidzly — shared frontend core: API client, auth state, navigasi (sidebar +
 * bottom nav), toast, modal helper. Dipakai di semua halaman lewat <script>.
 */

// ---------------- Auth / API client ----------------
const Auth = {
  TOKEN_KEY: 'vidzly_token',
  getToken() { return localStorage.getItem(this.TOKEN_KEY); },
  setToken(t) { localStorage.setItem(this.TOKEN_KEY, t); },
  clear() { localStorage.removeItem(this.TOKEN_KEY); },
};

const Api = {
  async request(method, path, body) {
    const headers = { 'Content-Type': 'application/json' };
    const token = Auth.getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    const res = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : undefined });
    let data = {};
    try { data = await res.json(); } catch (_) { /* respons non-JSON (mis. 204) */ }
    if (!res.ok) throw new Error(data.error || `Request gagal (${res.status})`);
    return data;
  },
  get(path) { return this.request('GET', path); },
  post(path, body) { return this.request('POST', path, body); },
  patch(path, body) { return this.request('PATCH', path, body); },
  delete(path) { return this.request('DELETE', path); },
};

// ---------------- Toast ----------------
function ensureToastContainer() {
  let c = document.getElementById('toast-container');
  if (!c) { c = document.createElement('div'); c.id = 'toast-container'; c.className = 'toast-container'; document.body.appendChild(c); }
  return c;
}
function showToast(message, type = 'info') {
  const c = ensureToastContainer();
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.textContent = message;
  c.appendChild(el);
  setTimeout(() => { el.style.transition = 'opacity .2s'; el.style.opacity = '0'; setTimeout(() => el.remove(), 200); }, 3800);
}

// ---------------- Format helpers ----------------
function formatDuration(sec) {
  sec = Math.round(sec || 0);
  const m = Math.floor(sec / 60), s = sec % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}
function formatDate(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleDateString('id-ID', { day: 'numeric', month: 'short', year: 'numeric' });
}
function escapeHtml(str) {
  return String(str == null ? '' : str).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function timeGreeting() {
  const h = new Date().getHours();
  if (h < 11) return 'Good morning';
  if (h < 15) return 'Good afternoon';
  if (h < 19) return 'Good evening';
  return 'Working late';
}

// ---------------- Icons (dipakai berulang) ----------------
const ICONS = {
  dashboard: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/></svg>',
  create: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 5v14M5 12h14" stroke-linecap="round"/></svg>',
  projects: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>',
  clips: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m10 9 5 3-5 3z" fill="currentColor" stroke="none"/></svg>',
  templates: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>',
  editor: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="4" width="20" height="13" rx="2"/><path d="M8 21h8M12 17v4" stroke-linecap="round"/></svg>',
  brand: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 2 2 7l10 5 10-5z"/><path d="m2 17 10 5 10-5M2 12l10 5 10-5" stroke-linejoin="round"/></svg>',
  analytics: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 3v18h18" stroke-linecap="round"/><path d="M7 15l4-5 3 3 5-7" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  script: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M9 13h6M9 17h6" stroke-linecap="round"/></svg>',
  caption: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="5" width="20" height="14" rx="2"/><path d="M6 10h4M6 14h8M14 10h4" stroke-linecap="round"/></svg>',
  thumbnail: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="9" cy="9" r="1.8"/><path d="M21 15l-5-5L5 21" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  repurpose: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M17 2l4 4-4 4M3 11V9a4 4 0 0 1 4-4h14M7 22l-4-4 4-4M21 13v2a4 4 0 0 1-4 4H3" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  settings: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9c.14.31.22.65.22 1v.09a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
  help: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="10"/><path d="M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.8.4-1 1-1 1.7" stroke-linecap="round"/><circle cx="12" cy="17" r="0.6" fill="currentColor"/></svg>',
  profile: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="8" r="4"/><path d="M4 21c1.5-4 5-6 8-6s6.5 2 8 6" stroke-linecap="round"/></svg>',
  home: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 11l9-8 9 8" stroke-linecap="round" stroke-linejoin="round"/><path d="M5 10v10h14V10" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  tools: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14.7 6.3a4 4 0 1 0-5.4 5.4L2 19l3 3 7.3-7.3a4 4 0 0 0 5.4-5.4z"/></svg>',
  chevronDown: '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  logout: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" stroke-linecap="round" stroke-linejoin="round"/></svg>',
};

function logoMarkup(size = 34) {
  const gid = 'vzGrad_' + Math.random().toString(36).slice(2);
  return `<svg viewBox="0 0 48 48" width="${size}" height="${size}" class="sidebar__logo"><defs><linearGradient id="${gid}" x1="0" y1="0" x2="48" y2="48" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#8B5CF6"/><stop offset="1" stop-color="#EC4899"/></linearGradient></defs><rect x="1" y="1" width="46" height="46" rx="14" fill="url(#${gid})"/><path d="M17 15 L24 33 L31 15" stroke="#fff" stroke-width="3.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
}

// ---------------- Navigation shell ----------------
const NAV_ITEMS = {
  workspace: [
    { href: '/dashboard.html', icon: 'dashboard', label: 'Dashboard' },
    { href: '/create.html', icon: 'create', label: 'Create' },
    { href: '/projects.html', icon: 'projects', label: 'Projects' },
    { href: '/clips.html', icon: 'clips', label: 'AI Clips' },
    { href: '/templates.html', icon: 'templates', label: 'Templates' },
    { href: '/editor.html', icon: 'editor', label: 'Editor' },
    { href: '/brand-kit.html', icon: 'brand', label: 'Brand Kit' },
    { href: '/analytics.html', icon: 'analytics', label: 'Analytics' },
  ],
  tools: [
    { href: '/tool-script.html', icon: 'script', label: 'AI Script' },
    { href: '/tool-captions.html', icon: 'caption', label: 'AI Captions' },
    { href: '/tool-thumbnail.html', icon: 'thumbnail', label: 'Thumbnail' },
    { href: '/tool-repurpose.html', icon: 'repurpose', label: 'Content Repurpose' },
  ],
  account: [
    { href: '/settings.html', icon: 'settings', label: 'Settings' },
    { href: '/help.html', icon: 'help', label: 'Help' },
    { href: '/profile.html', icon: 'profile', label: 'Profile' },
  ],
};
const BOTTOM_NAV_ITEMS = [
  { href: '/dashboard.html', icon: 'home', label: 'Home' },
  { href: '/projects.html', icon: 'projects', label: 'Projects' },
  { href: '/create.html', icon: 'create', label: 'Create', isFab: true },
  { href: '/tool-script.html', icon: 'tools', label: 'Tools' },
  { href: '/profile.html', icon: 'profile', label: 'Profile' },
];

function navLinkHtml(item, currentPath) {
  const active = currentPath === item.href ? ' is-active' : '';
  return `<a href="${item.href}" class="nav-link${active}">${ICONS[item.icon]}<span>${item.label}</span></a>`;
}

function renderShell(pageTitle) {
  const currentPath = location.pathname;
  const shellHtml = `
    <div id="sidebar-overlay" class="sidebar-overlay" hidden></div>
    <aside class="sidebar" id="sidebar">
      <div class="sidebar__brand">${logoMarkup()}<span class="sidebar__brand-name">Vidzly</span></div>
      <a href="/create.html" class="btn-create">${ICONS.create}<span>Create</span></a>
      <nav style="overflow-y:auto;flex:1;">
        <div class="nav-group"><div class="nav-group__label">Workspace</div>${NAV_ITEMS.workspace.map((i) => navLinkHtml(i, currentPath)).join('')}</div>
        <div class="nav-group"><div class="nav-group__label">Tools</div>${NAV_ITEMS.tools.map((i) => navLinkHtml(i, currentPath)).join('')}</div>
        <div class="nav-group"><div class="nav-group__label">Account</div>${NAV_ITEMS.account.map((i) => navLinkHtml(i, currentPath)).join('')}</div>
      </nav>
      <div class="sidebar__bottom">
        <button class="nav-link" id="btn-logout" style="width:100%">${ICONS.logout}<span>Logout</span></button>
      </div>
    </aside>
    <main id="main-content">
      <header class="topbar">
        <button class="icon-btn mobile-only" id="btn-open-sidebar" aria-label="Menu"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 6h16M4 12h16M4 18h16" stroke-linecap="round"/></svg></button>
        <h1 class="topbar__title">${pageTitle}</h1>
        <div class="topbar__spacer"></div>
        <div id="user-chip" class="chip chip--muted" style="cursor:pointer;"></div>
      </header>
      <div class="page-content" id="page-content"></div>
    </main>
    <nav class="bottom-nav">${BOTTOM_NAV_ITEMS.map((i) => `<a href="${i.href}" class="bottom-nav__item${currentPath === i.href ? ' is-active' : ''}${i.isFab ? ' is-fab' : ''}">${ICONS[i.icon]}<span>${i.isFab ? '' : i.label}</span></a>`).join('')}</nav>
  `;
  const root = document.getElementById('app-shell');
  root.insertAdjacentHTML('afterbegin', shellHtml);

  document.getElementById('btn-open-sidebar').addEventListener('click', () => {
    document.getElementById('sidebar').classList.add('is-open');
    document.getElementById('sidebar-overlay').hidden = false;
  });
  document.getElementById('sidebar-overlay').addEventListener('click', () => {
    document.getElementById('sidebar').classList.remove('is-open');
    document.getElementById('sidebar-overlay').hidden = true;
  });
  document.getElementById('btn-logout').addEventListener('click', () => { Auth.clear(); location.href = '/login.html'; });

  Api.get('/api/auth/me').then(({ user }) => {
    document.getElementById('user-chip').textContent = user.isDemo ? '👤 Demo Mode' : `👤 ${user.name}`;
  }).catch(() => {});

  return document.getElementById('page-content');
}

function requireShell(pageTitle) {
  if (!document.getElementById('app-shell')) {
    const shell = document.createElement('div');
    shell.id = 'app-shell';
    document.body.prepend(shell);
  }
  return renderShell(pageTitle);
}

// ---------------- Modal helper ----------------
function openModal(id) { const el = document.getElementById(id); if (el) el.hidden = false; }
function closeModal(id) { const el = document.getElementById(id); if (el) el.hidden = true; }
document.addEventListener('click', (e) => {
  const closer = e.target.closest('[data-close-modal]');
  if (closer) closeModal(closer.closest('.modal').id);
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') document.querySelectorAll('.modal:not([hidden])').forEach((m) => (m.hidden = true));
});

function confirmDialog({ title, message, confirmLabel = 'Delete', danger = true }) {
  return new Promise((resolve) => {
    let modal = document.getElementById('global-confirm-modal');
    if (!modal) {
      modal = document.createElement('div');
      modal.id = 'global-confirm-modal';
      modal.className = 'modal';
      modal.hidden = true;
      modal.innerHTML = `<div class="modal__backdrop" data-close-modal></div><div class="modal__panel"><div class="modal__header"><h2 id="gc-title"></h2></div><div class="modal__body"><p id="gc-message" class="text-secondary"></p></div><div class="modal__footer"><button class="btn-secondary" id="gc-cancel">Cancel</button><button class="btn-danger" id="gc-ok"></button></div></div>`;
      document.body.appendChild(modal);
    }
    modal.querySelector('#gc-title').textContent = title;
    modal.querySelector('#gc-message').textContent = message;
    const okBtn = modal.querySelector('#gc-ok');
    okBtn.textContent = confirmLabel;
    okBtn.className = danger ? 'btn-danger' : 'btn-primary';
    modal.hidden = false;
    const cleanup = (result) => { modal.hidden = true; okBtn.onclick = null; modal.querySelector('#gc-cancel').onclick = null; resolve(result); };
    okBtn.onclick = () => cleanup(true);
    modal.querySelector('#gc-cancel').onclick = () => cleanup(false);
  });
}
