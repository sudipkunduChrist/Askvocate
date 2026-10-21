const dateTimeFormatter = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
});

const dateFormatter = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
});

const timeFormatter = new Intl.DateTimeFormat('en-IN', {
  hour: 'numeric',
  minute: '2-digit',
  second: '2-digit',
});

const numberFormatter = new Intl.NumberFormat('en-IN');

const relativeFormatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

const FIELD_LABELS: Record<string, string> = {
  name: 'Name',
  fathername: "Father's name",
  dob: 'Date of birth',
  gender: 'Gender',
  address: 'Address',
  pincode: 'PIN code',
  aadhaarlast4: 'Aadhaar last 4',
  pannumber: 'PAN number',
  barcouncilid: 'Bar Council ID',
  university: 'University',
  graduationyear: 'Graduation year',
  specialization: 'Specialisation',
};

export function toDate(value: string | number | null | undefined): Date | null {
  if (value === null || value === undefined || value === '') return null;
  const date =
    typeof value === 'number'
      ? new Date(value < 1_000_000_000_000 ? value * 1000 : value)
      : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDateTime(value: string | number | null | undefined): string {
  const date = toDate(value);
  return date ? dateTimeFormatter.format(date) : '—';
}

export function formatDate(value: string | number | null | undefined): string {
  const date = toDate(value);
  return date ? dateFormatter.format(date) : '—';
}

export function formatTime(value: Date | string | number | null | undefined): string {
  const date = value instanceof Date ? value : toDate(value);
  return date ? timeFormatter.format(date) : '—';
}

export function formatRelative(value: string | number | null | undefined): string {
  const date = toDate(value);
  if (!date) return '—';

  const difference = date.getTime() - Date.now();
  const minutes = Math.round(difference / 60_000);

  if (Math.abs(difference) < 45_000) return 'just now';
  if (Math.abs(minutes) < 60) return relativeFormatter.format(minutes, 'minute');

  const hours = Math.round(minutes / 60);
  if (Math.abs(hours) < 24) return relativeFormatter.format(hours, 'hour');

  const days = Math.round(hours / 24);
  if (Math.abs(days) < 8) return relativeFormatter.format(days, 'day');

  return dateFormatter.format(date);
}

export function formatNumber(value: number): string {
  return numberFormatter.format(value);
}

export function pluralize(count: number, singular: string, plural?: string): string {
  return count === 1 ? singular : (plural ?? `${singular}s`);
}

export function displayValue(value: unknown): string {
  if (value === null || value === undefined) return '—';
  const text = String(value).trim();
  return text || '—';
}

export function initials(name: string): string {
  const parts = name
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

export function fieldLabel(key: string): string {
  const normalized = key.replace(/[^a-z0-9]/gi, '').toLowerCase();
  if (FIELD_LABELS[normalized]) return FIELD_LABELS[normalized];
  const spaced = key
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim();
  return spaced.replace(/\b\w/g, (character) => character.toUpperCase());
}

export function formatPracticeAreas(
  practiceAreas: string[] | null | undefined,
): string[] {
  return (practiceAreas ?? []).map((area) => area.trim()).filter(Boolean);
}
