import type { AdminData } from '../hooks/useAdminData';
import type { View } from '../navigation';
import {
  applicantName,
  hasMismatch,
  isAwaitingAadhaar,
  isDuplicate,
  maskedPan,
} from '../utils/documents';
import {
  formatDateTime,
  formatNumber,
  formatRelative,
  pluralize,
} from '../utils/format';
import { Chip, StatusChip } from '../components/StatusChip';
import { Icon } from '../components/Icon';
import {
  EmptyState,
  ErrorBanner,
  ErrorState,
  OverviewSkeleton,
} from '../components/StateViews';

interface OverviewScreenProps {
  data: AdminData;
  onOpenDocument: (id: string) => void;
  onNavigate: (view: View) => void;
}

export function OverviewScreen({
  data,
  onOpenDocument,
  onNavigate,
}: OverviewScreenProps) {
  const pendingLawyers = [...data.freshers, ...data.experienced].filter(
    (lawyer) =>
      lawyer.verificationStatus === 'PENDING' ||
      lawyer.verificationStatus === 'UNDER_VERIFICATION',
  ).length;
  const pendingPan = data.documents.length;
  const pendingTotal = pendingPan + pendingLawyers;

  if (data.loading && !data.loaded) return <OverviewSkeleton />;
  if (data.error && !data.loaded) {
    return <ErrorState message={data.error} onRetry={data.refresh} />;
  }

  const recent = data.documents.slice(0, 5);
  const composition = [
    {
      label: 'Field mismatches',
      count: data.documents.filter(hasMismatch).length,
      tone: 'amber' as const,
    },
    {
      label: 'Possible duplicates',
      count: data.documents.filter(isDuplicate).length,
      tone: 'brick' as const,
    },
    {
      label: 'Awaiting Aadhaar',
      count: data.documents.filter(isAwaitingAadhaar).length,
      tone: 'neutral' as const,
    },
  ];

  return (
    <div className="overview">
      {data.error && <ErrorBanner message={data.error} onRetry={data.refresh} />}

      <section className="page-intro" aria-labelledby="overview-heading">
        <div>
          <p className="eyebrow">Queue summary</p>
          <h2 id="overview-heading">
            {pendingTotal === 0
              ? 'No items awaiting review'
              : `${formatNumber(pendingTotal)} ${pluralize(
                  pendingTotal,
                  'item',
                )} awaiting review`}
          </h2>
          <p className="page-intro__copy">
            Review pending PAN documents and lawyer profiles. Decisions are
            recorded against your admin session.
          </p>
        </div>
      </section>

      <section className="stat-grid" aria-label="Queue summary">
        <article className="stat-card stat-card--amber">
          <p className="stat-card__label">Pending PAN</p>
          <p className="stat-card__value">{formatNumber(pendingPan)}</p>
          <p className="stat-card__foot">Documents in the manual queue</p>
        </article>
        <article className="stat-card stat-card--ink">
          <p className="stat-card__label">Pending lawyers</p>
          <p className="stat-card__value">{formatNumber(pendingLawyers)}</p>
          <p className="stat-card__foot">Fresher and experienced profiles</p>
        </article>
        <article className="stat-card stat-card--teal">
          <p className="stat-card__label">Approved since sign-in</p>
          <p className="stat-card__value">{formatNumber(data.verifiedToday)}</p>
          <p className="stat-card__foot">PAN and lawyer approvals</p>
        </article>
      </section>

      <div className="overview-grid">
        <section className="panel" aria-labelledby="recent-heading">
          <header className="panel__header">
            <div>
              <p className="eyebrow">Recent queue</p>
              <h3 id="recent-heading">First five pending PAN documents</h3>
            </div>
            <button
              type="button"
              className="text-link"
              onClick={() => onNavigate('pan')}
            >
              Open PAN review
              <Icon name="chevronRight" size={14} />
            </button>
          </header>

          {recent.length === 0 ? (
            <EmptyState
              compact
              icon="check"
              title="No PAN documents waiting"
              message="New manual-review items will appear here as they arrive."
              action={
                <button
                  type="button"
                  className="btn btn--quiet"
                  onClick={data.refresh}
                  disabled={data.loading}
                >
                  <Icon name="refresh" size={15} />
                  Refresh queue
                </button>
              }
            />
          ) : (
            <div className="queue-preview">
              {recent.map((document) => (
                <button
                  type="button"
                  key={document.id}
                  className="queue-preview__row"
                  onClick={() => onOpenDocument(document.id)}
                >
                  <span className="queue-preview__main">
                    <span className="queue-preview__name">
                      {applicantName(document)}
                    </span>
                    <span className="queue-preview__meta">
                      <span className="mono">{maskedPan(document)}</span>
                      <span aria-hidden="true">·</span>
                      <span title={formatDateTime(document.submittedAt)}>
                        {formatRelative(document.submittedAt)}
                      </span>
                    </span>
                  </span>
                  <span className="queue-preview__signal">
                    {hasMismatch(document) ? (
                      <Chip tone="brick">
                        {document.crossDocumentMismatchFields?.length ?? 0}{' '}
                        {pluralize(
                          document.crossDocumentMismatchFields?.length ?? 0,
                          'mismatch',
                          'mismatches',
                        )}
                      </Chip>
                    ) : (
                      <StatusChip
                        status={document.verificationStatus}
                        size="sm"
                      />
                    )}
                  </span>
                  <Icon
                    name="chevronRight"
                    size={16}
                    className="queue-preview__chevron"
                  />
                </button>
              ))}
            </div>
          )}
        </section>

        <div className="overview-side">
          <section className="panel" aria-labelledby="composition-heading">
            <header className="panel__header">
              <div>
                <p className="eyebrow">Queue composition</p>
                <h3 id="composition-heading">What needs attention</h3>
              </div>
            </header>
            <div className="composition">
              {composition.map((item) => {
                const percent =
                  pendingPan === 0
                    ? 0
                    : Math.round((item.count / pendingPan) * 100);
                return (
                  <div className="composition__row" key={item.label}>
                    <div className="composition__top">
                      <span>{item.label}</span>
                      <strong className="mono">
                        {formatNumber(item.count)}
                      </strong>
                    </div>
                    <div
                      className={`composition__track composition__track--${item.tone}`}
                      aria-hidden="true"
                    >
                      <span style={{ width: `${percent}%` }} />
                    </div>
                  </div>
                );
              })}
              <p className="composition__note">
                Categories can overlap. A document may carry more than one
                review signal.
              </p>
            </div>
          </section>

          <section className="panel panel--note" aria-labelledby="how-heading">
            <header className="panel__header">
              <div>
                <p className="eyebrow">Reference</p>
                <h3 id="how-heading">How review works</h3>
              </div>
            </header>
            <ol className="how-list">
              <li>
                <span className="how-list__index">01</span>
                <div>
                  <strong>Compare the records.</strong>
                  <p>
                    Check the PAN OCR fields against the signed Aadhaar QR
                    before making a decision.
                  </p>
                </div>
              </li>
              <li>
                <span className="how-list__index">02</span>
                <div>
                  <strong>Decide with context.</strong>
                  <p>
                    Approvals can carry a note. Rejections always require a
                    reason.
                  </p>
                </div>
              </li>
              <li>
                <span className="how-list__index">03</span>
                <div>
                  <strong>Keep the trail clean.</strong>
                  <p>
                    Decisions are final in this console and remain attached to
                    your admin session.
                  </p>
                </div>
              </li>
            </ol>
          </section>
        </div>
      </div>
    </div>
  );
}
