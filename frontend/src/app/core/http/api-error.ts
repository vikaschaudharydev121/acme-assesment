import { HttpErrorResponse } from '@angular/common/http';
import { ApiProblem } from '../models/api.models';

const FALLBACK = 'Something went wrong. Please try again.';

/** Extracts the RFC 7807 body from an HTTP error, if the server sent one. */
export function toProblem(error: unknown): ApiProblem | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const body = error.error as Partial<ApiProblem> | null;
  if (body && typeof body === 'object' && typeof body.status === 'number') {
    return body as ApiProblem;
  }
  return { status: error.status };
}

/** A user-facing sentence for any error: the server's message when safe, a generic one otherwise. */
export function errorMessage(error: unknown): string {
  const problem = toProblem(error);
  if (!problem) {
    return FALLBACK;
  }
  if (problem.status === 0) {
    return 'Cannot reach the server. Check your connection and try again.';
  }
  if (problem.status >= 500) {
    return FALLBACK;
  }
  return problem.detail ?? FALLBACK;
}

/** Field-level validation messages keyed by field name. */
export function fieldErrors(error: unknown): Record<string, string> {
  const result: Record<string, string> = {};
  for (const violation of toProblem(error)?.errors ?? []) {
    result[violation.field] ??= violation.message;
  }
  return result;
}
