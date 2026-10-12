import { useEffect, useRef, useState } from "react"
import * as THREE from "three"
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js"
import { VRMLoaderPlugin } from "@pixiv/three-vrm"
import { resolvePocketScene, resolvePocketDaypart } from "@/lib/pocketSceneRegistry"
import { getPocketBackground } from "@/lib/pocketSceneBackgrounds"

export default function PocketScene({ className = "", simulatedAt = "", location = "", sessionId = null, playerModel = null, characterModels = [] }) {
  const hostRef = useRef(null)
  const [zoom, setZoom] = useState(1.35)
  const [sceneHeight, setSceneHeight] = useState(260)
  const [pixelHeight, setPixelHeight] = useState(160)
  const [failedBackground, setFailedBackground] = useState(null)
  const preset = resolvePocketScene(location)
  const daypart = resolvePocketDaypart(simulatedAt)
  const registeredBackground = getPocketBackground(sessionId, location)
  const requestedBackground = registeredBackground || preset.backgroundUrl
  const backgroundUrl = failedBackground === requestedBackground ? preset.backgroundUrl : requestedBackground

  useEffect(() => {
    const host = hostRef.current
    if (!host) return
    host.replaceChildren()

    let disposed = false
    const scene = new THREE.Scene()
    // Only actors are rendered in WebGL; the 2D backdrop lives behind its transparent canvas.
    scene.userData.runtime = { simulatedAt, location, daypart, sceneId: preset.id }

    const camera = new THREE.OrthographicCamera(-4, 4, 2.25, -2.25, 0.1, 100)
    // Fixed side-on stage view: keep the room depth for distant scenery.
    camera.position.set(0, 1.55, 10)
    camera.lookAt(0, 1.25, 0)

    const renderer = new THREE.WebGLRenderer({ antialias: false, alpha: true, premultipliedAlpha: false })
    renderer.setPixelRatio(1)
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.setClearColor(0x000000, 0)
    renderer.shadowMap.enabled = false
    renderer.shadowMap.type = THREE.BasicShadowMap
    renderer.domElement.className = "pocket-scene__canvas"
    host.appendChild(renderer.domElement)

    const renderTarget = new THREE.WebGLRenderTarget(1, 1, {
      minFilter: THREE.NearestFilter,
      magFilter: THREE.NearestFilter,
      depthBuffer: true,
    })
    renderTarget.texture.colorSpace = THREE.SRGBColorSpace

    const postScene = new THREE.Scene()
    const postCamera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1)
    const postMaterial = new THREE.ShaderMaterial({
      uniforms: {
        tScene: { value: renderTarget.texture },
        resolution: { value: new THREE.Vector2(1, 1) },
      },
      vertexShader: `
        varying vec2 vUv;
        void main() {
          vUv = uv;
          gl_Position = vec4(position.xy, 0.0, 1.0);
        }
      `,
      fragmentShader: `
        uniform sampler2D tScene;
        uniform vec2 resolution;
        varying vec2 vUv;

        void main() {
          vec2 texel = 1.0 / resolution;
          vec4 center = texture2D(tScene, vUv);
          vec3 left = texture2D(tScene, vUv - vec2(texel.x, 0.0)).rgb;
          vec3 right = texture2D(tScene, vUv + vec2(texel.x, 0.0)).rgb;
          vec3 up = texture2D(tScene, vUv + vec2(0.0, texel.y)).rgb;
          vec3 down = texture2D(tScene, vUv - vec2(0.0, texel.y)).rgb;

          // Preserve authored color. Only add a restrained pixel-scale edge
          // accent where neighboring low-res texels differ strongly.
          float edge = max(
            max(length(center.rgb - left), length(center.rgb - right)),
            max(length(center.rgb - up), length(center.rgb - down))
          );
          float edgeMask = smoothstep(0.20, 0.48, edge);
          vec3 outlined = mix(center.rgb, center.rgb * 0.82, edgeMask * 0.18);
          gl_FragColor = vec4(outlined, center.a);
        }
      `,
      depthTest: false,
      depthWrite: false,
    })
    const postQuad = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), postMaterial)
    postScene.add(postQuad)

    // Keep a soft ambient base, then let local lights define the room.
    scene.add(new THREE.HemisphereLight(
      daypart === "day" ? 0xffffff : daypart === "evening" ? 0xfffbf2 : 0xd9e2ff,
      daypart === "night" ? 0x30364b : 0x596071,
      daypart === "day" ? 1.55 : daypart === "evening" ? 1.48 : 1.12,
    ))

    const key = new THREE.DirectionalLight(
      daypart === "day" ? 0xffffff : daypart === "evening" ? 0xfffdf8 : 0xc9d5ff,
      daypart === "day" ? 2.6 : daypart === "evening" ? 2.52 : 1.85,
    )
    key.position.set(4, 7, 5)
    key.castShadow = true
    key.shadow.mapSize.set(128, 128)
    key.shadow.camera.left = -4
    key.shadow.camera.right = 4
    key.shadow.camera.top = 4
    key.shadow.camera.bottom = -4
    key.shadow.bias = -0.001
    scene.add(key)

    const roomFill = new THREE.PointLight(0xfff8ed, 1.35, 5.5, 2)
    roomFill.position.set(1.8, 2.7, 1.5)
    scene.add(roomFill)

    const loader = new GLTFLoader()
    loader.register(parser => new VRMLoaderPlugin(parser))

    function pixelizeMaterial(material) {
      if (!material) return

      // Do not mutate MToon shader hooks or flatShading here. VRM materials
      // own their shader pipeline; touching those flags can invalidate the
      // compiled program. Pixel character is produced safely by low-res
      // rendering plus nearest texture sampling.
      for (const key of ["map", "emissiveMap", "normalMap", "roughnessMap", "metalnessMap"]) {
        const texture = material[key]
        if (!texture) continue
        texture.magFilter = THREE.NearestFilter
        texture.minFilter = THREE.NearestFilter
        texture.generateMipmaps = false
        texture.needsUpdate = true
      }
    }

    function pixelizeModel(root) {
      root.traverse(object => {
        if (!object.isMesh && !object.isSkinnedMesh) return
        const materials = Array.isArray(object.material) ? object.material : [object.material]
        materials.forEach(pixelizeMaterial)
      })
    }

    function render() {
      if (disposed) return
      renderer.setRenderTarget(renderTarget)
      renderer.render(scene, camera)
      renderer.setRenderTarget(null)
      renderer.render(postScene, postCamera)
    }

    function loadVrm(model, position, facingY = 0) {
      const { url, height: targetHeight } = model
      loader.load(
        url,
        gltf => {
          if (disposed) return
          const vrm = gltf.userData.vrm
          if (!vrm) {
            console.warn("[PocketScene] VRM data missing:", url)
            return
          }

          const box = new THREE.Box3().setFromObject(vrm.scene)
          const height = box.max.y - box.min.y
          if (height > 0) {
            const scale = targetHeight / height
            vrm.scene.scale.setScalar(scale)
          }

          const scaledBox = new THREE.Box3().setFromObject(vrm.scene)
          vrm.scene.position.copy(position)
          vrm.scene.position.y -= scaledBox.min.y
          vrm.scene.rotation.y = facingY
          vrm.scene.traverse(object => {
            object.frustumCulled = false
            if (object.isMesh || object.isSkinnedMesh) {
              object.castShadow = true
              object.receiveShadow = true
            }
          })
          pixelizeModel(vrm.scene)
          scene.add(vrm.scene)
          render()
        },
        undefined,
        error => console.error("[PocketScene] VRM load failed:", url, error),
      )
    }

    const visibleModels = [
      ...(playerModel ? [{ ...playerModel, role: "player" }] : []),
      ...characterModels.map(model => ({ ...model, role: "character" })),
    ]
    const spacing = 1.65
    const startX = -((visibleModels.length - 1) * spacing) / 2
    visibleModels.forEach((model, index) => {
      const x = startX + index * spacing
      const z = 0.55 - Math.abs(index - (visibleModels.length - 1) / 2) * 0.08
      const facing = x <= 0 ? Math.PI * 0.12 : -Math.PI * 0.12
      loadVrm(model, new THREE.Vector3(x, 0, z), facing)
    })

    function resize() {
      const width = Math.max(1, host.getBoundingClientRect().width)
      const height = Math.max(1, sceneHeight)
      const internalWidth = Math.max(1, Math.round(pixelHeight * width / height))
      renderer.setSize(internalWidth, pixelHeight, false)
      renderTarget.setSize(internalWidth, pixelHeight)
      postMaterial.uniforms.resolution.value.set(internalWidth, pixelHeight)
      renderer.domElement.style.width = "100%"
      renderer.domElement.style.height = "100%"

      const aspect = width / height
      const viewHeight = 3.25 / zoom
      camera.left = -(viewHeight * aspect) / 2
      camera.right = (viewHeight * aspect) / 2
      camera.top = viewHeight / 2
      camera.bottom = -viewHeight / 2
      camera.updateProjectionMatrix()
      render()
    }

    const observer = new ResizeObserver(resize)
    observer.observe(host)
    resize()

    return () => {
      disposed = true
      observer.disconnect()
      renderTarget.dispose()
      postMaterial.dispose()
      postQuad.geometry.dispose()
      renderer.dispose()
      renderer.domElement.remove()
      scene.traverse(object => {
        if (object.geometry) object.geometry.dispose()
        if (object.material) {
          const materials = Array.isArray(object.material) ? object.material : [object.material]
          materials.forEach(material => material.dispose())
        }
      })
    }
  }, [zoom, sceneHeight, pixelHeight, simulatedAt, location, sessionId, playerModel, characterModels])

  return <div className={`pocket-scene-shell ${className}`}>
    <div className="pocket-scene" style={{ height: `${sceneHeight}px` }}>
      <div className="pocket-scene__backdrop" data-daypart={daypart} aria-hidden="true">
        <img src={backgroundUrl} alt="" style={{ transform: `scale(${zoom / 1.35})` }}
          onError={() => setFailedBackground(requestedBackground)} />
      </div>
      <div ref={hostRef} className="pocket-scene__viewport" aria-label="2D 배경과 3D 캐릭터 장면" />
    </div>
    <div className="pocket-scene__controls">
      <div className="pocket-scene__control" aria-label="장면 확대 배율">
        <span className="pocket-scene__control-label">배율</span>
        <button type="button" onClick={() => setZoom(value => Math.max(0.8, +(value - 0.1).toFixed(1)))} aria-label="장면 축소">−</button>
        <span>{Math.round(zoom * 100)}%</span>
        <button type="button" onClick={() => setZoom(value => Math.min(2.2, +(value + 0.1).toFixed(1)))} aria-label="장면 확대">+</button>
      </div>
      <div className="pocket-scene__control" aria-label="내부 렌더 해상도">
        <span className="pocket-scene__control-label">픽셀</span>
        <button type="button" onClick={() => setPixelHeight(value => Math.max(80, value - 20))} aria-label="내부 렌더 해상도 낮추기">−</button>
        <span>{pixelHeight}px</span>
        <button type="button" onClick={() => setPixelHeight(value => Math.min(200, value + 20))} aria-label="내부 렌더 해상도 높이기">+</button>
      </div>
      <div className="pocket-scene__control" aria-label="장면 높이">
        <span className="pocket-scene__control-label">높이</span>
        <button type="button" onClick={() => setSceneHeight(value => Math.max(180, value - 20))} aria-label="장면 높이 줄이기">−</button>
        <span>{sceneHeight}px</span>
        <button type="button" onClick={() => setSceneHeight(value => Math.min(520, value + 20))} aria-label="장면 높이 늘리기">+</button>
      </div>
    </div>
  </div>
}
