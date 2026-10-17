import { useCallback, useEffect, useRef, useState } from 'react'

/** Show loading UI only after `delayMs` to avoid flicker on fast responses. */
export function useDelayedLoading(loading: boolean, delayMs = 150, resetKey?: string | number) {
  const [show, setShow] = useState(false)
  const hadData = useRef(false)

  useEffect(() => {
    hadData.current = false
  }, [resetKey])

  useEffect(() => {
    if (!loading) {
      setShow(false)
      return
    }
    if (hadData.current) {
      setShow(false)
      return
    }
    const id = window.setTimeout(() => setShow(true), delayMs)
    return () => window.clearTimeout(id)
  }, [loading, delayMs])

  const markHasData = useCallback(() => {
    hadData.current = true
  }, [])

  return {
    showSkeleton: show,
    /** Call when first successful payload arrives so later refetches keep content. */
    markHasData,
    isRefetching: loading && hadData.current,
  }
}
