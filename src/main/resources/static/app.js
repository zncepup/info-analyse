/* ===== Shared Utilities ===== */
const $ = (s) => document.getElementById(s);
const toast = $('toast');
const showToast = (msg) => { toast.textContent = msg; toast.classList.add('show'); setTimeout(() => toast.classList.remove('show'), 2000); };
const fmt = (v) => v ? new Date(v).toLocaleString('zh-CN', {hour12:false}) : '--';
const fetchJson = async (url, opts={}) => {
  const r = await fetch(url, opts);
  if (!r.ok) { let t = r.statusText; try { const d = await r.json(); t = d.message||d.error||t; } catch(e) { t = await r.text(); } throw new Error(t); }
  return r.json();
};
const postJson = (url, body) => fetchJson(url, { method:'POST', headers:{'Content-Type':'application/json'}, body: body ? JSON.stringify(body) : '{}' });

let authorCache = [];
let tagCache = [];

const refreshAuthors = async () => {
  try { authorCache = await fetchJson('/api/zhihu/authors'); } catch(e) { authorCache = []; }
};
const refreshTags = async () => {
  try { tagCache = await fetchJson('/api/tags'); } catch(e) { tagCache = []; }
};

/* ===== Tab Router ===== */
const TABS = ['crawl', 'browse', 'manage'];
const tabItems = document.querySelectorAll('.tab-item');
const tabPanels = document.querySelectorAll('.tab-panel');

function switchTab(name) {
  if (!TABS.includes(name)) name = 'browse';
  tabPanels.forEach(p => p.classList.toggle('active', p.id === 'panel-' + name));
  tabItems.forEach(t => t.classList.toggle('active', t.dataset.tab === name));
  // Trigger tab activation
  if (name === 'crawl' && typeof initCrawlTab === 'function') initCrawlTab();
  if (name === 'browse' && typeof initBrowseTab === 'function') initBrowseTab();
  if (name === 'manage' && typeof initManageTab === 'function') initManageTab();
}

tabItems.forEach(t => {
  t.addEventListener('click', (e) => {
    e.preventDefault();
    const tab = t.dataset.tab;
    location.hash = tab;
  });
});

window.addEventListener('hashchange', () => {
  switchTab(location.hash.replace('#', '') || 'browse');
});

/* ===== Tap Feedback ===== */
const TAP_SEL = 'button, .ios-btn, .ios-btn-ghost, .ios-btn-danger, .ios-btn-sm, .section-action, .pager-btn, .tab-item, .disclosure-btn, [onclick]';
document.addEventListener('touchstart', (e) => { const el = e.target.closest(TAP_SEL); if (el && !el.disabled) el.classList.add('tapped'); }, {passive: true});
document.addEventListener('touchend', () => { setTimeout(() => document.querySelectorAll('.tapped').forEach(el => el.classList.remove('tapped')), 120); }, {passive: true});
document.addEventListener('touchcancel', () => { document.querySelectorAll('.tapped').forEach(el => el.classList.remove('tapped')); }, {passive: true});
document.addEventListener('mousedown', (e) => { const el = e.target.closest(TAP_SEL); if (el && !el.disabled) el.classList.add('tapped'); });
document.addEventListener('mouseup', () => { setTimeout(() => document.querySelectorAll('.tapped').forEach(el => el.classList.remove('tapped')), 120); });

// Close dropdowns on outside click
document.addEventListener('click', (e) => {
  if (e.target.closest('.tag-picker-dropdown') || e.target.closest('.tag-select-chips')) return;
  document.querySelectorAll('.tag-picker-dropdown.open').forEach(d => d.classList.remove('open'));
  if (typeof searchTagDropdownOpen !== 'undefined') searchTagDropdownOpen = false;
});

/* ===== Init ===== */
async function boot() {
  await Promise.all([refreshAuthors(), refreshTags()]);
  const hash = location.hash.replace('#', '') || 'browse';
  switchTab(hash);
}
boot();
