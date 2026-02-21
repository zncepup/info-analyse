/* ===== Search & Browse Tab ===== */
let browseInited = false;
let browseOffset = 0, browseLimit = 20, browseTotal = 0;
let browseResults = [];
let selectedItems = new Set();
let selectedSearchTags = [];
let searchTagDropdownOpen = false;

function initBrowseTab() {
  if (!browseInited) {
    browseInited = true;
    $('search-input').addEventListener('keydown', (e) => { if (e.key === 'Enter') doSearch(); });
  }
  // 确保 tagCache 已加载
  const ready = tagCache.length ? Promise.resolve() : refreshTags();
  ready.then(() => {
    populateSearchAuthor();
    renderSearchTagDropdown();
    if (!browseResults.length) doSearch();
  });
}

function populateSearchAuthor() {
  const sel = $('search-author');
  const prev = sel.value;
  sel.innerHTML = '<option value="">全部作者</option>';
  authorCache.forEach(a => {
    const o = document.createElement('option');
    o.value = a.authorName || a.userId;
    o.textContent = a.authorName || a.userId;
    sel.appendChild(o);
  });
  if (prev) sel.value = prev;
}

// ===== Tag selector in search =====
function renderSearchTagDropdown() {
  const dd = $('search-tag-dropdown');
  dd.innerHTML = '';
  tagCache.forEach(t => {
    const item = document.createElement('div');
    item.className = 'tag-picker-item';
    const checked = selectedSearchTags.includes(t.id);
    item.innerHTML = '<span class="tag-dot" style="background:'+(t.color||'#007AFF')+'"></span>'
      + '<span>'+t.tagName+'</span>' + (checked ? '<span class="tag-check">✓</span>' : '');
    item.onclick = (e) => { e.stopPropagation(); toggleSearchTag(t.id); };
    dd.appendChild(item);
  });
  if (!tagCache.length) dd.innerHTML = '<div class="tag-picker-item" style="color:var(--label-tertiary)">暂无标签</div>';
  renderSearchTagChips();
}

function toggleSearchTag(tagId) {
  const idx = selectedSearchTags.indexOf(tagId);
  if (idx >= 0) selectedSearchTags.splice(idx, 1); else selectedSearchTags.push(tagId);
  renderSearchTagDropdown();
}

function renderSearchTagChips() {
  const c = $('search-tag-chips');
  c.innerHTML = '';
  if (!selectedSearchTags.length) { c.innerHTML = '<span class="tag-placeholder">点击选择标签</span>'; return; }
  selectedSearchTags.forEach(id => {
    const t = tagCache.find(x => x.id === id);
    if (!t) return;
    const chip = document.createElement('span');
    chip.className = 'tag-chip'; chip.style.background = t.color || '#007AFF';
    chip.innerHTML = t.tagName + ' <span class="chip-x" onclick="event.stopPropagation();toggleSearchTag('+id+')">&times;</span>';
    c.appendChild(chip);
  });
}

function toggleSearchTagDropdown(e) {
  e.stopPropagation();
  searchTagDropdownOpen = !searchTagDropdownOpen;
  $('search-tag-dropdown').classList.toggle('open', searchTagDropdownOpen);
}

// ===== Reset search =====
function resetSearch() {
  $('search-input').value = '';
  $('search-author').value = '';
  $('search-type').value = '';
  selectedSearchTags = [];
  renderSearchTagDropdown();
  populateSearchAuthor();
  doSearch();
}

// ===== Search execution =====
async function doSearch(offset) {
  if (offset === undefined) offset = 0;
  browseOffset = offset;

  const q = $('search-input').value.trim();
  const author = $('search-author').value;
  const type = $('search-type').value;
  const tags = selectedSearchTags.join(',');

  const resultList = $('browse-results');
  resultList.innerHTML = '<li class="list-cell empty">加载中...</li>';
  selectedItems.clear();
  updateExportToolbar();

  try {
    let params = 'limit=' + browseLimit + '&offset=' + browseOffset;
    if (q) params += '&q=' + encodeURIComponent(q);
    if (author) params += '&author=' + encodeURIComponent(author);
    if (type) params += '&type=' + encodeURIComponent(type);
    if (tags) params += '&tags=' + tags;

    const data = await fetchJson('/api/search/advanced?' + params);
    browseResults = data.results || [];
    browseTotal = data.total || 0;

    $('browse-title').textContent = q ? '搜索结果 (' + browseTotal + ')' : '全部内容 (' + browseTotal + ')';
    renderBrowseResults(q);
    renderBrowsePager();
  } catch(e) {
    resultList.innerHTML = '<li class="list-cell empty">加载失败: '+e.message+'</li>';
  }
}

function renderBrowseResults(q) {
  const list = $('browse-results');
  list.innerHTML = '';
  if (!browseResults.length) { list.innerHTML = '<li class="list-cell empty">暂无内容</li>'; return; }

  const typeLabels = {answer:'回答',article:'文章',pin:'想法',guba_post:'股吧帖子'};
  const hl = (text) => {
    if (!text || !q) return text || '';
    const s = text.replace(/</g,'&lt;').replace(/>/g,'&gt;');
    const escaped = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return s.replace(new RegExp(escaped, 'gi'), function(m) { return '<mark style="background:var(--orange-bg);color:var(--orange);padding:0 2px;border-radius:3px">'+m+'</mark>'; });
  };

  browseResults.forEach((r, idx) => {
    const li = document.createElement('li');
    li.className = 'list-cell file-cell';
    const checked = selectedItems.has(idx) ? ' checked' : '';
    const link = r.link || '#';
    const qParam = q ? '?q=' + encodeURIComponent(q) : '';

    // Tags
    let tagsHtml = '';
    if (r.tags && r.tags.length) {
      r.tags.forEach(t => {
        tagsHtml += '<span class="content-tag" style="background:'+t.color+'20;color:'+t.color+'">'+t.name+'</span>';
      });
    }

    // Source info for tagging
    const source = r.source || 'zhihu';
    const tgtType = r.type === 'guba_post' ? 'post' : r.type;
    const tgtId = String(r.targetId || '');

    let html = '<div style="display:flex;gap:10px;align-items:flex-start;width:100%">'
      + '<div class="sr-checkbox'+checked+'" onclick="toggleBrowseSelect('+idx+',this)"></div>'
      + '<div class="file-info" style="flex:1">'
      + '<a href="'+link+qParam+'" class="file-link" target="_blank">'+(r.title ? hl(r.title) : '(内容)')+'</a>'
      + '<div class="file-meta">'
      + '<span class="tag tag-ok" style="margin-right:6px">'+(typeLabels[r.type]||r.type)+'</span>'
      + (r.authorName ? r.authorName + ' · ' : '')
      + (r.time ? new Date(r.time).toLocaleDateString('zh-CN') : '')
      + '</div>';
    if (tagsHtml) html += '<div class="file-tags">' + tagsHtml + '</div>';
    if (r.snippet) {
      html += '<div style="font-size:13px;color:var(--label-secondary);margin-top:4px;line-height:1.4;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">'+hl(r.snippet)+'</div>';
    }
    html += '</div>'
      + '</div>';
    li.innerHTML = html;
    list.appendChild(li);
  });
}

function renderBrowsePager() {
  const pager = $('browse-pager');
  const totalPages = Math.ceil(browseTotal / browseLimit);
  const curPage = Math.floor(browseOffset / browseLimit) + 1;
  if (totalPages <= 1) { pager.innerHTML = ''; return; }
  pager.innerHTML = '<button class="pager-btn" '+(curPage<=1?'disabled':'')+' onclick="doSearch('+(browseOffset-browseLimit)+')">上一页</button>'
    + '<span>'+curPage+' / '+totalPages+'</span>'
    + '<button class="pager-btn" '+(curPage>=totalPages?'disabled':'')+' onclick="doSearch('+(browseOffset+browseLimit)+')">下一页</button>';
}

// ===== Selection & Export =====
function toggleBrowseSelect(idx, el) {
  if (selectedItems.has(idx)) { selectedItems.delete(idx); el.classList.remove('checked'); }
  else { selectedItems.add(idx); el.classList.add('checked'); }
  updateExportToolbar();
}

function toggleSelectAll() {
  const btn = $('select-all-btn');
  if (selectedItems.size === browseResults.length) {
    selectedItems.clear(); btn.textContent = '全选';
  } else {
    browseResults.forEach((_, i) => selectedItems.add(i)); btn.textContent = '取消全选';
  }
  document.querySelectorAll('#browse-results .sr-checkbox').forEach((el, i) => {
    el.classList.toggle('checked', selectedItems.has(i));
  });
  updateExportToolbar();
}

function updateExportToolbar() {
  const toolbar = $('export-toolbar');
  if (selectedItems.size > 0) {
    toolbar.classList.add('show');
    $('export-toolbar-info').textContent = '已选 ' + selectedItems.size + ' 项';
  } else { toolbar.classList.remove('show'); }
}

function openExportModal() { $('export-modal').classList.add('open'); }
function closeExportModal() { $('export-modal').classList.remove('open'); }

async function doBatchExport() {
  if (!selectedItems.size) { showToast('请先选择内容'); return; }
  closeExportModal();
  const contents = [];
  selectedItems.forEach(idx => {
    const r = browseResults[idx];
    if (!r) return;
    const source = r.source || 'zhihu';
    const targetType = r.type === 'guba_post' ? 'post' : r.type;
    const targetId = String(r.targetId || '');
    if (targetId) contents.push({ source, targetId, targetType });
  });
  if (!contents.length) { showToast('无有效内容可导出'); return; }
  try {
    await postJson('/api/export/batch', {
      contents,
      includeBody: $('export-body').checked,
      includeComments: $('export-comments').checked,
      includeAi: $('export-ai').checked
    });
    showToast('导出任务已创建');
    if (typeof refreshTasks === 'function') refreshTasks();
  } catch(e) { showToast('导出失败: ' + e.message); }
}

// ===== Tag picker on browse items =====
function openBrowseTagPicker(btn, source, targetId, targetType, resultIdx) {
  document.querySelectorAll('.file-tag-dd').forEach(d => d.remove());
  const dd = document.createElement('div');
  dd.className = 'tag-picker-dropdown open file-tag-dd';
  dd.style.cssText = 'position:absolute;top:100%;right:0;z-index:30';
  dd.onclick = (e) => e.stopPropagation();

  const r = browseResults[resultIdx];
  const currentTagIds = r && r.tags ? r.tags.map(t => t.id) : [];

  tagCache.forEach(t => {
    const item = document.createElement('div');
    item.className = 'tag-picker-item';
    const has = currentTagIds.includes(t.id);
    item.innerHTML = '<span class="tag-dot" style="background:'+(t.color||'#007AFF')+'"></span>'
      + '<span>'+t.tagName+'</span>' + (has ? '<span class="tag-check">✓</span>' : '');
    item.onclick = async () => {
      try {
        if (has) {
          await fetchJson('/api/tags/'+t.id+'/contents', {
            method:'DELETE', headers:{'Content-Type':'application/json'},
            body: JSON.stringify({source, targetId, targetType})
          });
        } else {
          await postJson('/api/tags/'+t.id+'/contents', {source, targetId, targetType});
        }
        dd.remove();
        // Refresh current page
        doSearch(browseOffset);
      } catch(e) { showToast(e.message); }
    };
    dd.appendChild(item);
  });
  if (!tagCache.length) dd.innerHTML = '<div class="tag-picker-item" style="color:var(--label-tertiary)">请先创建标签</div>';
  btn.parentElement.appendChild(dd);
  setTimeout(() => {
    const closer = (e) => { if (!dd.contains(e.target)) { dd.remove(); document.removeEventListener('click', closer); } };
    document.addEventListener('click', closer);
  }, 0);
}

// ===== Batch Delete =====
async function doBatchDelete() {
  if (!selectedItems.size) { showToast('请先选择内容'); return; }
  if (!confirm('确定删除选中的 ' + selectedItems.size + ' 项内容？关联的评论和AI分析也会一并删除，此操作不可撤销。')) return;

  let ok = 0, fail = 0;
  for (const idx of selectedItems) {
    const r = browseResults[idx];
    if (!r) continue;
    const source = r.source || 'zhihu';
    const targetType = r.type === 'guba_post' ? 'post' : r.type;
    const targetId = r.targetId;
    try {
      await fetchJson('/api/outputs/' + source + '/' + targetType + '/' + targetId, { method: 'DELETE' });
      ok++;
    } catch(e) { fail++; }
  }
  showToast('已删除 ' + ok + ' 项' + (fail ? '，失败 ' + fail + ' 项' : ''));
  selectedItems.clear();
  updateExportToolbar();
  doSearch(browseOffset);
}

// ===== Batch Tag =====
let batchTagSelected = new Set();

function openBatchTagModal() {
  if (!selectedItems.size) { showToast('请先选择内容'); return; }
  batchTagSelected.clear();
  renderBatchTagList();
  $('batch-tag-modal').classList.add('open');
}

function closeBatchTagModal() {
  $('batch-tag-modal').classList.remove('open');
}

function renderBatchTagList() {
  const container = $('batch-tag-list');
  container.innerHTML = '';
  if (!tagCache.length) {
    container.innerHTML = '<span style="font-size:13px;color:var(--label-tertiary)">暂无标签，请先在管理页创建</span>';
    return;
  }
  tagCache.forEach(t => {
    const chip = document.createElement('span');
    const active = batchTagSelected.has(t.id);
    chip.style.cssText = 'display:inline-flex;align-items:center;padding:6px 12px;border-radius:8px;font-size:13px;font-weight:600;cursor:pointer;transition:all 0.15s;'
      + (active ? 'background:'+t.color+';color:#fff' : 'background:'+t.color+'20;color:'+t.color);
    chip.textContent = t.tagName;
    chip.onclick = () => {
      if (batchTagSelected.has(t.id)) batchTagSelected.delete(t.id);
      else batchTagSelected.add(t.id);
      renderBatchTagList();
    };
    container.appendChild(chip);
  });
}

async function doBatchTag() {
  if (!batchTagSelected.size) { showToast('请选择至少一个标签'); return; }
  closeBatchTagModal();

  const contents = [];
  selectedItems.forEach(idx => {
    const r = browseResults[idx];
    if (!r) return;
    const source = r.source || 'zhihu';
    const targetType = r.type === 'guba_post' ? 'post' : r.type;
    const targetId = String(r.targetId || '');
    if (targetId) contents.push({ source, targetId, targetType });
  });
  if (!contents.length) { showToast('无有效内容'); return; }

  let ok = 0;
  for (const tagId of batchTagSelected) {
    try {
      await postJson('/api/tags/' + tagId + '/contents/batch', { contents });
      ok++;
    } catch(e) { showToast('标签 ' + tagId + ' 失败: ' + e.message); }
  }
  showToast('已为 ' + contents.length + ' 项内容添加 ' + ok + ' 个标签');
  doSearch(browseOffset);
}
