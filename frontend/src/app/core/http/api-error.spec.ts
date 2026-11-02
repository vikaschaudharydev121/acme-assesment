import { HttpErrorResponse } from '@angular/common/http';
import { errorMessage, fieldErrors } from './api-error';

function httpError(status: number, body: unknown): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('api-error', () => {
  it('shows the server detail for client errors', () => {
    const err = httpError(409, { status: 409, code: 'EMAIL_ALREADY_EXISTS', detail: 'Email in use' });
    expect(errorMessage(err)).toBe('Email in use');
  });

  it('never shows server internals for 5xx', () => {
    const err = httpError(500, { status: 500, detail: 'NullPointerException at Foo.java:42' });
    expect(errorMessage(err)).toBe('Something went wrong. Please try again.');
  });

  it('explains network failures', () => {
    expect(errorMessage(httpError(0, null))).toContain('Cannot reach the server');
  });

  it('maps validation errors by field, keeping the first message per field', () => {
    const err = httpError(400, {
      status: 400,
      errors: [
        { field: 'email', message: 'must be a well-formed email address' },
        { field: 'email', message: 'second message' },
        { field: 'annualSalary', message: 'must be greater than 0' },
      ],
    });
    expect(fieldErrors(err)).toEqual({
      email: 'must be a well-formed email address',
      annualSalary: 'must be greater than 0',
    });
  });

  it('handles non-HTTP errors', () => {
    expect(errorMessage(new Error('boom'))).toBe('Something went wrong. Please try again.');
    expect(fieldErrors(new Error('boom'))).toEqual({});
  });
});
