import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import type { DocumentImage } from '../api/types';
import { Icon } from './Icon';

interface LightboxProps {
  image: DocumentImage;
  onClose: () => void;
}

const FOCUSABLE =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function Lightbox({ image, onClose }: LightboxProps) {
  const [failed, setFailed] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    const root = document.getElementById('root');
    const previousOverflow = document.body.style.overflow;
    const previouslyFocused =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    const rootWasInert = root?.hasAttribute('inert') ?? false;
    const rootAriaHidden = root?.getAttribute('aria-hidden') ?? null;

    document.body.style.overflow = 'hidden';
    if (root) {
      root.setAttribute('inert', '');
      root.setAttribute('aria-hidden', 'true');
    }
    closeRef.current?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onCloseRef.current();
        return;
      }
      if (event.key !== 'Tab') return;

      const focusable = Array.from(
        dialogRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [],
      ).filter(
        (element) =>
          element.offsetParent !== null && !element.hasAttribute('disabled'),
      );

      if (focusable.length === 0) {
        event.preventDefault();
        dialogRef.current?.focus();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];

      if (!dialogRef.current?.contains(document.activeElement)) {
        event.preventDefault();
        first.focus();
      } else if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
      if (root) {
        if (!rootWasInert) root.removeAttribute('inert');
        if (rootAriaHidden === null) {
          root.removeAttribute('aria-hidden');
        } else {
          root.setAttribute('aria-hidden', rootAriaHidden);
        }
      }
      previouslyFocused?.focus();
    };
  }, []);

  return createPortal(
    <div
      ref={dialogRef}
      className="lightbox"
      role="dialog"
      aria-modal="true"
      aria-label={`${image.label} document preview`}
      tabIndex={-1}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <button
        ref={closeRef}
        type="button"
        className="lightbox__close"
        onClick={onClose}
        aria-label="Close document preview"
      >
        <Icon name="close" size={19} />
      </button>
      <figure className="lightbox__figure">
        {failed ? (
          <div className="lightbox__error" role="alert">
            <Icon name="alert" size={22} />
            <p>The document image could not be loaded.</p>
          </div>
        ) : (
          <img
            src={image.secureUrl}
            alt={`${image.label} — uploaded PAN document`}
            onError={() => setFailed(true)}
          />
        )}
        <figcaption>
          <span className="mono">{image.label}</span>
          <span>Uploaded document preview</span>
        </figcaption>
      </figure>
    </div>,
    document.body,
  );
}
