# Obsolete: draw-crop Manage Vehicles screen

**Status:** Decision recorded at tag `obsolete-manage-vehicles-draw` (tree still had drag-draw crops on route `managevehicles`). The following commit promotes the paddle seed screen to that route and deletes `ManageVehiclesScreen.kt`.

**Annotated tag (peel this tree — last with draw rectangles + ML Kit discovery harness on `managevehicles`):** `obsolete-manage-vehicles-draw`  
**Commit (tag peel):** `3731a7b3a7040c1c4eab95763baac651d3544116`  
**Subject:** `MV2: seed-pad 0.6/0.8 crops; ignore crop by seed tap`

## Why

The product drawer should expose one **Manage Vehicles** entry. The draw UI made users drag odometer and ignore rectangles, then ran multi-engine discovery via `OcrHarness.runDiscovery` with text-angle deskew in the photo pipeline. The paddle screen is simpler: **Run Discovery** runs `productDetLandmarks2048` at vertical factor **0.40**, shows yellow seeds on the photo, and **Odo Crop** / **Ignore Crop** pick boxes by tapping a seed ( **Edit Crops** still adjusts rectangles). Landmark JSON remains under the key **`"ML Kit"`**. The paddle path does not text-angle deskew for discovery.

| Surface | Retired draw screen | Live paddle screen (`managevehicles`) |
|---------|---------------------|----------------------------------------|
| Odo / ignore regions | Drag to draw rectangles | Tap a seed after discovery (Edit Crops to adjust) |
| Run Discovery | `OcrHarness.runDiscovery` | Paddle det seeds on upright gray |
| Deskew | Text-angle deskew in discovery pipeline | Not used for this discovery step |
| Clear Crops | (no dedicated control) | Clears both rectangles in memory; persist on **Save** |

## Recovery

```bash
git rev-parse obsolete-manage-vehicles-draw^{}
git show obsolete-manage-vehicles-draw:app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/vehicle/ManageVehiclesScreen.kt
```

Peel commit if the tag is missing: `3731a7b3a7040c1c4eab95763baac651d3544116`.
