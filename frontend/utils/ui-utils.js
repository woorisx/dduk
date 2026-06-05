/**
 * UI utilities for notifications and loading states.
 */
export const UIUtils = {
    showToast: (message, type = 'info') => {
        const toastId = 'global-toast-' + Date.now();
        const toast = document.createElement('div');
        toast.id = toastId;
        toast.className = `toast toast-${type}`;
        toast.innerHTML = `
            <div class="toast-content">
                <span class="toast-icon">${type === 'error' ? '!' : 'i'}</span>
                <span class="toast-message">${message}</span>
            </div>
        `;

        document.body.appendChild(toast);

        setTimeout(() => {
            const el = document.getElementById(toastId);
            if (el) el.remove();
        }, 3000);
    },

    setLoading: (buttonId, isLoading) => {
        const btn = document.getElementById(buttonId);
        if (!btn) return;

        if (isLoading) {
            btn.disabled = true;
            btn.dataset.originalText = btn.innerText;
            btn.innerText = 'Processing...';
        } else {
            btn.disabled = false;
            btn.innerText = btn.dataset.originalText || btn.innerText;
        }
    }
};
