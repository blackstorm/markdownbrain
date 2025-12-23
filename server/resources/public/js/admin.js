/**
 * MarkdownBrain Admin Dashboard
 * Enhanced UX with clean interactions and HTMX integration
 */

// ============================================================================
// Modal Management
// ============================================================================

/**
 * Open create modal
 */
function openCreateModal() {
  const modal = document.getElementById('modal-create');
  if (modal) {
    modal.classList.add('open');
    // Focus first input
    setTimeout(() => {
      const input = modal.querySelector('input[type="text"]');
      if (input) input.focus();
    }, 100);
  }
}

/**
 * Close create modal
 */
function closeCreateModal() {
  const modal = document.getElementById('modal-create');
  if (modal) {
    modal.classList.remove('open');
    // Clear form
    const form = modal.querySelector('form');
    if (form) form.reset();
    // Clear result area
    const resultDiv = document.getElementById('create-result');
    if (resultDiv) resultDiv.innerHTML = '';
  }
}

/**
 * Open edit modal with vault data
 * @param {string} vaultId - Vault ID
 * @param {string} name - Vault name
 * @param {string} domain - Vault domain
 */
function openEditModal(vaultId, name, domain) {
  const modal = document.getElementById('modal-edit');
  const form = document.getElementById('edit-form');

  if (!modal || !form) {
    console.error('Edit modal or form not found');
    return;
  }

  // Populate form
  document.getElementById('edit-vault-id').value = vaultId;
  document.getElementById('edit-name').value = name;
  document.getElementById('edit-domain').value = domain;

  // Set form action URL
  form.setAttribute('hx-put', `/admin/vaults/${vaultId}`);

  // IMPORTANT: Re-process the form element so HTMX picks up the new hx-put attribute
  htmx.process(form);

  // Clear previous errors
  const resultDiv = document.getElementById('edit-result');
  if (resultDiv) resultDiv.innerHTML = '';

  // Show modal
  modal.classList.add('open');

  // Focus name input
  setTimeout(() => {
    document.getElementById('edit-name').focus();
  }, 100);
}

/**
 * Close edit modal
 */
function closeEditModal() {
  const modal = document.getElementById('modal-edit');
  if (modal) {
    modal.classList.remove('open');
    // Clear form
    const form = modal.querySelector('form');
    if (form) form.reset();
    // Clear result area
    const resultDiv = document.getElementById('edit-result');
    if (resultDiv) resultDiv.innerHTML = '';
  }
}

// ============================================================================
// HTMX Event Handlers
// ============================================================================

/**
 * Handle vault creation response
 */
document.body.addEventListener('htmx:afterRequest', function(event) {
  const requestPath = event.detail.pathInfo.requestPath;

  // Create vault response (POST only)
  if (requestPath === '/admin/vaults' && event.detail.requestConfig?.verb === 'post') {
    if (event.detail.successful) {
      try {
        const response = JSON.parse(event.detail.xhr.responseText);
        if (response.success) {
          showNotification(`站点「${response.vault.name}」创建成功`, 'success');
          closeCreateModal();
          // Trigger list refresh
          htmx.trigger('body', 'refreshList');
        } else {
          // Show error in modal
          const resultDiv = document.getElementById('create-result');
          if (resultDiv) {
            resultDiv.innerHTML = `
              <div class="alert alert-error">
                <i data-lucide="alert-circle" style="width: 1rem; height: 1rem;"></i>
                <span>${response.error || '创建失败'}</span>
              </div>
            `;
            lucide.createIcons();
          }
        }
      } catch (e) {
        console.error('Failed to parse response:', e);
        showNotification('服务器响应异常', 'error');
      }
    } else {
      showNotification('网络错误，请重试', 'error');
    }
  }

  // Update vault response
  if (requestPath.startsWith('/admin/vaults/')) {
    if (event.detail.successful) {
      try {
        const response = JSON.parse(event.detail.xhr.responseText);
        if (response.success) {
          showNotification('站点已更新', 'success');
          closeEditModal();
          // Trigger list refresh
          htmx.trigger('body', 'refreshList');
        } else {
          // Show error in modal
          const resultDiv = document.getElementById('edit-result');
          if (resultDiv) {
            resultDiv.innerHTML = `
              <div class="alert alert-error">
                <i data-lucide="alert-circle" style="width: 1rem; height: 1rem;"></i>
                <span>${response.error || '更新失败'}</span>
              </div>
            `;
            lucide.createIcons();
          }
        }
      } catch (e) {
        console.error('Failed to parse response:', e);
        showNotification('服务器响应异常', 'error');
      }
    } else {
      showNotification('网络错误，请重试', 'error');
    }
  }
});

/**
 * Handle HTMX errors globally
 */
document.body.addEventListener('htmx:responseError', function(event) {
  const status = event.detail.xhr.status;
  let message = '操作失败，请重试';

  if (status === 401) {
    message = '会话已过期，请重新登录';
    setTimeout(() => window.location.href = '/admin/login', 2000);
  } else if (status === 403) {
    message = '没有权限执行此操作';
  } else if (status === 404) {
    message = '请求的资源不存在';
  } else if (status >= 500) {
    message = '服务器错误，请稍后重试';
  }

  showNotification(message, 'error', 5000);
});

/**
 * Re-process content after HTMX swap
 */
document.body.addEventListener('htmx:afterSwap', function(event) {
  if (event.detail.target.id === 'vault-list') {
    // Re-initialize any components if needed
    lucide.createIcons();
  }
});

// ============================================================================
// Keyboard Shortcuts
// ============================================================================

document.addEventListener('keydown', function(e) {
  // ESC to close modals
  if (e.key === 'Escape') {
    const createModal = document.getElementById('modal-create');
    const editModal = document.getElementById('modal-edit');

    if (createModal && createModal.classList.contains('open')) {
      closeCreateModal();
    }
    if (editModal && editModal.classList.contains('open')) {
      closeEditModal();
    }
  }

  // Cmd/Ctrl + K to open create modal
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault();
    openCreateModal();
  }
});

// ============================================================================
// Initialization
// ============================================================================

document.addEventListener('DOMContentLoaded', function() {
  console.log('MarkdownBrain Admin Dashboard initialized');

  // Setup domain validation on blur
  const domainInputs = document.querySelectorAll('input[name="domain"]');
  domainInputs.forEach(input => {
    input.addEventListener('blur', function() {
      validateDomain(this);
    });
  });
});

/**
 * Validate domain format
 * @param {HTMLInputElement} input - Domain input element
 */
function validateDomain(input) {
  const value = input.value.trim();
  const domainRegex = /^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/;

  if (value && !domainRegex.test(value)) {
    input.style.borderColor = '#ef4444';
    showNotification('请输入有效的域名格式（如：notes.example.com）', 'warning', 2000);
  } else {
    input.style.borderColor = '';
  }
}

// ============================================================================
// Expose Functions
// ============================================================================

window.openCreateModal = openCreateModal;
window.closeCreateModal = closeCreateModal;
window.openEditModal = openEditModal;
window.closeEditModal = closeEditModal;
window.validateDomain = validateDomain;
