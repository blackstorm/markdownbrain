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

/**
 * Show notification with auto-dismiss
 * @param {string} message - Notification message
 * @param {string} type - Notification type: 'success', 'error', 'warning', 'info'
 * @param {number} duration - Duration in milliseconds (default: 3000)
 */
function showNotification(message, type = 'info', duration = 3000) {
  const container = document.getElementById('notification-container');
  if (!container) return;

  const notification = document.createElement('div');

  const colors = {
    success: { bg: '#f0fdf4', border: '#86efac', text: '#166534', icon: 'check-circle' },
    error: { bg: '#fef2f2', border: '#fca5a5', text: '#991b1b', icon: 'alert-circle' },
    info: { bg: '#eff6ff', border: '#93c5fd', text: '#1e40af', icon: 'info' },
    warning: { bg: '#fef3c7', border: '#fbbf24', text: '#92400e', icon: 'alert-triangle' }
  };

  const colorScheme = colors[type] || colors.info;

  notification.style.cssText = `
    background: ${colorScheme.bg};
    border: 1px solid ${colorScheme.border};
    color: ${colorScheme.text};
    padding: 0.75rem 1rem;
    margin-bottom: 0.5rem;
    font-size: 0.875rem;
    display: flex;
    align-items: center;
    gap: 0.5rem;
    min-width: 280px;
    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
    animation: slideIn 0.2s ease-out;
  `;

  notification.innerHTML = `
    <i data-lucide="${colorScheme.icon}" style="width: 1rem; height: 1rem; flex-shrink: 0;"></i>
    <span>${message}</span>
  `;

  container.appendChild(notification);

  // Initialize icons if lucide is available
  if (typeof lucide !== 'undefined') {
    lucide.createIcons();
  }

  // Auto-dismiss
  setTimeout(() => {
    notification.style.animation = 'slideOut 0.2s ease-in';
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

// Add CSS for animations
const style = document.createElement('style');
style.textContent = `
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
`;
document.head.appendChild(style);

// Initialize
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
