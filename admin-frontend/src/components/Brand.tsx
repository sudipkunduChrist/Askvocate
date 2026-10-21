interface BrandProps {
  compact?: boolean;
  className?: string;
}

export function Brand({ compact = false, className }: BrandProps) {
  return (
    <div className={`brand${compact ? ' brand--compact' : ''}${className ? ` ${className}` : ''}`}>
      <span className="brand__mark" aria-hidden="true">
        <svg viewBox="0 0 32 32" fill="none" focusable="false">
          <path
            d="M16 3.5 27 9v7.2c0 6-4.4 10.2-11 12.3-6.6-2.1-11-6.3-11-12.3V9l11-5.5Z"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinejoin="round"
          />
          <path
            d="m10.8 15.8 3.5 3.6 7-7.6"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </span>
      <span className="brand__text">
        <strong>Askvocate</strong>
        {!compact && <span>Admin console</span>}
      </span>
    </div>
  );
}
