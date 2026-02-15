/* ===== Manage Tab: Login, Authors, Tags ===== */
let manageInited = false;
let qrSessionId = null, qrTimer = null;

function initManageTab() {
  if (!manageInited) {
    manageInited = true;
  }
  refreshStatus();
  renderAuthorList();
  renderTagMgmt();
}

// ===== Login Status =====
async function refreshStatus() {
  try {
    const d = await fetchJson('/api/zhihu/status');
    $('status-login').textContent = d.loggedIn ? '已登录' : '未登录';
    $('status-cookies').textContent = d.hasCookies ? '已保存' : '未保存';
  } catch(e) { $('status-login').textContent = '--'; $('status-cookies').textContent = '--'; }
}

// ===== QR Login =====
async function startQrLogin() {
  try {
    if (qrTimer) clearInterval(qrTimer);
    const p = await postJson('/api/zhihu/login/qr/session');
    qrSessionId = p.sessionId;
    renderQr(p);
    qrTimer = setInterval(pollQr, 1800);
  } catch(e) { showToast(e.message); }
}
async function stopQrLogin() {
  if (qrSessionId) { try { await postJson('/api/zhihu/login/qr/session/'+encodeURIComponent(qrSessionId)+'/cancel'); } catch(e){} }
  if (qrTimer) clearInterval(qrTimer);
  qrSessionId = null;
  $('qr-login-state').textContent = '';
  $('qr-login-message').textContent = '';
  $('qr-login-image').style.display = 'none';
}
function renderQr(p) {
  const labels = {WAITING:'等待扫码',SCANNED:'已扫码',SUCCESS:'登录成功',EXPIRED:'已过期',FAILED:'失败'};
  $('qr-login-state').textContent = labels[p.status]||p.status||'';
  $('qr-login-message').textContent = p.message||'';
  if (p.qrImage) { $('qr-login-image').src = p.qrImage; $('qr-login-image').style.display = 'block'; }
}
async function pollQr() {
  if (!qrSessionId) return;
  try {
    const p = await fetchJson('/api/zhihu/login/qr/session/'+encodeURIComponent(qrSessionId));
    renderQr(p);
    if (['SUCCESS','EXPIRED','FAILED'].includes(p.status)) { clearInterval(qrTimer); qrTimer=null; if (p.status==='SUCCESS') refreshStatus(); }
  } catch(e) { clearInterval(qrTimer); qrTimer=null; qrSessionId=null; }
}

// ===== Authors =====
function renderAuthorList() {
  const list = $('author-list');
  list.innerHTML = '';
  if (!authorCache.length) { list.innerHTML = '<li class="list-cell empty">暂无作者，请添加</li>'; return; }
  authorCache.forEach(a => {
    const li = document.createElement('li');
    li.className = 'list-cell';
    li.style.cssText = 'flex-direction:column;align-items:stretch;gap:8px';
    const checked = a.autoAnalyze ? 'checked' : '';
    const profileUrl = a.profileUrl || ('https://www.zhihu.com/people/' + a.userId);
    li.innerHTML = '<div style="display:flex;align-items:center;justify-content:space-between;gap:12px">'
      + '<div style="flex:1;min-width:0">'
      + '<a href="'+profileUrl+'" target="_blank" rel="noopener" style="font-size:15px;font-weight:500;color:var(--tint);text-decoration:none">'+(a.authorName||a.userId)+'</a>'
      + '<div style="font-size:13px;color:var(--label-tertiary)">'+a.userId+'</div></div>'
      + '<button class="ios-btn-danger ios-btn-sm" onclick="deleteAuthor('+a.id+',\''+a.authorName.replace(/'/g,"\\'")+'\')">删除</button></div>'
      + '<div class="toggle-cell" style="padding:0">'
      + '<span style="font-size:13px;color:var(--label-secondary)">同步后自动AI分析</span>'
      + '<label class="ios-switch"><input type="checkbox" '+checked+' onchange="toggleAutoAnalyze('+a.id+',this.checked)"><span class="switch-track"></span></label></div>';
    list.appendChild(li);
  });
}

async function addAuthor() {
  const input = $('author-url-input');
  const url = input.value.trim();
  if (!url) { showToast('请输入知乎主页链接'); return; }
  const btn = $('add-author-btn');
  btn.disabled = true;
  try {
    await postJson('/api/zhihu/authors', { url });
    input.value = ''; showToast('添加成功');
    await refreshAuthors(); renderAuthorList();
    if (typeof populateCrawlSyncSelect === 'function') populateCrawlSyncSelect();
    if (typeof populateSearchAuthor === 'function') populateSearchAuthor();
  } catch(e) { showToast(e.message); }
  finally { btn.disabled = false; }
}

async function deleteAuthor(id, name) {
  if (!confirm('确定删除作者「'+name+'」？')) return;
  try {
    await fetchJson('/api/zhihu/authors/'+id, { method:'DELETE' }); showToast('已删除');
    await refreshAuthors(); renderAuthorList();
    if (typeof populateCrawlSyncSelect === 'function') populateCrawlSyncSelect();
    if (typeof populateSearchAuthor === 'function') populateSearchAuthor();
  } catch(e) { showToast(e.message); }
}

async function toggleAutoAnalyze(id, value) {
  try {
    await fetchJson('/api/zhihu/authors/'+id+'/auto-analyze', {
      method:'PUT', headers:{'Content-Type':'application/json'}, body: JSON.stringify({autoAnalyze: value})
    });
    const a = authorCache.find(x => x.id === id);
    if (a) a.autoAnalyze = value;
    showToast(value ? 'AI分析已开启' : 'AI分析已关闭');
  } catch(e) { showToast(e.message); }
}

// ===== Tags =====
function renderTagMgmt() {
  const list = $('tag-mgmt-list');
  if (!tagCache.length) { list.innerHTML = '<span style="font-size:13px;color:var(--label-tertiary)">暂无标签，请添加</span>'; return; }
  list.innerHTML = '';
  tagCache.forEach(t => {
    const el = document.createElement('span');
    el.className = 'tag-mgmt-item';
    el.style.background = t.color || '#007AFF';
    el.innerHTML = t.tagName + ' <button class="tag-del" onclick="deleteTag('+t.id+',\''+t.tagName.replace(/'/g,"\\'")+'\')">&times;</button>';
    list.appendChild(el);
  });
}

async function createTag() {
  const name = $('tag-name-input').value.trim();
  if (!name) { showToast('请输入标签名'); return; }
  const color = $('tag-color-input').value;
  try {
    await postJson('/api/tags', { tagName: name, color });
    $('tag-name-input').value = '';
    showToast('标签已创建');
    await refreshTags(); renderTagMgmt();
    if (typeof renderSearchTagDropdown === 'function') renderSearchTagDropdown();
  } catch(e) { showToast(e.message); }
}

async function deleteTag(id, name) {
  if (!confirm('确定删除标签「'+name+'」？关联的内容标记也会一并删除。')) return;
  try {
    await fetchJson('/api/tags/'+id, { method:'DELETE' }); showToast('已删除');
    await refreshTags(); renderTagMgmt();
    if (typeof renderSearchTagDropdown === 'function') renderSearchTagDropdown();
  } catch(e) { showToast(e.message); }
}
