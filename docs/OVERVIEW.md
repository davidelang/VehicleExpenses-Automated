# Vehicle Expenses Automated — Overview

The app records vehicle costs from the phone. Fuel is the camera path. Everything else is ordinary entry, with optional sync under accounts the user already has.

## What a person does

1. **Add a vehicle** from a dashboard photo. The app keeps that photo, an odometer crop, and the words that identify the dash.
2. **Quick Fill** takes two pictures: the dash, then the pump. The vehicle is chosen from the dash. The odometer, cost, and volume land in the form for review. The phone’s location is taken once for that visit and used to name the station.
3. **Save** writes a fuel row. Numbers can be typed when the camera misses.
4. **Trips** mark business or personal use as their own fuel rows.
5. **Expenses** are the non-fuel costs, with an optional receipt photo.
6. **Reports** summarize economy, spending, fills, and trip miles.
7. **Sync** copies the table and the photos to a spreadsheet and a photo folder the user owns. The phone works with no network. Sync runs when there is one.

The illustrated steps are the [full manual](user-manual/index.html). The short form is the [quick guide](QUICK_GUIDE.md).

## Where the detail lives

| Question | Document |
|----------|----------|
| What Quick Fill does with the dash, the pump, and the location | [reference/QUICK_FILL_CAMERA.md](reference/QUICK_FILL_CAMERA.md) |
| How a pump heatmap is expanded in current experiments | [reference/PUMP_EXPERIMENT_FLOWS.md](reference/PUMP_EXPERIMENT_FLOWS.md) |
| Crop preprocessing before OCR | [reference/OCR_PREPROCESSING.md](reference/OCR_PREPROCESSING.md) |
| How cost and volume are chosen from those OCR boxes | [reference/PUMP_CLASSIFICATION.md](reference/PUMP_CLASSIFICATION.md) |
| Screens and the drawer | [reference/NAVIGATION_MAP.md](reference/NAVIGATION_MAP.md) |
| Image buffers | [specs/BUFFER_SET_SPEC.md](specs/BUFFER_SET_SPEC.md) |
| Stored crop coordinates | [specs/ISOTROPIC_COORDINATE_SPEC.md](specs/ISOTROPIC_COORDINATE_SPEC.md) |
| Paddle and third-party pins | [specs/PADDLE_PIN_BUILDS.md](specs/PADDLE_PIN_BUILDS.md), [specs/THIRD_PARTY_PIN_BUILDS.md](specs/THIRD_PARTY_PIN_BUILDS.md) |
| Things we tried and stopped | [obsolete/](obsolete/) |

Specs are locked: if the code and the spec disagree, the code is wrong. Reference pages are updated when the code changes. The full index is [README.md](README.md).
