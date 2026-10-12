import { useCallback, useEffect, useRef, useState } from "react"

export default function usePocketProgress(sessionId, onUpdated) {
  const [status, setStatus] = useState(null)
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState("")
  const [freshReportId, setFreshReportId] = useState(null)
  const updated = useRef(onUpdated)
  updated.current = onUpdated
  const scope = useRef(null)
  if (scope.current?.id !== sessionId) scope.current = { id: sessionId, pending: null, failed: false, ready: false }
  const base = `/api/worlds/pockets/${sessionId}/progress`
  const request = useCallback(async (suffix, options = {}) => {
    const response = await fetch(`${base}${suffix}`, { credentials: "include", ...options })
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw new Error(body?.detail || body?.message || "시간 체크를 처리하지 못했어요. 다시 시도해 주세요.")
    }
    return response.status === 204 ? null : response.json().catch(() => null)
  }, [base])
  const run = useCallback((suffix, options, refresh) => {
    const active = scope.current
    if (active.pending) return active.pending
    setBusy(true)
    setError("")
    active.pending = (async () => {
      try {
        // Keep the real ON/OFF state visible even if the first catch-up fails.
        if (["/resume", "/simulate"].includes(suffix) && !active.ready) {
          const previous = await request("")
          if (scope.current !== active) return false
          setStatus(previous)
          active.reportId = previous?.lastReport?.id
        }
        const result = await request(suffix, options)
        if (scope.current !== active) return false
        if (["/resume", "/simulate"].includes(suffix) && result?.lastReport?.id != null && result.lastReport.id !== active.reportId)
          setFreshReportId(result.lastReport.id)
        const changed = !active.ready || active.reportId !== result?.lastReport?.id || active.simulatedAt !== result?.simulatedAt
        if (options?.method === "DELETE") setFreshReportId(null)
        setStatus(result); active.failed = false; active.ready = true; active.testHours = null
        active.reportId = result?.lastReport?.id; active.simulatedAt = result?.simulatedAt
        if (refresh && changed) await updated.current?.()
        return true
      } catch (failure) {
        if (scope.current === active) { active.failed = true; setError(failure.message) }
        return false
      } finally {
        active.pending = null
        if (scope.current === active) setBusy(false)
      }
    })()
    return active.pending
  }, [request])
  const resume = useCallback(() => scope.current.testHours != null
    ? run("/simulate", { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ hours: scope.current.testHours }) }, true)
    : run("/resume", { method: "POST" }, true), [run])
  const simulate = useCallback(hours => {
    if (scope.current.pending) return scope.current.pending
    scope.current.testHours = hours
    return resume()
  }, [resume])
  const discard = useCallback(reportId => run(`/reports/${reportId}`, { method: "DELETE" }, true), [run])
  const configure = useCallback(enabled => run("", {
    method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ enabled }),
  }, false), [run])
  useEffect(() => {
    let mounted = true
    const active = scope.current
    resume()
    const heartbeat = () => {
      if (!mounted || !active.ready || active.pending || active.failed || document.hidden) return
      request("/presence", { method: "POST" }).catch(failure => {
        if (mounted && scope.current === active) { active.failed = true; setError(failure.message) }
      })
    }
    const hiddenPresence = () => {
      if (active.ready && !active.pending && !active.failed)
        request("/presence", { method: "POST", keepalive: true }).catch(() => {})
    }
    const visibility = () => { if (document.hidden) hiddenPresence(); else resume() }
    const pageShow = event => { if (event.persisted) resume() }
    const interval = setInterval(heartbeat, 30_000)
    document.addEventListener("visibilitychange", visibility)
    window.addEventListener("pagehide", hiddenPresence)
    window.addEventListener("pageshow", pageShow)
    return () => {
      mounted = false; clearInterval(interval)
      document.removeEventListener("visibilitychange", visibility)
      window.removeEventListener("pagehide", hiddenPresence)
      window.removeEventListener("pageshow", pageShow)
      hiddenPresence()
    }
  }, [request, resume])
  return { status, busy, error, freshReportId, resume, simulate, discard, configure, reload: () => run("", {}, false) }
}
