import { isCompensationChange, percentChange, todayIso } from './employee-form';

describe('isCompensationChange', () => {
  const original = { annualSalary: 2_400_000, countryCode: 'IN' };

  it('is false when creating a new employee', () => {
    expect(isCompensationChange(null, 100, 'US')).toBe(false);
  });

  it('is false for edits that leave pay and country alone', () => {
    expect(isCompensationChange(original, 2_400_000, 'IN')).toBe(false);
  });

  it('is true for a new salary', () => {
    expect(isCompensationChange(original, 2_700_000, 'IN')).toBe(true);
  });

  it('is true for a relocation even with the same number, because the currency changes', () => {
    expect(isCompensationChange(original, 2_400_000, 'DE')).toBe(true);
  });
});

describe('percentChange', () => {
  it('computes relative change', () => {
    expect(percentChange(100_000, 112_500)).toBeCloseTo(0.125);
    expect(percentChange(100_000, 90_000)).toBeCloseTo(-0.1);
  });

  it('is null without a previous value', () => {
    expect(percentChange(undefined, 100)).toBeNull();
  });
});

describe('todayIso', () => {
  it('uses the local calendar date, zero-padded', () => {
    expect(todayIso(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05');
  });
});
