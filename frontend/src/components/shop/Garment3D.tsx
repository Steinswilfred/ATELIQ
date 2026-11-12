import { ContactShadows, OrbitControls, useGLTF } from '@react-three/drei'
import { Canvas, useFrame, useThree } from '@react-three/fiber'
import {
  useEffect,
  useMemo,
  useRef,
} from 'react'
import type { MutableRefObject } from 'react'
import * as THREE from 'three'

import { buildBuiltin } from '../../lib/builtinModels'
import {
  isFabricMaterial,
  isHex,
  isNodeVisible,
  RenderState,
} from '../../lib/renderState'
import { getMediaUrl } from '../../lib/api'

export type ViewName = 'front' | 'back' | 'left' | 'right'

const TARGET = new THREE.Vector3(0, 0.12, 0)
const DIST = 1.9

const VIEWS: Record<ViewName, THREE.Vector3> = {
  front: new THREE.Vector3(0, 0.3, DIST),
  back: new THREE.Vector3(0, 0.3, -DIST),
  left: new THREE.Vector3(-DIST, 0.3, 0),
  right: new THREE.Vector3(DIST, 0.3, 0),
}

// Cache external fabric textures.
const textures = new Map<string, THREE.Texture>()
const loader = new THREE.TextureLoader()

// function texture(url: string) {
//   let t = textures.get(url)

//   if (!t) {
//     t = loader.load(url)
//     t.colorSpace = THREE.SRGBColorSpace
//     t.wrapS = THREE.RepeatWrapping
//     t.wrapT = THREE.RepeatWrapping
//     t.repeat.set(3, 3)

//     textures.set(url, t)
//   }

//   return t
// }

function texture(url: string) {
  const resolvedUrl = getMediaUrl(url)

  let t = textures.get(resolvedUrl)

  if (!t) {
    t = loader.load(resolvedUrl)

    t.colorSpace = THREE.SRGBColorSpace
    t.wrapS = THREE.RepeatWrapping
    t.wrapT = THREE.RepeatWrapping
    t.repeat.set(3, 3)

    textures.set(resolvedUrl, t)
  }

  return t
}

/**
 * Apply customization to built-in and GLB models.
 *
 * Supports:
 * - Visibility
 * - Individual material colours
 * - Fabric colours
 * - Fabric textures
 * - Component textures
 * - Morph targets / shape keys
 */
function applyState(root: THREE.Object3D, rs: RenderState) {
  root.traverse((o) => {
    // Component visibility.
    if (o !== root && o.name) {
      o.visible = isNodeVisible(o.name, rs)
    }

    const mesh = o as THREE.Mesh

    if (!mesh.isMesh) return

    const materials = Array.isArray(mesh.material)
      ? mesh.material
      : [mesh.material]

    for (const material of materials) {
      const m = material as THREE.MeshStandardMaterial

      if (!m.color) continue

      // Save original GLB material properties only once.
      if (!m.userData.baseColor) {
        m.userData.baseColor = m.color.clone()
      }

      if (m.userData.baseMap === undefined) {
        m.userData.baseMap = m.map ?? null
      }

      // Identify fabric material.
      const isFabric = isFabricMaterial(m.name)

      // Get selected individual component colour.
      const explicitColor = rs.colors[m.name]

      // Get selected general fabric colour.
      const fabricColor =
        isFabric && isHex(rs.fabricColor)
          ? rs.fabricColor
          : undefined

      const selectedColor = explicitColor ?? fabricColor

      // Apply selected colour.
      if (selectedColor && isHex(selectedColor)) {
        m.color.set(selectedColor)
      } else {
        m.color.copy(m.userData.baseColor)
      }

      // Get selected component/fabric texture.
      const selectedTexture =
        rs.textures[m.name] ??
        (isFabric ? rs.fabricTexture : undefined)

      // Apply texture or solid colour.
      if (selectedTexture) {
        // User selected a new fabric texture.
        m.map = texture(selectedTexture)
      } else if (selectedColor) {
        // IMPORTANT:
        // Remove the original embedded GLB texture.
        // Otherwise the original image can hide the selected colour.
        m.map = null
      } else {
        // No customization: restore original GLB texture.
        m.map = m.userData.baseMap
      }

      // Correct texture colour space.
      if (m.map) {
        m.map.colorSpace = THREE.SRGBColorSpace
        m.map.needsUpdate = true
      }

      // Realistic fabric appearance.
      if (isFabric) {
        m.metalness = 0
        m.roughness = 0.85
      }

      m.needsUpdate = true
    }

    // Apply shape keys / morph targets.
    if (
      mesh.morphTargetDictionary &&
      mesh.morphTargetInfluences
    ) {
      for (const [name, index] of Object.entries(
        mesh.morphTargetDictionary
      )) {
        mesh.morphTargetInfluences[index] =
          rs.morphs[name] ?? 0
      }
    }
  })

  // Apply custom morph function if available.
  root.userData.applyMorphs?.(rs.morphs)
}

/**
 * Common model renderer.
 * Used by built-in and uploaded GLB models.
 */
function Model({
  root,
  rs,
}: {
  root: THREE.Object3D
  rs: RenderState
}) {
  useEffect(() => {
    applyState(root, rs)
  }, [root, rs])

  // Automatically fit model to viewer.
  const fit = useMemo(() => {
    const box = new THREE.Box3().setFromObject(root)

    const size = box.getSize(new THREE.Vector3())
    const center = box.getCenter(new THREE.Vector3())

    const scale = size.y > 0 ? 0.70 / size.y : 1

    return {
      scale,
      offset: center.multiplyScalar(-scale),
    }
  }, [root])

  return (
    <group
      position={[
        fit.offset.x,
        fit.offset.y + 0.12,
        fit.offset.z,
      ]}
      scale={fit.scale}
    >
      <primitive object={root} />
    </group>
  )
}

/**
 * Built-in garment model.
 */
function BuiltinModel({
  url,
  rs,
}: {
  url: string
  rs: RenderState
}) {
  const root = useMemo(
    () => buildBuiltin(url),
    [url]
  )

  if (!root) return null

  return <Model root={root} rs={rs} />
}

/**
 * Uploaded GLB model.
 */
// function GlbModel({
//   url,
//   rs,
// }: {
//   url: string
//   rs: RenderState
// }) {
//   const { scene } = useGLTF(url)
function GlbModel({
  url,
  rs,
}: {
  url: string
  rs: RenderState
}) {
  // Convert relative backend media URL to absolute Render URL.
  const resolvedUrl = getMediaUrl(url)

  // Load GLB from the correct backend.
  const { scene } = useGLTF(resolvedUrl)

  const root = useMemo(() => {
    const clone = scene.clone(true)

    clone.traverse((o) => {
      const mesh = o as THREE.Mesh

      if (!mesh.isMesh) return

      // Clone materials so customization does not modify
      // the shared GLTF loader cache.
      const cloneMaterial = (original: THREE.Material) => {
        const material = original.clone()

        // AI-generated GLBs sometimes have generic material names.
        // Treat these as fabric materials.
        const materialName = material.name?.trim() ?? ''

        // Recognize common AI-generated garment material names.
        if (
          !materialName ||
          /^material([_. ]\d+)?$/i.test(materialName) ||
          /^model$/i.test(materialName) ||
          /^geometry_0$/i.test(materialName)
        ) {
          material.name = 'fabric'
        }

        if (material instanceof THREE.MeshStandardMaterial) {
          if (material.map) {
            material.map.colorSpace = THREE.SRGBColorSpace
            material.map.needsUpdate = true
          }

          material.needsUpdate = true
        }

        return material
      }

      mesh.material = Array.isArray(mesh.material)
        ? mesh.material.map(cloneMaterial)
        : cloneMaterial(mesh.material)
    })

    return clone
  }, [scene])

  return <Model root={root} rs={rs} />
}

/**
 * Camera view and zoom controller.
 */
function ViewRig({
  view,
  nonce,
  reduceMotion,
  api,
}: {
  view: ViewName
  nonce: number
  reduceMotion: boolean
  api: MutableRefObject<{
    zoom: (f: number) => void
    cancel: () => void
  }>
}) {
  const { camera } = useThree()

  const goal = useRef<THREE.Vector3 | null>(null)

  useEffect(() => {
    goal.current = VIEWS[view].clone()

    if (reduceMotion) {
      camera.position.copy(goal.current)
      goal.current = null
    }
  }, [view, nonce, camera, reduceMotion])

  useEffect(() => {
    api.current.cancel = () => {
      goal.current = null
    }

    api.current.zoom = (f: number) => {
      const dir = camera.position.clone().sub(TARGET)

      const len = Math.min(
        3.5,
        Math.max(0.9, dir.length() * f)
      )

      camera.position
        .copy(TARGET)
        .add(dir.setLength(len))
    }
  }, [camera, api])

  useFrame((_, dt) => {
    if (!goal.current) return

    camera.position.lerp(
      goal.current,
      1 - Math.exp(-7 * dt)
    )

    if (
      camera.position.distanceTo(goal.current) < 0.01
    ) {
      goal.current = null
    }
  })

  return null
}

interface Props {
  modelUrl: string
  rs: RenderState
  view: ViewName
  viewNonce: number
  autoRotate: boolean
  reduceMotion: boolean
  api: MutableRefObject<{
    zoom: (f: number) => void
    cancel: () => void
  }>
  label: string
}

/**
 * Main Garment 3D Viewer.
 */
export default function Garment3D({
  modelUrl,
  rs,
  view,
  viewNonce,
  autoRotate,
  reduceMotion,
  api,
  label,
}: Props) {
  return (
    <Canvas
      camera={{
        position: VIEWS.front.toArray(),
        fov: 35,
      }}
      dpr={[1, 2]}
      shadows
      role="img"
      aria-label={label}
      gl={{ antialias: true }}
    >
      <color
        attach="background"
        args={['#f6f6f7']}
      />

      <ambientLight intensity={0.6} />

      <hemisphereLight
        args={['#ffffff', '#e5e5e5', 1.2]}
      />

      <directionalLight
        position={[3, 5, 4]}
        intensity={1.5}
        castShadow
      />

      <directionalLight
        position={[-3, 3, -3]}
        intensity={0.8}
      />

      <directionalLight
        position={[0, 2, 5]}
        intensity={0.5}
      />

      {modelUrl.startsWith('builtin:') ? (
        <BuiltinModel
          url={modelUrl}
          rs={rs}
        />
      ) : (
        <GlbModel
          url={modelUrl}
          rs={rs}
        />
      )}

      <ContactShadows
        position={[0, -0.45, 0]}
        opacity={0.35}
        scale={3}
        blur={2.5}
        far={1.2}
      />

      <ViewRig
        view={view}
        nonce={viewNonce}
        reduceMotion={reduceMotion}
        api={api}
      />

      <OrbitControls
        makeDefault
        target={TARGET.toArray()}
        enablePan={false}
        enableDamping
        minDistance={0.9}
        maxDistance={3.5}
        autoRotate={autoRotate && !reduceMotion}
        autoRotateSpeed={1.4}
        onStart={() => api.current.cancel()}
      />
    </Canvas>
  )
}
