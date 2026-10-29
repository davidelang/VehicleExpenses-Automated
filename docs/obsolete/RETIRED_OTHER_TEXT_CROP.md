# Obsolete: ignore / otherText crop

**Status:** Retired. Manage Vehicles already saved these crops as null. Room migration 21→22 drops the four columns. Tabular vehicle rows no longer have Other Crop L/T/R/B.

The product kept an odometer crop and a second rectangle, the ignore / otherText crop (`otherTextCropLeft`, `otherTextCropTop`, `otherTextCropRight`, `otherTextCropBottom`). Landmark filtering could drop text blocks inside that rectangle. The paddle Manage Vehicles screen stopped editing it and wrote null on save.

## What remains

Odometer crop stays on the vehicle and in the Vehicles tab (`Odo Crop L/T/R/B`). Landmark JSON stays. Definition overlay still fills a thin odometer crop from the LWW loser. It does not copy an ignore crop.

## Not restored

Do not add the four columns back to `Vehicle`, the unassigned-vehicle insert, or `TabularSchema` vehicle headers. Historical migration `MIGRATION_12_13` still names them because that step rebuilt the v13 table, which still had the columns.
