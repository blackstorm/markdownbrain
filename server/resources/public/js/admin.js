/**
 * MarkdownBrain Admin Dashboard Utilities
 * Enhanced UX with toast notifications, clipboard operations, and smart form helpers
 */

// ============================================================================
// Toast Notification System
// ============================================================================

/**
 * Show a toast notification with automatic dismissal
 * @param {string} message - The message to display
 * @param {string} type - Type of toast: 'success', 'error', 'warning', 'info'
 * @param {number} duration - Duration in milliseconds (default: 3000)
 */
function showToast(message, type = 'success', duration = 3000) {
  const toast = document.createElement('div');
  toast.className = `alert alert-${type} shadow-lg animate-in`;

  // Select appropriate icon based on type
  const iconMap = {
    success: 'check_circle',
    error: 'error',
    warning: 'warning',
    info: 'info'
  };

  toast.innerHTML = `
    <div class="flex items-center gap-3">
      <span class="material-symbols-outlined text-xl">
        ${iconMap[type] || 'info'}
      </span>
      <span class="text-sm font-medium">${message}</span>
    </div>
  `;

  const container = document.getElementById('toast-container');
  if (!container) {
    console.warn('Toast container not found. Creating one...');
    const newContainer = document.createElement('div');
    newContainer.id = 'toast-container';
    newContainer.className = 'toast toast-top toast-end';
    document.body.appendChild(newContainer);
  }

  const toastContainer = document.getElementById('toast-container');
  toastContainer.appendChild(toast);

  // Auto-dismiss with fade out animation
  setTimeout(() => {
    toast.classList.add('animate-out');
    setTimeout(() => toast.remove(), 300);
  }, duration);
}

// ============================================================================
// Clipboard Operations
// ============================================================================

/**
 * Copy text to clipboard with visual feedback
 * @param {string} text - Text to copy
 * @param {HTMLElement} buttonElement - Button element to provide visual feedback
 */
function copyToClipboard(text, buttonElement) {
  navigator.clipboard.writeText(text)
    .then(() => {
      // Visual feedback on button
      const icon = buttonElement.querySelector('.material-symbols-outlined');
      if (icon) {
        const originalIcon = icon.textContent;
        icon.textContent = 'check';
        buttonElement.classList.add('text-success');

        // Show success toast
        showToast('Sync key copied to clipboard!', 'success', 2000);

        // Restore original state after 2 seconds
        setTimeout(() => {
          icon.textContent = originalIcon;
          buttonElement.classList.remove('text-success');
        }, 2000);
      } else {
        showToast('Copied!', 'success', 2000);
      }
    })
    .catch(err => {
      console.error('Failed to copy:', err);
      showToast('Failed to copy. Please try manually.', 'error');
    });
}

// ============================================================================
// Sync Key Visibility Toggle
// ============================================================================

/**
 * Toggle sync key visibility (show/hide)
 * @param {HTMLElement} buttonElement - Toggle button element
 * @param {string} vaultId - ID of the vault
 */
function toggleKey(buttonElement, vaultId) {
  const codeElement = document.getElementById(`key-${vaultId}`);
  if (!codeElement) {
    console.error(`Key element not found for vault: ${vaultId}`);
    return;
  }

  const icon = buttonElement.querySelector('.material-symbols-outlined');
  const fullKey = codeElement.dataset.key;
  const currentText = codeElement.textContent;
  const isHidden = currentText.includes('*');

  if (isHidden) {
    // Show full key
    codeElement.textContent = fullKey;
    if (icon) {
      icon.textContent = 'visibility_off';
      buttonElement.title = 'Hide key';
    }
  } else {
    // Mask key (show first 8 and last 8 chars)
    const masked = fullKey.substring(0, 8) + '******' + fullKey.substring(fullKey.length - 8);
    codeElement.textContent = masked;
    if (icon) {
      icon.textContent = 'visibility';
      buttonElement.title = 'Show key';
    }
  }
}

// ============================================================================
// Smart Form Helpers
// ============================================================================

/**
 * Auto-suggest domain from site name
 * Converts site name to a valid domain slug
 */
function setupDomainSuggestion() {
  const nameInput = document.querySelector('input[name="name"]');
  const domainInput = document.querySelector('input[name="domain"]');

  if (nameInput && domainInput) {
    nameInput.addEventListener('input', function(e) {
      // Only suggest if domain is empty
      if (!domainInput.value || domainInput.value === domainInput.placeholder) {
        const slug = e.target.value
          .toLowerCase()
          .trim()
          .replace(/[^a-z0-9]+/g, '-')  // Replace non-alphanumeric with dash
          .replace(/^-|-$/g, '');        // Remove leading/trailing dashes

        if (slug) {
          domainInput.placeholder = `${slug}.yourdomain.com`;
        } else {
          domainInput.placeholder = 'notes.example.com';
        }
      }
    });
  }
}

/**
 * Validate domain input in real-time
 * @param {HTMLInputElement} input - Domain input element
 */
function validateDomain(input) {
  const value = input.value.trim();

  // Basic domain regex (allows subdomains)
  const domainRegex = /^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/;

  const feedback = input.parentElement.nextElementSibling;

  if (value && !domainRegex.test(value)) {
    input.classList.add('input-error');
    if (feedback && feedback.classList.contains('label')) {
      const feedbackText = feedback.querySelector('.label-text-alt');
      if (feedbackText) {
        feedbackText.textContent = 'Please enter a valid domain (e.g., notes.example.com)';
        feedbackText.classList.add('text-error');
      }
    }
  } else {
    input.classList.remove('input-error');
    if (feedback && feedback.classList.contains('label')) {
      const feedbackText = feedback.querySelector('.label-text-alt');
      if (feedbackText) {
        feedbackText.textContent = 'Your custom domain (without https://)';
        feedbackText.classList.remove('text-error');
      }
    }
  }
}

// ============================================================================
// HTMX Event Handlers
// ============================================================================

/**
 * Handle HTMX errors globally
 */
document.body.addEventListener('htmx:responseError', function(event) {
  const status = event.detail.xhr.status;
  let message = 'Something went wrong. Please try again.';

  if (status === 401) {
    message = 'Session expired. Please log in again.';
    setTimeout(() => window.location.href = '/admin/login', 2000);
  } else if (status === 403) {
    message = 'Permission denied.';
  } else if (status === 404) {
    message = 'Resource not found.';
  } else if (status >= 500) {
    message = 'Server error. Please try again later.';
  }

  showToast(message, 'error', 5000);
});

/**
 * Handle successful vault creation
 */
document.body.addEventListener('htmx:afterRequest', function(event) {
  // Handle create vault form submission
  if (event.detail.elt.matches('form[hx-post="/admin/vaults"]')) {
    try {
      const response = JSON.parse(event.detail.xhr.responseText);

      if (response.success) {
        // Show success toast
        showToast(`Site "${response.vault.name}" created successfully!`, 'success');

        // Close modal
        const modal = document.getElementById('my_modal_create');
        if (modal) {
          modal.close();
        }

        // Reset form
        event.detail.elt.reset();

        // Clear result area
        const resultDiv = document.getElementById('create-result');
        if (resultDiv) {
          resultDiv.innerHTML = '';
        }

        // Trigger list refresh
        htmx.trigger('#vault-list', 'refreshList');
      } else {
        // Show error in form
        const resultDiv = document.getElementById('create-result');
        if (resultDiv) {
          resultDiv.innerHTML = `
            <div class="alert alert-error">
              <span class="material-symbols-outlined">error</span>
              <span>${response.error || 'Failed to create site'}</span>
            </div>
          `;
        }
      }
    } catch (e) {
      console.error('Failed to parse response:', e);
      showToast('Failed to process response', 'error');
    }
  }
});

/**
 * Re-process newly swapped content for HTMX
 */
document.body.addEventListener('htmx:afterSwap', function(event) {
  if (event.detail.target.id === 'vault-list') {
    htmx.process(event.detail.target);
  }
});

/**
 * Listen for refresh list event
 */
document.body.addEventListener('refreshList', function() {
  // Fetch updated vault list
  htmx.ajax('GET', '/admin/vaults', {target:'#vault-list', swap:'innerHTML'});
});

// ============================================================================
// Initialization
// ============================================================================

document.addEventListener('DOMContentLoaded', function() {
  console.log('MarkdownBrain Admin Dashboard initialized');

  // Setup smart form helpers
  setupDomainSuggestion();

  // Setup domain validation
  const domainInput = document.querySelector('input[name="domain"]');
  if (domainInput) {
    domainInput.addEventListener('blur', function() {
      validateDomain(this);
    });
  }

  // Focus first input in modals when they open
  const modals = document.querySelectorAll('.modal');
  modals.forEach(modal => {
    modal.addEventListener('click', function(e) {
      if (e.target === modal) {
        const firstInput = modal.querySelector('input[autofocus]');
        if (firstInput) {
          setTimeout(() => firstInput.focus(), 100);
        }
      }
    });
  });
});

// Expose functions globally for inline event handlers
window.showToast = showToast;
window.copyToClipboard = copyToClipboard;
window.toggleKey = toggleKey;
window.validateDomain = validateDomain;

// ============================================================================
// Edit Modal Handler
// ============================================================================

/**
 * Open edit modal and populate with vault data
 * @param {string} vaultId - ID of the vault to edit
 * @param {string} name - Current vault name
 * @param {string} domain - Current vault domain
 */
function openEditModal(vaultId, name, domain) {
  const modal = document.getElementById('my_modal_edit');
  const form = document.getElementById('edit-form');

  if (!modal || !form) {
    console.error('Edit modal or form not found');
    return;
  }

  // Populate form fields
  document.getElementById('edit-vault-id').value = vaultId;
  document.getElementById('edit-name').value = name;
  document.getElementById('edit-domain').value = domain;

  // Set the form action URL
  form.setAttribute('hx-put', `/admin/vaults/${vaultId}`);

  // Clear any previous errors
  const resultDiv = document.getElementById('edit-result');
  if (resultDiv) {
    resultDiv.innerHTML = '';
  }

  // Show modal
  modal.showModal();

  // Focus on name input
  setTimeout(() => {
    document.getElementById('edit-name').focus();
  }, 100);
}

window.openEditModal = openEditModal;
