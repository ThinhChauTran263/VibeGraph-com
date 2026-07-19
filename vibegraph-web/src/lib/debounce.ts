/**
 * Trailing-edge debounce with explicit cancel.
 *
 * Used to coalesce bursts of expensive work (re-building + re-rendering the Sigma
 * graph when filter toggles fire in quick succession) into a single run after the
 * user stops interacting, instead of rebuilding the whole graph on every keystroke
 * or checkbox click — which is what makes a large graph feel like it "freezes".
 */
export interface Debounced<A extends unknown[]> {
  (...args: A): void
  /** Cancel any pending trailing invocation. Safe to call when nothing is pending. */
  cancel(): void
}

export function debounce<A extends unknown[]>(
  fn: (...args: A) => void,
  waitMs: number,
): Debounced<A> {
  let timer: ReturnType<typeof setTimeout> | null = null

  const debounced = (...args: A): void => {
    if (timer !== null) {
      clearTimeout(timer)
    }
    timer = setTimeout(() => {
      timer = null
      fn(...args)
    }, waitMs)
  }

  debounced.cancel = (): void => {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  return debounced
}
