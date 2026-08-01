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
            title = "Set up sync",
            endRoute = "syncing",
            endCtaLabel = "Go to Syncing",
            steps = listOf(
                TutorialStep(
                    title = "Open Syncing",
                    body = "From the menu (☰), open Syncing. You use your own Google / Microsoft / other account — " +
                        "this app does not provide a private cloud for you.",
                    imageAsset = "tutorials/drawer.jpg",
                ),
                TutorialStep(
                    title = "Spreadsheet and/or photos",
                    body = "Add a Spreadsheet destination (e.g. Google Sheets URL or browse) and/or a Photo backup " +
                        "(Drive folder or other provider). You can start with one and add the other later.",
                    imageAsset = "tutorials/sync_hub.jpg",
                ),
                TutorialStep(
                    title = "Configure spreadsheet",
                    body = "Pick a provider, sign in if needed, paste a sheet URL or browse Drive, then Test " +
                        "connection. Network is required only when you actually configure or sync.",
                    imageAsset = "tutorials/sync_sheet.jpg",
                ),
                TutorialStep(
                    title = "Configure photo backup",
                    body = "Choose Google Drive, OneDrive, S3, or Other. Point at a folder, Test connection, " +
                        "then Sync now when ready. Vehicle reference images download automatically; fill photos " +
                        "can be fetched on demand.",
                    imageAsset = "tutorials/sync_photo.jpg",
                ),
                TutorialStep(
                    title = "Join an existing setup",
                    body = "If another device already has vehicles and data, the same spreadsheet + photo dests " +
                        "will pull definitions after Sync now. Then create or match vehicles as needed.",
                    imageAsset = "tutorials/sync_hub.jpg",
                ),
            ),
        ),
    )
}
