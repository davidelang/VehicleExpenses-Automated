package com.davidlang.vehicleexpensesautomated.ui.onboarding

import com.davidlang.vehicleexpensesautomated.ui.util.S

/** Stable tutorial IDs (T1). */
object TutorialIds {
    const val ADD_VEHICLE = "tutorial_add_vehicle"
    const val SETUP_SYNC = "tutorial_setup_sync"
}

data class TutorialStep(
    val title: String,
    val body: String,
    /** Asset path under `assets/tutorials/` or null for text-only. */
    val imageAsset: String? = null,
)

data class Tutorial(
    val id: String,
    val title: String,
    val steps: List<TutorialStep>,
    /** Nav route after Done (e.g. managevehicles, syncing). */
    val endRoute: String,
    val endCtaLabel: String,
)

object TutorialCatalog {
    fun get(id: String): Tutorial? = all().find { it.id == id }

    /** Rebuilt on each call so an in-app language change is not stuck on the first locale. */
    fun all(): List<Tutorial> = listOf(
        Tutorial(
            id = TutorialIds.ADD_VEHICLE,
            title = S("Add a vehicle"),
            endRoute = "managevehicles",
            endCtaLabel = S("Go to Manage Vehicles"),
            steps = listOf(
                TutorialStep(
                    title = S("Open Manage Vehicles"),
                    body = S("From the menu (☰), choose Manage Vehicles. This is where you create dashboards ") +
                        S("the app can recognize later."),
                    imageAsset = "tutorials/drawer.jpg",
                ),
                TutorialStep(
                    title = S("Add New Vehicle"),
                    body = S("Open the vehicle dropdown and pick Add New Vehicle. You are setting up this phone ") +
                        S("as stand-alone / first vehicle (or an additional vehicle)."),
                    imageAsset = "tutorials/vehicle_manage.jpg",
                ),
                TutorialStep(
                    title = S("Dashboard photo"),
                    body = S("Take or pick a clear photo of the instrument cluster. Good lighting and a square-on ") +
                        S("view of the odometer help discovery."),
                    imageAsset = "tutorials/vehicle_dash.jpg",
                ),
                TutorialStep(
                    title = S("Odo Crop & Run Discovery"),
                    body = S("Run Discovery, Select Odo, tap the seed, adjust, Save. ") +
                        S("Review landmarks. Edit OCR text if something was missed."),
                    imageAsset = "tutorials/vehicle_crops.jpg",
                ),
                TutorialStep(
                    title = S("Name and Create"),
                    body = S("Enter a Vehicle Name, then Create Vehicle. After at least one user vehicle exists, ") +
                        S("the first-run splash will not appear again."),
                    imageAsset = "tutorials/vehicle_manage.jpg",
                ),
            ),
        ),
        Tutorial(
            id = TutorialIds.SETUP_SYNC,
            title = S("Connect existing setup"),
            endRoute = "syncing",
            endCtaLabel = S("Go to Syncing"),
            steps = listOf(
                TutorialStep(
                    title = S("You already have a cluster"),
                    body = S("This path is for a **new phone or tablet** joining an **existing** Vehicle Expenses setup. ") +
                        S("Another device already has vehicles, a **shared spreadsheet**, and usually a **shared photo folder**. ") +
                        S("You use **your** Google / Microsoft / other account — not an app-hosted cloud. ") +
                        S("Stand-alone first setup (no other device yet) is **Add a vehicle**, not this tutorial."),
                    imageAsset = "tutorials/drawer.jpg",
                ),
                TutorialStep(
                    title = S("Open Syncing on this device"),
                    body = S("From the menu (☰), open **Syncing**. You will add destinations that point at the **same** ") +
                        S("sheet and photo folder the other device already uses — not brand-new empty ones."),
                    imageAsset = "tutorials/sync_hub.jpg",
                ),
                TutorialStep(
                    title = S("Spreadsheet: open the existing shared file"),
                    body = S("Add a spreadsheet destination → pick the same provider as the other device (often Google Sheets) → ") +
                        S("sign in. Paste the **existing sheet URL** from the other phone (or Drive **browse to that file**). ") +
                        S("Test connection, then **Sync now** to pull vehicles and rows. ") +
                        S("**Do not Create** a new blank spreadsheet for this path (if the UI still offers Create, skip it)."),
                    imageAsset = "tutorials/sync_sheet.jpg",
                ),
                TutorialStep(
                    title = S("Photos: same existing folder"),
                    body = S("Add a photo destination → same provider as the other device → sign in. ") +
                        S("Choose the **same photo folder** already used by the cluster (URL or browse). ") +
                        S("Test connection → Sync now. Vehicle reference images can download automatically; ") +
                        S("fill/receipt photos are on-demand via Fetch from archive. ") +
                        S("**Do not create a new empty folder** for this path."),
                    imageAsset = "tutorials/sync_photo.jpg",
                ),
                TutorialStep(
                    title = S("After the first Sync now"),
                    body = S("Vehicles and data should appear from the shared sheet. Only then, if a vehicle is missing ") +
                        S("a local dash photo, open Manage Vehicles and capture or fetch the reference image. ") +
                        S("You do not need to re-type the whole fleet from scratch."),
                    imageAsset = "tutorials/sync_hub.jpg",
                ),
            ),
        ),
    )
}
