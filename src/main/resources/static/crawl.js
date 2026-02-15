/* ===== Crawl & Analysis Tab ===== */
let allTasks = [], taskPageSize = 5, taskPageIdx = 0;
let crawlInited = false;

function initCrawlTab() {
  if (!crawlInited) {
    crawlInited = true;
    populateCrawlSyncSelect();
    $('fetch-form').addEventListener('submit', onFetchSubmit);
    $('sync-form').addEventListener('submit', onSyncSubmit);
  }
  refreshTasks();
}

function populateCrawlSyncSelect() {
  const sel = $('sync-user');
  sel.innerHTML = '';
  if (!authorCache.length) { sel.innerHTML = '<option value="">请先添加作者</option>'; return; }
  authorCache.forEach(a => {
    const o = document.createElement('option');
    o.value = a.userId; o.textContent = a.authorName || a.userId;
    sel.appendChild(o);
  });
  const dang = [...sel.options].find(o => o.value.toLowerCase().includes('dang'));
  if (dang) sel.value = dang.value;
}

async function onFetchSubmit(e) {
  e.preventDefault();
  const btn = e.target.querySelector('button[type=submit]');
  const url = $('fetch-url').value.trim();
  if (!url) { showToast('请输入链接'); return; }
  btn.disabled = true;
  try { await postJson('/api/zhihu/fetch', {url, save:true, withComments:$('fetch-with-comments').checked}); showToast('任务已创建'); refreshTasks(); }
  catch(e) { showToast(e.message); }
  finally { btn.disabled = false; }
}

async function onSyncSubmit(e) {
  e.preventDefault();
  const btn = e.target.querySelector('button[type=submit]');
  const userId = $('sync-user').value;
  if (!userId) { showToast('请先选择作者'); return; }
  btn.disabled = true;
  try { await postJson('/api/zhihu/sync', {userId, limit:Number($('sync-limit').value||3), withComments:$('sync-with-comments').checked}); showToast('任务已创建'); refreshTasks(); }
  catch(e) { showToast(e.message); }
  finally { btn.disabled = false; }
}

const fmtRemaining = (ms) => { if (!ms || ms <= 0) return ''; const s = Math.ceil(ms/1000); return s < 60 ? '约'+s+'秒' : '约'+Math.ceil(s/60)+'分钟'; };
const chevronSvg = '<svg width="6" height="10" viewBox="0 0 6 10" fill="none"><path d="M1 1l4 4-4 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';

function renderPhases(phases) {
  if (!phases || !Object.keys(phases).length) return '';
  let html = '<div class="phase-grid">';
  for (const [name, p] of Object.entries(phases)) {
    const pct = p.total > 0 ? Math.round((p.done / p.total) * 100) : 0;
    const color = p.failed > 0 ? 'var(--orange)' : p.done >= p.total ? 'var(--green)' : 'var(--tint)';
    let detail = '';
    if (p.remaining > 0) detail += '待处理 ' + p.remaining;
    if (p.failed > 0) detail += (detail ? ' · ' : '') + '失败 ' + p.failed;
    if (p.skipped > 0) detail += (detail ? ' · ' : '') + '跳过 ' + p.skipped;
    html += '<div class="phase-chip"><div class="phase-name">' + name + '</div>'
      + '<div class="phase-bar"><div class="phase-track"><div class="phase-track-fill" style="width:'+pct+'%;background:'+color+'"></div></div>'
      + '<span class="phase-count">'+p.done+'/'+p.total+'</span></div>'
      + (detail ? '<div class="phase-detail">'+detail+'</div>' : '') + '</div>';
  }
  return html + '</div>';
}

async function refreshTasks() {
  try {
    allTasks = await fetchJson('/api/tasks');
    if (taskPageIdx > 0 && taskPageIdx >= Math.ceil(allTasks.length / taskPageSize))
      taskPageIdx = Math.max(0, Math.ceil(allTasks.length / taskPageSize) - 1);
    renderTaskPage();
  } catch(e) {}
}

function renderTaskPage() {
  const taskList = $('task-list'), taskPager = $('task-pager');
  taskList.innerHTML = ''; taskPager.innerHTML = '';
  if (!allTasks.length) { taskList.innerHTML = '<li class="list-cell empty">暂无任务</li>'; return; }
  const start = taskPageIdx * taskPageSize;
  allTasks.slice(start, start + taskPageSize).forEach((t, i) => {
    const idx = start + i;
    const cls = t.status==='COMPLETED'?'tag-ok':t.status==='FAILED'?'tag-err':t.status==='RUNNING'?'tag-run':'tag-pending';
    const labels = {PENDING:'排队中',RUNNING:'进行中',COMPLETED:'已完成',FAILED:'失败'};
    const msg = t.error||t.message||'';
    const li = document.createElement('li');
    li.className = 'list-cell task-cell';
    let progressHtml = '';
    if (t.status === 'RUNNING') {
      const remaining = fmtRemaining(t.estimatedRemainingMs);
      const stepInfo = t.currentStep || '';
      progressHtml = '<div class="progress-track"><div class="progress-fill" style="width:'+t.progress+'%"></div></div>'
        + '<div class="progress-meta">' + (t.totalSteps > 0 ? t.completedSteps+'/'+t.totalSteps+' 项' : '')
        + (remaining ? ' · 剩余'+remaining : '') + (stepInfo ? '<br>'+stepInfo : '') + '</div>' + renderPhases(t.phases);
    }
    let itemsHtml = '';
    if (t.completedItems && t.completedItems.length > 0) {
      const itemId = 'task-items-'+idx, btnId = 'task-btn-'+idx;
      itemsHtml = '<button class="disclosure-btn" id="'+btnId+'" onclick="var l=document.getElementById(\''+itemId+'\'),b=document.getElementById(\''+btnId+'\');l.classList.toggle(\'open\');b.classList.toggle(\'open\')">'
        + chevronSvg + ' 已完成 ' + t.completedItems.length + ' 项</button>'
        + '<div class="disclosure-list" id="'+itemId+'">' + t.completedItems.map(i => '<div>'+i+'</div>').join('') + '</div>';
    }
    li.innerHTML = '<div class="task-head"><div><div class="task-title">'+t.title+'</div>'
      + '<div class="task-sub">'+fmt(t.createdAt)+(msg && t.status!=='RUNNING'?' · '+msg:'')+'</div></div>'
      + '<span class="tag '+cls+'">'+(labels[t.status]||t.status)+'</span></div>' + progressHtml + itemsHtml;
    taskList.appendChild(li);
  });
  const totalPages = Math.ceil(allTasks.length / taskPageSize);
  if (totalPages > 1) {
    taskPager.innerHTML = '<button class="pager-btn" '+(taskPageIdx<=0?'disabled':'')+' onclick="taskPageIdx--;renderTaskPage()">上一页</button>'
      + '<span>'+(taskPageIdx+1)+' / '+totalPages+'</span>'
      + '<button class="pager-btn" '+(taskPageIdx>=totalPages-1?'disabled':'')+' onclick="taskPageIdx++;renderTaskPage()">下一页</button>';
  }
}

setInterval(refreshTasks, 5000);
