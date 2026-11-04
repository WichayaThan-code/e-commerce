import { useCallback, useEffect, useState } from "react";

export type Loadable<T> = { data: T; loading: boolean; error: boolean; status: number };

/**
 * Fetches on mount and whenever `deps` change; `retry` forces a reload. Stale responses are ignored.
 * `status` is the HTTP status of a failed request (404 → "not found" screens), 0 for network errors.
 */
export function useAsyncData<T>(fetcher: () => Promise<T>, initial: T, enabled: boolean, deps: unknown[]) {
  const [state, setState] = useState<Loadable<T>>({ data: initial, loading: true, error: false, status: 0 });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    setState((prev) => ({ ...prev, loading: true, error: false, status: 0 }));
    fetcher()
      .then((data) => {
        if (!cancelled) setState({ data, loading: false, error: false, status: 200 });
      })
      .catch((reason: unknown) => {
        const status = typeof reason === "object" && reason && "status" in reason ? Number((reason as { status: number }).status) : 0;
        if (!cancelled) setState((prev) => ({ ...prev, loading: false, error: true, status }));
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, attempt, ...deps]);
  const retry = useCallback(() => setAttempt((value) => value + 1), []);
  return { ...state, retry };
}
