import { describe, expect, it } from 'vitest'
import { computeRenderState, isNodeVisible, meshMatches, normalizeMeasurements } from './renderState'
import type { DesignConfig, ProductT } from './shopTypes'

const product = {
  id: 1, name: 'Shirt', slug: 'shirt', basePrice: 1000, categoryName: 'Shirts', sectionName: 'Men', images: [], sizes: [],
  fabrics: [{ id: 10, name: 'Cotton', priceDelta: 0, imageUrl: '/media/catalog/c.jpg', colors: [{ id: 1, name: 'Navy', hexCode: '#1F3A5F' }] }],
  groups: [
    { id: 5, code: 'collar', name: 'Collar', required: true, options: [
      { id: 100, code: 'spread', name: 'Spread', priceDelta: 0, isDefault: true, renderBinding: { type: 'mesh_toggle', target: 'collar_spread' } },
      { id: 101, code: 'mandarin', name: 'Mandarin', priceDelta: 0, isDefault: false, renderBinding: { type: 'mesh_toggle', target: 'collar_mandarin' } }] },
    { id: 6, code: 'pocket', name: 'Pocket', required: false, options: [
      { id: 110, code: 'patch', name: 'Patch', priceDelta: 0, isDefault: false, renderBinding: { type: 'mesh_toggle', target: 'pocket_patch' } },
      { id: 111, code: 'none', name: 'None', priceDelta: 0, isDefault: false, renderBinding: null }] },
    { id: 7, code: 'button', name: 'Buttons', required: false, options: [
      { id: 120, code: 'gold', name: 'Gold', priceDelta: 0, isDefault: false, renderBinding: { type: 'material_color', target: 'button', value: '#C9A227' } },
      { id: 121, code: 'match', name: 'Match fabric', priceDelta: 0, isDefault: false, renderBinding: { type: 'material_color', target: 'button' } }] },
    { id: 8, code: 'fit', name: 'Fit', required: false, options: [
      { id: 130, code: 'slim', name: 'Slim', priceDelta: 0, isDefault: false, renderBinding: { type: 'morph_target', target: 'fit_slim', value: '2' } }] },
    { id: 9, code: 'logo', name: 'Logo', required: false, options: [
      { id: 140, code: 'crest', name: 'Crest', priceDelta: 0, isDefault: false, renderBinding: { type: 'layer_2d', target: 'crest', value: '/media/catalog/crest.png' } }] },
  ],
} as unknown as ProductT

const cfg = (options: Record<string, number>): DesignConfig => ({ sizeCode: 'M', measurementProfileId: null, fabricId: 10, colorId: 1, options })

describe('meshMatches', () => {
  it('matches exact names and underscore-prefixed children only', () => {
    expect(meshMatches('sleeve_long_l', 'sleeve_long')).toBe(true)
    expect(meshMatches('sleeve_long', 'sleeve_long')).toBe(true)
    expect(meshMatches('sleeve_longer', 'sleeve_long')).toBe(false)
    expect(meshMatches('collar', 'collar_spread')).toBe(false)
  })
})

describe('computeRenderState', () => {
  it('shows only the selected collar and hides the other controlled meshes', () => {
    const rs = computeRenderState(product, cfg({ '5': 100 }))
    expect(isNodeVisible('collar_spread', rs)).toBe(true)
    expect(isNodeVisible('collar_mandarin', rs)).toBe(false)
    expect(isNodeVisible('body', rs)).toBe(true)          // nothing controls the body
  })
  it('hides controlled meshes when nothing or an unbound option is chosen', () => {
    expect(isNodeVisible('pocket_patch', computeRenderState(product, cfg({})))).toBe(false)
    expect(isNodeVisible('pocket_patch', computeRenderState(product, cfg({ '6': 111 })))).toBe(false)
    expect(isNodeVisible('pocket_patch', computeRenderState(product, cfg({ '6': 110 })))).toBe(true)
  })
  it('uses the fabric colour, an explicit colour, or neither', () => {
    expect(computeRenderState(product, cfg({})).fabricColor).toBe('#1F3A5F')
    expect(computeRenderState(product, cfg({ '7': 120 })).colors.button).toBe('#C9A227')
    expect(computeRenderState(product, cfg({ '7': 121 })).colors.button).toBe('#1F3A5F')
    expect(computeRenderState(product, cfg({})).colors.button).toBeUndefined()
  })
  it('uses the fabric swatch only when no colour is chosen', () => {
    expect(computeRenderState(product, { ...cfg({}), colorId: null }).fabricTexture).toBe('/media/catalog/c.jpg')
    expect(computeRenderState(product, cfg({})).fabricTexture).toBeUndefined()
  })
  it('clamps morph values and collects 2D layers', () => {
    const rs = computeRenderState(product, cfg({ '8': 130, '9': 140 }))
    expect(rs.morphs.fit_slim).toBe(1)
    expect(rs.layers).toEqual([{ name: 'crest', url: '/media/catalog/crest.png' }])
  })
})


describe('measurement shape-key mapping', () => {
  it('normalizes known body measurements into Blender shape-key influences', () => {
    expect(normalizeMeasurements({ chest: 110, waist: 100, sleeve_length: 60 })).toEqual({
      measure_chest: 0.5, measure_waist: 0.5, measure_sleeve_length: 0.5,
    })
  })
  it('clamps out-of-range values and ignores unknown or invalid measurements', () => {
    expect(normalizeMeasurements({ chest: 200, waist: 0, unknown: 50, hip: Number.NaN })).toEqual({ measure_chest: 1 })
  })
  it('includes measurement morphs in the computed render state', () => {
    const rs = computeRenderState(product, cfg({}), { chest: 110 })
    expect(rs.measurementMorphs.measure_chest).toBe(0.5)
  })
})
