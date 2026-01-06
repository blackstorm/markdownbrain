// MarkdownBrain Frontend Helpers

/**
 * Copy text to clipboard with notification
 * @param {string} text - Text to copy
 */
function copyToClipboard(text) {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => {
      showNotification('已复制到剪贴板', 'success');
    }).catch(err => {
      console.error('Failed to copy:', err);
      showNotification('复制失败', 'error');
    });
  } else {
    // Fallback for older browsers
    const textArea = document.createElement('textarea');
    textArea.value = text;
    textArea.style.position = 'fixed';
    textArea.style.left = '-999999px';
    document.body.appendChild(textArea);
    textArea.select();
    try {
      document.execCommand('copy');
      showNotification('已复制到剪贴板', 'success');
    } catch (err) {
      console.error('Failed to copy:', err);
      showNotification('复制失败', 'error');
    }
    document.body.removeChild(textArea);
  }
}

function showNotification(message, type = 'info', duration = 3000) {
  const container = document.getElementById('notification-container');
  if (!container) return;

  const icons = {
    success: 'check-circle',
    error: 'alert-circle',
    info: 'info',
    warning: 'alert-triangle'
  };

  const notification = document.createElement('div');
  notification.className = `toast toast-${type}`;
  notification.innerHTML = `
    <i data-lucide="${icons[type] || icons.info}" class="icon-sm"></i>
    <span>${message}</span>
  `;

  container.appendChild(notification);

  if (typeof lucide !== 'undefined') {
    lucide.createIcons();
  }

  setTimeout(() => {
    notification.classList.add('toast-out');
    setTimeout(() => notification.remove(), 200);
  }, duration);
}

/**
 * Format date string to locale format
 * @param {string} dateStr - Date string to format
 * @returns {string} Formatted date string
 */
function formatDate(dateStr) {
  if (!dateStr) return '';

  const date = new Date(dateStr);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
}

document.addEventListener('DOMContentLoaded', () => {
  console.log('MarkdownBrain frontend initialized');
});

/**
 * Open create modal
 */
function openCreateModal() {
  const modal = document.getElementById('modal-create');
  if (modal) {
    modal.showModal();
  }
}

/**
 * Close create modal
 */
function closeCreateModal() {
  const modal = document.getElementById('modal-create');
  if (modal) {
    modal.classList.add('closing');
    modal.addEventListener('animationend', () => {
      modal.close();
      modal.classList.remove('closing');
    }, { once: true });
  }
}

/**
 * Open edit modal with vault data
 * @param {string} id - Vault ID
 * @param {string} name - Vault name
 * @param {string} domain - Vault domain
 */
function openEditModal(id, name, domain) {
  const modal = document.getElementById('modal-edit');
  if (modal) {
    // Fill form fields
    const vaultIdInput = document.getElementById('edit-vault-id');
    const nameInput = document.getElementById('edit-name');
    const domainInput = document.getElementById('edit-domain');

    if (vaultIdInput) vaultIdInput.value = id;
    if (nameInput) nameInput.value = name;
    if (domainInput) domainInput.value = domain;

    // Update form action URL
    const form = document.getElementById('edit-form');
    if (form) {
      form.setAttribute('hx-put', `/admin/vaults/${id}`);
      // Re-process HTMX attributes after dynamic update
      htmx.process(form);
    }

    modal.showModal();
  }
}

/**
 * Close edit modal
 */
function closeEditModal() {
  const modal = document.getElementById('modal-edit');
  if (modal) {
    modal.classList.add('closing');
    modal.addEventListener('animationend', () => {
      modal.close();
      modal.classList.remove('closing');
    }, { once: true });
  }
}

// Setup backdrop click handlers
document.addEventListener('DOMContentLoaded', () => {
  const createModal = document.getElementById('modal-create');
  const editModal = document.getElementById('modal-edit');

  // Click outside modal content to close
  [createModal, editModal].forEach(modal => {
    if (modal) {
      modal.addEventListener('click', (e) => {
        // Check if clicked on content or its children
        const clickedOnContent = e.target.closest('.modal-content');
        if (!clickedOnContent) {
          // Clicked on backdrop, close modal
          if (modal.id === 'modal-create') {
            closeCreateModal();
          } else if (modal.id === 'modal-edit') {
            closeEditModal();
          }
        }
      });
    }
  });
});

// Expose global functions
window.copyToClipboard = copyToClipboard;
window.showNotification = showNotification;
window.formatDate = formatDate;
window.openCreateModal = openCreateModal;
window.closeCreateModal = closeCreateModal;
window.openEditModal = openEditModal;
window.closeEditModal = closeEditModal;
