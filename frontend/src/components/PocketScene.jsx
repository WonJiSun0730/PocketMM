import { useEffect, useRef } from "react"
import * as THREE from "three"

const INTERNAL_HEIGHT = 180

export default function PocketScene({ className = "" }) {
  const hostRef = useRef(null)

  useEffect(() => {
    const host = hostRef.current
    if (!host) return

    const scene = new THREE.Scene()
    scene.background = new THREE.Color(0x171821)

    const camera = new THREE.OrthographicCamera(-4, 4, 2.25, -2.25, 0.1, 100)
    camera.position.set(5, 5, 7)
    camera.lookAt(0, 0.8, 0)

    const renderer = new THREE.WebGLRenderer({ antialias: false, alpha: false })
    renderer.setPixelRatio(1)
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.domElement.className = "pocket-scene__canvas"
    host.appendChild(renderer.domElement)

    const ambient = new THREE.HemisphereLight(0xfff4dd, 0x4c526b, 2.2)
    scene.add(ambient)

    const key = new THREE.DirectionalLight(0xffffff, 2.4)
    key.position.set(4, 7, 5)
    scene.add(key)

    const floor = new THREE.Mesh(
      new THREE.BoxGeometry(6.4, 0.2, 4.2),
      new THREE.MeshStandardMaterial({ color: 0x6f6a78, roughness: 0.95 }),
    )
    floor.position.y = -0.1
    scene.add(floor)

    const backWall = new THREE.Mesh(
      new THREE.BoxGeometry(6.4, 3.2, 0.18),
      new THREE.MeshStandardMaterial({ color: 0xb9b1c7, roughness: 1 }),
    )
    backWall.position.set(0, 1.5, -2)
    scene.add(backWall)

    const sideWall = new THREE.Mesh(
      new THREE.BoxGeometry(0.18, 3.2, 4.2),
      new THREE.MeshStandardMaterial({ color: 0x9993aa, roughness: 1 }),
    )
    sideWall.position.set(-3.1, 1.5, 0)
    scene.add(sideWall)

    const table = new THREE.Mesh(
      new THREE.BoxGeometry(2.2, 0.18, 1.05),
      new THREE.MeshStandardMaterial({ color: 0x6b5148, roughness: 0.85 }),
    )
    table.position.set(0.55, 0.95, -0.2)
    scene.add(table)

    for (const x of [-0.35, 1.45]) {
      for (const z of [-0.55, 0.15]) {
        const leg = new THREE.Mesh(
          new THREE.BoxGeometry(0.14, 0.95, 0.14),
          new THREE.MeshStandardMaterial({ color: 0x514039, roughness: 0.9 }),
        )
        leg.position.set(x, 0.45, z)
        scene.add(leg)
      }
    }

    const marker = new THREE.Mesh(
      new THREE.CapsuleGeometry(0.28, 0.8, 4, 8),
      new THREE.MeshStandardMaterial({ color: 0x8e8ab7, roughness: 0.8 }),
    )
    marker.position.set(-0.65, 0.72, 0.55)
    scene.add(marker)

    function resize() {
      const width = Math.max(1, host.clientWidth)
      const height = Math.max(1, host.clientHeight)
      const internalWidth = Math.max(1, Math.round(INTERNAL_HEIGHT * width / height))
      renderer.setSize(internalWidth, INTERNAL_HEIGHT, false)

      const aspect = width / height
      const viewHeight = 4.5
      camera.left = -(viewHeight * aspect) / 2
      camera.right = (viewHeight * aspect) / 2
      camera.top = viewHeight / 2
      camera.bottom = -viewHeight / 2
      camera.updateProjectionMatrix()
      renderer.render(scene, camera)
    }

    const observer = new ResizeObserver(resize)
    observer.observe(host)
    resize()

    return () => {
      observer.disconnect()
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
  }, [])

  return <div ref={hostRef} className={`pocket-scene ${className}`} aria-label="Pocket 3D scene preview" />
}
