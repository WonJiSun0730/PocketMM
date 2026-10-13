import { useEffect, useState } from "react"
import { LOOK_KEY, normalizeLook } from "@/lib/pocketSceneLook"
function read() { try { return normalizeLook(JSON.parse(localStorage.getItem(LOOK_KEY))) } catch { return normalizeLook() } }
export default function usePocketSceneLook() {
  const [look, setLook] = useState(read)
  useEffect(() => {
    const refresh = event => setLook(event.detail ? normalizeLook(event.detail) : read())
    window.addEventListener("pocket-scene-look", refresh); window.addEventListener("storage", refresh)
    return () => { window.removeEventListener("pocket-scene-look", refresh); window.removeEventListener("storage", refresh) }
  }, [])
  function update(value) {
    const next = normalizeLook(value); setLook(next)
    try { localStorage.setItem(LOOK_KEY, JSON.stringify(next)) } catch { /* Storage can be disabled. */ }
    window.dispatchEvent(new CustomEvent("pocket-scene-look", { detail: next }))
  }
  return [look, update]
}
