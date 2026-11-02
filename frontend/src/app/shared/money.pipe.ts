import { Pipe, PipeTransform } from '@angular/core';

const formatters = new Map<string, Intl.NumberFormat>();

/** Formats whole-unit amounts in their own currency, e.g. 2400000 INR → ₹24,00,000 (en-IN grouping). */
export function formatMoney(amount: number | null | undefined, currency: string): string {
  if (amount === null || amount === undefined) {
    return '—';
  }
  let formatter = formatters.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat(currency === 'INR' ? 'en-IN' : 'en-US', {
      style: 'currency',
      currency,
      maximumFractionDigits: 0,
      minimumFractionDigits: 0,
    });
    formatters.set(currency, formatter);
  }
  return formatter.format(amount);
}

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(amount: number | null | undefined, currency: string): string {
    return formatMoney(amount, currency);
  }
}
