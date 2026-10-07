import { useEffect, useRef, useState } from "react"
import * as THREE from "three"
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js"
import { VRMLoaderPlugin } from "@pixiv/three-vrm"

export default function PocketScene({ className = "", simulatedAt = "", location = "", playerModel = null, characterModels = [] }) {
  const hostRef = useRef(null)
  const [zoom, setZoom] = useState(1.35)
  const [sceneHeight, setSceneHeight] = useState(260)
  const [pixelHeight, setPixelHeight] = useState(160)

  useEffect(() => {
    const host = hostRef.current
    if (!host) return
    host.replaceChildren()

    let disposed = false
    const scene = new THREE.Scene()
    scene.background = new THREE.Color(0x171821)

    // Runtime hooks are intentionally simple for the first linkage pass.
    // Scene selection comes next; for now location is exposed to the renderer
    // and authoritative Pocket time drives the room's daypart.
    const hour = simulatedAt ? Number(simulatedAt.slice(11, 13)) : 12
    const daypart = hour >= 6 && hour < 17 ? "day" : hour < 21 ? "evening" : "night"
    scene.userData.runtime = { simulatedAt, location, daypart }

    const camera = new THREE.OrthographicCamera(-4, 4, 2.25, -2.25, 0.1, 100)
    camera.position.set(4.2, 3.8, 6)
    camera.lookAt(0, 1.05, 0)

    const renderer = new THREE.WebGLRenderer({ antialias: false, alpha: false })
    renderer.setPixelRatio(1)
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.shadowMap.enabled = true
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

    // The real PointLight stays intentionally subtle. The visible lamp pool is
    // built from stepped emissive patches below so it reads as pixel lighting
    // instead of a smooth high-resolution radial gradient.
    const lampLight = new THREE.PointLight(0xffc184, 0.08, 1.8, 2)
    lampLight.position.set(-2.15, 1.45, -1.25)
    lampLight.castShadow = true
    lampLight.shadow.mapSize.set(64, 64)
    lampLight.shadow.bias = -0.002
    scene.add(lampLight)

    const roomFill = new THREE.PointLight(0xfff8ed, 1.35, 5.5, 2)
    roomFill.position.set(1.8, 2.7, 1.5)
    scene.add(roomFill)

    function makePixelTexture(width, height, paint, repeatX = 1, repeatY = 1) {
      const canvas = document.createElement("canvas")
      canvas.width = width
      canvas.height = height
      const context = canvas.getContext("2d")
      paint(context, width, height)
      const texture = new THREE.CanvasTexture(canvas)
      texture.colorSpace = THREE.SRGBColorSpace
      texture.magFilter = THREE.NearestFilter
      texture.minFilter = THREE.NearestFilter
      texture.generateMipmaps = false
      texture.wrapS = THREE.RepeatWrapping
      texture.wrapT = THREE.RepeatWrapping
      texture.repeat.set(repeatX, repeatY)
      return texture
    }

    const floorTexture = makePixelTexture(32, 32, (ctx, w, h) => {
      ctx.fillStyle = "#76533c"
      ctx.fillRect(0, 0, w, h)
      for (let y = 0; y < h; y += 8) {
        ctx.fillStyle = y % 16 === 0 ? "#8a6347" : "#684733"
        ctx.fillRect(0, y, w, 1)
        for (let x = (y / 8) % 2 ? 8 : 0; x < w; x += 16) ctx.fillRect(x, y, 1, 8)
      }
      ctx.fillStyle = "#967052"
      for (const [x, y] of [[3,4],[12,11],[25,6],[19,22],[6,27],[29,17]]) ctx.fillRect(x, y, 4, 1)
    }, 4, 3)

    const wallTexture = makePixelTexture(24, 24, (ctx, w, h) => {
      ctx.fillStyle = "#d8c9ae"
      ctx.fillRect(0, 0, w, h)
      ctx.fillStyle = "#c8b99e"
      for (const [x, y] of [[3,4],[16,2],[8,13],[20,17],[2,21],[13,8]]) {
        ctx.fillRect(x, y, 2, 3)
        ctx.fillRect(x + 2, y + 1, 1, 1)
      }
      ctx.fillStyle = "#e2d4bc"
      for (const [x, y] of [[10,3],[5,17],[18,11]]) ctx.fillRect(x, y, 3, 2)
    }, 5, 3)

    const rugTexture = makePixelTexture(32, 24, (ctx, w, h) => {
      ctx.fillStyle = "#5f7259"
      ctx.fillRect(0, 0, w, h)
      ctx.fillStyle = "#9b916b"
      ctx.fillRect(1, 1, w - 2, 1)
      ctx.fillRect(1, h - 2, w - 2, 1)
      ctx.fillRect(1, 1, 1, h - 2)
      ctx.fillRect(w - 2, 1, 1, h - 2)
      ctx.fillStyle = "#748267"
      for (let y = 4; y < h - 3; y += 4) {
        for (let x = 4; x < w - 3; x += 4) {
          if ((x + y) % 8 === 0) ctx.fillRect(x, y, 2, 2)
        }
      }
    }, 2, 2)

    const woodTexture = makePixelTexture(24, 16, (ctx, w, h) => {
      ctx.fillStyle = "#76513b"
      ctx.fillRect(0, 0, w, h)
      ctx.fillStyle = "#8b6247"
      for (let y = 3; y < h; y += 5) ctx.fillRect(0, y, w, 1)
      ctx.fillStyle = "#5e402f"
      for (const [x, y] of [[3,6],[14,2],[18,12]]) ctx.fillRect(x, y, 5, 1)
    }, 3, 2)

    // Warm temporary counseling room. Furniture GLBs can replace these
    // primitives incrementally without touching the VRM pipeline.
    const floor = new THREE.Mesh(
      new THREE.BoxGeometry(6.4, 0.16, 4.2),
      new THREE.MeshStandardMaterial({ map: floorTexture, roughness: 0.96 }),
    )
    floor.position.y = -0.08
    scene.add(floor)

    const backWall = new THREE.Mesh(
      new THREE.BoxGeometry(6.4, 3.2, 0.16),
      new THREE.MeshStandardMaterial({ map: wallTexture, roughness: 1 }),
    )
    backWall.position.set(0, 1.5, -2)
    scene.add(backWall)

    // Blocky lamp glow: three nested low-poly patches with hard boundaries.
    // Slightly offset from the wall to avoid z-fighting.
    const glowBands = [
      { radius: 0.92, color: 0x8a4d25, opacity: 0.22 },
      { radius: 0.62, color: 0xc87932, opacity: 0.28 },
      { radius: 0.34, color: 0xf2b45f, opacity: 0.34 },
    ]
    for (const band of glowBands) {
      const glow = new THREE.Mesh(
        new THREE.CircleGeometry(band.radius, 12),
        new THREE.MeshBasicMaterial({
          color: band.color,
          transparent: true,
          opacity: band.opacity,
          depthWrite: false,
        }),
      )
      glow.position.set(-2.15, 1.48, -1.905)
      scene.add(glow)
    }

    const sideWall = new THREE.Mesh(
      new THREE.BoxGeometry(0.16, 3.2, 4.2),
      new THREE.MeshStandardMaterial({ map: wallTexture, color: 0xeee5d4, roughness: 1 }),
    )
    sideWall.position.set(-3.1, 1.5, 0)
    scene.add(sideWall)

    const rug = new THREE.Mesh(
      new THREE.BoxGeometry(3.4, 0.035, 2.15),
      new THREE.MeshStandardMaterial({ map: rugTexture, roughness: 1 }),
    )
    rug.position.set(0.15, 0.02, 0.15)
    scene.add(rug)

    const coffeeTable = new THREE.Mesh(
      new THREE.BoxGeometry(1.45, 0.12, 0.72),
      new THREE.MeshStandardMaterial({ map: woodTexture, roughness: 0.9 }),
    )
    coffeeTable.position.set(0.25, 0.48, 0.05)
    scene.add(coffeeTable)
    for (const x of [-0.58, 0.58]) {
      for (const z of [-0.25, 0.25]) {
        const leg = new THREE.Mesh(
          new THREE.BoxGeometry(0.09, 0.46, 0.09),
          new THREE.MeshStandardMaterial({ color: 0x51382d, roughness: 0.95 }),
        )
        leg.position.set(0.25 + x, 0.23, 0.05 + z)
        scene.add(leg)
      }
    }

    const lampStem = new THREE.Mesh(
      new THREE.CylinderGeometry(0.045, 0.055, 1.35, 8),
      new THREE.MeshStandardMaterial({ color: 0x5b493d, roughness: 0.9 }),
    )
    lampStem.position.set(-2.15, 0.68, -1.35)
    scene.add(lampStem)
    const lampShade = new THREE.Mesh(
      new THREE.CylinderGeometry(0.22, 0.36, 0.42, 8),
      new THREE.MeshStandardMaterial({ color: 0xe4b66f, emissive: 0x6b421c, emissiveIntensity: 0.25, roughness: 1 }),
    )
    lampShade.position.set(-2.15, 1.48, -1.35)
    scene.add(lampShade)

    const plantPot = new THREE.Mesh(
      new THREE.CylinderGeometry(0.22, 0.17, 0.38, 8),
      new THREE.MeshStandardMaterial({ color: 0xa65f43, roughness: 1 }),
    )
    plantPot.position.set(2.35, 0.19, -1.45)
    scene.add(plantPot)
    for (let i = 0; i < 6; i += 1) {
      const leaf = new THREE.Mesh(
        new THREE.SphereGeometry(0.25, 6, 4),
        new THREE.MeshStandardMaterial({ color: i % 2 ? 0x526c4c : 0x667d58, roughness: 1 }),
      )
      const angle = (Math.PI * 2 * i) / 6
      leaf.scale.set(0.55, 1.25, 0.35)
      leaf.rotation.z = Math.sin(angle) * 0.55
      leaf.position.set(2.35 + Math.cos(angle) * 0.22, 0.62 + (i % 3) * 0.15, -1.45 + Math.sin(angle) * 0.18)
      scene.add(leaf)
    }

    scene.traverse(object => {
      if (!object.isMesh) return
      object.castShadow = true
      object.receiveShadow = true
    })

    const loader = new GLTFLoader()
    loader.register(parser => new VRMLoaderPlugin(parser))

    // CC0 Cozy Home Interiors sofa. Loaded from the pack's permanent CORS CDN.
    loader.load(
      "https://cdn.3dassets.dev/assets/17137/v1/model.glb",
      gltf => {
        if (disposed) return
        const sofa = gltf.scene
        sofa.position.set(0.15, 0, -1.35)
        sofa.rotation.y = 0
        sofa.traverse(object => {
          if (!object.isMesh) return
          object.castShadow = true
          object.receiveShadow = true
        })
        scene.add(sofa)
        render()
      },
      undefined,
      error => console.warn("[PocketScene] Cozy sofa load failed:", error),
    )

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
      const facing = x <= 0 ? Math.PI * 0.1 : -Math.PI * 0.2
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
      floorTexture.dispose()
      wallTexture.dispose()
      rugTexture.dispose()
      woodTexture.dispose()
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
  }, [zoom, sceneHeight, pixelHeight, simulatedAt, location, playerModel, characterModels])

  return <div className={`pocket-scene-shell ${className}`}>
    <div className="pocket-scene" style={{ height: `${sceneHeight}px` }}>
      <div ref={hostRef} className="pocket-scene__viewport" aria-label="Pocket 3D scene preview" />
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
