/** Formatting helpers shared by all screens. */

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['week', 7 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
];

/** Compact Instagram-style age: "now", "5m", "3h", "2d", "4w", then a date. */
export function shortAge(createdAt: number, now = Date.now() / 1000): string {
  const diff = Math.max(0, Math.floor(now - createdAt));
  if (diff < 60) return 'now';
  if (diff < 3600) return `${Math.floor(diff / 60)}m`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h`;
  if (diff < 7 * 86400) return `${Math.floor(diff / 86400)}d`;
  if (diff < 30 * 86400) return `${Math.floor(diff / (7 * 86400))}w`;
  return new Date(createdAt * 1000).toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: diff > 365 * 86400 ? 'numeric' : undefined,
  });
}

/** Long relative time: "5 minutes ago". */
export function relativeTime(createdAt: number, now = Date.now() / 1000): string {
  const diff = createdAt - now;
  const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
  for (const [unit, seconds] of UNITS) {
    if (Math.abs(diff) >= seconds) return rtf.format(Math.round(diff / seconds), unit);
  }
  return 'just now';
}

/** Full date + time for tooltips and chat. */
export function fullDate(createdAt: number): string {
  return new Date(createdAt * 1000).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

/** Time of day for chat bubbles. */
export function clockTime(createdAt: number): string {
  return new Date(createdAt * 1000).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

/** Compact count: 999, 1.2K, 3.4M. */
export function compactNumber(value: number): string {
  return new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(value);
}

/** Format sats with thousands separator. */
export function formatSats(sats: number): string {
  return new Intl.NumberFormat(undefined, { notation: sats >= 100_000 ? 'compact' : 'standard', maximumFractionDigits: 1 }).format(sats);
}

/** Pluralize a simple English noun. */
export function plural(count: number, singular: string, pluralForm = `${singular}s`): string {
  return count === 1 ? singular : pluralForm;
}
