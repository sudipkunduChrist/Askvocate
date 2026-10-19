export type IconName =
  | 'overview'
  | 'pan'
  | 'lawyers'
  | 'refresh'
  | 'logout'
  | 'close'
  | 'check'
  | 'alert'
  | 'arrowLeft'
  | 'chevronRight'
  | 'clock'
  | 'document'
  | 'shield'
  | 'info'
  | 'eye'
  | 'eyeOff'
  | 'lock'
  | 'zoom'
  | 'mail'
  | 'phone'
  | 'pin'
  | 'building'
  | 'scale';

interface IconProps {
  name: IconName;
  size?: number;
  className?: string;
  strokeWidth?: number;
}

export function Icon({
  name,
  size = 18,
  className,
  strokeWidth = 1.7,
}: IconProps) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {name === 'overview' && (
        <>
          <rect x="3" y="3" width="7.5" height="7.5" rx="1.2" />
          <rect x="13.5" y="3" width="7.5" height="7.5" rx="1.2" />
          <rect x="3" y="13.5" width="7.5" height="7.5" rx="1.2" />
          <rect x="13.5" y="13.5" width="7.5" height="7.5" rx="1.2" />
        </>
      )}
      {name === 'pan' && (
        <>
          <rect x="2.5" y="5" width="19" height="14" rx="2" />
          <circle cx="8" cy="11" r="2" />
          <path d="M4.8 16c.7-1.4 1.8-2.1 3.2-2.1s2.5.7 3.2 2.1" />
          <path d="M14.5 9.5h4M14.5 13h4" />
        </>
      )}
      {name === 'lawyers' && (
        <>
          <circle cx="9" cy="8" r="3" />
          <path d="M3.2 20c0-3.3 2.6-6 5.8-6 1.5 0 2.9.6 4 1.6" />
          <path d="m15.5 17.2 2 2 4-4.2" />
        </>
      )}
      {name === 'refresh' && (
        <>
          <path d="M20.5 12a8.5 8.5 0 1 1-2.4-5.9" />
          <path d="M20.5 3.5v5.7h-5.7" />
        </>
      )}
      {name === 'logout' && (
        <>
          <path d="M9.5 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4.5" />
          <path d="m16 16.5 4.5-4.5L16 7.5" />
          <path d="M20.5 12H9" />
        </>
      )}
      {name === 'close' && <path d="M5.5 5.5l13 13M18.5 5.5l-13 13" />}
      {name === 'check' && <path d="m4.5 12.5 5 5 10-11" />}
      {name === 'alert' && (
        <>
          <path d="M12 3.5 21 20H3L12 3.5Z" />
          <path d="M12 9.5v4.5" />
          <path d="M12 17.3v.2" />
        </>
      )}
      {name === 'arrowLeft' && (
        <>
          <path d="M20 12H4" />
          <path d="m10 6-6 6 6 6" />
        </>
      )}
      {name === 'chevronRight' && <path d="m9 5 7 7-7 7" />}
      {name === 'clock' && (
        <>
          <circle cx="12" cy="12" r="8.5" />
          <path d="M12 7.5V12l3 2" />
        </>
      )}
      {name === 'document' && (
        <>
          <path d="M6 3h8l4 4v14H6V3Z" />
          <path d="M14 3v5h4" />
          <path d="M9 12h6M9 16h6" />
        </>
      )}
      {name === 'shield' && (
        <>
          <path d="M12 3 20 6v5.6c0 4.5-3.4 7.7-8 9.4-4.6-1.7-8-4.9-8-9.4V6l8-3Z" />
          <path d="m8.5 11.8 2.4 2.5 4.6-5" />
        </>
      )}
      {name === 'info' && (
        <>
          <circle cx="12" cy="12" r="8.5" />
          <path d="M12 11v5.5" />
          <path d="M12 7.6v.2" />
        </>
      )}
      {name === 'eye' && (
        <>
          <path d="M2.5 12S6 5.8 12 5.8 21.5 12 21.5 12 18 18.2 12 18.2 2.5 12 2.5 12Z" />
          <circle cx="12" cy="12" r="3" />
        </>
      )}
      {name === 'eyeOff' && (
        <>
          <path d="M4 4l16 16" />
          <path d="M9.9 5.2A10.6 10.6 0 0 1 12 5c6 0 9.5 7 9.5 7a16.4 16.4 0 0 1-2.7 3.6" />
          <path d="M6.2 6.8A16.2 16.2 0 0 0 2.5 12S6 19 12 19a9.8 9.8 0 0 0 4.1-.9" />
          <path d="M9.9 9.8a3 3 0 0 0 4.3 4.3" />
        </>
      )}
      {name === 'lock' && (
        <>
          <rect x="4.5" y="10" width="15" height="10.5" rx="2" />
          <path d="M8 10V7.5a4 4 0 0 1 8 0V10" />
        </>
      )}
      {name === 'zoom' && (
        <>
          <circle cx="10.5" cy="10.5" r="6.5" />
          <path d="m15.5 15.5 4.5 4.5" />
          <path d="M10.5 7.5v6M7.5 10.5h6" />
        </>
      )}
      {name === 'mail' && (
        <>
          <rect x="3" y="5.5" width="18" height="13" rx="1.8" />
          <path d="m4 7 8 6 8-6" />
        </>
      )}
      {name === 'phone' && (
        <path d="M6.2 3.5h3l1.4 4.2-2 1.4a13 13 0 0 0 6.3 6.3l1.4-2 4.2 1.4v3a2 2 0 0 1-2.2 2A17.5 17.5 0 0 1 4.2 5.7a2 2 0 0 1 2-2.2Z" />
      )}
      {name === 'pin' && (
        <>
          <path d="M12 21s7-6.1 7-11a7 7 0 1 0-14 0c0 4.9 7 11 7 11Z" />
          <circle cx="12" cy="10" r="2.6" />
        </>
      )}
      {name === 'building' && (
        <>
          <path d="M4 21V4.5h10V21" />
          <path d="M14 9h6v12" />
          <path d="M2.5 21h19" />
          <path d="M7 8h4M7 12h4M7 16h4M17 12h1M17 16h1" />
        </>
      )}
      {name === 'scale' && (
        <>
          <path d="M12 3.5v17" />
          <path d="M6 20.5h12" />
          <path d="M4 7h16" />
          <path d="M7 7 4 13h6L7 7ZM17 7l-3 6h6l-3-6Z" />
        </>
      )}
    </svg>
  );
}
