import type { DesignConfig, ProductT } from './shopTypes'

/**
 * Turns "what the customer chose" into "what the model should look like", using the render bindings the admin
 * configured on each option. Pure logic: the 3D viewer and the 2D fallback both read this, and it is unit-tested.
 *
 * Binding types (see docs/3D_MODEL_GUIDE.md):
 *  - mesh_toggle     target = mesh/node name. Meshes named in ANY option's toggle are "controlled": they show only when
 *                    the selected option names them. Meshes no option mentions are always shown.
 *  - material_color  target = material name, value = hex (empty = the chosen fabric colour)
 *  - texture         target = material name, value = image address
 *  - morph_target    target = morph/shape name, value = 0..1 (default 1)
 *  - layer_2d        target = layer name, value = transparent PNG address stacked over the 2D photo
 */
export type GarmentMeasurements = Record<string, number>

export interface RenderState {
  controlledTargets: string[]
  visibleTargets: string[]
  colors: Record<string, string>
  textures: Record<string, string>
  morphs: Record<string, number>
  layers: { name: string; url: string }[]
  fabricColor?: string
  fabricTexture?: string
  /** Normalized 0..1 values for Blender shape keys named measure_<measurement_code>. */
  measurementMorphs: Record<string, number>
}

const HEX = /^#[0-9A-Fa-f]{6}$/
export const isHex = (v: unknown): v is string => typeof v === 'string' && HEX.test(v)

/** A node matches a target by exact name or by "target_" prefix, so "sleeve_long" covers sleeve_long_l and sleeve_long_r. */
export const meshMatches = (name: string, target: string) => name === target || name.startsWith(`${target}_`)

/**
 * Converts saved body measurements into normalized shape-key values.
 * Blender artists should create shape keys named measure_chest, measure_waist,
 * measure_hip, measure_shoulder, measure_sleeve_length and measure_shirt_length.
 * The ranges are conservative defaults; they are not a substitute for garment
 * pattern drafting or a physically simulated fit system.
 */
export const MEASUREMENT_RANGES: Record<string, [number, number]> = {
  chest: [80, 140], waist: [60, 140], hip: [80, 145],
  shoulder: [32, 58], sleeve_length: [45, 75], shirt_length: [60, 90],
}

export function normalizeMeasurements(measurements?: GarmentMeasurements): Record<string, number> {
  const result: Record<string, number> = {}
  if (!measurements) return result
  for (const [code, [min, max]] of Object.entries(MEASUREMENT_RANGES)) {
    const value = Number(measurements[code])
    if (Number.isFinite(value) && value > 0) result[`measure_${code}`] = Math.min(1, Math.max(0, (value - min) / (max - min)))
  }
  return result
}

export function computeRenderState(product: ProductT, config: DesignConfig, measurements?: GarmentMeasurements): RenderState {
  const fabric = product.fabrics.find((f) => f.id === config.fabricId)
  const color = fabric?.colors.find((c) => c.id === config.colorId)
  const rs: RenderState = { controlledTargets: [], visibleTargets: [], colors: {}, textures: {}, morphs: {}, layers: [], fabricColor: color?.hexCode, fabricTexture: color ? undefined : fabric?.imageUrl, measurementMorphs: normalizeMeasurements(measurements) }

  for (const group of product.groups) {
    const chosen = config.options?.[String(group.id)]
    for (const option of group.options) {
      const b = option.renderBinding
      if (!b) continue
      const selected = option.id === chosen
      switch (b.type) {
        case 'mesh_toggle':
          rs.controlledTargets.push(b.target)
          if (selected) rs.visibleTargets.push(b.target)
          break
        case 'material_color': {
          if (!selected) break
          const c = b.value != null ? String(b.value) : color?.hexCode
          if (isHex(c)) rs.colors[b.target] = c
          break
        }
        case 'texture':
          if (selected && b.value) rs.textures[b.target] = String(b.value)
          break
        case 'morph_target':
          if (selected) {
            const n = b.value != null ? Number(b.value) : 1
            rs.morphs[b.target] = Number.isFinite(n) ? Math.min(1, Math.max(0, n)) : 1
          }
          break
        case 'layer_2d':
          if (selected && b.value) rs.layers.push({ name: b.target, url: String(b.value) })
          break
      }
    }
  }
  return rs
}

/** True when this node should be visible. Nodes no option controls are always visible. */
export function isNodeVisible(name: string, rs: RenderState): boolean {
  const controlled = rs.controlledTargets.some((t) => meshMatches(name, t))
  return !controlled || rs.visibleTargets.some((t) => meshMatches(name, t))
}

/** Materials named "fabric" or "fabric_*" take the chosen fabric colour. */
export const isFabricMaterial = (name: string) => { const key = name.trim().toLowerCase(); return key === 'fabric' || key.startsWith('fabric_') }
