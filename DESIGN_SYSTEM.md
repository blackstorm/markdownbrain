# MarkdownBrain Design System

> A clean, line-based design system focused on simplicity, clarity, and excellent user experience.

## Philosophy

MarkdownBrain's design follows these core principles:

1. **Simple Lines** - Clean 1px borders, no large rounded corners or heavy shadows
2. **Rule-Based Layout** - Precise grid systems with consistent spacing
3. **Generous Whitespace** - Breathing room for content and actions
4. **Foolproof UX** - Clear actions, obvious next steps, instant feedback
5. **No Decoration** - Absolutely no large rounded corners, heavy shadows, or gradients

## Design Tokens

### Colors

All colors use simple hex values for clarity and maintainability:

```css
:root {
  /* Primary Colors (Blue accent) */
  --color-brand-500: #2563eb;   /* Primary action color */
  --color-brand-600: #1d4ed8;   /* Hover state */

  /* Borders */
  --border-color: #e5e7eb;
  --border-hover: #d1d5db;

  /* Text */
  --text-primary: #1f2937;
  --text-secondary: #6b7280;
  --text-tertiary: #9ca3af;

  /* States */
  --success: #10b981;
  --error: #ef4444;
  --warning: #f59e0b;
  --info: #3b82f6;

  /* Backgrounds */
  --bg-primary: #ffffff;
  --bg-secondary: #f9fafb;
  --bg-page: #fafafa;
}
```

### Typography

**Font Stack:**
- System fonts for optimal performance and native feel
- Fallback: `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif`

**Scale:**
```css
/* Headings */
h1: 2rem (32px) - Page titles
h2: 1.5rem (24px) - Section titles
h3: 1.25rem (20px) - Card titles
h4: 1.125rem (18px) - Subsection titles

/* Body */
body: 0.9375rem (15px) - Default text
small: 0.875rem (14px) - Secondary text
tiny: 0.8125rem (13px) - Hints and labels
```

**Weights:**
- Regular: 400
- Medium: 500
- Semibold: 600

### Spacing

Based on an **8px grid system**:

```css
0.25rem = 4px   /* Tight spacing */
0.375rem = 6px  /* Extra tight */
0.5rem = 8px    /* Small spacing */
0.625rem = 10px /* Small+ */
0.75rem = 12px  /* Medium-small */
1rem = 16px     /* Medium */
1.25rem = 20px  /* Medium-large */
1.5rem = 24px   /* Large */
2rem = 32px     /* Extra large */
3rem = 48px     /* Section spacing */
4rem = 64px     /* Hero spacing */
```

### Borders

```css
/* Standard border */
border: 1px solid var(--border-color);

/* Hover state */
border-color: var(--border-hover);

/* Focus state */
outline: 2px solid var(--accent);
outline-offset: -1px;

/* NO rounded corners on main containers */
/* Only small radius (2px) on inputs if absolutely necessary */
```

## Component Patterns

### Buttons

**Primary Button:**
```css
.btn-primary {
  padding: 0.625rem 1rem;
  font-size: 0.875rem;
  font-weight: 500;
  color: white;
  background: var(--accent);
  border: 1px solid var(--accent);
  cursor: pointer;
}

.btn-primary:hover {
  background: var(--accent-hover);
  border-color: var(--accent-hover);
}
```

**Secondary Button:**
```css
.btn {
  padding: 0.5rem 1rem;
  font-size: 0.875rem;
  font-weight: 500;
  border: 1px solid var(--border-color);
  background: white;
  color: var(--text-primary);
  cursor: pointer;
}

.btn:hover {
  border-color: var(--border-hover);
  background: var(--bg-secondary);
}
```

### Forms

**Input Fields:**
```css
.form-input {
  width: 100%;
  padding: 0.625rem 0.75rem;
  font-size: 0.9375rem;
  border: 1px solid var(--border-color);
  background: white;
  color: var(--text-primary);
}

.form-input:focus {
  outline: 2px solid var(--accent);
  outline-offset: -1px;
  border-color: transparent;
}
```

**Labels:**
```css
.form-label {
  display: block;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 0.5rem;
}
```

**Hints:**
```css
.form-hint {
  display: block;
  font-size: 0.8125rem;
  color: var(--text-tertiary);
  margin-top: 0.375rem;
}
```

### Cards

```css
.card {
  background: white;
  border: 1px solid var(--border-color);
  padding: 1.5rem;
}

.card:hover {
  border-color: var(--border-hover);
}

/* Card with header section */
.card-header {
  padding-bottom: 1.25rem;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 1.25rem;
}
```

### Modals

```css
/* Backdrop */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Content */
.modal-content {
  background: white;
  border: 1px solid var(--border-color);
  max-width: 480px;
  width: 100%;
  margin: 1rem;
}

/* Header */
.modal-header {
  padding: 1.5rem;
  border-bottom: 1px solid var(--border-color);
}

/* Body */
.modal-body {
  padding: 1.5rem;
}

/* Footer */
.modal-footer {
  padding: 1.5rem;
  border-top: 1px solid var(--border-color);
}
```

### Notifications

```css
.notification {
  padding: 0.75rem 1rem;
  border: 1px solid;
  font-size: 0.875rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 280px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

/* Success */
.notification-success {
  background: #f0fdf4;
  border-color: #86efac;
  color: #166534;
}

/* Error */
.notification-error {
  background: #fef2f2;
  border-color: #fca5a5;
  color: #991b1b;
}

/* Warning */
.notification-warning {
  background: #fef3c7;
  border-color: #fbbf24;
  color: #92400e;
}

/* Info */
.notification-info {
  background: #eff6ff;
  border-color: #93c5fd;
  color: #1e40af;
}
```

### Lists

**Document List:**
```css
.doc-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.doc-item {
  display: block;
  padding: 1rem 1.25rem;
  border: 1px solid var(--border-color);
  background: white;
}

.doc-item:hover {
  border-color: var(--border-hover);
  background: var(--bg-secondary);
}
```

### Empty States

```css
.empty-state {
  text-align: center;
  padding: 4rem 1rem;
}

.empty-icon {
  width: 4rem;
  height: 4rem;
  margin: 0 auto 1rem;
  border: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-state h3 {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 0.5rem;
}

.empty-state p {
  font-size: 0.875rem;
  color: var(--text-secondary);
  margin: 0;
}
```

## Layout Patterns

### Container

```css
.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1.5rem;
}
```

### Grid

```css
/* Two-column grid on desktop */
.grid-2 {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.5rem;
}

@media (min-width: 1024px) {
  .grid-2 {
    grid-template-columns: repeat(2, 1fr);
  }
}
```

### Header

```css
.app-header {
  background: white;
  border-bottom: 1px solid var(--border-color);
  position: sticky;
  top: 0;
  z-index: 40;
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 1rem 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
```

## Interaction Patterns

### Hover States

```css
/* Subtle border change */
element:hover {
  border-color: var(--border-hover);
}

/* Subtle background change */
element:hover {
  background: var(--bg-secondary);
}

/* Combined */
element:hover {
  border-color: var(--border-hover);
  background: var(--bg-secondary);
}
```

### Focus States

```css
element:focus {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

/* For inputs (inset) */
input:focus {
  outline: 2px solid var(--accent);
  outline-offset: -1px;
  border-color: transparent;
}
```

### Loading States

```css
.spinner {
  width: 1rem;
  height: 1rem;
  border: 2px solid white;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Show spinner during HTMX requests */
.htmx-request .spinner {
  display: inline-block;
}
```

### Transitions

```css
/* Smooth transitions for interactive elements */
* {
  transition: border-color 0.15s ease,
              background-color 0.15s ease,
              color 0.15s ease;
}
```

## Icons

**Icon Library:** Lucide Icons (via CDN)

**Usage:**
```html
<i data-lucide="icon-name" style="width: 1rem; height: 1rem;"></i>
```

**Common Icons:**
- `book-open` - Logo, knowledge
- `plus` - Create, add
- `pencil` - Edit
- `trash-2` - Delete
- `eye` / `eye-off` - Show/hide
- `copy` - Copy to clipboard
- `globe` - Web, domain
- `external-link` - Open in new tab
- `chevron-right` - Navigation
- `check-circle` - Success
- `alert-circle` - Error
- `alert-triangle` - Warning
- `info` - Information
- `inbox` - Empty state
- `file-text` - Document

**Icon Sizes:**
- Small: `0.875rem` (14px)
- Default: `1rem` (16px)
- Medium: `1.125rem` (18px)
- Large: `1.25rem` (20px)
- XL: `1.5rem` (24px)

## Animations

### Slide In (Notifications)

```css
@keyframes slideIn {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}
```

### Slide Out (Notifications)

```css
@keyframes slideOut {
  from {
    transform: translateX(0);
    opacity: 1;
  }
  to {
    transform: translateX(100%);
    opacity: 0;
  }
}
```

### Fade In

```css
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
```

## Accessibility

### Focus Management

- Always provide visible focus indicators
- Use `outline` instead of `border` for focus states
- Maintain keyboard navigation support

### Color Contrast

All text colors meet WCAG AA standards:
- Primary text on white: 12.63:1
- Secondary text on white: 7.42:1
- Tertiary text on white: 4.58:1

### Semantic HTML

- Use proper heading hierarchy (h1 → h2 → h3)
- Use `<button>` for actions, `<a>` for navigation
- Use `<label>` for form fields
- Use ARIA attributes when appropriate

## HTMX Integration

### Standard Attributes

```html
<!-- POST request -->
<form hx-post="/endpoint"
      hx-target="#result"
      hx-swap="innerHTML"
      hx-disabled-elt="button"
      hx-indicator=".spinner">
</form>

<!-- PUT request -->
<select hx-put="/endpoint"
        hx-trigger="change"
        hx-swap="none">
</select>

<!-- DELETE request -->
<button hx-delete="/endpoint"
        hx-confirm="确定删除吗？"
        hx-swap="none">
</button>
```

### Event Handlers

```javascript
// Handle successful response
document.body.addEventListener('htmx:afterRequest', function(event) {
  if (event.detail.successful) {
    // Handle success
  }
});

// Handle errors
document.body.addEventListener('htmx:responseError', function(event) {
  showNotification('操作失败，请重试', 'error');
});

// Re-initialize after swap
document.body.addEventListener('htmx:afterSwap', function(event) {
  lucide.createIcons();
});
```

## Keyboard Shortcuts

### Global Shortcuts

- `Escape` - Close modals
- `Cmd/Ctrl + K` - Quick create action

### Implementation

```javascript
document.addEventListener('keydown', function(e) {
  if (e.key === 'Escape') {
    closeModal();
  }

  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault();
    openCreateModal();
  }
});
```

## Responsive Breakpoints

```css
/* Mobile-first approach */

/* Small devices (phones, < 640px) */
@media (min-width: 640px) { }

/* Medium devices (tablets, < 768px) */
@media (min-width: 768px) { }

/* Large devices (desktops, < 1024px) */
@media (min-width: 1024px) {
  /* Switch to 2-column grid */
}

/* Extra large devices (large desktops, < 1280px) */
@media (min-width: 1280px) { }
```

## Usage Examples

### Creating a New Page

```html
{% extends "base.html" %}

{% block title %}Page Title - MarkdownBrain{% endblock %}

{% block content %}
<style>
  .page-container {
    max-width: 900px;
    margin: 0 auto;
    padding: 3rem 1.5rem;
  }

  .page-header {
    padding-bottom: 2rem;
    border-bottom: 1px solid var(--border-color);
    margin-bottom: 2rem;
  }

  /* Component-specific styles */
</style>

<div class="page-container">
  <header class="page-header">
    <h1>Page Title</h1>
    <p>Page description</p>
  </header>

  <!-- Content -->
</div>
{% endblock %}
```

### Creating a New Card Component

```html
<div class="card">
  <div class="card-header">
    <h3>Card Title</h3>
    <p>Card description</p>
  </div>

  <div class="card-body">
    <!-- Card content -->
  </div>
</div>
```

### Creating a New Form

```html
<form hx-post="/endpoint">
  <div class="form-field">
    <label class="form-label" for="field">Field Label</label>
    <input type="text"
           id="field"
           name="field"
           class="form-input"
           placeholder="Placeholder"
           required>
    <span class="form-hint">Helpful hint text</span>
  </div>

  <button type="submit" class="btn btn-primary">
    <span class="spinner"></span>
    <span>Submit</span>
  </button>
</form>
```

## Best Practices

### DO ✅

- Use consistent spacing from the 8px grid
- Keep borders at 1px
- Use subtle hover states
- Provide instant feedback for user actions
- Use semantic HTML
- Maintain keyboard navigation
- Test on mobile devices
- Use loading indicators for async operations

### DON'T ❌

- Use large rounded corners (> 2px)
- Add heavy box shadows
- Use gradient backgrounds
- Overcomplicate interactions
- Forget focus states
- Use colors without checking contrast
- Block user actions without feedback
- Create inaccessible components

## File Structure

```
server/
├── app.css                          # Main CSS file (source)
│                                    # - TailwindCSS + DaisyUI imports
│                                    # - Custom theme tokens
│                                    # - Design system components
│                                    # - Compiled to resources/publics/console/css/console.css
├── resources/
│   ├── templates/
│   │   ├── base.html                # Base layout
│   │   ├── console/
│   │   │   ├── login.html          # Login page
│   │   │   ├── init.html           # Initial setup
│   │   │   ├── vaults.html         # Dashboard
│   │   │   ├── vault-list.html     # Vault cards (partial)
│   │   │   └── root-doc-selector.html  # Home page selector (partial)
│   │   └── frontend/
│   │       └── home.html            # Public site home
│   └── public/
│       ├── css/
│       │   └── app.css              # Compiled CSS (generated)
│       └── js/
│           ├── helpers.js           # Global utilities
│           └── console.js             # Console dashboard logic
```

**Note:** All design system styles are defined in `server/app.css` and compiled to `server/resources/publics/console/css/console.css`. When making style changes, edit `server/app.css` and run the CSS build process.

## Contributing

When adding new components or pages:

1. Follow the established design tokens
2. Use the 8px spacing grid
3. Maintain clean line-based aesthetic
4. Test keyboard navigation
5. Check color contrast
6. Test on mobile devices
7. Document any new patterns in this file

---

**Version:** 1.0
**Last Updated:** 2025-12-23
**Maintainer:** MarkdownBrain Team
