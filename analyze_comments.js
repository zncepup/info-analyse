const fs = require('fs');
const html = fs.readFileSync('debug_comment_page.html', 'utf8');

// 找 Modal 的完整结构
// 从 css-19q29v6 开始（Modal 外层）
const modal19q29v6 = html.indexOf('css-19q29v6');
if (modal19q29v6 > 0) {
    console.log('=== css-19q29v6 (Modal 外层) 附近 ===');
    console.log(html.substring(modal19q29v6 - 50, modal19q29v6 + 500));
}

// 找 css-1aq8hf9 的样式
const aq8hf9Style = html.match(/\.css-1aq8hf9\{[^}]+\}/g) || [];
console.log('\n=== css-1aq8hf9 样式定义 ===');
aq8hf9Style.forEach(s => console.log(s));

// 找 css-1e7fksk 的样式
const e7fkskStyle = html.match(/\.css-1e7fksk\{[^}]+\}/g) || [];
console.log('\n=== css-1e7fksk 样式定义 ===');
e7fkskStyle.forEach(s => console.log(s));

// 找所有 css-xxx 的样式定义中包含 overflow 的
const cssOverflow = html.match(/\.css-[a-z0-9]+\{[^}]*overflow[^}]+\}/g) || [];
console.log('\n=== 包含 overflow 的 css 样式 ===');
cssOverflow.slice(0, 10).forEach(s => console.log(s));
