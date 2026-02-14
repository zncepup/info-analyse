(function(){
  var q = new URLSearchParams(location.search).get('q');
  if (!q) return;

  // Walk all text nodes in body (covers content, comments, AI analysis, etc.)
  var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
    acceptNode: function(n) {
      var p = n.parentNode;
      if (!p) return NodeFilter.FILTER_REJECT;
      var tag = p.tagName;
      if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT') return NodeFilter.FILTER_REJECT;
      return NodeFilter.FILTER_ACCEPT;
    }
  }, false);

  var nodes = [], node;
  while (node = walker.nextNode()) nodes.push(node);

  var marks = [];
  var esc = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  var re = new RegExp('(' + esc + ')', 'gi');

  nodes.forEach(function(n) {
    if (!re.test(n.textContent)) { re.lastIndex = 0; return; }
    re.lastIndex = 0;
    var span = document.createElement('span');
    span.innerHTML = n.textContent
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(re, '<mark class="sq">$1</mark>');
    n.parentNode.replaceChild(span, n);
    span.querySelectorAll('mark.sq').forEach(function(m) { marks.push(m); });
  });

  if (!marks.length) return;

  // Inject styles
  var st = document.createElement('style');
  st.textContent = 'mark.sq{background:#FF950040;padding:0 1px;border-radius:2px}'
    + 'mark.sq.active{background:#FF9500;color:#fff;border-radius:3px}'
    + '.sq-bar{position:fixed;bottom:60px;left:50%;transform:translateX(-50%);'
    + 'display:flex;align-items:center;gap:10px;'
    + 'background:rgba(255,255,255,0.95);backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);'
    + 'padding:6px 14px;border-radius:20px;box-shadow:0 2px 12px rgba(0,0,0,0.15);z-index:999;font-size:13px}'
    + '.sq-bar button{border:none;background:none;color:#007AFF;font-size:18px;'
    + 'width:32px;height:32px;border-radius:50%;cursor:pointer;display:grid;place-items:center}'
    + '.sq-bar button:active{background:rgba(0,122,255,0.12)}';
  document.head.appendChild(st);

  // Navigation bar
  var bar = document.createElement('div'); bar.className = 'sq-bar';
  var btnUp = document.createElement('button'); btnUp.innerHTML = '&#x25B2;'; btnUp.title = '上一个';
  var info = document.createElement('span');
  var btnDn = document.createElement('button'); btnDn.innerHTML = '&#x25BC;'; btnDn.title = '下一个';
  bar.appendChild(btnUp); bar.appendChild(info); bar.appendChild(btnDn);
  document.body.appendChild(bar);

  var idx = -1;
  function go(i) {
    if (idx >= 0) marks[idx].classList.remove('active');
    idx = i;
    marks[idx].classList.add('active');
    marks[idx].scrollIntoView({ behavior: 'smooth', block: 'center' });
    info.textContent = (idx + 1) + ' / ' + marks.length;
  }
  btnDn.onclick = function() { go((idx + 1) % marks.length); };
  btnUp.onclick = function() { go((idx - 1 + marks.length) % marks.length); };

  setTimeout(function() { go(0); }, 300);
})();
