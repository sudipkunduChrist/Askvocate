import { useId, useState, type KeyboardEvent } from 'react';
import { isAuthError, toErrorMessage } from '../api/errors';
import type { Lawyer, LawyerKind } from '../api/types';
import { Icon } from '../components/Icon';
import { Modal } from '../components/Modal';
import {
  EmptyState,
  ErrorBanner,
  ErrorState,
  Skeleton,
} from '../components/StateViews';
import { Chip, StatusChip } from '../components/StatusChip';
import type { ToastTone } from '../components/Toast';
import {
  displayValue,
  formatDateTime,
  formatPracticeAreas,
  formatRelative,
  initials,
  pluralize,
} from '../utils/format';

interface LawyerApprovalsScreenProps {
  freshers: Lawyer[];
  experienced: Lawyer[];
  loading: boolean;
  loaded: boolean;
  error: string | null;
  onRetry: () => void;
  onApprove: (kind: LawyerKind, id: string) => Promise<void>;
  onReject: (kind: LawyerKind, id: string, reason: string) => Promise<void>;
  notify: (message: string, tone?: ToastTone) => void;
}

interface DecisionState {
  kind: LawyerKind;
  lawyer: Lawyer;
  action: 'approve' | 'reject';
}

function isPending(lawyer: Lawyer): boolean {
  return (
    lawyer.verificationStatus === 'PENDING' ||
    lawyer.verificationStatus === 'UNDER_VERIFICATION'
  );
}

export function LawyerApprovalsScreen({
  freshers,
  experienced,
  loading,
  loaded,
  error,
  onRetry,
  onApprove,
  onReject,
  notify,
}: LawyerApprovalsScreenProps) {
  const [kind, setKind] = useState<LawyerKind>('fresher');
  const [decision, setDecision] = useState<DecisionState | null>(null);
  const [reason, setReason] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const reasonId = useId();

  const profiles = kind === 'fresher' ? freshers : experienced;
  const pendingCount =
    profiles.filter(isPending).length;
  const totalPending =
    freshers.filter(isPending).length + experienced.filter(isPending).length;

  function handleTabKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const order: LawyerKind[] = ['fresher', 'experienced'];
    const currentIndex = order.indexOf(kind);
    let nextIndex: number | null = null;

    if (event.key === 'ArrowRight') {
      nextIndex = (currentIndex + 1) % order.length;
    } else if (event.key === 'ArrowLeft') {
      nextIndex = (currentIndex - 1 + order.length) % order.length;
    } else if (event.key === 'Home') {
      nextIndex = 0;
    } else if (event.key === 'End') {
      nextIndex = order.length - 1;
    } else {
      return;
    }

    event.preventDefault();
    const nextKind = order[nextIndex];
    setKind(nextKind);
    document.getElementById(`lawyer-tab-${nextKind}`)?.focus();
  }

  function openDecision(
    kindOfLawyer: LawyerKind,
    lawyer: Lawyer,
    action: 'approve' | 'reject',
  ) {
    setReason('');
    setFormError(null);
    setDecision({ kind: kindOfLawyer, lawyer, action });
  }

  async function submitDecision() {
    if (!decision) return;
    const trimmed = reason.trim();
    if (decision.action === 'reject' && !trimmed) {
      setFormError('Enter a reason before rejecting this profile.');
      return;
    }

    setBusy(true);
    setFormError(null);
    try {
      if (decision.action === 'approve') {
        await onApprove(decision.kind, decision.lawyer.id);
        notify(`${decision.lawyer.name} was approved.`, 'success');
      } else {
        await onReject(decision.kind, decision.lawyer.id, trimmed);
        notify(`${decision.lawyer.name} was rejected.`, 'info');
      }
      setDecision(null);
      setReason('');
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

  return (
    <div className="lawyer-approvals">
      {error && loaded && <ErrorBanner message={error} onRetry={onRetry} />}

      <section className="section-intro" aria-labelledby="lawyer-heading">
        <div>
          <p className="eyebrow">Credential review</p>
          <h2 id="lawyer-heading">Profiles awaiting verification</h2>
          <p>
            Check bar council details, academic history, and practice
            information before approving a profile.
          </p>
        </div>
        {loaded && (
          <div className="pending-summary">
            <span className="pending-summary__dot" aria-hidden="true" />
            <span>
              <strong>{totalPending}</strong>{' '}
              {pluralize(totalPending, 'profile')} pending
            </span>
          </div>
        )}
      </section>

      <div
        className="segmented"
        role="tablist"
        aria-label="Lawyer type"
        onKeyDown={handleTabKeyDown}
      >
        {(
          [
            { id: 'fresher' as const, label: 'Fresher', list: freshers },
            {
              id: 'experienced' as const,
              label: 'Experienced',
              list: experienced,
            },
          ]
        ).map((tab) => (
          <button
            key={tab.id}
            id={`lawyer-tab-${tab.id}`}
            type="button"
            role="tab"
            className="segmented__tab"
            aria-selected={kind === tab.id}
            aria-controls="lawyer-panel"
            tabIndex={kind === tab.id ? 0 : -1}
            onClick={() => setKind(tab.id)}
          >
            {tab.label}
            {loaded && (
              <span className="segmented__count">
                {tab.list.filter(isPending).length}
              </span>
            )}
          </button>
        ))}
      </div>

      <div
        id="lawyer-panel"
        role="tabpanel"
        aria-labelledby={`lawyer-tab-${kind}`}
        className="lawyer-panel"
      >
        {loading && !loaded ? (
          <div className="lawyer-list" aria-busy="true" aria-label="Loading lawyer profiles">
            {Array.from({ length: 3 }, (_, index) => (
              <div className="lawyer-card lawyer-card--skeleton" key={index}>
                <div className="lawyer-card__header">
                  <div className="lawyer-card__identity">
                    <Skeleton width={46} height={46} style={{ borderRadius: '50%' }} />
                    <div>
                      <Skeleton width={190} height={14} />
                      <Skeleton width={140} height={10} style={{ marginTop: 9 }} />
                    </div>
                  </div>
                  <Skeleton width={90} height={22} />
                </div>
                <div className="lawyer-facts">
                  {Array.from({ length: 6 }, (_, factIndex) => (
                    <div key={factIndex}>
                      <Skeleton width={74} height={9} />
                      <Skeleton width="80%" height={12} style={{ marginTop: 8 }} />
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        ) : profiles.length === 0 ? (
          <EmptyState
            icon="check"
            title={
              kind === 'fresher'
                ? 'No pending fresher profiles'
                : 'No pending experienced profiles'
            }
            message="New credential submissions will appear here for review."
            action={
              <button
                type="button"
                className="btn btn--quiet"
                onClick={onRetry}
              >
                <Icon name="refresh" size={15} />
                Refresh queue
              </button>
            }
          />
        ) : (
          <div className="lawyer-list">
            {profiles.map((lawyer) => (
              <LawyerCard
                key={lawyer.id}
                lawyer={lawyer}
                kind={kind}
                onApprove={() =>
                  openDecision(kind, lawyer, 'approve')
                }
                onReject={() => openDecision(kind, lawyer, 'reject')}
              />
            ))}
          </div>
        )}
      </div>

      {pendingCount === 0 && profiles.length > 0 && (
        <p className="panel-footnote">
          All profiles in this view have a recorded decision. Refresh the queue
          to pull in new submissions.
        </p>
      )}

      {decision && (
        <Modal
          eyebrow={decision.kind === 'fresher' ? 'Fresher profile' : 'Experienced profile'}
          title={
            decision.action === 'approve'
              ? `Approve ${decision.lawyer.name}?`
              : `Reject ${decision.lawyer.name}?`
          }
          description={
            decision.action === 'approve'
              ? 'This marks the lawyer profile as verified and makes it eligible for the platform.'
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
                  decision.action === 'approve'
                    ? 'btn btn--approve'
                    : 'btn btn--danger'
                }
                onClick={submitDecision}
                disabled={busy}
                data-autofocus
              >
                {busy
                  ? decision.action === 'approve'
                    ? 'Approving…'
                    : 'Rejecting…'
                  : decision.action === 'approve'
                    ? 'Approve profile'
                    : 'Reject profile'}
              </button>
            </>
          }
        >
          <div className="decision-summary">
            <span>
              <span>Name</span>
              <strong>{decision.lawyer.name}</strong>
            </span>
            <span>
              <span>Bar Council ID</span>
              <strong className="mono">
                {displayValue(decision.lawyer.barCouncilId)}
              </strong>
            </span>
          </div>

          {decision.action === 'reject' && (
            <div className="field">
              <div className="field__label-row">
                <label htmlFor={reasonId}>Reason for rejection</label>
                <span className="field__optional">Required</span>
              </div>
              <textarea
                id={reasonId}
                className="textarea"
                rows={4}
                value={reason}
                onChange={(event) => {
                  setReason(event.target.value);
                  if (formError) setFormError(null);
                }}
                maxLength={500}
                placeholder="Explain what needs to be corrected…"
                disabled={busy}
                data-autofocus
              />
              <p className="field__hint">
                Required. Keep the note factual and specific.
              </p>
            </div>
          )}

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

interface LawyerCardProps {
  lawyer: Lawyer;
  kind: LawyerKind;
  onApprove: () => void;
  onReject: () => void;
}

function LawyerCard({ lawyer, kind, onApprove, onReject }: LawyerCardProps) {
  const resolved =
    lawyer.verificationStatus === 'VERIFIED' ||
    lawyer.verificationStatus === 'REJECTED';
  const practiceAreas = formatPracticeAreas(lawyer.practiceAreas);
  const graduation =
    lawyer.graduationYear !== null &&
    lawyer.graduationYear !== undefined &&
    String(lawyer.graduationYear) !== '0' &&
    String(lawyer.graduationYear).trim() !== ''
      ? String(lawyer.graduationYear)
      : '—';

  return (
    <article className={`lawyer-card${resolved ? ' lawyer-card--resolved' : ''}`}>
      <header className="lawyer-card__header">
        <div className="lawyer-card__identity">
          <span className="avatar" aria-hidden="true">
            {initials(lawyer.name)}
          </span>
          <div>
            <h3>{lawyer.name}</h3>
            <p>
              {kind === 'fresher' ? 'Fresher' : 'Experienced'} ·{' '}
              {displayValue(lawyer.specialization)}
            </p>
            <p className="lawyer-card__applied">
              Applied {formatRelative(lawyer.createdAt)}
            </p>
          </div>
        </div>
        <StatusChip status={lawyer.verificationStatus} size="sm" />
      </header>

      <dl className="lawyer-facts">
        <div>
          <dt>Email</dt>
          <dd className="lawyer-facts__contact">
            <Icon name="mail" size={13} />
            {displayValue(lawyer.email)}
          </dd>
        </div>
        <div>
          <dt>Phone</dt>
          <dd className="lawyer-facts__contact">
            <Icon name="phone" size={13} />
            {displayValue(lawyer.phone)}
          </dd>
        </div>
        <div>
          <dt>Bar Council ID</dt>
          <dd className="mono">{displayValue(lawyer.barCouncilId)}</dd>
        </div>
        {kind === 'experienced' && (
          <div>
            <dt>Current firm</dt>
            <dd>{displayValue(lawyer.currentFirm)}</dd>
          </div>
        )}
        <div>
          <dt>University</dt>
          <dd>{displayValue(lawyer.university)}</dd>
        </div>
        <div>
          <dt>Graduation</dt>
          <dd className="mono">{graduation}</dd>
        </div>
        <div>
          <dt>Specialisation</dt>
          <dd>{displayValue(lawyer.specialization)}</dd>
        </div>
        <div>
          <dt>Address</dt>
          <dd>{displayValue(lawyer.address)}</dd>
        </div>
        {kind === 'experienced' && (
          <div className="lawyer-facts__wide">
            <dt>Practice areas</dt>
            <dd>
              {practiceAreas.length > 0 ? (
                <span className="practice-list">
                  {practiceAreas.map((area) => (
                    <Chip key={area}>{area}</Chip>
                  ))}
                </span>
              ) : (
                '—'
              )}
            </dd>
          </div>
        )}
      </dl>

      {lawyer.rejectionReason && (
        <p className="lawyer-card__rejection">
          <Icon name="alert" size={14} />
          <span>
            <strong>Rejection note:</strong> {lawyer.rejectionReason}
          </span>
        </p>
      )}

      <footer className="lawyer-card__actions">
        {resolved ? (
          <p className="decision-recorded">
            <Icon
              name={lawyer.verificationStatus === 'VERIFIED' ? 'check' : 'alert'}
              size={14}
            />
            <span>
              {lawyer.verificationStatus === 'VERIFIED'
                ? 'Approved'
                : 'Rejected'}
              {lawyer.verifiedAt
                ? ` · ${formatDateTime(lawyer.verifiedAt)}`
                : ''}
            </span>
          </p>
        ) : (
          <>
            <button
              type="button"
              className="btn btn--danger-quiet"
              onClick={onReject}
            >
              <Icon name="close" size={15} />
              Reject
            </button>
            <button
              type="button"
              className="btn btn--approve"
              onClick={onApprove}
            >
              <Icon name="check" size={15} />
              Approve profile
            </button>
          </>
        )}
      </footer>
    </article>
  );
}
