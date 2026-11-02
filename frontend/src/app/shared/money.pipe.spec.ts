import { formatMoney } from './money.pipe';

describe('formatMoney', () => {
  it('formats whole units without decimals', () => {
    expect(formatMoney(120000, 'USD')).toBe('$120,000');
  });

  it('uses Indian digit grouping for INR', () => {
    expect(formatMoney(2400000, 'INR')).toBe('₹24,00,000');
  });

  it('formats other currencies with their symbol', () => {
    expect(formatMoney(65000, 'EUR')).toBe('€65,000');
  });

  it('renders a dash for missing values', () => {
    expect(formatMoney(null, 'USD')).toBe('—');
    expect(formatMoney(undefined, 'USD')).toBe('—');
  });
});
