import type { CSSProperties, ReactNode } from 'react';
import { Icon, type IconName } from './Icon';

interface SkeletonProps {
  width?: string | number;
  height?: string | number;
  style?: CSSProperties;
}

export function Skeleton({ width = '100%', height = 12, style }: SkeletonProps) {
  return (
    <span
      className="skeleton"
      aria-hidden="true"
      style={{ width, height, ...style }}
    />
  );
}

export function QueueSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="queue-skeleton" aria-busy="true" aria-label="Loading review queue">
      {Array.from({ length: rows }, (_, index) => (
        <div className="queue-skeleton__row" key={index} aria-hidden="true">
          <div className="queue-skeleton__top">
            <Skeleton width="48%" height={13} />
            <Skeleton width={74} height={20} />
          </div>
          <Skeleton width="64%" height={10} />
          <div className="queue-skeleton__chips">
            <Skeleton width={66} height={18} />
            <Skeleton width={48} height={18} />
          </div>
        </div>
      ))}
    </div>
  );
}

export function DetailSkeleton() {
  return (
    <div className="detail-skeleton" aria-busy="true" aria-label="Loading document details">
      <div className="detail-skeleton__header">
        <div>
          <Skeleton width={120} height={10} />
          <Skeleton width={240} height={28} style={{ marginTop: 12 }} />
          <Skeleton width={190} height={11} style={{ marginTop: 12 }} />
        </div>
        <Skeleton width={96} height={64} />
      </div>
      <div className="detail-skeleton__body">
        <Skeleton width={150} height={10} />
        <div className="detail-skeleton__timeline">
          {Array.from({ length: 3 }, (_, index) => (
            <div className="detail-skeleton__step" key={index}>
              <Skeleton width={12} height={12} style={{ borderRadius: '50%' }} />
              <div>
                <Skeleton width={180} height={12} />
                <Skeleton width={280} height={10} style={{ marginTop: 8 }} />
              </div>
            </div>
          ))}
        </div>
        <Skeleton width={170} height={10} style={{ marginTop: 28 }} />
        <div className="detail-skeleton__columns">
          <Skeleton height={170} />
          <Skeleton height={170} />
        </div>
      </div>
    </div>
  );
}

export function OverviewSkeleton() {
  return (
    <div className="overview-skeleton" aria-busy="true" aria-label="Loading overview">
      <div className="stat-grid">
        {Array.from({ length: 3 }, (_, index) => (
          <div className="stat-card" key={index}>
            <Skeleton width={92} height={10} />
            <Skeleton width={64} height={38} style={{ marginTop: 18 }} />
            <Skeleton width="70%" height={10} style={{ marginTop: 14 }} />
          </div>
        ))}
      </div>
      <div className="overview-grid">
        <div className="panel">
          <div className="panel__header">
            <Skeleton width={140} height={14} />
          </div>
          <QueueSkeleton rows={4} />
        </div>
        <div className="panel">
          <div className="panel__header">
            <Skeleton width={120} height={14} />
          </div>
          <div className="panel__padded">
            <Skeleton height={90} />
          </div>
        </div>
      </div>
    </div>
  );
}

interface ErrorStateProps {
  message: string;
  onRetry: () => void;
  compact?: boolean;
}

export function ErrorState({ message, onRetry, compact = false }: ErrorStateProps) {
  return (
    <div className={`state-view state-view--error${compact ? ' state-view--compact' : ''}`}>
      <span className="state-view__icon" aria-hidden="true">
        <Icon name="alert" size={22} />
      </span>
      <h3>Could not load the review desk</h3>
      <p>{message}</p>
      <button type="button" className="btn btn--primary" onClick={onRetry}>
        <Icon name="refresh" size={15} />
        Try again
      </button>
    </div>
  );
}

interface EmptyStateProps {
  icon?: IconName;
  title: string;
  message: string;
  action?: ReactNode;
  compact?: boolean;
}

export function EmptyState({
  icon = 'check',
  title,
  message,
  action,
  compact = false,
}: EmptyStateProps) {
  return (
    <div className={`state-view${compact ? ' state-view--compact' : ''}`}>
      <span className="state-view__icon state-view__icon--quiet" aria-hidden="true">
        <Icon name={icon} size={22} />
      </span>
      <h3>{title}</h3>
      <p>{message}</p>
      {action}
    </div>
  );
}

interface ErrorBannerProps {
  message: string;
  onRetry: () => void;
}

export function ErrorBanner({ message, onRetry }: ErrorBannerProps) {
  return (
    <div className="error-banner" role="alert">
      <Icon name="alert" size={16} />
      <p>{message}</p>
      <button type="button" className="btn btn--quiet" onClick={onRetry}>
        Retry
      </button>
    </div>
  );
}
