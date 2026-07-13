package com.davidlang.vehicleexpensesautomated.data.sync

import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class DriveBrowserItem(
    val id: String,
    val name: String,
)

@Singleton
class GoogleDriveBrowserClient @Inject constructor(
    private val driveAuth: GoogleDriveAuth,
) {

    companion object {
        private const val MIME_FOLDER = "application/vnd.google-apps.folder"
        private const val MIME_SPREADSHEET = "application/vnd.google-apps.spreadsheet"

        fun parseFolderIdFromUrl(url: String): String? {
            val trimmed = url.trim()
            val regex = Regex("""/drive/folders/([a-zA-Z0-9-_]+)""")
            return regex.find(trimmed)?.groupValues?.get(1)
                ?: Regex("""[?&]id=([a-zA-Z0-9-_]+)""").find(trimmed)?.groupValues?.get(1)
        }

        fun folderUrlFromId(id: String): String =
            "https://drive.google.com/drive/folders/${id.trim()}"
    }

    private fun buildDriveService(accountHint: String?): Drive {
        val account = driveAuth.resolveAccountFromHint(accountHint)
            ?: throw IllegalStateException("No Google account signed in for Drive")
        return driveAuth.buildDriveServiceForAccountName(account.name)
    }

    suspend fun listSpreadsheets(
        accountHint: String? = null,
        searchQuery: String? = null,
    ): List<DriveBrowserItem> = withContext(Dispatchers.IO) {
        try {
            val drive = buildDriveService(accountHint)
            val q = buildString {
                append("mimeType='$MIME_SPREADSHEET' and trashed=false")
                val term = searchQuery?.trim().orEmpty()
                if (term.isNotBlank()) {
                    append(" and name contains '")
                    append(term.replace("'", "\\'"))
                    append("'")
                }
            }
            listFiles(drive, q)
        } catch (e: Exception) {
            throw DriveAuthRecovery.wrapIfRecoverable(e)
        }
    }

    suspend fun listFolders(
        accountHint: String? = null,
        parentId: String? = null,
        searchQuery: String? = null,
    ): List<DriveBrowserItem> = withContext(Dispatchers.IO) {
        try {
            val drive = buildDriveService(accountHint)
            val q = buildString {
                append("mimeType='$MIME_FOLDER' and trashed=false")
                if (!parentId.isNullOrBlank()) {
                    append(" and '")
                    append(parentId.trim())
                    append("' in parents")
                }
                val term = searchQuery?.trim().orEmpty()
                if (term.isNotBlank()) {
                    append(" and name contains '")
                    append(term.replace("'", "\\'"))
                    append("'")
                }
            }
            listFiles(drive, q)
        } catch (e: Exception) {
            throw DriveAuthRecovery.wrapIfRecoverable(e)
        }
    }

    suspend fun createFolder(
        name: String,
        accountHint: String? = null,
        parentId: String? = null,
    ): DriveBrowserItem = withContext(Dispatchers.IO) {
        try {
            val drive = buildDriveService(accountHint)
            val folder = com.google.api.services.drive.model.File().apply {
                this.name = name.trim()
                mimeType = MIME_FOLDER
                if (!parentId.isNullOrBlank()) {
                    parents = listOf(parentId.trim())
                }
            }
            val created = drive.files().create(folder).setFields("id,name").execute()
            DriveBrowserItem(id = created.id, name = created.name ?: name.trim())
        } catch (e: Exception) {
            throw DriveAuthRecovery.wrapIfRecoverable(e)
        }
    }

    suspend fun getFile(
        fileId: String,
        accountHint: String? = null,
    ): DriveBrowserItem = withContext(Dispatchers.IO) {
        try {
            val drive = buildDriveService(accountHint)
            val file = drive.files().get(fileId).setFields("id,name").execute()
            DriveBrowserItem(id = file.id, name = file.name ?: fileId)
        } catch (e: Exception) {
            throw DriveAuthRecovery.wrapIfRecoverable(e)
        }
    }

    private fun listFiles(drive: Drive, query: String): List<DriveBrowserItem> {
        val items = mutableListOf<DriveBrowserItem>()
        var pageToken: String? = null
        do {
            val result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("nextPageToken, files(id, name)")
                .setPageSize(100)
                .setOrderBy("modifiedTime desc")
                .apply { pageToken?.let { setPageToken(it) } }
                .execute()
            result.files?.forEach { file ->
                if (!file.id.isNullOrBlank()) {
                    items.add(DriveBrowserItem(id = file.id, name = file.name ?: file.id))
                }
            }
            pageToken = result.nextPageToken
        } while (pageToken != null)
        return items
    }
}