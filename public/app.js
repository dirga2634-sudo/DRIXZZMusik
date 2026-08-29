/**
 * Roum AI — frontend logic (vanilla JS, no framework/build step).
 * Talks only to our own backend (`/api/chat`, `/api/models`, `/api/health`) —
 * never calls OpenRouter directly, so the API key never touches the browser.
 */

// ---------------------------------------------------------
// Constants
// ---------------------------------------------------------
const LS_CONVERSATIONS = 'roum_conversations_v1';
const LS_SETTINGS = 'roum_settings_v1';

// Nilai default ini dipakai sampai /api/models berhasil di-fetch (lihat init()).
// Backend Express (server.js) mengizinkan file besar; backend Vercel (/api/*.js)
// melaporkan limit yang jauh lebih kecil karena batas keras Vercel Functions —
// nilai dari server yang sebenarnya dipakai, bukan konstanta di bawah ini.
const DEFAULT_MAX_IMAGE_BYTES = 10 * 1024 * 1024; // 10MB
const DEFAULT_MAX_VIDEO_BYTES = 30 * 1024 * 1024; // 30MB
const ALLOWED_IMAGE_MIME = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];
const ALLOWED_VIDEO_MIME = ['video/mp4', 'video/webm', 'video/quicktime'];

const FALLBACK_MODELS = [
  { id: 'z-ai/glm-5.2:free', label: 'GLM 5.2 (Free)', tag: 'Gratis · Coding & chat', description: 'Best free model for coding + natural chat. Text only.', supportsImage: false, supportsVideo: false },
  { id: 'nvidia/nemotron-3-super-120b-a12b:free', label: 'Nemotron 3 Super (Free)', tag: 'Gratis · Reasoning besar', description: 'Bigger free NVIDIA model, strong reasoning/coding. Text only.', supportsImage: false, supportsVideo: false },
  { id: 'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free', label: 'Nemotron Nano Omni', tag: 'Gratis · Gambar & video', description: 'Truly free open-source model. Text, image, video.', supportsImage: true, supportsVideo: true },
  { id: 'z-ai/glm-5.3-flash', label: 'GLM-5.3 Flash', tag: 'Murah & cepat', description: 'Formerly "Ox Alpha". 1M context, image & video support.', supportsImage: true, supportsVideo: true },
  { id: 'anthropic/claude-sonnet-5', label: 'Claude Sonnet 5', tag: 'Strong reasoning', description: 'Strong reasoning and file/image analysis.', supportsImage: true, supportsVideo: false },
  { id: 'google/gemini-3-pro-preview', label: 'Gemini 3 Pro', tag: 'Best multimodal', description: 'Best for video, audio, image and document understanding.', supportsImage: true, supportsVideo: true },
];

const ICONS = {
  copy: '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>',
  edit: '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke-linecap="round" stroke-linejoin="round"/><path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  refresh: '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 1 1-3-6.7" stroke-linecap="round"/><path d="M21 3v6h-6" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  trash: '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6h14z" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  dots: '<svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="19" cy="12" r="1.6"/></svg>',
  chevronRight: '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M9 18l6-6-6-6" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  video: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="2" y="5" width="15" height="14" rx="2"/><path d="M22 8.5l-5 3.5 5 3.5v-7z" stroke-linejoin="round"/></svg>',
};

// ---------------------------------------------------------
// State
// ---------------------------------------------------------
let els = {};
let logoIdCounter = 0;
let dragCounter = 0;
let userScrolledUp = false;

const state = {
  conversations: [],
  activeConversationId: null,
  settings: null,
  models: [],
  defaultModel: 'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',
  maxImageBytes: DEFAULT_MAX_IMAGE_BYTES,
  maxVideoBytes: DEFAULT_MAX_VIDEO_BYTES,
  videoDisabledReason: '',
  pendingAttachments: [],
  isGenerating: false,
  abortController: null,
};

function defaultSettings() {
  return { theme: 'dark', fontSize: 'md', model: null, reasoningEffort: 'medium', sidebarCollapsed: false, activeConversationId: null };
}

// ---------------------------------------------------------
// Small utilities
// ---------------------------------------------------------
function uuid() {
  return window.crypto && crypto.randomUUID ? crypto.randomUUID() : 'id-' + Date.now() + '-' + Math.random().toString(16).slice(2);
}
function nowISO() { return new Date().toISOString(); }
function escapeHtml(str) {
  return String(str == null ? '' : str).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function deriveTitle(text, attachments) {
  if (text && text.trim()) {
    const t = text.trim().replace(/\s+/g, ' ');
    return t.length > 48 ? t.slice(0, 48) + '…' : t;
  }
  if (attachments && attachments.length) return attachments[0].type === 'video' ? 'Video analysis' : 'Image analysis';
  return 'New chat';
}
function logoSvgMarkup(size) {
  const gid = 'rmGrad_' + logoIdCounter++;
  return `<svg viewBox="0 0 48 48" width="${size}" height="${size}" aria-hidden="true"><defs><linearGradient id="${gid}" x1="0" y1="0" x2="48" y2="48" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#7C5CFF"/><stop offset="1" stop-color="#22D3EE"/></linearGradient></defs><rect x="1" y="1" width="46" height="46" rx="14" fill="url(#${gid})"/><text x="24" y="31" text-anchor="middle" font-family="'Space Grotesk',sans-serif" font-weight="700" font-size="18" fill="#F5F4FF">RM</text></svg>`;
}
function showToast(message, type) {
  const toast = document.createElement('div');
  toast.className = 'toast' + (type ? ' ' + type : '');
  toast.textContent = message;
  els.toastContainer.appendChild(toast);
  setTimeout(() => {
    toast.style.transition = 'opacity .2s';
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 220);
  }, 4200);
}

// ---------------------------------------------------------
// Markdown rendering + code block enhancement
// ---------------------------------------------------------
if (window.marked) marked.setOptions({ breaks: true, gfm: true });

function renderMarkdownSafe(text) {
  if (!text) return '';
  try {
    const raw = marked.parse(text);
    return DOMPurify.sanitize(raw);
  } catch (e) {
    return escapeHtml(text);
  }
}

function enhanceMessageContent(bubbleEl) {
  if (!bubbleEl) return;
  bubbleEl.querySelectorAll('pre code').forEach((block) => {
    if (window.hljs) {
      try { hljs.highlightElement(block); } catch (e) { /* ignore */ }
    }
    const pre = block.parentElement;
    if (!pre || pre.parentElement.classList.contains('code-block')) return;
    const langMatch = block.className.match(/language-([\w-]+)/);
    const lang = langMatch ? langMatch[1] : 'text';
    const wrapper = document.createElement('div');
    wrapper.className = 'code-block';
    const header = document.createElement('div');
    header.className = 'code-block__header';
    header.innerHTML = `<span>${escapeHtml(lang)}</span><button type="button" class="code-block__copy">${ICONS.copy}<span>Copy</span></button>`;
    pre.replaceWith(wrapper);
    wrapper.appendChild(header);
    wrapper.appendChild(pre);
  });
}

// ---------------------------------------------------------
// LocalStorage persistence
// ---------------------------------------------------------
function loadConversations() {
  try {
    const raw = localStorage.getItem(LS_CONVERSATIONS);
    return raw ? JSON.parse(raw) : [];
  } catch (e) { return []; }
}
function saveConversations() {
  try {
    localStorage.setItem(LS_CONVERSATIONS, JSON.stringify(state.conversations));
  } catch (e) {
    try {
      const stripped = state.conversations.map((c) => ({
        ...c,
        messages: c.messages.map((m) => ({
          ...m,
          attachments: (m.attachments || []).map((a) => ({ id: a.id, type: a.type, mime: a.mime, name: a.name })),
        })),
      }));
      localStorage.setItem(LS_CONVERSATIONS, JSON.stringify(stripped));
      showToast('Local storage is full — attachments were trimmed from saved history (still visible now).', 'error');
    } catch (e2) {
      console.error('Failed to persist conversations', e2);
    }
  }
}
function loadSettings() {
  try {
    const raw = localStorage.getItem(LS_SETTINGS);
    return raw ? { ...defaultSettings(), ...JSON.parse(raw) } : defaultSettings();
  } catch (e) { return defaultSettings(); }
}
function saveSettings() {
  try { localStorage.setItem(LS_SETTINGS, JSON.stringify(state.settings)); } catch (e) { console.error(e); }
}

// ---------------------------------------------------------
// Conversation helpers
// ---------------------------------------------------------
function getActiveConversation() {
  return state.conversations.find((c) => c.id === state.activeConversationId) || null;
}
function getActiveModel() {
  return state.models.find((m) => m.id === state.settings.model) || state.models[0] || FALLBACK_MODELS[0];
}
function setActiveConversationId(id) {
  state.activeConversationId = id;
  state.settings.activeConversationId = id;
  saveSettings();
}

function groupByDate(list) {
  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const startOfYesterday = new Date(startOfToday); startOfYesterday.setDate(startOfYesterday.getDate() - 1);
  const sevenDaysAgo = new Date(startOfToday); sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
  const groups = [
    { label: 'Today', items: [] },
    { label: 'Yesterday', items: [] },
    { label: 'Previous 7 days', items: [] },
    { label: 'Older', items: [] },
  ];
  for (const conv of list) {
    const updated = new Date(conv.updatedAt);
    if (updated >= startOfToday) groups[0].items.push(conv);
    else if (updated >= startOfYesterday) groups[1].items.push(conv);
    else if (updated >= sevenDaysAgo) groups[2].items.push(conv);
    else groups[3].items.push(conv);
  }
  return groups;
}

// ---------------------------------------------------------
// Sidebar / conversation list rendering
// ---------------------------------------------------------
function renderSidebarConversations() {
  const query = els.searchInput.value.trim().toLowerCase();
  const list = state.conversations
    .filter((c) => c.messages.length > 0)
    .filter((c) => !query || c.title.toLowerCase().includes(query) || c.messages.some((m) => (m.content || '').toLowerCase().includes(query)))
    .sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt));

  els.conversationList.innerHTML = '';
  if (list.length === 0) {
    const empty = document.createElement('div');
    empty.className = 'conversation-list__group-label';
    empty.textContent = query ? 'No matches' : 'No conversations yet';
    els.conversationList.appendChild(empty);
    return;
  }
  for (const group of groupByDate(list)) {
    if (group.items.length === 0) continue;
    const label = document.createElement('div');
    label.className = 'conversation-list__group-label';
    label.textContent = group.label;
    els.conversationList.appendChild(label);
    for (const conv of group.items) els.conversationList.appendChild(createConversationItemEl(conv));
  }
}

function createConversationItemEl(conv) {
  const item = document.createElement('div');
  item.className = 'conversation-item' + (conv.id === state.activeConversationId ? ' is-active' : '');
  item.innerHTML = `<span class="conversation-item__title">${escapeHtml(conv.title || 'New chat')}</span><button type="button" class="conversation-item__menu-btn" aria-label="More options">${ICONS.dots}</button>`;
  item.addEventListener('click', (e) => {
    if (e.target.closest('.conversation-item__menu-btn') || e.target.closest('.conversation-menu')) return;
    selectConversation(conv.id);
  });
  item.querySelector('.conversation-item__menu-btn').addEventListener('click', (e) => {
    e.stopPropagation();
    toggleConversationMenu(item, conv);
  });
  return item;
}

function closeAllConversationMenus() {
  document.querySelectorAll('.conversation-menu').forEach((m) => m.remove());
  document.querySelectorAll('.conversation-item__menu-btn.is-open').forEach((b) => b.classList.remove('is-open'));
}
document.addEventListener('click', closeAllConversationMenus);

function toggleConversationMenu(item, conv) {
  const btn = item.querySelector('.conversation-item__menu-btn');
  const wasOpen = btn.classList.contains('is-open');
  closeAllConversationMenus();
  if (wasOpen) return;
  const menu = document.createElement('div');
  menu.className = 'conversation-menu';
  menu.innerHTML = `<button type="button" data-menu="rename">${ICONS.edit}<span>Rename</span></button><button type="button" class="danger" data-menu="delete">${ICONS.trash}<span>Delete</span></button>`;
  item.appendChild(menu);
  btn.classList.add('is-open');
  menu.querySelector('[data-menu="rename"]').addEventListener('click', (e) => { e.stopPropagation(); closeAllConversationMenus(); openRenameModal(conv); });
  menu.querySelector('[data-menu="delete"]').addEventListener('click', (e) => { e.stopPropagation(); closeAllConversationMenus(); openDeleteConfirm(conv); });
}

// ---------------------------------------------------------
// View switching
// ---------------------------------------------------------
function showChatView() { els.welcomeScreen.hidden = true; els.messages.hidden = false; }
function showWelcomeView() { els.welcomeScreen.hidden = false; els.messages.hidden = true; els.messages.innerHTML = ''; }

function selectConversation(id) {
  setActiveConversationId(id);
  const conv = getActiveConversation();
  if (!conv) { showWelcomeView(); } else { showChatView(); renderMessages(conv); }
  renderSidebarConversations();
  closeMobileSidebar();
}

function createNewChat() {
  setActiveConversationId(null);
  showWelcomeView();
  clearPendingAttachments();
  els.chatInput.value = '';
  autoResizeTextarea();
  renderSidebarConversations();
  closeMobileSidebar();
  els.chatInput.focus();
}

// ---------------------------------------------------------
// Message rendering
// ---------------------------------------------------------
function renderMessages(conv) {
  els.messages.innerHTML = '';
  for (const msg of conv.messages) els.messages.appendChild(createMessageEl(msg));
  scrollToBottom(true);
}

function createMessageEl(msg) {
  const wrap = document.createElement('div');
  wrap.className = 'message message--' + msg.role + (msg.error ? ' message--error' : '');
  wrap.dataset.id = msg.id;

  let html = '';
  if (msg.role === 'assistant') html += `<div class="message__avatar">${logoSvgMarkup(30)}</div>`;
  html += '<div class="message__col">';

  if (msg.role === 'assistant' && msg.reasoning) {
    html += `<div class="thinking"><button type="button" class="thinking-toggle">${ICONS.chevronRight}<span>Show thinking</span></button><div class="thinking-body">${escapeHtml(msg.reasoning)}</div></div>`;
  }
  if (msg.attachments && msg.attachments.length) {
    html += '<div class="message__attachments">';
    for (const att of msg.attachments) {
      if (att.dataUrl && att.type === 'image') html += `<img src="${att.dataUrl}" alt="${escapeHtml(att.name || 'image')}" />`;
      else if (att.dataUrl && att.type === 'video') html += `<video src="${att.dataUrl}" controls></video>`;
    }
    html += '</div>';
  }
  if (msg.error) {
    html += `<div class="message__bubble"><span>⚠️ ${escapeHtml(msg.error)}</span><div><button type="button" class="btn-retry" data-action="retry">Retry</button></div></div>`;
  } else {
    html += `<div class="message__bubble${msg.role === 'assistant' ? ' message__bubble--assistant' : ''}">${renderMarkdownSafe(msg.content)}</div>`;
  }
  if (!msg.error) {
    if (msg.role === 'user') {
      html += `<div class="message__actions"><button type="button" data-action="edit">${ICONS.edit}<span>Edit</span></button><button type="button" data-action="copy">${ICONS.copy}<span>Copy</span></button></div>`;
    } else {
      html += `<div class="message__actions"><button type="button" data-action="copy">${ICONS.copy}<span>Copy</span></button><button type="button" data-action="regenerate">${ICONS.refresh}<span>Regenerate</span></button></div>`;
    }
  }
  html += '</div>';
  wrap.innerHTML = html;
  enhanceMessageContent(wrap.querySelector('.message__bubble'));
  return wrap;
}

// Delegated handler for all message-level interactions (works for both
// statically-rendered and live-streamed messages without re-binding listeners).
function handleMessagesClick(e) {
  const thinkingToggle = e.target.closest('.thinking-toggle');
  if (thinkingToggle) {
    const box = thinkingToggle.closest('.thinking');
    box.classList.toggle('is-open');
    thinkingToggle.querySelector('span').textContent = box.classList.contains('is-open') ? 'Hide thinking' : 'Show thinking';
    return;
  }
  const copyCodeBtn = e.target.closest('.code-block__copy');
  if (copyCodeBtn) {
    const codeEl = copyCodeBtn.closest('.code-block').querySelector('code');
    navigator.clipboard.writeText(codeEl.textContent).then(() => flashLabel(copyCodeBtn.querySelector('span'), 'Copied'));
    return;
  }
  const actionBtn = e.target.closest('[data-action]');
  if (!actionBtn) return;
  const messageEl = actionBtn.closest('.message');
  const conv = getActiveConversation();
  if (!conv || !messageEl) return;
  const msg = conv.messages.find((m) => m.id === messageEl.dataset.id);
  if (!msg) return;
  const action = actionBtn.dataset.action;
  if (action === 'copy') {
    navigator.clipboard.writeText(msg.content || '').then(() => flashLabel(actionBtn.querySelector('span'), 'Copied'));
  } else if (action === 'edit') {
    editMessage(conv, msg.id);
  } else if (action === 'regenerate' || action === 'retry') {
    regenerateFrom(conv, msg.id);
  }
}
function flashLabel(spanEl, text) {
  if (!spanEl) return;
  const original = spanEl.textContent;
  spanEl.textContent = text;
  setTimeout(() => { spanEl.textContent = original; }, 1400);
}

function editMessage(conv, msgId) {
  if (state.isGenerating) { showToast('Please wait for the current response to finish.', 'error'); return; }
  const idx = conv.messages.findIndex((m) => m.id === msgId);
  if (idx === -1) return;
  const msg = conv.messages[idx];
  els.chatInput.value = msg.content || '';
  state.pendingAttachments = (msg.attachments || []).map((a) => ({ ...a }));
  renderAttachmentPreview();
  autoResizeTextarea();
  conv.messages = conv.messages.slice(0, idx);
  conv.updatedAt = nowISO();
  renderMessages(conv);
  saveConversations();
  renderSidebarConversations();
  els.chatInput.focus();
}

function regenerateFrom(conv, msgId) {
  if (state.isGenerating) { showToast('Please wait for the current response to finish.', 'error'); return; }
  const idx = conv.messages.findIndex((m) => m.id === msgId);
  if (idx === -1) return;
  conv.messages = conv.messages.slice(0, idx);
  renderMessages(conv);
  saveConversations();
  streamAssistantReply(conv);
}

// ---------------------------------------------------------
// Sending messages + streaming
// ---------------------------------------------------------
async function sendUserMessage(rawText, attachments) {
  if (state.isGenerating) return;
  const text = (rawText || '').trim();
  if (!text && attachments.length === 0) return;

  const model = getActiveModel();
  for (const att of attachments) {
    if (att.type === 'video' && !model.supportsVideo) {
      showToast(`"${model.label}" doesn't support video input. Switch models in Settings first.`, 'error');
      return;
    }
  }

  let conv = getActiveConversation();
  if (!conv) {
    conv = { id: uuid(), title: '', createdAt: nowISO(), updatedAt: nowISO(), messages: [] };
    state.conversations.unshift(conv);
    setActiveConversationId(conv.id);
  }

  const userMsg = { id: uuid(), role: 'user', content: text, attachments, reasoning: null, error: null, createdAt: nowISO() };
  conv.messages.push(userMsg);
  if (!conv.title) conv.title = deriveTitle(text, attachments);
  conv.updatedAt = nowISO();

  clearPendingAttachments();
  els.chatInput.value = '';
  autoResizeTextarea();
  showChatView();
  els.messages.appendChild(createMessageEl(userMsg));
  saveConversations();
  renderSidebarConversations();
  scrollToBottom(true);

  await streamAssistantReply(conv);
}

async function streamAssistantReply(conv) {
  const model = getActiveModel();
  const assistantMsg = { id: uuid(), role: 'assistant', content: '', reasoning: '', attachments: [], error: null, createdAt: nowISO() };
  conv.messages.push(assistantMsg);

  const wrap = document.createElement('div');
  wrap.className = 'message message--assistant';
  wrap.dataset.id = assistantMsg.id;
  wrap.innerHTML = `<div class="message__avatar">${logoSvgMarkup(30)}</div><div class="message__col"><div class="message__bubble message__bubble--assistant"><span class="typing-dots"><span></span><span></span><span></span></span></div></div>`;
  els.messages.appendChild(wrap);
  scrollToBottom(true);

  const colEl = wrap.querySelector('.message__col');
  let bubbleEl = wrap.querySelector('.message__bubble');
  let thinkingEl = null;
  let firstContentChunk = true;

  setGenerating(true);
  const controller = new AbortController();
  state.abortController = controller;

  const payloadMessages = conv.messages
    .filter((m) => m.id !== assistantMsg.id)
    .map((m) => ({ role: m.role, content: m.content || '', attachments: (m.attachments || []).map(({ type, mime, name, dataUrl }) => ({ type, mime, name, dataUrl })) }));

  try {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ model: model.id, reasoningEffort: state.settings.reasoningEffort, messages: payloadMessages }),
      signal: controller.signal,
    });

    if (!res.ok) {
      const errBody = await res.json().catch(() => ({}));
      throw new Error(errBody.error || `Server error (${res.status}).`);
    }
    if (!res.body) throw new Error('Streaming is not supported in this browser.');

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop();
      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed.startsWith('data:')) continue;
        const data = trimmed.slice(5).trim();
        if (!data || data === '[DONE]') continue;
        let json;
        try { json = JSON.parse(data); } catch (e) { continue; }
        if (json.error) throw new Error(json.error.message || 'The model returned an error.');
        const delta = (json.choices && json.choices[0] && json.choices[0].delta) || {};
        const reasoningPiece = delta.reasoning || delta.reasoning_content;
        if (reasoningPiece) {
          assistantMsg.reasoning += reasoningPiece;
          if (!thinkingEl) {
            thinkingEl = document.createElement('div');
            thinkingEl.className = 'thinking is-open';
            thinkingEl.innerHTML = `<button type="button" class="thinking-toggle">${ICONS.chevronRight}<span>Hide thinking</span></button><div class="thinking-body"></div>`;
            colEl.insertBefore(thinkingEl, bubbleEl);
          }
          thinkingEl.querySelector('.thinking-body').textContent = assistantMsg.reasoning;
          scrollToBottom();
        }
        if (delta.content) {
          if (firstContentChunk) { bubbleEl.innerHTML = ''; firstContentChunk = false; }
          assistantMsg.content += delta.content;
          bubbleEl.innerHTML = renderMarkdownSafe(assistantMsg.content);
          enhanceMessageContent(bubbleEl);
          scrollToBottom();
        }
      }
    }

    if (!assistantMsg.content && !assistantMsg.reasoning) {
      throw new Error('The model returned an empty response. Please try again.');
    }
    if (!assistantMsg.content) {
      bubbleEl.innerHTML = renderMarkdownSafe('*(Only a reasoning trace was returned — no final answer text.)*');
    }
    if (thinkingEl) {
      thinkingEl.classList.remove('is-open');
      thinkingEl.querySelector('.thinking-toggle span').textContent = 'Show thinking';
    }
    const actions = document.createElement('div');
    actions.className = 'message__actions';
    actions.innerHTML = `<button type="button" data-action="copy">${ICONS.copy}<span>Copy</span></button><button type="button" data-action="regenerate">${ICONS.refresh}<span>Regenerate</span></button>`;
    colEl.appendChild(actions);
  } catch (err) {
    if (err.name === 'AbortError') {
      if (!assistantMsg.content) { bubbleEl.innerHTML = '<em>Stopped.</em>'; }
    } else {
      assistantMsg.error = err.message || 'Something went wrong.';
      wrap.classList.add('message--error');
      bubbleEl.outerHTML = `<div class="message__bubble"><span>⚠️ ${escapeHtml(assistantMsg.error)}</span><div><button type="button" class="btn-retry" data-action="retry">Retry</button></div></div>`;
      showToast(assistantMsg.error, 'error');
    }
  } finally {
    setGenerating(false);
    state.abortController = null;
    conv.updatedAt = nowISO();
    saveConversations();
    renderSidebarConversations();
  }
}

function setGenerating(isGen) {
  state.isGenerating = isGen;
  els.btnSend.hidden = isGen;
  els.btnStop.hidden = !isGen;
  updateSendButtonState();
}

// ---------------------------------------------------------
// Composer: textarea, attachments, drag & drop
// ---------------------------------------------------------
function autoResizeTextarea() {
  const ta = els.chatInput;
  ta.style.height = 'auto';
  ta.style.height = Math.min(ta.scrollHeight, 200) + 'px';
  updateSendButtonState();
}
function updateSendButtonState() {
  const hasText = els.chatInput.value.trim().length > 0;
  const hasAttachments = state.pendingAttachments.length > 0;
  els.btnSend.disabled = state.isGenerating || !(hasText || hasAttachments);
}

function fileToDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

async function handleFilesSelected(fileList) {
  const files = Array.from(fileList || []);
  const model = getActiveModel();
  for (const file of files) {
    const isImage = ALLOWED_IMAGE_MIME.includes(file.type);
    const isVideo = ALLOWED_VIDEO_MIME.includes(file.type);
    if (!isImage && !isVideo) { showToast(`Unsupported file type: ${file.name}`, 'error'); continue; }
    if (isVideo && !model.supportsVideo) { showToast(state.videoDisabledReason || `"${model.label}" doesn't support video. Switch models in Settings first.`, 'error'); continue; }
    const limit = isVideo ? state.maxVideoBytes : state.maxImageBytes;
    if (file.size > limit) { showToast(`"${file.name}" exceeds the ${Math.floor(limit / (1024 * 1024))}MB limit.`, 'error'); continue; }
    try {
      const dataUrl = await fileToDataUrl(file);
      state.pendingAttachments.push({ id: uuid(), type: isImage ? 'image' : 'video', mime: file.type, name: file.name, dataUrl });
    } catch (e) {
      showToast(`Failed to read "${file.name}".`, 'error');
    }
  }
  renderAttachmentPreview();
}
function removePendingAttachment(id) {
  state.pendingAttachments = state.pendingAttachments.filter((a) => a.id !== id);
  renderAttachmentPreview();
}
function clearPendingAttachments() { state.pendingAttachments = []; renderAttachmentPreview(); }

function renderAttachmentPreview() {
  const wrap = els.attachmentPreview;
  wrap.innerHTML = '';
  if (state.pendingAttachments.length === 0) { wrap.hidden = true; updateSendButtonState(); return; }
  wrap.hidden = false;
  for (const att of state.pendingAttachments) {
    const chip = document.createElement('div');
    chip.className = 'attachment-chip';
    chip.innerHTML = (att.type === 'image' ? `<img src="${att.dataUrl}" alt="" />` : `<span class="attachment-chip__icon">${ICONS.video}</span>`)
      + `<span class="attachment-chip__label">${escapeHtml(att.name || '')}</span><button type="button" class="attachment-chip__remove" aria-label="Remove attachment">×</button>`;
    chip.querySelector('.attachment-chip__remove').addEventListener('click', () => removePendingAttachment(att.id));
    wrap.appendChild(chip);
  }
  updateSendButtonState();
}

// ---------------------------------------------------------
// Scrolling
// ---------------------------------------------------------
function isNearBottomRaw() {
  const el = els.chatScroll;
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80;
}
function scrollToBottom(force) {
  if (force) userScrolledUp = false;
  if (!userScrolledUp) els.chatScroll.scrollTop = els.chatScroll.scrollHeight;
}

// ---------------------------------------------------------
// Modals
// ---------------------------------------------------------
function showModal(modalEl) { modalEl.hidden = false; }
function hideModal(modalEl) { modalEl.hidden = true; }

function openRenameModal(conv) {
  els.renameInput.value = conv.title || '';
  showModal(els.renameModal);
  els.renameInput.focus();
  els.renameInput.select();
  els.btnRenameSave.onclick = () => {
    const newTitle = els.renameInput.value.trim();
    if (newTitle) { conv.title = newTitle; saveConversations(); renderSidebarConversations(); }
    hideModal(els.renameModal);
  };
}

function openDeleteConfirm(conv) {
  els.confirmTitle.textContent = 'Delete this chat?';
  els.confirmMessage.textContent = `"${conv.title || 'New chat'}" will be permanently deleted.`;
  els.btnConfirmOk.textContent = 'Delete';
  showModal(els.confirmModal);
  els.btnConfirmOk.onclick = () => {
    state.conversations = state.conversations.filter((c) => c.id !== conv.id);
    if (state.activeConversationId === conv.id) { setActiveConversationId(null); showWelcomeView(); }
    saveConversations();
    renderSidebarConversations();
    hideModal(els.confirmModal);
  };
}

function openClearHistoryConfirm() {
  els.confirmTitle.textContent = 'Clear all chat history?';
  els.confirmMessage.textContent = 'This permanently deletes every conversation stored on this device. This cannot be undone.';
  els.btnConfirmOk.textContent = 'Clear all';
  showModal(els.confirmModal);
  els.btnConfirmOk.onclick = () => {
    state.conversations = [];
    setActiveConversationId(null);
    saveConversations();
    renderSidebarConversations();
    showWelcomeView();
    hideModal(els.confirmModal);
    showToast('Chat history cleared.', 'success');
  };
}

function openSettingsModal() { renderSettingsControls(); showModal(els.settingsModal); }

function buildSegmented(container, options, activeValue, onChange) {
  container.innerHTML = '';
  for (const opt of options) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.textContent = opt.label;
    btn.className = opt.value === activeValue ? 'is-active' : '';
    btn.addEventListener('click', () => onChange(opt.value));
    container.appendChild(btn);
  }
}

function renderSettingsControls() {
  buildSegmented(els.settingTheme, [{ label: 'Dark', value: 'dark' }, { label: 'Midnight', value: 'midnight' }], state.settings.theme, (val) => {
    state.settings.theme = val; applyTheme(); saveSettings(); renderSettingsControls();
  });
  buildSegmented(els.settingFontsize, [{ label: 'Small', value: 'sm' }, { label: 'Medium', value: 'md' }, { label: 'Large', value: 'lg' }], state.settings.fontSize, (val) => {
    state.settings.fontSize = val; applyFontSize(); saveSettings(); renderSettingsControls();
  });
  buildSegmented(els.settingReasoning, [{ label: 'Off', value: 'none' }, { label: 'Low', value: 'low' }, { label: 'Medium', value: 'medium' }, { label: 'High', value: 'high' }], state.settings.reasoningEffort, (val) => {
    state.settings.reasoningEffort = val; saveSettings(); renderSettingsControls();
  });
  renderModelList();
}

function renderModelList() {
  els.modelList.innerHTML = '';
  for (const m of state.models) {
    const card = document.createElement('button');
    card.type = 'button';
    card.className = 'model-card' + (m.id === state.settings.model ? ' is-active' : '');
    card.innerHTML = `<div class="model-card__top"><span class="model-card__name">${escapeHtml(m.label)}</span><span class="model-card__tag">${escapeHtml(m.tag || '')}</span></div><div class="model-card__desc">${escapeHtml(m.description || '')}</div>`;
    card.addEventListener('click', () => {
      state.settings.model = m.id;
      saveSettings();
      renderModelList();
      updateModelStatusUI();
    });
    els.modelList.appendChild(card);
  }
}

function applyTheme() { document.documentElement.dataset.theme = state.settings.theme; }
function applyFontSize() { document.documentElement.dataset.fontsize = state.settings.fontSize; }
function updateModelStatusUI() {
  const model = getActiveModel();
  els.modelStatusLabel.textContent = model.label;
  els.aboutModelLabel.textContent = model.label;
}

async function checkHealth() {
  try {
    const res = await fetch('/api/health');
    const data = await res.json();
    els.modelStatusDot.classList.toggle('is-off', !data.configured);
    if (!data.configured) {
      showToast('The server has no OPENROUTER_API_KEY configured — add it to .env and restart the server.', 'error');
    }
  } catch (e) {
    els.modelStatusDot.classList.add('is-off');
  }
}

// ---------------------------------------------------------
// Sidebar open/close (mobile) & collapse (desktop)
// ---------------------------------------------------------
function openMobileSidebar() { els.sidebar.classList.add('is-open'); els.sidebarOverlay.hidden = false; }
function closeMobileSidebar() { els.sidebar.classList.remove('is-open'); els.sidebarOverlay.hidden = true; }

// ---------------------------------------------------------
// DOM refs + event wiring
// ---------------------------------------------------------
function grabDomRefs() {
  const $ = (id) => document.getElementById(id);
  return {
    sidebar: $('sidebar'), sidebarOverlay: $('sidebar-overlay'),
    btnCollapseSidebar: $('btn-collapse-sidebar'), btnNewChatSidebar: $('btn-new-chat-sidebar'),
    searchInput: $('search-conversations'), conversationList: $('conversation-list'),
    btnOpenSettings: $('btn-open-settings'),
    btnOpenSidebar: $('btn-open-sidebar'), modelStatusBtn: $('model-status-btn'),
    modelStatusDot: $('model-status-dot'), modelStatusLabel: $('model-status-label'),
    btnNewChatHeader: $('btn-new-chat-header'),
    chatScroll: $('chat-scroll'), welcomeScreen: $('welcome-screen'), messages: $('messages'),
    suggestionCards: $('suggestion-cards'),
    composerForm: $('composer-form'), attachmentPreview: $('attachment-preview'),
    btnAttach: $('btn-attach'), fileInput: $('file-input'), chatInput: $('chat-input'),
    btnSend: $('btn-send'), btnStop: $('btn-stop'),
    dropOverlay: $('drop-overlay'),
    settingsModal: $('settings-modal'), settingTheme: $('setting-theme'), settingFontsize: $('setting-fontsize'),
    modelList: $('model-list'), settingReasoning: $('setting-reasoning'),
    btnClearHistory: $('btn-clear-history'), aboutModelLabel: $('about-model-label'),
    confirmModal: $('confirm-modal'), confirmTitle: $('confirm-title'), confirmMessage: $('confirm-message'),
    btnConfirmCancel: $('btn-confirm-cancel'), btnConfirmOk: $('btn-confirm-ok'),
    renameModal: $('rename-modal'), renameInput: $('rename-input'),
    btnRenameCancel: $('btn-rename-cancel'), btnRenameSave: $('btn-rename-save'),
    toastContainer: $('toast-container'),
  };
}

function wireEventListeners() {
  els.btnOpenSidebar.addEventListener('click', openMobileSidebar);
  els.sidebarOverlay.addEventListener('click', closeMobileSidebar);
  els.btnCollapseSidebar.addEventListener('click', () => {
    state.settings.sidebarCollapsed = !state.settings.sidebarCollapsed;
    els.sidebar.classList.toggle('is-collapsed', state.settings.sidebarCollapsed);
    saveSettings();
  });

  els.btnNewChatSidebar.addEventListener('click', createNewChat);
  els.btnNewChatHeader.addEventListener('click', createNewChat);
  els.searchInput.addEventListener('input', renderSidebarConversations);
  els.btnOpenSettings.addEventListener('click', openSettingsModal);
  els.modelStatusBtn.addEventListener('click', openSettingsModal);

  els.suggestionCards.addEventListener('click', (e) => {
    const card = e.target.closest('.suggestion-card');
    if (!card) return;
    els.chatInput.value = card.dataset.prompt;
    autoResizeTextarea();
    els.chatInput.focus();
  });

  els.messages.addEventListener('click', handleMessagesClick);

  els.chatInput.addEventListener('input', autoResizeTextarea);
  els.chatInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); els.composerForm.requestSubmit(); }
  });
  els.composerForm.addEventListener('submit', (e) => {
    e.preventDefault();
    if (state.isGenerating) return;
    sendUserMessage(els.chatInput.value, state.pendingAttachments.slice());
  });
  els.btnStop.addEventListener('click', () => { if (state.abortController) state.abortController.abort(); });
  els.btnAttach.addEventListener('click', () => els.fileInput.click());
  els.fileInput.addEventListener('change', (e) => { handleFilesSelected(e.target.files); e.target.value = ''; });

  els.chatScroll.addEventListener('scroll', () => { userScrolledUp = !isNearBottomRaw(); });

  window.addEventListener('dragenter', (e) => {
    if (!e.dataTransfer || !Array.from(e.dataTransfer.types || []).includes('Files')) return;
    dragCounter++;
    els.dropOverlay.hidden = false;
  });
  window.addEventListener('dragleave', () => {
    dragCounter = Math.max(0, dragCounter - 1);
    if (dragCounter === 0) els.dropOverlay.hidden = true;
  });
  window.addEventListener('dragover', (e) => e.preventDefault());
  window.addEventListener('drop', (e) => {
    e.preventDefault();
    dragCounter = 0;
    els.dropOverlay.hidden = true;
    if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length) handleFilesSelected(e.dataTransfer.files);
  });

  document.querySelectorAll('[data-close-modal]').forEach((el) => el.addEventListener('click', (e) => hideModal(e.target.closest('.modal'))));
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') document.querySelectorAll('.modal:not([hidden])').forEach(hideModal); });

  els.btnClearHistory.addEventListener('click', openClearHistoryConfirm);
  els.btnConfirmCancel.addEventListener('click', () => hideModal(els.confirmModal));
  els.btnRenameCancel.addEventListener('click', () => hideModal(els.renameModal));
}

// ---------------------------------------------------------
// Init
// ---------------------------------------------------------
async function init() {
  els = grabDomRefs();
  state.settings = loadSettings();
  state.conversations = loadConversations();

  applyTheme();
  applyFontSize();
  if (state.settings.sidebarCollapsed) els.sidebar.classList.add('is-collapsed');

  wireEventListeners();

  try {
    const res = await fetch('/api/models');
    const data = await res.json();
    state.models = (data.models && data.models.length) ? data.models : FALLBACK_MODELS;
    state.defaultModel = data.default || 'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free';
    if (typeof data.maxImageBytes === 'number') state.maxImageBytes = data.maxImageBytes;
    if (typeof data.maxVideoBytes === 'number') state.maxVideoBytes = data.maxVideoBytes;
    state.videoDisabledReason = data.videoDisabledReason || '';
    if (!state.maxVideoBytes) {
      els.fileInput.accept = ALLOWED_IMAGE_MIME.join(',');
    }
  } catch (e) {
    state.models = FALLBACK_MODELS;
  }
  if (!state.settings.model || !state.models.some((m) => m.id === state.settings.model)) {
    state.settings.model = state.defaultModel;
    saveSettings();
  }
  updateModelStatusUI();
  checkHealth();

  renderSidebarConversations();
  state.activeConversationId = state.settings.activeConversationId || null;
  const conv = getActiveConversation();
  if (conv) { showChatView(); renderMessages(conv); } else { state.activeConversationId = null; showWelcomeView(); }
  renderSidebarConversations();
  autoResizeTextarea();
}

document.addEventListener('DOMContentLoaded', init);
