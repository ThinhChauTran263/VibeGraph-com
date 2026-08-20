import { onActivated } from 'vue'

/**
 * Re-runs `refresh` in the background every time a kept-alive view is
 * RE-activated (navigated back to), so cached pages show fresh data without a
 * reload flash. The initial mount activation is skipped — onMounted already
 * loads — and errors are swallowed: a failed background refresh must keep the
 * last good data on screen (the view's own retry UI owns error states).
 * `refresh` itself should avoid flipping loading flags while data exists.
 */
export function useSilentRefresh(refresh: () => unknown): void {
  let firstActivation = true
  onActivated(() => {
    if (firstActivation) {
      firstActivation = false
      return
    }
    void Promise.resolve()
      .then(refresh)
      .catch(() => undefined)
  })
}
