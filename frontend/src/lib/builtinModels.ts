import * as THREE from 'three'

/**
 * PLACEHOLDER garments built from simple shapes, so the whole customization pipeline can be developed and tested
 * before real 3D models exist. They follow the same naming rules as real models (docs/3D_MODEL_GUIDE.md):
 * named meshes for options, and a material called "fabric" that takes the chosen colour.
 * Replace by uploading a .glb for the product; nothing else changes.
 */
const mat = (name: string, color: string, roughness = 0.85) => {
  const m = new THREE.MeshStandardMaterial({ color, roughness })
  m.name = name
  return m
}
const part = (name: string, geo: THREE.BufferGeometry, material: THREE.Material, pos: [number, number, number], rot: [number, number, number] = [0, 0, 0]) => {
  const m = new THREE.Mesh(geo, material)
  m.name = name
  m.position.set(...pos)
  m.rotation.set(...rot)
  m.castShadow = true
  return m
}

export function buildShirt(): THREE.Group {
  const g = new THREE.Group()
  g.name = 'builtin_shirt'
  const fabric = mat('fabric', '#ffffff')
  const button = mat('button', '#f1f1f1', 0.4)

  const body = part('body', new THREE.CylinderGeometry(0.21, 0.19, 0.62, 40), fabric, [0, 0.15, 0])
  body.scale.set(1, 1, 0.62)
  g.add(body)
  g.add(part('shoulders', new THREE.SphereGeometry(0.22, 32, 16, 0, Math.PI * 2, 0, Math.PI / 2), fabric, [0, 0.43, 0]))
  Object.assign(g.children[1].scale, { x: 1.05, y: 0.35, z: 0.62 })

  const sleeve = (len: number, kind: 'short' | 'long', side: 1 | -1) => {
    const theta = 0.3
    const topX = 0.235 * side, topY = 0.4
    const cx = topX + Math.sin(theta) * (len / 2) * side, cy = topY - Math.cos(theta) * (len / 2)
    return part(`sleeve_${kind}_${side === 1 ? 'r' : 'l'}`, new THREE.CylinderGeometry(0.075, 0.062, len, 24), fabric, [cx, cy, 0], [0, 0, theta * side])
  }
  for (const side of [1, -1] as const) { g.add(sleeve(0.2, 'short', side)); g.add(sleeve(0.55, 'long', side)) }

  g.add(part('collar_spread', new THREE.TorusGeometry(0.1, 0.022, 10, 32, Math.PI * 1.7), fabric, [0, 0.47, 0], [Math.PI / 2, 0, Math.PI * 0.65]))
  g.add(part('collar_button_down', new THREE.TorusGeometry(0.095, 0.02, 10, 32, Math.PI * 1.6), fabric, [0, 0.47, 0], [Math.PI / 2, 0, Math.PI * 0.7]))
  g.add(part('collar_mandarin', new THREE.CylinderGeometry(0.095, 0.1, 0.045, 32, 1, true), fabric, [0, 0.475, 0]))
  Object.assign((g.getObjectByName('collar_mandarin') as THREE.Mesh).material, { side: THREE.DoubleSide })

  g.add(part('pocket_patch', new THREE.BoxGeometry(0.09, 0.1, 0.012), fabric, [0.1, 0.22, 0.133]))
  g.add(part('pocket_double_r', new THREE.BoxGeometry(0.09, 0.1, 0.012), fabric, [0.1, 0.22, 0.133]))
  g.add(part('pocket_double_l', new THREE.BoxGeometry(0.09, 0.1, 0.012), fabric, [-0.1, 0.22, 0.133]))

  const buttons = new THREE.Group()
  buttons.name = 'placket_buttons'
  for (let i = 0; i < 6; i++) buttons.add(part(`placket_button_${i}`, new THREE.SphereGeometry(0.013, 12, 12), button, [0, 0.4 - i * 0.1, 0.133]))
  g.add(buttons)

  /** fit_slim / fit_relaxed (0..1) narrow or widen the body, standing in for morph targets on a real model */
  g.userData.applyMorphs = (m: Record<string, number>) => {
    body.scale.x = 1 - 0.12 * (m.fit_slim ?? 0) + 0.12 * (m.fit_relaxed ?? 0)
  }
  return g
}

export function buildTrousers(): THREE.Group {
  const g = new THREE.Group()
  g.name = 'builtin_trousers'
  const fabric = mat('fabric', '#ffffff')
  g.add(part('waistband', new THREE.CylinderGeometry(0.2, 0.2, 0.06, 40), fabric, [0, 0.5, 0]))
  const hips = part('hips', new THREE.CylinderGeometry(0.2, 0.19, 0.25, 40), fabric, [0, 0.355, 0])
  hips.scale.set(1, 1, 0.65)
  g.add(hips)
  for (const side of [1, -1] as const) {
    const s = side === 1 ? 'r' : 'l'
    g.add(part(`leg_${s}`, new THREE.CylinderGeometry(0.095, 0.075, 0.82, 28), fabric, [0.1 * side, -0.07, 0]))
    g.add(part(`pocket_slant_${s}`, new THREE.BoxGeometry(0.02, 0.12, 0.012), fabric, [0.15 * side, 0.4, 0.125], [0, 0, 0.5 * side]))
    g.add(part(`pocket_straight_${s}`, new THREE.BoxGeometry(0.1, 0.012, 0.012), fabric, [0.13 * side, 0.43, 0.125]))
    g.add(part(`pleat_single_${s}`, new THREE.BoxGeometry(0.008, 0.2, 0.01), fabric, [0.06 * side, 0.36, 0.13]))
    g.add(part(`hem_cuffed_${s}`, new THREE.TorusGeometry(0.078, 0.014, 10, 28), fabric, [0.1 * side, -0.46, 0], [Math.PI / 2, 0, 0]))
  }
  g.userData.applyMorphs = (m: Record<string, number>) => {
    const w = 1 - 0.1 * (m.fit_slim ?? 0) + 0.1 * (m.fit_relaxed ?? 0)
    g.getObjectByName('hips')?.scale.set(w, 1, 0.65)
  }
  return g
}

export function buildBuiltin(url: string): THREE.Group | null {
  if (url === 'builtin:shirt') return buildShirt()
  if (url === 'builtin:trousers') return buildTrousers()
  return null
}
