package com.davidlang.vehicleexpensesautomated.data.sync

data class TabularOtherKind(
    val id: String,
    val label: String,
)

data class TabularOtherProviderInfo(
    val provider: SpreadsheetProvider,
    val label: String,
    val kindId: String,
    val implemented: Boolean,
    val docsStem: String,
)

/** Kind-grouped catalog for spreadsheet Other destination picker (mirrors photo Other pattern). */
object TabularOtherProviderCatalog {

    val KIND_GROUPS: List<TabularOtherKind> = listOf(
        TabularOtherKind("row_db", "Row databases"),
        TabularOtherKind("app_backends", "App backends"),
        TabularOtherKind("collaborative", "Collaborative"),
        TabularOtherKind("experimental", "Experimental"),
    )

    val PROVIDERS: List<TabularOtherProviderInfo> = listOf(
        TabularOtherProviderInfo(SpreadsheetProvider.BASEROW, "Baserow", "row_db", true, "baserow"),
        TabularOtherProviderInfo(SpreadsheetProvider.NOCODB, "NocoDB", "row_db", true, "nocodb"),
        TabularOtherProviderInfo(SpreadsheetProvider.AIRTABLE, "Airtable", "row_db", true, "airtable"),
        TabularOtherProviderInfo(SpreadsheetProvider.POCKETBASE, "PocketBase", "app_backends", true, "pocketbase"),
        TabularOtherProviderInfo(SpreadsheetProvider.SUPABASE, "Supabase", "app_backends", true, "supabase-selfhost"),
        TabularOtherProviderInfo(SpreadsheetProvider.FIREBASE, "Firebase", "app_backends", true, "firebase"),
        TabularOtherProviderInfo(SpreadsheetProvider.ZOHO_SHEET, "Zoho Sheet", "collaborative", true, "zoho-sheet"),
        TabularOtherProviderInfo(SpreadsheetProvider.ONLYOFFICE, "OnlyOffice", "collaborative", false, "onlyoffice"),
        TabularOtherProviderInfo(SpreadsheetProvider.COLLABORA, "Collabora", "collaborative", false, "collabora"),
    )

    fun providersForKind(kindId: String): List<TabularOtherProviderInfo> =
        PROVIDERS.filter { it.kindId == kindId }

    fun infoFor(provider: SpreadsheetProvider): TabularOtherProviderInfo? =
        PROVIDERS.find { it.provider == provider }

    fun newDestIdFor(provider: SpreadsheetProvider): String = when (provider) {
        SpreadsheetProvider.BASEROW -> "new:baserow"
        SpreadsheetProvider.NOCODB -> "new:nocodb"
        SpreadsheetProvider.POCKETBASE -> "new:pocketbase"
        SpreadsheetProvider.SUPABASE -> "new:supabase"
        SpreadsheetProvider.AIRTABLE -> "new:airtable"
        SpreadsheetProvider.FIREBASE -> "new:firebase"
        SpreadsheetProvider.ZOHO_SHEET -> "new:zoho_sheet"
        SpreadsheetProvider.ONLYOFFICE -> "new:onlyoffice"
        SpreadsheetProvider.COLLABORA -> "new:collabora"
        SpreadsheetProvider.OTHER -> "new:other"
        else -> "new:sheets"
    }

    fun providerFromNewDestId(destId: String): SpreadsheetProvider? = when (destId) {
        "new:baserow" -> SpreadsheetProvider.BASEROW
        "new:nocodb" -> SpreadsheetProvider.NOCODB
        "new:pocketbase" -> SpreadsheetProvider.POCKETBASE
        "new:supabase" -> SpreadsheetProvider.SUPABASE
        "new:airtable" -> SpreadsheetProvider.AIRTABLE
        "new:firebase" -> SpreadsheetProvider.FIREBASE
        "new:zoho_sheet" -> SpreadsheetProvider.ZOHO_SHEET
        "new:onlyoffice" -> SpreadsheetProvider.ONLYOFFICE
        "new:collabora" -> SpreadsheetProvider.COLLABORA
        "new:other" -> SpreadsheetProvider.OTHER
        else -> null
    }

    fun isRowDbProvider(provider: SpreadsheetProvider): Boolean = when (provider) {
        SpreadsheetProvider.BASEROW,
        SpreadsheetProvider.NOCODB,
        SpreadsheetProvider.POCKETBASE,
        SpreadsheetProvider.SUPABASE,
        SpreadsheetProvider.AIRTABLE,
        SpreadsheetProvider.FIREBASE,
        -> true
        else -> false
    }
}