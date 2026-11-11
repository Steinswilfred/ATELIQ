# Advanced GLB customization implementation notes

This update extends the existing customization flow. It does not replace the existing product, pricing, saved-design, cart, order, 2D fallback, or admin APIs.

## Updated files

- `frontend/src/lib/renderState.ts`
  - Adds measurement normalization for Blender shape keys named `measure_<code>`.
  - Keeps existing option-driven mesh, colour, texture, morph and 2D-layer bindings.
- `frontend/src/components/shop/Garment3D.tsx`
  - Applies measurement shape keys and option shape keys to GLB morph targets.
  - Matches material names case-insensitively.
  - Keeps fabric materials matte and preserves non-fabric authored roughness.
- `frontend/src/components/shop/PreviewPanel.tsx`
  - Passes the selected measurement profile to the 3D render state.
- `frontend/src/pages/shop/ProductPage.tsx`
  - Supplies the selected saved measurement profile to the preview.
- `frontend/src/pages/admin/AdminCustomization.tsx`
  - Clarifies the exact mesh/material/shape-key binding conventions.
- `frontend/src/pages/admin/AdminProductEditor.tsx`
  - Rejects non-GLB files and files larger than the existing 20 MB upload limit before upload.
- `frontend/src/lib/renderState.test.ts`
  - Adds tests for measurement normalization and shape-key state.
- `docs/3D_MODEL_GUIDE.md`
  - Documents alternative geometry, material naming, texture binding, measurement shape keys and setup examples.

## Blender work still required

The GLB asset itself is not modified by these source-code changes. The current uploaded model has generic mesh names and does not contain the alternate collar/sleeve/cuff meshes or measurement shape keys. In Blender:

1. Identify each existing mesh visually and rename it; do not rely on the old `root.N` names as semantic labels.
2. Assign materials named `fabric`, `button`, `thread`, etc.
3. Create alternative collar/sleeve/cuff/pocket meshes in the same assembled position and use the documented names.
4. Create and calibrate shape keys on the relevant mesh objects using the documented `measure_*` names.
5. Optimize the model to <=20 MB and preferably <100k triangles, then export GLB.
6. Upload the optimized GLB to the product in Admin > Products and create the corresponding option bindings in Admin > Customization.

Measurement morphs are visual approximations only. They do not generate sewing patterns or guarantee garment fit. The profile values are converted from inches to centimetres before preview mapping.

## Verification

`npm ci` could not complete in the execution environment (dependency installation timed out), so `npm run build` and `npm test` could not be completed. The attempted build stopped because `vite/client` type definitions were unavailable in the incomplete `node_modules` directory. Run the following locally after restoring dependencies:

```bash
cd frontend
npm ci
npm test
npm run build
```
