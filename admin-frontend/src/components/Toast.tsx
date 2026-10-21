import { Icon, type IconName } from './Icon';

export type ToastTone = 'success' | 'error' | 'info';

export interface ToastData {
  id: number;
  message: string;
  tone: ToastTone;
}

interface ToastProps {
  toast: ToastData | null;
  onDismiss: () => void;
}

const ICONS: Record<ToastTone, IconName> = {
  success: 'check',
  error: 'alert',
  info: 'info',
};

export function Toast({ toast, onDismiss }: ToastProps) {
  return (
    <div className="toast-region" aria-live="polite" aria-atomic="true">
      {toast && (
        <div className={`toast toast--${toast.tone}`} role="status">
          <span className="toast__icon" aria-hidden="true">
            <Icon name={ICONS[toast.tone]} size={16} strokeWidth={2} />
          </span>
          <p>{toast.message}</p>
          <button
            type="button"
            className="toast__close"
            onClick={onDismiss}
            aria-label="Dismiss notification"
          >
            <Icon name="close" size={14} />
          </button>
        </div>
      )}
    </div>
  );
}
