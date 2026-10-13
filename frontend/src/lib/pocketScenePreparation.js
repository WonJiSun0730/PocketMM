export function scenePreparationState(scene, images, modelsReady = true) {
  if (scene.asset && images.ready && modelsReady) return "READY"
  if (images.error || scene.status === "FAILED" || (scene.loaded && !scene.asset && scene.status !== "GENERATING"
    && (!scene.available || !scene.automatic || scene.status === "READY"))) return "ERROR"
  return "LOADING"
}

export function loadSceneImages(asset, { createImage = () => new Image(), signal, timeoutMs = 30000 } = {}) {
  return Promise.all([asset.backgroundUrl, asset.sceneryUrl].map(url => new Promise((resolve, reject) => {
    const image = createImage()
    let done = false
    const finish = error => {
      if (done) return
      done = true; clearTimeout(timer); image.onload = null; image.onerror = null
      signal?.removeEventListener("abort", abort)
      if (error) reject(error); else resolve(url)
    }
    const abort = () => { const error = new Error("Scene preparation cancelled"); error.name = "AbortError"; finish(error); image.src = "" }
    const timer = setTimeout(() => finish(new Error("주머니 안쪽을 불러오지 못했어요.")), timeoutMs)
    image.onload = () => finish(image.naturalWidth > 0 ? null : new Error("주머니 안쪽을 불러오지 못했어요."))
    image.onerror = () => finish(new Error("주머니 안쪽을 불러오지 못했어요."))
    if (signal?.aborted) { abort(); return }
    signal?.addEventListener("abort", abort, { once: true })
    image.src = url
  })))
}
