import { Employee } from '../../core/models/api.models';

/**
 * A compensation change is a new salary *or* a move to a country with a different currency:
 * 50,000 EUR is not 50,000 INR. Both need a reason so the salary history explains itself.
 */
export function isCompensationChange(
  original: Pick<Employee, 'annualSalary' | 'countryCode'> | null,
  annualSalary: number | null,
  countryCode: string,
): boolean {
  if (!original) {
    return false;
  }
  return original.annualSalary !== annualSalary || original.countryCode !== countryCode;
}

/** Today in the user's local calendar as yyyy-MM-dd (not UTC, which can be "tomorrow"). */
export function todayIso(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

/** Relative change, e.g. 0.125 for a 12.5% raise; null when not comparable. */
export function percentChange(previous: number | undefined, next: number): number | null {
  if (!previous) {
    return null;
  }
  return (next - previous) / previous;
}
