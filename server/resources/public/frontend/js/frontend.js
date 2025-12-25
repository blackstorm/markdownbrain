/**
 * MarkdownBrain - Andy Matuschak 风格滑动面板管理
 *
 * URL 方案: /:id?stacked=note-1&stacked=note-2&...
 *
 * 功能:
 * 1. 点击内部链接时，在右侧添加新面板
 * 2. 移除右侧所有面板
 * 3. 更新 URL 的 stacked 参数
 * 4. 防止重复加载已存在的笔记
 * 5. 高亮已存在的笔记
 */

// 获取当前所有可见笔记的 ID（从左到右）
function getCurrentNoteIds() {
  const notes = document.querySelectorAll('.note[data-note-id]');
  return Array.from(notes).map(note => note.dataset.noteId);
}

// 构建 stacked 查询参数
function buildStackedParams(noteIds) {
  if (noteIds.length === 0) return '';
  return noteIds.map(id => `stacked=${encodeURIComponent(id)}`).join('&');
}

// 更新 URL（添加 stacked 参数）
// URL 方案: /?stacked=note-1&stacked=note-2&stacked=target-note
// 最左边的笔记在第一个 stacked，最右边的笔记在最后一个 stacked
function updateUrlWithStacked(targetNoteId) {
  const currentIds = getCurrentNoteIds();
  // 将当前所有笔记 + 目标笔记组成新的 stacked 列表
  const allIds = [...currentIds, targetNoteId];
  const stackedParams = allIds.map(id => `stacked=${encodeURIComponent(id)}`).join('&');

  return `/?${stackedParams}`;
}

// 高亮指定笔记（已存在时的视觉反馈）
function highlightNote(noteId) {
  const note = document.getElementById(`note-${noteId}`);
  if (note) {
    note.style.transition = 'background-color 0.3s ease, transform 0.3s ease';
    note.style.backgroundColor = '#fef3c7'; // yellow-100
    note.style.transform = 'scale(1.01)';

    // 滚动到该笔记
    note.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });

    setTimeout(() => {
      note.style.backgroundColor = '';
      note.style.transform = 'scale(1)';
    }, 500);
  }
}

// HTMX: 请求前拦截
htmx.on('htmx:beforeRequest', (evt) => {
  const target = evt.detail.elt;

  // 只处理内部链接和反向链接
  if (!target.classList.contains('internal-link') && !target.classList.contains('backlink-item')) {
    return;
  }

  const targetNoteId = target.dataset.noteId;
  if (!targetNoteId) return;

  // 检查笔记是否已经打开
  const existingNote = document.getElementById(`note-${targetNoteId}`);
  if (existingNote) {
    // 已存在，取消请求并高亮
    evt.preventDefault();
    highlightNote(targetNoteId);
    return;
  }

  // 更新 href 以包含 stacked 参数
  const newUrl = updateUrlWithStacked(targetNoteId);
  evt.detail.path = newUrl;
});

// HTMX: 交换前（移除右侧面板）
htmx.on('htmx:beforeSwap', (evt) => {
  const target = evt.detail.target;

  // 确保 target 是 notes-container
  if (!target.classList.contains('notes-container')) {
    return;
  }

  // 获取最后一个笔记（最右侧）
  const notes = target.querySelectorAll('.note');
  const lastNote = notes[notes.length - 1];

  if (lastNote) {
    // 移除该笔记右侧的所有笔记
    let sibling = lastNote.nextElementSibling;
    while (sibling) {
      const toRemove = sibling;
      sibling = sibling.nextElementSibling;
      toRemove.remove();
    }
  }
});

// HTMX: 交换后（更新浏览器标题和 URL）
htmx.on('htmx:afterSwap', (evt) => {
  // 更新浏览器标题为所有笔记的标题
  const notes = document.querySelectorAll('.note');
  const titles = Array.from(notes)
    .map(note => {
      const h1 = note.querySelector('article h1');
      return h1 ? h1.textContent.trim() : '';
    })
    .filter(t => t);

  if (titles.length > 0) {
    document.title = titles.join(' | ');
  }

  // 初始化代码高亮和数学公式渲染
  initializeSyntaxHighlighting();
  initializeMathRendering();
});

// 初始化代码高亮（Highlight.js）
function initializeSyntaxHighlighting() {
  if (typeof hljs !== 'undefined') {
    document.querySelectorAll('pre code').forEach((block) => {
      if (!block.classList.contains('hljs')) {
        hljs.highlightElement(block);
      }
    });
  }
}

// 初始化数学公式渲染（KaTeX）
function initializeMathRendering() {
  if (typeof renderMathInElement !== 'undefined') {
    // 渲染块级公式
    document.querySelectorAll('.math-block').forEach((el) => {
      if (!el.classList.contains('katex-rendered')) {
        const expr = el.dataset.expr || el.textContent;
        try {
          katex.render(expr, el, {
            displayMode: true,
            throwOnError: false
          });
          el.classList.add('katex-rendered');
        } catch (e) {
          console.error('KaTeX render error:', e);
        }
      }
    });

    // 渲染行内公式
    document.querySelectorAll('.math-inline').forEach((el) => {
      if (!el.classList.contains('katex-rendered')) {
        const expr = el.dataset.expr || el.textContent;
        try {
          katex.render(expr, el, {
            displayMode: false,
            throwOnError: false
          });
          el.classList.add('katex-rendered');
        } catch (e) {
          console.error('KaTeX render error:', e);
        }
      }
    });
  }
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', () => {
  initializeSyntaxHighlighting();
  initializeMathRendering();

  // 为所有内部链接添加 HTMX 属性
  document.querySelectorAll('.internal-link, .backlink-item').forEach(link => {
    link.setAttribute('hx-get', link.getAttribute('href'));
    link.setAttribute('hx-target', '.notes-container');
    link.setAttribute('hx-swap', 'beforeend');
    link.setAttribute('hx-push-url', 'true');
  });

  // 重新处理 HTMX
  if (typeof htmx !== 'undefined') {
    htmx.process(document.body);
  }
});
