import type { ReactNode } from 'react';
import type { VerificationStatus } from '../api/types';

type Tone = 'amber' | 'teal' | 'brick' | 'neutral' | 'ink';

interface StatusConfig {
  label: string;
  tone: Tone;
}

const STATUS_CONFIG: Record<VerificationStatus, StatusConfig> = {
  PENDING: { label: 'Pending review', tone: 'amber' },
  UNDER_VERIFICATION: { label: 'In review', tone: 'amber' },
  VERIFIED: { label: 'Verified', tone: 'teal' },
  FAILED: { label: 'Failed', tone: 'brick' },
  REJECTED: { label: 'Rejected', tone: 'brick' },
};

interface StatusChipProps {
  status: VerificationStatus;
  size?: 'sm' | 'md';
}

export function StatusChip({ status, size = 'md' }: StatusChipProps) {
  const config = STATUS_CONFIG[status] ?? {
    label: String(status).replace(/_/g, ' ').toLowerCase(),
    tone: 'neutral' as Tone,
  };
  return (
    <span className={`chip chip--${config.tone}${size === 'sm' ? ' chip--sm' : ''}`}>
      <span className="chip__dot" aria-hidden="true" />
      {config.label}
    </span>
  );
}

interface ChipProps {
  children: ReactNode;
  tone?: Tone;
  className?: string;
}

export function Chip({ children, tone = 'neutral', className }: ChipProps) {
  return (
    <span className={`chip chip--${tone}${className ? ` ${className}` : ''}`}>
      {children}
    </span>
  );
}
