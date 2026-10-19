import {
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { DocumentImage, PendingDocument } from '../api/types';
import { isAuthError, toErrorMessage } from '../api/errors';
import { useMediaQuery } from '../hooks/useMediaQuery';
import { CheckTimeline } from '../components/CheckTimeline';
import { Icon } from '../components/Icon';
import { Lightbox } from '../components/Lightbox';
import { Modal } from '../components/Modal';
import {
  EmptyState,
  ErrorBanner,
  ErrorState,
  DetailSkeleton,
  QueueSkeleton,
} from '../components/StateViews';
import { Chip, StatusChip } from '../components/StatusChip';
import type { ToastTone } from '../components/Toast';
import {
  PAN_FILTERS,
  applicantName,
  countByFilter,
  documentMatchesFilter,
  fullPan,
  isAwaitingAadhaar,
  isDuplicate,
  isFieldMismatched,
  maskedPan,
  mismatchFields,
  type PanFilter,
} from '../utils/documents';
import {
  displayValue,
  fieldLabel,
  formatDateTime,
  formatRelative,
  pluralize,
} from '../utils/format';

interface PanReviewScreenProps {
  documents: PendingDocument[];
  selectedId: string | null;
  onSelect: (id: string | null) => void;
  loading: boolean;
  loaded: boolean;
  error: string | null;
  onRetry: () => void;
  onApprove: (id: string, notes?: string) => Promise<void>;
  onReject: (id: string, reason: string) => Promise<void>;
  notify: (message: string, tone?: ToastTone) => void;
}

interface DecisionState {
  kind: 'approve' | 'reject';
  document: PendingDocument;
}

interface FieldRow {
  key: string;
  value: string | undefined;
}

const OCR_FIELDS = ['name', 'fatherName', 'dob'];
const QR_FIELDS = ['name', 'dob', 'gender', 'address', 'pincode'];
const FOCUSABLE =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function PanReviewScreen({
  documents,
  selectedId,
  onSelect,
  loading,
  loaded,
  error,
  onRetry,
  onApprove,
  onReject,
  notify,
}: PanReviewScreenProps) {
  const [filter, setFilter] = useState<PanFilter>('all');
  const [dismissed, setDismissed] = useState(() =>
    typeof window !== 'undefined'
      ? window.matchMedia('(max-width: 860px)').matches
      : false,
  );
  const [decision, setDecision] = useState<DecisionState | null>(null);
  const [notes, setNotes] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [lightbox, setLightbox] = useState<DocumentImage | null>(null);
  const decisionFieldId = useId();
  const isNarrow = useMediaQuery('(max-width: 860px)');
  const queueRef = useRef<HTMLElement>(null);
  const detailRef = useRef<HTMLElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);

  const filtered = useMemo(
    () => documents.filter((document) => documentMatchesFilter(document, filter)),
    [documents, filter],
  );
  const selected = documents.find((document) => document.id === selectedId) ?? null;
  const sheetOpen = isNarrow && Boolean(selected);

  const closeSheet = useCallback(() => {
    setDismissed(true);
    onSelect(null);
  }, [onSelect]);

  useEffect(() => {
    if (dismissed) return;
    if (selectedId && filtered.some((document) => document.id === selectedId)) {
      return;
    }
    const next = filtered[0]?.id ?? null;
    if (next !== selectedId) onSelect(next);
  }, [filtered, selectedId, dismissed, onSelect]);

  useEffect(() => {
    if (!sheetOpen) return;
    const nodes = Array.from(
      document.querySelectorAll<HTMLElement>(
        '.rail, .topbar, .mobile-nav, .queue-panel, .error-banner, .skip-link',
      ),
    );
    for (const node of nodes) {
      node.setAttribute('inert', '');
      node.setAttribute('aria-hidden', 'true');
    }
    return () => {
      for (const node of nodes) {
        node.removeAttribute('inert');
        node.removeAttribute('aria-hidden');
      }
    };
  }, [sheetOpen]);

  useEffect(() => {
    if (!sheetOpen) return;
    const detail = detailRef.current;
    if (!detail) return;

    const returnFocus =
      previousFocusRef.current ??
      (document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null);

    const focusFrame = window.requestAnimationFrame(() => {
      const back = detail.querySelector<HTMLElement>('.detail__back');
      (back ?? detail).focus();
    });

    const handleKeyDown = (event: KeyboardEvent) => {
      // A modal or lightbox opened above the sheet owns the keyboard.
      if (document.querySelector('.modal-backdrop, .lightbox')) return;

      if (event.key === 'Escape') {
        event.preventDefault();
        closeSheet();
        return;
      }
      if (event.key !== 'Tab') return;

      const focusable = Array.from(
        detail.querySelectorAll<HTMLElement>(FOCUSABLE),
      ).filter(
        (element) =>
          element.offsetParent !== null && !element.hasAttribute('disabled'),
      );

      if (focusable.length === 0) {
        event.preventDefault();
        detail.focus();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!detail.contains(document.activeElement)) {
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
      window.cancelAnimationFrame(focusFrame);
      document.removeEventListener('keydown', handleKeyDown);
      const fallback =
        queueRef.current?.querySelector<HTMLElement>('.queue-row') ??
        queueRef.current;
      const target = returnFocus?.isConnected ? returnFocus : fallback;
      window.requestAnimationFrame(() => target?.focus());
    };
  }, [sheetOpen, closeSheet]);

  function chooseFilter(nextFilter: PanFilter) {
    setFilter(nextFilter);
  }

  function openDecision(kind: 'approve' | 'reject', document: PendingDocument) {
    setNotes('');
    setFormError(null);
    setDecision({ kind, document });
  }

  async function submitDecision() {
    if (!decision) return;
    const trimmed = notes.trim();
    if (decision.kind === 'reject' && !trimmed) {
      setFormError('Enter a reason before rejecting this document.');
      return;
    }

    setBusy(true);
    setFormError(null);
    try {
      if (decision.kind === 'approve') {
        await onApprove(decision.document.id, trimmed || undefined);
        notify(
          `${applicantName(decision.document)}'s PAN was approved.`,
          'success',
        );
      } else {
        await onReject(decision.document.id, trimmed);
        notify(
          `${applicantName(decision.document)}'s PAN was rejected.`,
          'info',
        );
      }
      setDecision(null);
      setNotes('');
    } catch (submitError) {
      if (!isAuthError(submitError)) {
        setFormError(toErrorMessage(submitError));
      }
    } finally {
      setBusy(false);
    }
  }

  if (error && !loaded) {
    return <ErrorState message={error} onRetry={onRetry} />;
  }

  const mismatchList = selected ? mismatchFields(selected) : [];

  return (
    <div className="pan-review">
      {error && loaded && <ErrorBanner message={error} onRetry={onRetry} />}

      <div className="review-desk">
        <section
          ref={queueRef}
          className="queue-panel"
          aria-labelledby="pan-queue-heading"
          tabIndex={-1}
        >
          <header className="queue-panel__header">
            <p className="eyebrow">Manual review</p>
            <div className="queue-panel__title-row">
              <h2 id="pan-queue-heading">PAN queue</h2>
              {loaded && <span className="count-badge">{documents.length}</span>}
            </div>
            <p className="queue-panel__sub">
              Compare the PAN OCR fields with the signed Aadhaar record before
              deciding.
            </p>
          </header>

          <div
            className="filter-tabs"
            role="group"
            aria-label="Filter PAN queue"
          >
            {PAN_FILTERS.map((item) => (
              <button
                key={item.id}
                type="button"
                aria-pressed={filter === item.id}
                className="filter-tab"
                onClick={() => chooseFilter(item.id)}
              >
                {item.label}
                {loaded && (
                  <span className="filter-tab__count">
                    {countByFilter(documents, item.id)}
                  </span>
                )}
              </button>
            ))}
          </div>

          {loading && !loaded ? (
            <QueueSkeleton />
          ) : filtered.length === 0 ? (
            <EmptyState
              compact
              icon={documents.length === 0 ? 'check' : 'document'}
              title={
                documents.length === 0 ? 'Queue is clear' : 'Nothing in this view'
              }
              message={
                documents.length === 0
                  ? 'No PAN documents are waiting for manual review.'
                  : 'No documents match the selected filter.'
              }
              action={
                documents.length === 0 ? (
                  <button
                    type="button"
                    className="btn btn--quiet"
                    onClick={onRetry}
                  >
                    <Icon name="refresh" size={15} />
                    Refresh queue
                  </button>
                ) : (
                  <button
                    type="button"
                    className="btn btn--quiet"
                    onClick={() => chooseFilter('all')}
                  >
                    Show all documents
                  </button>
                )
              }
            />
          ) : (
            <div className="queue-list" aria-label="PAN review queue">
              {filtered.map((document) => {
                const mismatches = mismatchFields(document);
                const active = document.id === selectedId;
                return (
                  <button
                    key={document.id}
                    type="button"
                    className="queue-row"
                    aria-current={active ? 'true' : undefined}
                    onClick={(event) => {
                      previousFocusRef.current = event.currentTarget;
                      setDismissed(false);
                      onSelect(document.id);
                    }}
                  >
                    <span className="queue-row__top">
                      <span className="queue-row__name">
                        {applicantName(document)}
                      </span>
                      <StatusChip
                        status={document.verificationStatus}
                        size="sm"
                      />
                    </span>
                    <span className="queue-row__meta">
                      <span className="mono">{maskedPan(document)}</span>
                      <span aria-hidden="true">·</span>
                      <span title={formatDateTime(document.submittedAt)}>
                        {formatRelative(document.submittedAt)}
                      </span>
                    </span>
                    <span className="queue-row__chips">
                      {mismatches.slice(0, 3).map((field) => (
                        <Chip tone="brick" key={field}>
                          {fieldLabel(field)}
                        </Chip>
                      ))}
                      {mismatches.length > 3 && (
                        <Chip tone="brick">+{mismatches.length - 3}</Chip>
                      )}
                      {isDuplicate(document) && (
                        <Chip tone="ink">Duplicate</Chip>
                      )}
                      {isAwaitingAadhaar(document) && (
                        <Chip>Awaiting Aadhaar</Chip>
                      )}
                      {/confidence/i.test(
                        `${document.manualReviewReason ?? ''} ${
                          document.failureReason ?? ''
                        }`,
                      ) && <Chip>Low OCR confidence</Chip>}
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </section>

        <section
          ref={detailRef}
          className={`detail${selected ? ' detail--active' : ''}`}
          role={sheetOpen ? 'dialog' : undefined}
          aria-modal={sheetOpen || undefined}
          aria-labelledby={
            sheetOpen && selected ? 'pan-detail-title' : undefined
          }
          aria-label={sheetOpen ? undefined : 'Selected PAN document'}
          tabIndex={sheetOpen ? -1 : undefined}
        >
          {loading && !loaded ? (
            <DetailSkeleton />
          ) : !selected ? (
            <div className="detail-placeholder">
              <span className="detail-placeholder__icon" aria-hidden="true">
                <Icon name="pan" size={26} />
              </span>
              <h3>Select a document</h3>
              <p>
                Choose a row in the queue to see the record comparison,
                uploaded images, and review actions.
              </p>
            </div>
          ) : (
            <>
              <header className="detail__header">
                <button
                  type="button"
                  className="detail__back"
                  onClick={closeSheet}
                >
                  <Icon name="arrowLeft" size={15} />
                  Queue
                </button>
                <div className="detail__title">
                  <p className="detail__eyebrow mono">PAN review</p>
                  <h2 id="pan-detail-title">{applicantName(selected)}</h2>
                  <p className="detail__sub">
                    Submitted {formatDateTime(selected.submittedAt)}
                  </p>
                </div>
                <StatusChip status={selected.verificationStatus} />
              </header>

              <div className="detail__body">
                <dl className="identity-strip">
                  <div>
                    <dt>PAN number</dt>
                    <dd className="mono">{fullPan(selected)}</dd>
                  </div>
                  <div>
                    <dt>Masked</dt>
                    <dd className="mono">{maskedPan(selected)}</dd>
                  </div>
                  <div>
                    <dt>User ID</dt>
                    <dd className="mono">{selected.userId || '—'}</dd>
                  </div>
                  <div>
                    <dt>Submitted</dt>
                    <dd>{formatRelative(selected.submittedAt)}</dd>
                  </div>
                </dl>

                <section className="ledger-section" aria-labelledby="reason-heading">
                  <div className="section-heading">
                    <span className="section-index mono">01</span>
                    <h3 id="reason-heading">Why this is in the queue</h3>
                  </div>
                  <div className="callout callout--amber">
                    <Icon name="info" size={17} />
                    <div>
                      <p>
                        {selected.manualReviewReason ??
                          'Manual review was requested by the verification service.'}
                      </p>
                      {selected.failureReason && (
                        <p className="callout__note">
                          <strong>Automated check:</strong>{' '}
                          {selected.failureReason}
                        </p>
                      )}
                    </div>
                  </div>
                </section>

                <section className="ledger-section" aria-labelledby="timeline-heading">
                  <div className="section-heading">
                    <span className="section-index mono">02</span>
                    <h3 id="timeline-heading">Check timeline</h3>
                  </div>
                  <CheckTimeline document={selected} />
                </section>

                <section className="ledger-section" aria-labelledby="compare-heading">
                  <div className="section-heading">
                    <span className="section-index mono">03</span>
                    <h3 id="compare-heading">Record comparison</h3>
                    <span className="section-note">
                      Mismatched fields are highlighted
                    </span>
                  </div>

                  <div className="comparison">
                    <div className="comparison__column">
                      <div className="comparison__head">
                        <span className="mono">PAN · OCR extraction</span>
                        <span>Uploaded card</span>
                      </div>
                      {OCR_FIELDS.map((key) => (
                        <FieldRowView
                          key={key}
                          field={{ key, value: selected.extractedData?.[key] }}
                          mismatched={isFieldMismatched(selected, key)}
                        />
                      ))}
                    </div>
                    <div className="comparison__column">
                      <div className="comparison__head">
                        <span className="mono">Aadhaar · signed QR</span>
                        <span>Verified source</span>
                      </div>
                      {QR_FIELDS.map((key) => (
                        <FieldRowView
                          key={key}
                          field={{ key, value: selected.aadhaarQrData?.[key] }}
                          mismatched={isFieldMismatched(selected, key)}
                        />
                      ))}
                      {Object.keys(selected.aadhaarQrData ?? {}).length === 0 && (
                        <div className="comparison__empty">
                          No signed Aadhaar record is linked to this applicant.
                        </div>
                      )}
                    </div>
                  </div>

                  {mismatchList.length > 0 && (
                    <div className="mismatch-summary">
                      <Icon name="alert" size={15} />
                      <p>
                        <strong>
                          {mismatchList.length}{' '}
                          {pluralize(mismatchList.length, 'field')}
                        </strong>{' '}
                        {mismatchList.length === 1 ? 'differs' : 'differ'}{' '}
                        between the uploaded PAN and the Aadhaar record:{' '}
                        {mismatchList.map(fieldLabel).join(', ')}.
                      </p>
                    </div>
                  )}
                </section>

                <section className="ledger-section" aria-labelledby="images-heading">
                  <div className="section-heading">
                    <span className="section-index mono">04</span>
                    <h3 id="images-heading">Uploaded documents</h3>
                    <span className="section-note">Select to enlarge</span>
                  </div>
                  {selected.images?.length ? (
                    <div className="thumbs">
                      {selected.images.map((image) => (
                        <button
                          type="button"
                          className="thumb"
                          key={image.publicId || image.secureUrl}
                          onClick={() => setLightbox(image)}
                          aria-label={`Open ${image.label || 'document'} image`}
                        >
                          <span className="thumb__frame">
                            <img
                              src={image.secureUrl}
                              alt={`${image.label || 'PAN'} upload`}
                              loading="lazy"
                            />
                          </span>
                          <span className="thumb__caption">
                            <span>{image.label || 'Document'}</span>
                            <Icon name="zoom" size={14} />
                          </span>
                        </button>
                      ))}
                    </div>
                  ) : (
                    <p className="empty-line">
                      No document images were attached to this submission.
                    </p>
                  )}
                </section>
              </div>

              <footer className="detail__actions">
                <div className="detail__action-note">
                  <strong>Decision required</strong>
                  <p>
                    Approve once the records agree, or reject with a reason for
                    the audit trail.
                  </p>
                </div>
                <div className="detail__action-buttons">
                  <button
                    type="button"
                    className="btn btn--danger-quiet"
                    onClick={() => openDecision('reject', selected)}
                  >
                    <Icon name="close" size={15} />
                    Reject
                  </button>
                  <button
                    type="button"
                    className="btn btn--approve"
                    onClick={() => openDecision('approve', selected)}
                  >
                    <Icon name="check" size={15} />
                    Approve PAN
                  </button>
                </div>
              </footer>
            </>
          )}
        </section>
      </div>

      {lightbox && (
        <Lightbox image={lightbox} onClose={() => setLightbox(null)} />
      )}

      {decision && (
        <Modal
          eyebrow="PAN review"
          title={
            decision.kind === 'approve'
              ? `Approve ${applicantName(decision.document)}'s PAN?`
              : `Reject ${applicantName(decision.document)}'s PAN?`
          }
          description={
            decision.kind === 'approve'
              ? 'This marks the PAN as verified and removes it from the review queue.'
              : 'This closes the review and records your reason in the audit trail.'
          }
          onClose={() => {
            if (!busy) setDecision(null);
          }}
          busy={busy}
          footer={
            <>
              <button
                type="button"
                className="btn btn--quiet"
                onClick={() => setDecision(null)}
                disabled={busy}
              >
                Cancel
              </button>
              <button
                type="button"
                className={
                  decision.kind === 'approve'
                    ? 'btn btn--approve'
                    : 'btn btn--danger'
                }
                onClick={submitDecision}
                disabled={busy}
                data-autofocus
              >
                {busy
                  ? decision.kind === 'approve'
                    ? 'Approving…'
                    : 'Rejecting…'
                  : decision.kind === 'approve'
                    ? 'Approve PAN'
                    : 'Reject PAN'}
              </button>
            </>
          }
        >
          <div className="decision-summary">
            <span>
              <span>Applicant</span>
              <strong>{applicantName(decision.document)}</strong>
            </span>
            <span>
              <span>PAN</span>
              <strong className="mono">{maskedPan(decision.document)}</strong>
            </span>
          </div>

          <div className="field">
            <div className="field__label-row">
              <label htmlFor={decisionFieldId}>
                {decision.kind === 'approve' ? 'Review notes' : 'Reason for rejection'}
              </label>
              <span className="field__optional">
                {decision.kind === 'approve' ? 'Optional' : 'Required'}
              </span>
            </div>
            <textarea
              id={decisionFieldId}
              className="textarea"
              rows={4}
              value={notes}
              onChange={(event) => {
                setNotes(event.target.value);
                if (formError) setFormError(null);
              }}
              maxLength={500}
              placeholder={
                decision.kind === 'approve'
                  ? 'Add context for the audit trail…'
                  : 'Explain what needs to be corrected…'
              }
              disabled={busy}
              data-autofocus
            />
            <p className="field__hint">
              {decision.kind === 'approve'
                ? 'Optional. Recorded with your admin session.'
                : 'Required. Keep the note factual and specific.'}
            </p>
          </div>

          {formError && (
            <p className="form-error" role="alert">
              <Icon name="alert" size={15} />
              {formError}
            </p>
          )}
        </Modal>
      )}
    </div>
  );
}

function FieldRowView({
  field,
  mismatched,
}: {
  field: FieldRow;
  mismatched: boolean;
}) {
  return (
    <div className={`field-row${mismatched ? ' field-row--mismatch' : ''}`}>
      <span className="field-row__label">{fieldLabel(field.key)}</span>
      <span className="field-row__value">{displayValue(field.value)}</span>
      {mismatched && <span className="field-row__flag">Mismatch</span>}
    </div>
  );
}
