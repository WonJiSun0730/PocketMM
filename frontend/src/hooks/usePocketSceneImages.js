import { useEffect, useState } from "react"
import { loadSceneImages } from "@/lib/pocketScenePreparation"

export default function usePocketSceneImages(asset) {
  const key = asset ? `${asset.backgroundUrl}|${asset.sceneryUrl}` : ""
  const [result, setResult] = useState({ key: "", ready: false, error: "" })
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    if (!asset) return
    const controller = new AbortController()
    setResult({ key, ready: false, error: "" })
    loadSceneImages(asset, { signal: controller.signal })
      .then(() => { if (!controller.signal.aborted) setResult({ key, ready: true, error: "" }) })
      .catch(error => { if (error.name !== "AbortError") { setResult({ key, ready: false, error: error.message }); controller.abort() } })
    return () => controller.abort()
  }, [key, revision])
  return { ready: Boolean(key) && result.key === key && result.ready,
    error: result.key === key ? result.error : "", retry: () => setRevision(value => value + 1) }
}
