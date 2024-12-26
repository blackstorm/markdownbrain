function highlightNoteById(id) {
  const note = document.getElementById(id);
  note.style.transition = "background-color 0.3s ease, transform 0.3s ease";
  note.style.backgroundColor = "lightyellow";
  note.style.transform = "scale(1.01)";
  setTimeout(() => {
    note.style.backgroundColor = "";
    note.style.transform = "scale(1)";
  }, 500);

  return note
}

htmx.on("htmx:beforeSwap", (evt) => {
  const target = evt.target;
  if (
    target.className.includes("note") &&
    target.id != undefined &&
    target.id.startsWith("note")
  ) {
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
  document.title = titles.join('|');
});

htmx.on("htmx:beforeRequest", (evt) => {
  const target = evt.target;
  // /example-id -> example-id
  const href = target.getAttribute("href").split('/')[1];
  const pathSegments = window.location.pathname.split('/');
  for (let path of pathSegments) {
    if (path != "" && path == href) {
      evt.preventDefault();
      highlightNoteById(`note-${href}`);
    }
  }
});

htmx.on("htmx:afterSwap", (evt) => {
  noteWindowSizeAdjust();
});
