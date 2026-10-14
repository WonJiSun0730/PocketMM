import { useEffect, useRef, useState } from "react"
import * as THREE from "three"
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js"
import { VRMLoaderPlugin, VRMUtils } from "@pixiv/three-vrm"
import { resolvePocketScene } from "@/lib/pocketSceneRegistry"
import { getPocketBackground } from "@/lib/pocketSceneBackgrounds"
import usePocketBackground from "@/hooks/usePocketBackground"
import { backgroundContentBounds } from "@/lib/pocketBackgroundLayers"
import { activitySymbol } from "@/lib/pocketActivityPlayback"
import { actorCue, performanceActors, interactionActors, closenessPixels, speakingActors, speechSide, puppetPose, walkDirection, scrollingBackdrop, hasSceneTransition, sceneTransition, transitionYaw, turnToward } from "@/lib/pocketActivityPerformance"
import "./PocketActivityPerformance.css"
import { createStandingIdle, settleStandingPhysics } from "@/lib/pocketStandingIdle"
import usePocketSceneLook from "@/hooks/usePocketSceneLook"
import { lookFilter } from "@/lib/pocketSceneLook"

export default function PocketScene({ className = "", simulatedAt = "", location = "", sessionId = null, playerModel = null, characterModels = [], activity = null, compact = false, backgroundAsset = null, requireBackground = false, onModelsReady, onSceneError }) {
  const hostRef = useRef(null)
  const [look] = usePocketSceneLook()
  const bubbleNodes = useRef(new Map())
  const expressionNodes = useRef(new Map())
  const heartNode = useRef(null)
  const departureSides = useRef(new Map())
  const activityRef = useRef(activity)
  const readyCallback = useRef(onModelsReady), errorCallback = useRef(onSceneError)
  readyCallback.current = onModelsReady; errorCallback.current = onSceneError
  activityRef.current = activity
  const [speakingIndex, setSpeakingIndex] = useState(0)
  const actorKey = performanceActors(activity).join(",")
  useEffect(() => {
    setSpeakingIndex(0)
    if (activity?.kind !== "TALK" || !actorKey || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return
    const timer=setInterval(() => setSpeakingIndex(value=>value+1),900)
    return ()=>clearInterval(timer)
  }, [activity?.start, activity?.kind, actorKey])
  const projectBubbles = useRef(() => {})
  const refreshPose = useRef(() => {})
  const [zoom, setZoom] = useState(1.35)
  const [sceneHeight, setSceneHeight] = useState(260)
  const [pixelHeight, setPixelHeight] = useState(160)
  const [failedBackground, setFailedBackground] = useState(null)
  const [failedScenery, setFailedScenery] = useState(null)
  const [imageAspect,setImageAspect] = useState(1)
  const [contentBounds,setContentBounds] = useState({url:"",top:0,bottom:1})
  const [backdropSize,setBackdropSize] = useState({width:300,height:300})
  const preset = resolvePocketScene(location)
  const sceneBackground = usePocketBackground(backgroundAsset ? null : sessionId, location, {simulatedAt})
  const generated = backgroundAsset || sceneBackground.asset
  const registeredBackground = getPocketBackground(sessionId, location)
  const requestedBackground = generated?.backgroundUrl || registeredBackground || preset.backgroundUrl
  const backgroundUrl = failedBackground === requestedBackground && !requireBackground ? preset.backgroundUrl : requestedBackground
  const sceneryUrl = generated && backgroundUrl === generated.backgroundUrl && failedScenery !== generated.sceneryUrl ? generated.sceneryUrl : null

  useEffect(() => {
    const host = hostRef.current
    if (!host) return
    host.replaceChildren()
    bubbleNodes.current.forEach(node => { node.hidden = true })

    let disposed = false
    let loadedModels = 0
    readyCallback.current?.(false)
    const idleActors = []
    const bubbleActors = []
    const headPosition = new THREE.Vector3()
    let animationFrame = null
    let lastFrameTime = null
    let idleTime = 0
    let activityStartedAt = 0
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)")
    const scene = new THREE.Scene()
    // Only actors are rendered in WebGL; the 2D backdrop lives behind its transparent canvas.


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
      0xffffff,
      0x596071,
      1.55,
    ))

    const key = new THREE.DirectionalLight(
      0xffffff,
      2.6,
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

    function updateBubbles() {
      const placed=[]
      for (const actor of bubbleActors) {
        const bubble = bubbleNodes.current.get(actor.id)
        actor.vrm.scene.updateMatrixWorld(true)
        const expression = expressionNodes.current.get(actor.id)
        if (expression) {
          actor.head.getWorldPosition(headPosition)
          headPosition.y += 0.10
          headPosition.project(camera)
          expression.hidden = !actor.vrm.scene.visible || headPosition.z < -1 || headPosition.z > 1 || Math.abs(headPosition.x) > 1.1
          const headX=(headPosition.x+1)*host.clientWidth/2
          const radius=Math.abs(actor.radius*camera.projectionMatrix.elements[0]*host.clientWidth/2)
          const side=speechSide(actor.vrm.scene.rotation.y,actor.frontalSide)
          const expressionX=Math.max(3,Math.min(host.clientWidth-34,side>0 ? headX+radius+3 : headX-radius-3-34))
          expression.style.left = `${expressionX}px`
          expression.style.setProperty("--speech-side",String(side))
          expression.style.setProperty("--anger-x",`${headX-expressionX-radius*.5}px`)
          expression.style.setProperty("--mouth-x",`${headX-expressionX-10.5}px`)
          expression.style.setProperty("--head-radius",`${radius}px`)
          expression.style.top = `${Math.max(25,Math.min(host.clientHeight-25,(1-headPosition.y)*host.clientHeight/2))}px`
        }
        if (!bubble) continue
        actor.head.getWorldPosition(headPosition)
        headPosition.y = actor.vrm.scene.position.y + actor.topOffset
        headPosition.project(camera)
        bubble.hidden = headPosition.z < -1 || headPosition.z > 1 || Math.abs(headPosition.x) > 1.1
        const halfWidth=bubble.offsetWidth/2
        const headX=(headPosition.x+1)*host.clientWidth/2
        const centre=Math.max(halfWidth+4,Math.min(host.clientWidth-halfWidth-4,headX))
        bubble.style.left = `${centre}px`
        bubble.style.setProperty("--tail-x",`${halfWidth+headX-centre-6}px`)
        // The pixel tail extends ten pixels below the body; leave another ten above the model.
        bubble.style.top = `${Math.max(bubble.offsetHeight+4, Math.min(host.clientHeight-12,(1-headPosition.y)*host.clientHeight/2-20))}px`
        if(actor.vrm.scene.visible) placed.push({bubble,centre,headX,halfWidth})
      }
      if(placed.length===2) {
        const [left,right]=placed.sort((a,b)=>a.centre-b.centre)
        const missing=left.halfWidth+right.halfWidth+6-(right.centre-left.centre)
        if(missing>0 && 2*(left.halfWidth+right.halfWidth)+14<=host.clientWidth) {
          left.centre=Math.max(left.halfWidth+4,left.centre-missing/2)
          right.centre=Math.min(host.clientWidth-right.halfWidth-4,right.centre+missing/2)
          if(right.centre-left.centre<left.halfWidth+right.halfWidth+6) {
            left.centre=Math.max(left.halfWidth+4,right.centre-left.halfWidth-right.halfWidth-6)
            right.centre=left.centre+left.halfWidth+right.halfWidth+6
          }
          for(const entry of placed) {
            entry.bubble.style.left=`${entry.centre}px`
            entry.bubble.style.setProperty("--tail-x",`${entry.halfWidth+entry.headX-entry.centre-6}px`)
          }
        }
      }
      if(heartNode.current && activityRef.current?.interaction !== "INTIMACY") {
        const pair=interactionActors(activityRef.current)
        const actors=bubbleActors.filter(actor=>pair.includes(String(actor.id)))
        if(actors.length===2) {
          const positions=actors.map(actor=>{const p=new THREE.Vector3();actor.head.getWorldPosition(p);p.project(camera);return p})
          heartNode.current.style.left=`${(positions[0].x+positions[1].x+2)*host.clientWidth/4}px`
          heartNode.current.style.top=`${Math.max(35,(2-positions[0].y-positions[1].y)*host.clientHeight/4)}px`
        }
      }
    }
    projectBubbles.current = updateBubbles

    function render() {
      if (disposed) return
      renderer.setRenderTarget(renderTarget)
      renderer.render(scene, camera)
      renderer.setRenderTarget(null)
      renderer.render(postScene, postCamera)
      updateBubbles()
    }

    function loadVrm(model, position, facingY = 0, phase = 0) {
      const { url, height: targetHeight } = model
      loader.load(
        url,
        gltf => {
          if (disposed) { VRMUtils.deepDispose(gltf.scene); return }
          const vrm = gltf.userData.vrm
          if (!vrm) {
            console.warn("[PocketScene] VRM data missing:", url)
            errorCallback.current?.("캐릭터를 준비하지 못했어요. 다시 들여다봐 주세요.")
            VRMUtils.deepDispose(gltf.scene)
            return
          }

          const updateIdle = createStandingIdle(vrm, phase)
          vrm.scene.updateMatrixWorld(true)

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
          settleStandingPhysics(vrm)
          const actorId = model.role === "player" ? "player" : model.source?.characterId ?? model.source?.id
          const origin = vrm.scene.position.clone(), restRotation = vrm.scene.rotation.clone()
          const legBones=["leftUpperLeg","rightUpperLeg","leftLowerLeg","rightLowerLeg"].map(name=>{const bone=vrm.humanoid?.getNormalizedBoneNode?.(name);return bone ? {bone,rest:bone.rotation.clone()} : null})
          idleActors.push((time,delta,motion=true) => {
            // Absolute rest-relative offsets keep repeated playback from accumulating transforms.
            const elapsed=loadedModels<visibleModels.length ? 0 : Math.max(0,time-activityStartedAt)
            const pose=puppetPose(activityRef.current,actorId,elapsed,motion)
            vrm.scene.visible=!pose.hidden
            vrm.scene.position.copy(origin); vrm.scene.position.x+=pose.x; vrm.scene.position.y+=pose.y
            const pair=interactionActors(activityRef.current), peers=bubbleActors.filter(a=>pair.includes(String(a.id)))
            if(peers.length===2 && pair.includes(String(actorId))) {
              const centre=peers.reduce((sum,a)=>sum+a.origin.x,0)/2
              const pixelsPerUnit=Math.abs(camera.projectionMatrix.elements[0]*host.clientWidth/2)
              vrm.scene.position.x+=Math.sign(centre-origin.x)*closenessPixels(activityRef.current,actorId,elapsed,motion)/Math.max(1,pixelsPerUnit)
            }
            vrm.scene.rotation.copy(restRotation); vrm.scene.rotation.x+=pose.rx; vrm.scene.rotation.z+=pose.rz
            if (activityRef.current && ["WALK","TRAVEL","RUN"].includes(activityRef.current.kind)
                && performanceActors(activityRef.current).includes(String(actorId))) vrm.scene.rotation.y=pose.ry
            const cue=actorCue(activityRef.current,actorId)
            let transitionMoving=false
            if (["DEPART","ARRIVE"].includes(cue.pose)) {
              const edge=sceneTransition(cue.pose,elapsed,origin.x,1/Math.abs(camera.projectionMatrix.elements[0]),motion,departureSides.current.get(String(actorId)),model.role==="player" ? -1 : 1)
              if(cue.pose==="DEPART") departureSides.current.set(String(actorId),edge.side)
              vrm.scene.position.x=origin.x+edge.x
              vrm.scene.visible=!edge.hidden
              const travelYaw=transitionYaw(edge.side,cue.pose)
              vrm.scene.rotation.y=cue.pose==="DEPART" ? turnToward(restRotation.y,travelYaw,edge.turn) : edge.moving ? travelYaw : restRotation.y
              pose.stride=edge.stride
              transitionMoving=edge.moving
              if(edge.moving) vrm.scene.position.y+=Math.abs(edge.stride)*.045
            }
            const action=["MEAL","DRINK","COFFEE","RUN","SLEEP"].includes(activityRef.current?.kind) || activityRef.current?.interaction==="INTIMACY"
            updateIdle(time,delta,motion,cue.pose==="WAVE" ? "WAVE" : transitionMoving ? "WALK" : action ? "LIGHT" : cue.pose)
            legBones.forEach((entry,i)=>{if(entry){entry.bone.rotation.copy(entry.rest);entry.bone.rotation.x+=i<2 ? (i===0?1:-1)*pose.stride : Math.max(0,(i===2?-1:1)*pose.stride)*1.2}})
            if(pose.armRaise) {
              const upper=vrm.humanoid?.getNormalizedBoneNode?.("rightUpperArm"),lower=vrm.humanoid?.getNormalizedBoneNode?.("rightLowerArm")
              if(upper) upper.rotation.x-=pose.armRaise
              if(lower) lower.rotation.x-=pose.armRaise*1.2
            }
            vrm.update?.(0)
          })
          idleActors.at(-1)(0,0,false)
          const head = vrm.humanoid?.getRawBoneNode("head") ?? vrm.scene
          if (actorId != null) bubbleActors.push({ id: actorId, head, vrm, origin, topOffset:scaledBox.max.y, radius:targetHeight*0.19, frontalSide:Math.random()<.5 ? -1 : 1 })
          render()
          loadedModels++
          if (loadedModels === visibleModels.length) { activityStartedAt=idleTime;readyCallback.current?.(true) }
        },
        undefined,
        error => { console.error("[PocketScene] VRM load failed:", url, error); errorCallback.current?.("캐릭터를 준비하지 못했어요. 다시 들여다봐 주세요.") },
      )
    }

    const visibleModels = [
      ...(playerModel ? [{ ...playerModel, role: "player" }] : []),
      ...characterModels.map(model => ({ ...model, role: "character" })),
    ]
    const spacing = compact ? 1.45 : 1.65
    const startX = -((visibleModels.length - 1) * spacing) / 2
    if (!visibleModels.length) readyCallback.current?.(true)
    visibleModels.forEach((model, index) => {
      const x = startX + index * spacing
      const z = 0.55 - Math.abs(index - (visibleModels.length - 1) / 2) * 0.08
      const facing = x <= 0 ? Math.PI * 0.12 : -Math.PI * 0.12
      loadVrm(model, new THREE.Vector3(x, 0, z), facing, index * 1.7)
    })

    function animate(timestamp) {
      if (disposed) return
      animationFrame = requestAnimationFrame(animate)
      if (document.hidden || reducedMotion.matches) { lastFrameTime = null; return }
      if (lastFrameTime !== null && timestamp - lastFrameTime < 1000 / 30) return
      const delta = lastFrameTime === null ? 0 : Math.min(0.05, (timestamp - lastFrameTime) / 1000)
      lastFrameTime = timestamp
      idleTime += delta
      if (!idleActors.length) return
      idleActors.forEach(update => update(idleTime, delta))
      render()
    }
    refreshPose.current = () => { activityStartedAt=idleTime;idleActors.forEach(update=>update(idleTime,0,!reducedMotion.matches)); render() }
    function motionChanged() {
      lastFrameTime = null
      if (reducedMotion.matches) idleActors.forEach(update => update(0, 0, false))
      render()
    }
    reducedMotion.addEventListener("change", motionChanged)
    animationFrame = requestAnimationFrame(animate)

    function resize() {
      const width = Math.max(1, host.getBoundingClientRect().width)
      const height = Math.max(1, compact ? host.getBoundingClientRect().height : sceneHeight)
      setBackdropSize(current=>current.width===width && current.height===height ? current : {width,height})
      const internalWidth = Math.max(1, Math.round(pixelHeight * width / height))
      renderer.setSize(internalWidth, pixelHeight, false)
      renderTarget.setSize(internalWidth, pixelHeight)
      postMaterial.uniforms.resolution.value.set(internalWidth, pixelHeight)
      renderer.domElement.style.width = "100%"
      renderer.domElement.style.height = "100%"

      const aspect = width / height
      const viewHeight = compact ? Math.max(3.25 / zoom, visibleModels.length * 1.2 + 0.7) / 0.8 : 3.25 / zoom
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
      cancelAnimationFrame(animationFrame)
      reducedMotion.removeEventListener("change", motionChanged)
      observer.disconnect()
      renderTarget.dispose()
      postMaterial.dispose()
      postQuad.geometry.dispose()
      renderer.dispose()
      renderer.domElement.remove()
      VRMUtils.deepDispose(scene)
      projectBubbles.current = () => {}
    }
  }, [zoom, sceneHeight, pixelHeight, sessionId, playerModel, characterModels, compact])

  useEffect(() => { refreshPose.current(); projectBubbles.current() }, [activity])
  const symbol = activitySymbol(activity?.kind)
  const speakers = speakingActors(activity)
  const speaker = speakers.length ? speakers[speakingIndex % speakers.length] : null
  const [walkX] = walkDirection(activity)
  const walking = !hasSceneTransition(activity) && ["WALK","TRAVEL","RUN"].includes(activity?.kind)
  const intimate=activity?.interaction==="INTIMACY" && interactionActors(activity).length===2
  const inBed=activity?.kind==="SLEEP" || intimate
  const hearts=interactionActors(activity).length===2 && ["AFFECTION","INTIMACY"].includes(activity?.interaction)
  const bounds=contentBounds.url === backgroundUrl ? contentBounds : {top:0,bottom:1}
  const contentHeight=bounds.bottom-bounds.top
  const tile=generated && backgroundUrl === generated.backgroundUrl
    ? scrollingBackdrop(backdropSize.width*1.5,backdropSize.height,imageAspect/contentHeight,walkX)
    : scrollingBackdrop(backdropSize.width,backdropSize.height,imageAspect,walkX)
  const rasterHeight=tile.height/contentHeight
  const rasterTop=backdropSize.height/2-(bounds.top+bounds.bottom)/2*rasterHeight
  const rasterStyle={width:`${tile.width}px`,maxWidth:"none",height:`${rasterHeight}px`,position:"absolute",left:"50%",top:`${rasterTop}px`,transform:"translateX(-50%)"}
  const bubbleCharacters = activity ? characterModels.filter(model =>
    activity.characterIds.includes(model.source?.characterId ?? model.source?.id)) : []
  if (activity?.playerInvolved && playerModel) bubbleCharacters.push({ ...playerModel, bubbleId: "player" })

  return <div className={`pocket-scene-shell ${compact ? "pocket-scene-shell--compact" : ""} ${className}`}>
    <div className="pocket-scene" data-player-present={Boolean(playerModel)} style={{ height: compact ? "100%" : `${sceneHeight}px`, filter: lookFilter(look) }}>
      <div className={`pocket-scene__backdrop ${walking ? "pocket-scene__backdrop--walking" : ""}`} data-daypart="day" aria-hidden="true"
        style={walking ? {backgroundImage:`url(${backgroundUrl})`,backgroundSize:`${tile.width}px ${rasterHeight}px`,"--walk-y":`${rasterTop}px`,
          "--walk-x":`${tile.offset}px`,...(activity?.kind==="RUN" ? {animationDuration:"6s"} : {})} : undefined}>
        <img src={backgroundUrl} alt="" onLoad={event=>{
          const image=event.currentTarget;if(image.naturalHeight>0)setImageAspect(image.naturalWidth/image.naturalHeight)
          if (generated) { try {
            const canvas=document.createElement("canvas");canvas.width=image.naturalWidth;canvas.height=image.naturalHeight
            const context=canvas.getContext("2d",{willReadFrequently:true});context.drawImage(image,0,0)
            setContentBounds({url:backgroundUrl,...backgroundContentBounds(context.getImageData(0,0,canvas.width,canvas.height).data,canvas.width,canvas.height)})
          } catch { setContentBounds({url:backgroundUrl,top:0,bottom:1}) } }
        }} style={generated && backgroundUrl === generated.backgroundUrl
          ? rasterStyle
          : { transform: `scale(${zoom / 1.35})` }}
          onError={() => { setFailedBackground(requestedBackground); if (requireBackground) errorCallback.current?.("주머니 안쪽을 불러오지 못했어요.") }} />
      </div>
      {sceneryUrl && <div className={`pocket-scene__backdrop pocket-scene__scenery ${walking ? "pocket-scene__backdrop--walking" : ""}`}
        data-daypart="day" aria-hidden="true" style={walking ? {
          backgroundImage:`url(${sceneryUrl})`,backgroundSize:`${tile.width}px ${rasterHeight}px`,"--walk-x":`${tile.offset}px`,"--walk-y":`${rasterTop}px`,...(activity?.kind==="RUN" ? {animationDuration:"10s"} : {}),
        } : undefined}>
        <img src={sceneryUrl} alt="" onError={() => { setFailedScenery(sceneryUrl); if (requireBackground) errorCallback.current?.("주머니 안쪽을 불러오지 못했어요.") }} style={rasterStyle} />
      </div>}
      <div ref={hostRef} className="pocket-scene__viewport" aria-label="2D 배경과 3D 캐릭터 장면" />
      {inBed && <div className="pocket-scene__bed" aria-label="캐릭터가 이불 안에 있는 침대">
        <svg viewBox="0 0 300 165" role="img" aria-label="도톰한 이불이 덮인 침대">
          <path fill="#8D7864" d="M8 55h284v110H8z"/>
          <rect x="13" y="48" width="274" height="104" rx="13" fill="#E7E0D2"/>
          <rect x="23" y="52" width="74" height="33" rx="12" fill="#FAF7F1"/>
          {intimate && <rect x="202" y="52" width="74" height="33" rx="12" fill="#FAF7F1"/>}
          <path fill="#D9D1C1" d="M91 85Q100 17 170 20Q240 17 280 81L287 91v61H13V91h78z"/>
          <path fill="none" stroke="#B9B09F" strokeWidth="3" d="M91 85Q100 17 170 20Q240 17 280 81M13 104h274M25 110v36M275 110v36"/>
        </svg>
        {intimate && <div ref={heartNode} className="pocket-heart-stream pocket-heart-stream--pillow" aria-label="베개 위로 떠오르는 픽셀 하트">{[0,1,2,3].map(i=><svg key={i} viewBox="0 0 12 11" style={{animationDelay:`${i*.8}s`}}><path d="M1 1h4v1h2V1h4v1h1v4h-1v1h-1v1H9v1H8v1H7v1H5v-1H4V9H3V8H2V7H1V6H0V2h1z"/></svg>)}</div>}
        {activity?.kind==="SLEEP" && <div className="pocket-sleep-stream" aria-label="베개에서 떠오르는 Zzz">{[0,1,2].map(i=><span key={i} style={{animationDelay:`${i*1.2}s`}}>Zzz...</span>)}</div>}
      </div>}
      {hearts && !intimate && <div ref={heartNode} className="pocket-heart-stream" aria-label="두 캐릭터 사이의 픽셀 하트"><svg viewBox="0 0 12 11"><path d="M1 1h4v1h2V1h4v1h1v4h-1v1h-1v1H9v1H8v1H7v1H5v-1H4V9H3V8H2V7H1V6H0V2h1z"/></svg></div>}
      <div className="pocket-scene__expressions" aria-hidden="true">{bubbleCharacters.map(model => {
        const id=model.bubbleId ?? model.source.characterId ?? model.source.id
        const cue=actorCue(activity,id)
        const talking=activity?.kind === "TALK" && String(id)===speaker
        return <div key={id} hidden className="pocket-scene__expression" ref={node=>{if(node)expressionNodes.current.set(id,node);else expressionNodes.current.delete(id)}}>
          {talking && <span className="pocket-talk-lines"><i/><i/><i/></span>}
          {["MEAL","COFFEE","DRINK"].includes(activity?.kind) && <span className="pocket-food-prop">{activity.kind==="MEAL" ? cue.emojis[0] : activitySymbol(activity.kind).emoji}</span>}
          {cue.emotion === "ANGRY" && <svg className="pocket-anger-mark" viewBox="0 0 16 16"><path d="M4 0v4H0M12 0v4h4M4 16v-4H0M12 16v-4h4"/></svg>}
        </div>
      })}</div>
      <div className="pocket-scene__bubbles" aria-label="캐릭터 활동 말풍선">
        {bubbleCharacters.map(model => {
          const id = model.bubbleId ?? model.source.characterId ?? model.source.id
          const name = model.source.characterName ?? model.source.name
          return <div key={id} hidden className="pocket-scene__bubble" data-in-bed={inBed && (activity?.kind==="SLEEP" ? performanceActors(activity).includes(String(id)) : interactionActors(activity).includes(String(id)))} data-character-id={id}
            aria-label={`${name} · ${symbol.label}`}
            ref={node => { if (node) bubbleNodes.current.set(id, node); else bubbleNodes.current.delete(id) }}>
            <span aria-hidden="true">{actorCue(activity,id).emojis.join("")}</span>
          </div>
        })}
      </div>
    </div>
    {!compact && <div className="pocket-scene__controls">
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
    </div>}
  </div>
}
