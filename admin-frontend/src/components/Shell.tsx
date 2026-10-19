import type { ReactNode } from 'react';
import { formatTime } from '../utils/format';
import { Brand } from './Brand';
import { Icon } from './Icon';
import { NAV_ITEMS, VIEW_META, type View } from '../navigation';

interface ShellProps {
  view: View;
  onViewChange: (view: View) => void;
  panCount?: number;
  lawyerCount?: number;
  lastRefreshed: Date | null;
  loadError?: string | null;
  refreshing: boolean;
  onRefresh: () => void;
  onSignOut: () => void;
  children: ReactNode;
}

export function Shell({
  view,
  onViewChange,
  panCount,
  lawyerCount,
  lastRefreshed,
  loadError = null,
  refreshing,
  onRefresh,
  onSignOut,
  children,
}: ShellProps) {
  const meta = VIEW_META[view];
  const updatedLabel = loadError
    ? lastRefreshed
      ? 'Refresh failed'
      : 'Could not refresh'
    : lastRefreshed
      ? `Last refreshed ${formatTime(lastRefreshed)}`
      : 'Loading review queue';
  const counts: Partial<Record<View, number | undefined>> = {
    pan: panCount,
    lawyers: lawyerCount,
  };

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>

      <aside className="rail">
        <Brand />
        <nav className="rail__nav" aria-label="Primary">
          <p className="rail__section-label">Review desk</p>
          {NAV_ITEMS.map((item) => {
            const count = counts[item.id];
            return (
              <button
                key={item.id}
                type="button"
                className="rail__link"
                aria-current={view === item.id ? 'page' : undefined}
                onClick={() => onViewChange(item.id)}
              >
                <Icon name={item.icon} size={18} />
                <span className="rail__link-label">{item.label}</span>
                {typeof count === 'number' && count > 0 && (
                  <span className="rail__count" aria-label={`${count} pending`}>
                    {count}
                  </span>
                )}
              </button>
            );
          })}
        </nav>
        <div className="rail__footer">
          <div className="rail__user">
            <span className="rail__user-dot" aria-hidden="true" />
            <span>
              <strong>Signed in as Admin</strong>
              <span>Session protected</span>
            </span>
          </div>
          <button type="button" className="rail__signout" onClick={onSignOut}>
            <Icon name="logout" size={16} />
            Sign out
          </button>
        </div>
      </aside>

      <div className="app-main">
        <header className="topbar">
          <div className="topbar__left">
            <Brand compact className="topbar__brand" />
            <div>
              <p className="topbar__eyebrow">{meta.eyebrow}</p>
              <h1>{meta.title}</h1>
            </div>
          </div>
          <div className="topbar__right">
            <p className="topbar__updated">{updatedLabel}</p>
            <button
              type="button"
              className="btn btn--quiet topbar__refresh"
              onClick={onRefresh}
              disabled={refreshing}
              aria-label={refreshing ? 'Refreshing review queue' : 'Refresh review queue'}
              title="Refresh review queue"
            >
              <Icon
                name="refresh"
                size={15}
                className={refreshing ? 'spin' : undefined}
              />
              <span aria-hidden="true">{refreshing ? 'Refreshing' : 'Refresh'}</span>
            </button>
            <button
              type="button"
              className="btn btn--quiet topbar__signout"
              onClick={onSignOut}
              aria-label="Sign out"
              title="Sign out"
            >
              <Icon name="logout" size={15} />
              <span aria-hidden="true">Sign out</span>
            </button>
          </div>
        </header>

        <nav className="mobile-nav" aria-label="Primary">
          {NAV_ITEMS.map((item) => {
            const count = counts[item.id];
            return (
              <button
                key={item.id}
                type="button"
                className="mobile-nav__link"
                aria-current={view === item.id ? 'page' : undefined}
                onClick={() => onViewChange(item.id)}
              >
                <Icon name={item.icon} size={17} />
                <span>{item.label}</span>
                {typeof count === 'number' && count > 0 && (
                  <span className="mobile-nav__count">{count}</span>
                )}
              </button>
            );
          })}
        </nav>

        <main id="main-content" className="content" tabIndex={-1}>
          {children}
        </main>
      </div>
    </div>
  );
}
