export type SelectOpt = { value: string; label: string };

/**
 * SearchableSelect works with display labels, not values.
 * Makes labels unique (suffixing duplicates) so a picked label maps back to exactly one value.
 */
export function uniqueOptions(opts: SelectOpt[]): SelectOpt[] {
  const seen = new Map<string, number>();
  return opts.map((opt) => {
    const count = (seen.get(opt.label) ?? 0) + 1;
    seen.set(opt.label, count);
    if (count === 1) return opt;
    const suffix = opt.value ? ` (${opt.value})` : ` #${count}`;
    return { ...opt, label: `${opt.label}${suffix}` };
  });
}

/** Label for a value; falls back to `fallback` (e.g. "All") when the value is not in the list. Never mutates state. */
export function labelOf(opts: SelectOpt[], value: string, fallback = ""): string {
  return opts.find((opt) => opt.value === value)?.label ?? fallback;
}

/** Value for a picked label; "" when not found. */
export function valueOf(opts: SelectOpt[], label: string): string {
  return opts.find((opt) => opt.label === label)?.value ?? "";
}
