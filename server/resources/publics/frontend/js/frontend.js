window.NOTE_WIDTH = 625;
window.NOTE_OVERLAP = 75;

function noteWindowSizeAdjust() {
  const windowWidth = window.innerWidth;
  const notesContainer = document.querySelector("#notes");
  if (!notesContainer) return;
  
  const allNotes = notesContainer.querySelectorAll(".note");
  const noteCount = allNotes.length;
  
  if (windowWidth <= NOTE_WIDTH) {
    allNotes.forEach((note, i) => {
      note.classList.remove("stacked");
      if (i < noteCount - 1) {
        note.classList.add("hidden");
      } else {
        note.classList.remove("hidden");
      }
      note.style.left = "";
    });
    notesContainer.style.width = "";
    return;
  }
  
  const fullWidth = noteCount * NOTE_WIDTH;
  const needsStacking = windowWidth < fullWidth;
  
  allNotes.forEach((note, i) => {
    note.classList.remove("hidden");
    
    if (needsStacking) {
      note.style.left = (i * NOTE_OVERLAP) + "px";
      note.classList.add("stacked");
    } else {
      note.style.left = (i * NOTE_WIDTH) + "px";
      note.classList.remove("stacked");
    }
  });

  const totalWidth = needsStacking 
    ? (noteCount - 1) * NOTE_OVERLAP + NOTE_WIDTH
    : noteCount * NOTE_WIDTH;
  notesContainer.style.width = Math.max(totalWidth, windowWidth) + "px";
  
  requestAnimationFrame(() => {
    notesContainer.scrollTo({
      left: notesContainer.scrollWidth,
      behavior: "smooth",
    });
  });
}

function highlightNoteById(id) {
  const note = document.getElementById(id);
  if (!note) return null;
  
  note.style.transition = "background-color 0.3s ease, transform 0.3s ease";
  note.style.backgroundColor = "lightyellow";
  note.style.transform = "scale(1.01)";
  
  setTimeout(() => {
    note.style.backgroundColor = "";
    note.style.transform = "scale(1)";
  }, 500);

  note.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
  return note;
}

function initializeSyntaxHighlighting() {
  if (typeof hljs !== 'undefined') {
    document.querySelectorAll('pre code').forEach((block) => {
      if (!block.classList.contains('hljs')) {
        hljs.highlightElement(block);
      }
    });
  }
}

function initializeMathRendering() {
  if (typeof katex !== 'undefined') {
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

htmx.on("htmx:beforeSwap", (evt) => {
  const target = evt.target;
  if (target.className && target.className.includes("note") && target.id && target.id.startsWith("note")) {
    const targetElement = document.getElementById(target.id);
    if (targetElement) {
      let nextSibling = targetElement.nextElementSibling;
      while (nextSibling) {
        const elementToRemove = nextSibling;
        nextSibling = nextSibling.nextElementSibling;
        elementToRemove.parentNode.removeChild(elementToRemove);
      }
    }
  }
});

htmx.on("htmx:afterSwap", (evt) => {
  const notes = document.querySelectorAll(".note");
  const titles = [];
  notes.forEach(note => {
    const h1 = note.querySelector('article h1');
    if (h1) {
      titles.push(h1.textContent);
    }
  });
  if (titles.length > 0) {
    document.title = titles.join(' | ');
  }
  
  initializeSyntaxHighlighting();
  initializeMathRendering();
  noteWindowSizeAdjust();
});

htmx.on("htmx:beforeRequest", (evt) => {
  const target = evt.target;
  const href = target.getAttribute("href");
  if (!href) return;
  
  const noteId = href.split('/')[1];
  if (!noteId) return;
  
  const pathSegments = window.location.pathname.split('+').filter(s => s);
  for (let path of pathSegments) {
    const cleanPath = path.replace(/^\//, '');
    if (cleanPath === noteId) {
      evt.preventDefault();
      highlightNoteById(`note-${noteId}`);
      return;
    }
  }
  
  const currentNote = target.closest('.note');
  removeNotesAfter(currentNote);
});

document.addEventListener('DOMContentLoaded', () => {
  initializeSyntaxHighlighting();
  initializeMathRendering();
  noteWindowSizeAdjust();
  setupInternalLinkInterception();
});

function setupInternalLinkInterception() {
  document.body.addEventListener('click', handleInternalLinkClick);
}

function removeNotesAfter(noteElement) {
  if (!noteElement) return;
  let nextSibling = noteElement.nextElementSibling;
  while (nextSibling) {
    const toRemove = nextSibling;
    nextSibling = nextSibling.nextElementSibling;
    toRemove.remove();
  }
}

function handleInternalLinkClick(e) {
  const link = e.target.closest('a.internal-link');
  if (!link) return;
  
  e.preventDefault();
  
  const href = link.getAttribute('href');
  if (!href) return;
  
  const noteId = href.replace(/^\//, '').split('/')[0];
  if (!noteId) return;
  
  const pathSegments = window.location.pathname.split('+').filter(s => s && s !== '/');
  for (let path of pathSegments) {
    const cleanPath = path.replace(/^\//, '');
    if (cleanPath === noteId) {
      highlightNoteById(`note-${noteId}`);
      return;
    }
  }
  
  const currentNote = link.closest('.note');
  removeNotesAfter(currentNote);
  
  const fromNoteId = currentNote?.id?.replace('note-', '');
  
  htmx.ajax('GET', `/${noteId}`, {
    target: '#notes',
    swap: 'beforeend',
    headers: fromNoteId ? { 'X-From-Note-Id': fromNoteId } : {}
  });
}

window.addEventListener('resize', noteWindowSizeAdjust);
