package com.davidlang.vehicleexpensesautomated.ui.onboarding

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
    fun get(id: String): Tutorial? = all.find { it.id == id }

    val all: List<Tutorial> = listOf(
        Tutorial(
            id = TutorialIds.ADD_VEHICLE,
            title = "Add a vehicle",
            endRoute = "managevehicles",
            endCtaLabel = "Go to Manage Vehicles",
            steps = listOf(
                TutorialStep(
                    title = "Open Manage Vehicles",
                    body = "From the menu (☰), choose Manage Vehicles. This is where you create dashboards " +
                        "the app can recognize later.",
                    imageAsset = "tutorials/drawer.jpg",
                ),
                TutorialStep(
                    title = "Add New Vehicle",
                    body = "Open the vehicle dropdown and pick Add New Vehicle. You are setting up this phone " +
                        "as stand-alone / first vehicle (or an additional vehicle).",
                    imageAsset = "tutorials/vehicle_manage.jpg",
                ),
                TutorialStep(
                    title = "Dashboard photo",
                    body = "Take or pick a clear photo of the instrument cluster. Good lighting and a square-on " +
                        "view of the odometer help discovery.",
                    imageAsset = "tutorials/vehicle_dash.jpg",
                ),
                TutorialStep(
                    title = "Odo Crop & Run Discovery",
                    body = "Draw Odo Crop around the odometer digits (optional Ignore Crop for clutter). " +
                        "Tap Run Discovery and review landmarks. Edit OCR text if something was missed.",
                    imageAsset = "tutorials/vehicle_crops.jpg",
                ),
                TutorialStep(
                    title = "Name and Create",
                    body = "Enter a Vehicle Name, then Create Vehicle. After at least one user vehicle exists, " +
                        "the first-run splash will not appear again.",
                    imageAsset = "tutorials/vehicle_manage.jpg",
                ),
            ),
        ),
        Tutorial(
            id = TutorialIds.SETUP_SYNC,
            title = "Connect existing setup",
            endRoute = "syncing",
            endCtaLabel = "Go to Syncing",
            steps = listOf(
                TutorialStep(
                    title = "You already have a cluster",
                    body = "This path is for a **new phone or tablet** joining an **existing** Vehicle Expenses setup. " +
                        "Another device already has vehicles, a **shared spreadsheet**, and usually a **shared photo folder**. " +
                        "You use **your** Google / Microsoft / other account — not an app-hosted cloud. " +
                        "Stand-alone first setup (no other device yet) is **Add a vehicle**, not this tutorial.",
                    imageAsset = "tutorials/drawer.jpg",
                ),
                TutorialStep(
                    title = "Open Syncing on this device",
                    body = "From the menu (☰), open **Syncing**. You will add destinations that point at the **same** " +
                        "sheet and photo folder the other device already uses — not brand-new empty ones.",
                    imageAsset = "tutorials/sync_hub.jpg",
                ),
                TutorialStep(
                    title = "Spreadsheet: open the existing shared file",
                    body = "Add a spreadsheet destination → pick the same provider as the other device (often Google Sheets) → " +
                        "sign in. Paste the **existing sheet URL** from the other phone (or Drive **browse to that file**). " +
                        "Test connection, then **Sync now** to pull vehicles and rows. " +
                        "**Do not Create** a new blank spreadsheet for this path (if the UI still offers Create, skip it).",
                    imageAsset = "tutorials/sync_sheet.jpg",
                ),
                TutorialStep(
                    title = "Photos: same existing folder",
                    body = "Add a photo destination → same provider as the other device → sign in. " +
                        "Choose the **same photo folder** already used by the cluster (URL or browse). " +
                        "Test connection → Sync now. Vehicle reference images can download automatically; " +
                        "fill/receipt photos are on-demand via Fetch from archive. " +
                        "**Do not create a new empty folder** for this path.",
                    imageAsset = "tutorials/sync_photo.jpg",
                ),
                TutorialStep(
                    title = "After the first Sync now",
                    body = "Vehicles and data should appear from the shared sheet. Only then, if a vehicle is missing " +
                        "a local dash photo, open Manage Vehicles and capture or fetch the reference image. " +
                        "You do not need to re-type the whole fleet from scratch.",
                    imageAsset = "tutorials/sync_hub.jpg",
                ),
            ),
        ),
    )
}
