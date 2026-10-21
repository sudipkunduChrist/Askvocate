import type { PendingDocument } from '../api/types';

export type PanFilter = 'all' | 'mismatch' | 'duplicate' | 'awaiting';

export const PAN_FILTERS: { id: PanFilter; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'mismatch', label: 'Mismatch' },
  { id: 'duplicate', label: 'Duplicate' },
  { id: 'awaiting', label: 'Awaiting Aadhaar' },
];

export function firstNonEmpty(
  ...values: (string | null | undefined)[]
): string | null {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value.trim();
  }
  return null;
}

export function applicantName(document: PendingDocument): string {
  return (
    firstNonEmpty(
      document.extractedData?.name,
      document.aadhaarQrData?.name,
    ) ?? 'Unnamed applicant'
  );
}

export function maskPan(pan: string): string {
  const clean = pan.replace(/\s+/g, '').toUpperCase();
  if (!clean) return '—';
  if (clean.length === 10) return `XXXXX${clean.slice(5)}`;
  if (clean.length <= 4) return '••••';
  return `${'•'.repeat(clean.length - 4)}${clean.slice(-4)}`;
}

export function maskedPan(document: PendingDocument): string {
  return (
    firstNonEmpty(document.maskedDocumentNumber) ??
    (document.panNumber ? maskPan(document.panNumber) : '—')
  );
}

export function fullPan(document: PendingDocument): string {
  return firstNonEmpty(document.panNumber) ?? '—';
}

function normalizeField(key: string): string {
  return key.replace(/[^a-z0-9]/gi, '').toLowerCase();
}

export function mismatchFields(document: PendingDocument): string[] {
  return (document.crossDocumentMismatchFields ?? []).filter(
    (field) => typeof field === 'string' && field.trim().length > 0,
  );
}

export function hasMismatch(document: PendingDocument): boolean {
  return mismatchFields(document).length > 0;
}

export function isFieldMismatched(
  document: PendingDocument,
  field: string,
): boolean {
  const normalized = normalizeField(field);
  return mismatchFields(document).some(
    (candidate) => normalizeField(candidate) === normalized,
  );
}

export function isAwaitingAadhaar(document: PendingDocument): boolean {
  return Object.keys(document.aadhaarQrData ?? {}).length === 0;
}

export function isDuplicate(document: PendingDocument): boolean {
  const text = `${document.manualReviewReason ?? ''} ${
    document.failureReason ?? ''
  }`.toLowerCase();
  return text.includes('duplicate');
}

export function documentMatchesFilter(
  document: PendingDocument,
  filter: PanFilter,
): boolean {
  switch (filter) {
    case 'mismatch':
      return hasMismatch(document);
    case 'duplicate':
      return isDuplicate(document);
    case 'awaiting':
      return isAwaitingAadhaar(document);
    case 'all':
    default:
      return true;
  }
}

export function countByFilter(
  documents: PendingDocument[],
  filter: PanFilter,
): number {
  return documents.filter((document) => documentMatchesFilter(document, filter))
    .length;
}
