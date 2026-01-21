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
      form.setAttribute('hx-put', `/console/vaults/${id}`);
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

function filterNotes(vaultId, query) {
  const container = document.getElementById(`note-selector-${vaultId}`);
  if (!container) return;
  
  const items = container.querySelectorAll('.note-item');
  const q = query.toLowerCase().trim();
  
  items.forEach(item => {
    const path = item.dataset.path;
    item.style.display = (!q || path.includes(q)) ? '' : 'none';
  });
}

function toggleNoteSelector(vaultId) {
  const container = document.getElementById(`note-selector-${vaultId}`);
  if (!container) return;
  
  const wasOpen = container.classList.contains('open');
  
  document.querySelectorAll('.note-selector.open').forEach(el => el.classList.remove('open'));
  
  if (!wasOpen) {
    container.classList.add('open');
    const input = container.querySelector('.note-search-input');
    if (input) {
      input.value = '';
      filterNotes(vaultId, '');
      setTimeout(() => input.focus(), 10);
    }
  }
}

function handleNoteSelect(vaultId, element, event) {
  const container = document.getElementById(`note-selector-${vaultId}`);
  if (!container) return;

  if (event.detail.successful) {
    container.querySelectorAll('.note-item').forEach(item => {
      item.classList.remove('selected');
      const icon = item.querySelector('.note-check');
      if (icon) icon.remove();
    });
    
    element.classList.add('selected');
    if (!element.querySelector('.note-check')) {
      const icon = document.createElement('i');
      icon.setAttribute('data-lucide', 'check');
      icon.className = 'icon-xs note-check';
      element.appendChild(icon);
      lucide.createIcons();
    }
    
    const trigger = container.querySelector('.note-selector-value');
    if (trigger) {
      trigger.textContent = element.dataset.display;
    }
    
    container.classList.remove('open');
    showNotification('Homepage updated', 'success');
  } else {
    showNotification('Update failed', 'error');
  }
}

document.addEventListener('click', (e) => {
  if (!e.target.closest('.note-selector')) {
    document.querySelectorAll('.note-selector.open').forEach(el => el.classList.remove('open'));
  }
});

window.filterNotes = filterNotes;
window.toggleNoteSelector = toggleNoteSelector;
window.handleNoteSelect = handleNoteSelect;

// ============================================================
// Vault Actions Menu
// ============================================================

function toggleActionMenu(button) {
  const menu = button.nextElementSibling;
  const isOpen = menu.classList.contains('open');
  document.querySelectorAll('.action-menu.open').forEach(m => m.classList.remove('open'));
  if (!isOpen) {
    menu.classList.add('open');
  }
}

document.addEventListener('click', function(e) {
  if (!e.target.closest('.vault-actions')) {
    document.querySelectorAll('.action-menu.open').forEach(m => m.classList.remove('open'));
  }
});

function toggleSyncKey(button, vaultId) {
  const keyElement = document.getElementById(`key-${vaultId}`);
  const fullKey = keyElement.dataset.key;
  const currentText = keyElement.textContent.trim();
  const isHidden = currentText.includes('*');
  const icon = button.querySelector('i');

  if (isHidden) {
    keyElement.textContent = fullKey;
    icon.setAttribute('data-lucide', 'eye-off');
    button.title = 'Hide';
  } else {
    const masked = fullKey.substring(0, 8) + '******' + fullKey.substring(fullKey.length - 8);
    keyElement.textContent = masked;
    icon.setAttribute('data-lucide', 'eye');
    button.title = 'Show';
  }
  lucide.createIcons();
}

window.toggleActionMenu = toggleActionMenu;
window.toggleSyncKey = toggleSyncKey;

// ============================================================
// Logo Upload & Delete
// ============================================================

function uploadLogo(input, vaultId) {
  const file = input.files[0];
  if (!file) return;

  const maxSize = 2 * 1024 * 1024;
  if (file.size > maxSize) {
    showNotification('File too large. Maximum size is 2MB.', 'error');
    return;
  }

  const allowedTypes = ['image/png', 'image/jpeg'];
  if (!allowedTypes.includes(file.type)) {
    showNotification('Invalid file type. Allowed: PNG, JPEG', 'error');
    return;
  }

  // Validate minimum dimensions (128x128)
  const img = new Image();
  img.onload = function() {
    URL.revokeObjectURL(img.src);
    if (img.width < 128 || img.height < 128) {
      showNotification('Image too small. Minimum size is 128x128 pixels.', 'error');
      input.value = '';
      return;
    }
    doLogoUpload(file, vaultId);
  };
  img.onerror = function() {
    URL.revokeObjectURL(img.src);
    showNotification('Failed to read image file.', 'error');
    input.value = '';
  };
  img.src = URL.createObjectURL(file);
  input.value = '';
}

function doLogoUpload(file, vaultId) {
  const formData = new FormData();
  formData.append('logo', file);

  fetch(`/console/vaults/${vaultId}/logo`, {
    method: 'POST',
    body: formData
  })
  .then(response => response.json())
  .then(data => {
    if (data.success) {
      showNotification('Logo uploaded successfully', 'success');
      htmx.trigger('body', 'refreshList');
    } else {
      showNotification(data.error || 'Upload failed', 'error');
    }
  })
  .catch(err => {
    console.error('Logo upload error:', err);
    showNotification('Upload failed', 'error');
  });
}

function deleteLogo(vaultId) {
  if (!confirm('Are you sure you want to delete this logo?')) return;

  fetch(`/console/vaults/${vaultId}/logo`, {
    method: 'DELETE'
  })
  .then(response => response.json())
  .then(data => {
    if (data.success) {
      showNotification('Logo deleted', 'success');
      htmx.trigger('body', 'refreshList');
    } else {
      showNotification(data.error || 'Delete failed', 'error');
    }
  })
  .catch(err => {
    console.error('Logo delete error:', err);
    showNotification('Delete failed', 'error');
  });
}

window.uploadLogo = uploadLogo;
window.deleteLogo = deleteLogo;
