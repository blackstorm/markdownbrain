// MarkdownBrain Frontend Helpers

// 复制到剪贴板
function copyToClipboard(text) {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => {
      showNotification('已复制到剪贴板', 'success');
    }).catch(err => {
      console.error('Failed to copy:', err);
      showNotification('复制失败', 'error');
    });
  }
}

// 显示通知
function showNotification(message, type = 'info') {
  const container = document.getElementById('notification-container');
  if (!container) return;

  const notification = document.createElement('div');

  const colors = {
    success: 'bg-green-500',
    error: 'bg-red-500',
    info: 'bg-blue-500',
    warning: 'bg-yellow-500'
  };

  notification.className = `fixed top-4 right-4 px-6 py-3 rounded-lg text-white shadow-lg ${colors[type] || colors.info}`;
  notification.textContent = message;

  container.appendChild(notification);

  setTimeout(() => {
    notification.remove();
  }, 3000);
}

// 格式化日期
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

// 初始化
document.addEventListener('DOMContentLoaded', () => {
  console.log('MarkdownBrain frontend initialized');
});

// 暴露全局函数
window.copyToClipboard = copyToClipboard;
window.showNotification = showNotification;
window.formatDate = formatDate;
