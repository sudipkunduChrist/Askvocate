import { demoApi } from './demo';
import { ApiError } from './errors';
import type {
  LawyerKind,
  LoginResponse,
  PendingDocument,
  PendingLawyersResponse,
} from './types';

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '');
export const TOKEN_KEY = 'askvocateAdminToken';

const inDemoQuery =
  typeof window !== 'undefined' &&
  new URLSearchParams(window.location.search).get('demo') === '1';

export const IS_DEMO_MODE =
  import.meta.env.VITE_DEMO_MODE === 'true' || inDemoQuery;

function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function errorFromPayload(payload: unknown): string | null {
  const record = asRecord(payload);
  if (!record) return null;
  const error = record.error;
  if (typeof error === 'string' && error.trim()) return error;
  const message = record.message;
  if (typeof message === 'string' && message.trim()) return message;
  return null;
}

async function readPayload(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

export function getStoredToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function storeToken(token: string): void {
  try {
    sessionStorage.setItem(TOKEN_KEY, token);
  } catch {
    // Storage can be unavailable in private browsing; the in-memory token still works.
  }
}

export function clearStoredToken(): void {
  try {
    sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    // Nothing to clear.
  }
}

export async function login(password: string): Promise<string> {
  if (IS_DEMO_MODE) {
    const response = await demoApi.login(password);
    return response.appToken;
  }

  const response = await fetch(`${API_BASE_URL}/api/admin/login`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ password }),
  });
  const payload = await readPayload(response);

  if (!response.ok) {
    throw new ApiError(
      errorFromPayload(payload) ??
        'Sign-in failed. Check the admin password and try again.',
      response.status,
    );
  }

  const record = asRecord(payload) as Partial<LoginResponse> | null;
  if (!record || typeof record.appToken !== 'string' || !record.appToken) {
    throw new ApiError('The server did not return an admin session.');
  }
  return record.appToken;
}

async function adminRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getStoredToken();
  if (!token) {
    throw new ApiError('Session expired. Sign in again.', 401);
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    ...(init.headers as Record<string, string> | undefined),
  };
  if (init.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  const payload = await readPayload(response);

  if (response.status === 401 || response.status === 403) {
    throw new ApiError('Session expired. Sign in again.', response.status);
  }
  if (!response.ok) {
    throw new ApiError(
      errorFromPayload(payload) ??
        `The server could not complete that request (${response.status}).`,
      response.status,
    );
  }

  return payload as T;
}

export async function fetchPendingDocuments(): Promise<PendingDocument[]> {
  if (IS_DEMO_MODE) return demoApi.fetchPendingDocuments();

  const payload = await adminRequest<unknown>(
    '/api/admin/verification/documents/pending?type=PAN',
  );
  if (!Array.isArray(payload)) {
    throw new ApiError('The server returned an unexpected document list.');
  }
  return payload as PendingDocument[];
}

export async function approveDocument(id: string, notes?: string): Promise<void> {
  if (IS_DEMO_MODE) {
    await demoApi.approveDocument(id, notes);
    return;
  }
  const trimmed = notes?.trim();
  await adminRequest(
    `/api/admin/verification/documents/${encodeURIComponent(id)}/approve`,
    {
      method: 'POST',
      body: JSON.stringify(trimmed ? { notes: trimmed } : {}),
    },
  );
}

export async function rejectDocument(id: string, reason: string): Promise<void> {
  if (IS_DEMO_MODE) {
    await demoApi.rejectDocument(id, reason);
    return;
  }
  await adminRequest(
    `/api/admin/verification/documents/${encodeURIComponent(id)}/reject`,
    {
      method: 'POST',
      body: JSON.stringify({ reason: reason.trim() }),
    },
  );
}

export async function fetchPendingLawyers(): Promise<PendingLawyersResponse> {
  if (IS_DEMO_MODE) return demoApi.fetchPendingLawyers();

  const payload = await adminRequest<unknown>('/api/admin/verification/pending');
  const record = asRecord(payload);
  if (!record) {
    throw new ApiError('The server returned an unexpected lawyer list.');
  }
  return {
    success: record.success === true,
    freshers: Array.isArray(record.freshers)
      ? (record.freshers as PendingLawyersResponse['freshers'])
      : [],
    experienced: Array.isArray(record.experienced)
      ? (record.experienced as PendingLawyersResponse['experienced'])
      : [],
    totalCount: typeof record.totalCount === 'number' ? record.totalCount : 0,
  };
}

export async function approveLawyer(kind: LawyerKind, id: string): Promise<void> {
  if (IS_DEMO_MODE) {
    await demoApi.approveLawyer(kind, id);
    return;
  }
  await adminRequest(
    `/api/admin/verification/${kind}/${encodeURIComponent(id)}/approve`,
    { method: 'PATCH' },
  );
}

export async function rejectLawyer(
  kind: LawyerKind,
  id: string,
  reason: string,
): Promise<void> {
  if (IS_DEMO_MODE) {
    await demoApi.rejectLawyer(kind, id, reason);
    return;
  }
  await adminRequest(
    `/api/admin/verification/${kind}/${encodeURIComponent(id)}/reject`,
    {
      method: 'PATCH',
      body: JSON.stringify({ reason: reason.trim() }),
    },
  );
}
