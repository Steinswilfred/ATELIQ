# 3D Model and Option Binding Guide

How garments are previewed, and what a 3D artist must deliver. Real models are assets you must create or buy;
until a product has one it can use a built-in placeholder (`builtin:shirt`, `builtin:trousers`) or the 2D fallback.

## File format
Binary glTF (`.glb`), up to 20 MB, uploaded on the product edit page. Real-time friendly: aim for under 100k triangles and
textures at most 2048 px. Y-up, any scale (the viewer rescales to a fixed height), origin anywhere (it recentres).

## Naming rules (these are the contract between artist and admin)
1. **Option parts**: every part a customer can switch on or off (collar styles, sleeve styles, cuffs, pockets, pleats, hems…)
   is its own node/mesh with a clear name, e.g. `collar_spread`, `collar_mandarin`, `sleeve_long_l`, `sleeve_long_r`, `sleeve_short_l`, `pocket_patch`.
   A binding target matches a node by exact name or by `target_` prefix, so the target `sleeve_long` shows both `sleeve_long_l` and `sleeve_long_r`.
2. **Fabric material**: the cloth is a material named `fabric` (or `fabric_something`). It receives the colour the customer picked
   (or the fabric swatch image when no colour is chosen). Give it a light base colour and a tileable normal/roughness map if you have one.
3. **Other materials** (buttons, lining, thread, zipper…) get their own names, e.g. `button`, `lining`.
4. **Fit**: optional morph targets (shape keys) such as `fit_slim`, `fit_relaxed`. For measurement-driven preview, create shape keys with these exact names on the appropriate meshes: `measure_chest`, `measure_waist`, `measure_hip`, `measure_shoulder`, `measure_sleeve_length`, `measure_shirt_length`. The storefront maps saved measurement profile values to 0..1 influences using the ranges documented below. Create and test the shape keys in Blender; the application cannot invent garment deformation from measurements alone.
5. **Alternative garment styles**: create each alternative as a separate mesh in the same coordinate space and name it for the option, e.g. `collar_spread`, `collar_mandarin`, `sleeve_long_l`, `sleeve_long_r`, `sleeve_short_l`, `sleeve_short_r`, `cuff_barrel_l`, `cuff_barrel_r`, `cuff_french_l`, `cuff_french_r`, `pocket_patch`. Use `mesh_toggle` options to select one alternative per group. Keep alternatives aligned; do not export them scattered/exploded.
6. **Performance**: keep the model below the 20 MB upload limit, aim for under 100k triangles, and use compressed/appropriately sized textures. Decimate carefully and inspect seams/silhouette after optimization.
5. Keep parts that never change (body of the shirt, legs) free of option names so they are always visible.

## Measurement preview mapping

When the customer selects a saved measurement profile, the 3D preview reads its measurements and sets matching morph targets. Values are converted to centimetres when a profile is stored in inches, then normalized and clamped to 0..1 using these default ranges (centimetres):

| Measurement code | Shape key | Range |
|---|---|---:|
| `chest` | `measure_chest` | 80–140 |
| `waist` | `measure_waist` | 60–140 |
| `hip` | `measure_hip` | 80–145 |
| `shoulder` | `measure_shoulder` | 32–58 |
| `sleeve_length` | `measure_sleeve_length` | 45–75 |
| `shirt_length` | `measure_shirt_length` | 60–90 |

These are preview mapping ranges, not validated tailoring formulas. The artist must create a useful Basis-to-target shape key for each measurement and validate garment fit with a pattern maker. This does not yet alter the saved order measurements or calculate production patterns.

## Binding types (set per option under Admin → Customization)
| Type | Target | Value | Effect |
|---|---|---|---|
| Part toggle (`mesh_toggle`) | node name | none | Shown only while this option is selected. A part named by *any* option of the product is hidden whenever no option naming it is selected, so "No pocket" needs no binding at all. |
| Material colour (`material_color`) | material name | hex like `#1F3A5F`, or empty | Sets the material colour; empty means the customer's chosen fabric colour. |
| Texture (`texture`) | material name | uploaded image address (`/media/...`) | Swaps the material's texture. |
| Shape (`morph_target`) | shape-key name | number 0 to 1 (default 1) | Sets the morph influence. |
| 2D layer (`layer_2d`) | any label | uploaded PNG address | Transparent image stacked over the product photo in the 2D fallback. |

## How it behaves
- Choosing an option updates the preview instantly in the browser; the **price always comes from the server**.
- 360° rotation, zoom (wheel, pinch, buttons) and Front / Back / Left / Right views are built into the viewer.
- If the product has no model, the browser lacks WebGL, or the model fails to load, the page shows the product photos with any 2D layers.
- Saved designs store only the choices (ids), never the 3D state, so models can be replaced without breaking designs.

## Testing a new model
1. Upload it on the product page, set the model to "Uploaded .glb" and save.
2. Under Customization create options whose targets are your node names; attach them to the product.
3. Open the product on the storefront and click through every option; check hidden/shown parts from all four views.


## ATELIQ shirt setup example

Create the following customization groups in Admin → Customization, then attach the groups/options to the product in Admin → Products:

| Group | Option | Binding type | Target | Value |
|---|---|---|---|---|
| Collar | Spread | `mesh_toggle` | `collar_spread` | blank |
| Collar | Mandarin | `mesh_toggle` | `collar_mandarin` | blank |
| Sleeve | Long | `mesh_toggle` | `sleeve_long` | blank |
| Sleeve | Short | `mesh_toggle` | `sleeve_short` | blank |
| Cuff | Barrel | `mesh_toggle` | `cuff_barrel` | blank |
| Cuff | French | `mesh_toggle` | `cuff_french` | blank |
| Pocket | Patch pocket | `mesh_toggle` | `pocket_patch` | blank |
| Buttons | Gold | `material_color` | `button` | `#C9A227` |
| Buttons | White | `material_color` | `button` | `#FFFFFF` |
| Fabric finish | Cotton texture | `texture` | `fabric` | `/media/fabrics/cotton.jpg` |

Mesh targets support suffix matching: `sleeve_long` controls both `sleeve_long_l` and `sleeve_long_r`. Each mutually exclusive option should live in the same group. The model artist must supply all alternative meshes; the admin binding only switches geometry visibility and does not create new geometry.

## Current implementation boundary

Measurement preview is a visual shape-key approximation. It maps saved profiles (inches are converted to centimetres) to the `measure_*` morph targets. It does not generate sewing patterns, guarantee fit, or replace tailor verification. Existing quote, saved-design, cart, order and backend measurement workflows remain unchanged. No database migration is required for these preview bindings.
