/**
 * MarkdownBrain - Andy Matuschak Style Sliding Panels
 *
 * URL Scheme: /doc-a+doc-b+doc-c
 *
 * Features:
 * 1. Click internal links to add new panels on the right
 * 2. Remove all panels to the right when clicking existing doc
 * 3. Update URL with + separated document IDs
 * 4. Prevent duplicate loading of existing docs
 * 5. Highlight existing docs when clicked again
 * 6. SEO-friendly URLs in HTML (/doc-id)
 * 7. HTMX handles history and URL updates automatically
 */

// Get all visible doc IDs (left to right)
function getCurrentDocIds() {
  const docs = document.querySelectorAll('.doc[data-doc-id]');
  return Array.from(docs).map(doc => doc.dataset.docId);
}

// Build + separated path from doc IDs
function buildDocPath(docIds) {
  if (docIds.length === 0) return '/';
  return '/' + docIds.join('+');
}

// Update URL with + separated path
// Path: /doc-a+doc-b+target-doc
// Leftmost doc is first, rightmost doc is last
function updateUrlWithDoc(targetDocId) {
  const currentIds = getCurrentDocIds();
  const allIds = [...currentIds, targetDocId];
  return buildDocPath(allIds);
}

// Highlight specified doc (visual feedback when already exists)
function highlightDoc(docId) {
  const doc = document.getElementById(`doc-${docId}`);
  if (doc) {
    doc.style.transition = 'background-color 0.3s ease, transform 0.3s ease';
    doc.style.backgroundColor = '#fef3c7'; // yellow-100
    doc.style.transform = 'scale(1.01)';

    // Scroll to the doc
    doc.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });

    setTimeout(() => {
      doc.style.backgroundColor = '';
      doc.style.transform = 'scale(1)';
    }, 500);
  }
}

// HTMX: Intercept before request
htmx.on('htmx:beforeRequest', (evt) => {
  const target = evt.detail.elt;

  // Only handle internal links and backlinks
  if (!target.classList.contains('internal-link') && !target.classList.contains('backlink-item')) {
    return;
  }

  const targetDocId = target.dataset.docId;
  if (!targetDocId) return;

  // Check if doc is already open
  const existingDoc = document.getElementById(`doc-${targetDocId}`);
  if (existingDoc) {
    // Already exists, cancel request and highlight
    evt.preventDefault();
    highlightDoc(targetDocId);
    return;
  }

  // Build new path with + separator
  const newPath = updateUrlWithDoc(targetDocId);

  // Update request path to /doc-id
  evt.detail.path = `/${targetDocId}`;

  // Set hx-push-url to the new + separated path
  target.setAttribute('hx-push-url', newPath);
});

// HTMX: Before swap (remove panels to the right)
htmx.on('htmx:beforeSwap', (evt) => {
  const target = evt.detail.target;

  // Ensure target is docs-container
  if (!target.classList.contains('docs-container')) {
    return;
  }

  // Get the last doc (rightmost)
  const docs = target.querySelectorAll('.doc');
  const lastDoc = docs[docs.length - 1];

  if (lastDoc) {
    // Remove all docs to the right of this doc
    let sibling = lastDoc.nextElementSibling;
    while (sibling) {
      const toRemove = sibling;
      sibling = sibling.nextElementSibling;
      toRemove.remove();
    }
  }
});

// HTMX: After swap (update browser title and URL)
htmx.on('htmx:afterSwap', (evt) => {
  // Update browser title with all doc titles
  const docs = document.querySelectorAll('.doc');
  const titles = Array.from(docs)
    .map(doc => {
      const h1 = doc.querySelector('article h1');
      return h1 ? h1.textContent.trim() : '';
    })
    .filter(t => t);

  if (titles.length > 0) {
    document.title = titles.join(' | ');
  }

  // Initialize syntax highlighting and math rendering
  initializeSyntaxHighlighting();
  initializeMathRendering();
});

// Initialize syntax highlighting (Highlight.js)
function initializeSyntaxHighlighting() {
  if (typeof hljs !== 'undefined') {
    document.querySelectorAll('pre code').forEach((block) => {
      if (!block.classList.contains('hljs')) {
        hljs.highlightElement(block);
      }
    });
  }
}

// Initialize math rendering (KaTeX)
function initializeMathRendering() {
  if (typeof renderMathInElement !== 'undefined') {
    // Render block formulas
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

    // Render inline formulas
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

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
  initializeSyntaxHighlighting();
  initializeMathRendering();

  // Add HTMX attributes to all internal links
  document.querySelectorAll('.internal-link, .backlink-item').forEach(link => {
    link.setAttribute('hx-get', link.getAttribute('href'));
    link.setAttribute('hx-target', '.docs-container');
    link.setAttribute('hx-swap', 'beforeend');
    // hx-push-url will be set dynamically in beforeRequest
  });

  // Reprocess HTMX
  if (typeof htmx !== 'undefined') {
    htmx.process(document.body);
  }
});
