export class ApiError extends Error {
  readonly status: number | null;

  constructor(message: string, status: number | null = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export function isAuthError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 401 || error.status === 403);
}

export function isNetworkError(error: unknown): boolean {
  return error instanceof TypeError;
}

export function toErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (isNetworkError(error)) {
    return 'Could not reach the server. Check that the backend is running, then try again.';
  }
  if (error instanceof Error && error.message.trim()) return error.message;
  return 'Something went wrong. Try again.';
}
