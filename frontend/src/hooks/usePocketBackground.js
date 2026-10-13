import { useCallback, useEffect, useState } from "react"
import { normalizeSceneAsset, backgroundPeriod } from "@/lib/pocketBackgroundLayers"

const changes = new EventTarget()
const cached = new Map()
const keyFor = (sessionId, location, period) => `${sessionId}:${location.trim()}:${period}`
const empty = { status: "MISSING", asset: null, automatic: true, available: false, loaded: false }
function publish(key, result) {
  const next = { ...result, loaded: true, asset: normalizeSceneAsset(result.asset) }
  cached.set(key, next)
  changes.dispatchEvent(new Event("change"))
  return next
}
async function read(response) {
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.detail || body.message || "주머니 안쪽을 준비하지 못했어요. 다시 시도해 주세요.")
  }
  return response.json()
}

export default function usePocketBackground(sessionId, location = "", { autoGenerate = true, simulatedAt = "" } = {}) {
  const place = typeof location === "string" && location.trim() ? location.trim() : ""
  const period=backgroundPeriod(simulatedAt)
  const key = keyFor(sessionId, place,period)
  const [snapshot, setSnapshot] = useState(() => ({ key, result: cached.get(key) ?? empty }))
  const [revision, setRevision] = useState(0)
  const result = snapshot.key === key ? snapshot.result : cached.get(key) ?? empty
  const url = `/api/worlds/pockets/${sessionId}/backgrounds`
  useEffect(() => {
    const changed = () => { setSnapshot({ key, result: cached.get(key) ?? empty }) }
    changes.addEventListener("change", changed)
    return () => changes.removeEventListener("change", changed)
  }, [key])
  useEffect(() => {
    if (!sessionId || !place) return
    let active = true, timer, controller
    const poll = async () => {
      if (!active) return
      controller = new AbortController()
      try {
        let next = await read(await fetch(`${url}?location=${encodeURIComponent(place)}${simulatedAt ? `&simulatedAt=${encodeURIComponent(simulatedAt)}` : ""}`, {
          credentials: "include", signal: controller.signal,
        }))
        if (!active) return
        if (autoGenerate && next.status === "MISSING" && next.automatic && next.available) {
          next = await read(await fetch(url, { method: "POST", credentials: "include",
            headers: { "Content-Type": "application/json" }, body: JSON.stringify({ location: place, simulatedAt }), signal: controller.signal }))
        }
        if (!active) return
        publish(key, next)
        if (next.status === "GENERATING") timer = setTimeout(poll, 2000)
      } catch (error) {
        if (active && error.name !== "AbortError") publish(key, { ...(cached.get(key) ?? empty), status: "FAILED", error: error.message })
      }
    }
    poll()
    return () => { active = false; clearTimeout(timer); controller?.abort() }
  }, [key, sessionId, place, revision, autoGenerate])
  const generate = useCallback(async () => {
    const next = await read(await fetch(url, { method: "POST", credentials: "include",
      headers: { "Content-Type": "application/json" }, body: JSON.stringify({ location: place, simulatedAt }) }))
    publish(key, next); setRevision(value => value + 1)
  }, [key, url, place,simulatedAt])
  const configure = useCallback(async automatic => {
    const next = await read(await fetch(`${url}/configuration`, { method: "PUT", credentials: "include",
      headers: { "Content-Type": "application/json" }, body: JSON.stringify({ automatic, location: place, simulatedAt }) }))
    publish(key, next); changes.dispatchEvent(new Event("reload")); setRevision(value => value + 1)
  }, [key, url, place,simulatedAt])
  const upload = useCallback(async form => {
    if (simulatedAt) form.set("simulatedAt",simulatedAt)
    const next = await read(await fetch(`${url}/upload`, { method: "POST", credentials: "include", body: form }))
    publish(key, next); changes.dispatchEvent(new Event("reload")); setRevision(value => value + 1)
  }, [key, url])
  useEffect(() => {
    const reload = () => setRevision(value => value + 1)
    changes.addEventListener("reload", reload)
    return () => changes.removeEventListener("reload", reload)
  }, [])
  return { ...result, generate, configure, upload, reload: () => setRevision(value => value + 1) }
}
