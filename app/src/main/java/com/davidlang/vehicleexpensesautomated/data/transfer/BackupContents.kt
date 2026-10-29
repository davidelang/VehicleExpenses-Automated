package com.davidlang.vehicleexpensesautomated.data.transfer

import android.util.Xml
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleDriveAuth
import com.davidlang.vehicleexpensesautomated.data.sync.GoogleSheetsAuth
import com.davidlang.vehicleexpensesautomated.data.sync.MicrosoftOneDriveAuth
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Include and exclude rules for one backup. Everything under the four roots is included
 * unless it matches an exclusion. New data is kept by default.
 */
object BackupContents {
    const val FORMAT = "davidlang-android-app-backup"
    const val FORMAT_VERSION = 1
    const val APP_KEY = "vehicleexpenses"
    const val DB_FILE_NAME = "vehicle_expenses.db"
    const val SETTINGS_LEAF = "vehicle_settings.xml"
    const val MANIFEST_ENTRY = "backup-manifest.json"

    const val ROOT_FILES = "files"
    const val ROOT_DATABASES = "databases"
    const val ROOT_PREFS = "shared_prefs"
    const val ROOT_EXTERNAL = "external"

    val ROOTS = listOf(ROOT_FILES, ROOT_DATABASES, ROOT_PREFS, ROOT_EXTERNAL)

    val EXCLUDED_LABELS = listOf(
        "cache",
        "models",
        "oauth-tokens",
        "keystore-secrets",
        "workmanager",
        "debug-outputs",
    )

    /**
     * Zoho [com.davidlang.vehicleexpensesautomated.data.sync.tabular.internal.ZohoSheetConfig]
     * token fields inside a sync-destination `configJson`.
     */
    val CONFIG_JSON_TOKEN_KEYS = listOf("accessToken", "refreshToken")

    /**
     * Keys those classes write into `vehicle_settings`.
     * [MicrosoftOneDriveAuth.PREFS_ONEDRIVE_ACCOUNT] is an account email, not a token.
     * [GoogleLegacySignIn.persistEmail] has no fixed key. Its callers store account emails at
     * [GoogleDriveAuth.PREFS_DRIVE_ACCOUNT] and [GoogleSheetsAuth.PREFS_SHEETS_ACCOUNT].
     * None of those three values are tokens, so export does not blank them.
     * Token values that are blanked are [CONFIG_JSON_TOKEN_KEYS] inside `configJson`.
     */
    val ENUMERATED_PREF_KEYS = listOf(
        MicrosoftOneDriveAuth.PREFS_ONEDRIVE_ACCOUNT,
        GoogleDriveAuth.PREFS_DRIVE_ACCOUNT,
        GoogleSheetsAuth.PREFS_SHEETS_ACCOUNT,
    )

    private val DEBUG_DIR_NAMES = setOf(
        "heatmap_stage",
        "preprocess_stage",
        "pump_so_debug",
        "multi_scale_det_reports",
        "pump_reports",
        "experiment_reports",
    )

    fun isExcluded(tarPath: String, rawFiles: Boolean): Boolean {
        val segments = tarPath.split('/')
        if (segments.any { it.startsWith("app_") }) return true
        if (segments.any { it in DEBUG_DIR_NAMES }) return true
        val leaf = segments.last()
        if (leaf.endsWith("-shm")) return true
        if (tarPath.startsWith("$ROOT_FILES/rclone/") || tarPath == "$ROOT_FILES/rclone") return true
        if (tarPath.startsWith("$ROOT_FILES/exp_det_") && tarPath.endsWith(".nb") && segments.size == 2) {
            return true
        }
        if (tarPath.startsWith("$ROOT_DATABASES/androidx.work.workdb")) return true
        if (tarPath == "$ROOT_PREFS/email_receipt_imap_secrets.xml") return true
        if (leaf.startsWith("__androidx_security_crypto_encrypted_prefs__")) return true
        if (leaf.startsWith("com.microsoft.identity")) return true
        if (!rawFiles && tarPath.startsWith("$ROOT_DATABASES/")) {
            if (leaf.endsWith("-wal") || leaf.endsWith("-journal") || leaf == DB_FILE_NAME) return true
        }
        if (rawFiles && tarPath == "$ROOT_DATABASES/$DB_FILE_NAME-shm") return true
        return false
    }


    fun carryOver(liveRelative: String): Boolean {
        val tarPath = liveRelative.trimStart('/')
        if (tarPath.startsWith("$ROOT_FILES/exp_det_") && tarPath.endsWith(".nb")) return true
        if (tarPath.startsWith("$ROOT_FILES/rclone/") || tarPath == "$ROOT_FILES/rclone") return true
        if (tarPath == "$ROOT_PREFS/email_receipt_imap_secrets.xml") return true
        val leaf = tarPath.substringAfterLast('/')
        if (leaf.startsWith("__androidx_security_crypto_encrypted_prefs__")) return true
        if (leaf.startsWith("com.microsoft.identity")) return true
        if (tarPath.startsWith("$ROOT_DATABASES/androidx.work.workdb")) return true
        return false
    }

    fun rootOf(tarPath: String): String? {
        val root = tarPath.substringBefore('/', missingDelimiterValue = "")
        return if (root in ROOTS && tarPath.contains('/')) root else null
    }

    /**
     * Blank Zoho token values inside sync-destination `configJson`. Keys stay, values become "".
     * A settings file or a `configJson` value that does not parse fails the export.
     */
    fun stripVehicleSettings(xmlBytes: ByteArray): ByteArray {
        val parser = Xml.newPullParser()
        try {
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
            val entries = mutableListOf<SettingsEntry>()
            var event = parser.eventType
            var seenMap = false
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "map" -> seenMap = true
                        "string" -> {
                            val name = parser.getAttributeValue(null, "name")
                                ?: throw IllegalArgumentException("vehicle_settings.xml doesn't parse: string without name")
                            val value = readText(parser)
                            entries.add(SettingsEntry.Str(name, transformStringPref(name, value)))
                        }
                        "boolean", "int", "long", "float" -> {
                            val name = parser.getAttributeValue(null, "name")
                                ?: throw IllegalArgumentException("vehicle_settings.xml doesn't parse")
                            val value = parser.getAttributeValue(null, "value")
                                ?: throw IllegalArgumentException("vehicle_settings.xml doesn't parse")
                            entries.add(SettingsEntry.Typed(parser.name, name, value))
                        }
                        "set" -> {
                            val name = parser.getAttributeValue(null, "name")
                                ?: throw IllegalArgumentException("vehicle_settings.xml doesn't parse")
                            entries.add(SettingsEntry.StringSet(name, readSet(parser)))
                        }
                    }
                }
                event = parser.next()
            }
            if (!seenMap) {
                throw IllegalArgumentException("vehicle_settings.xml doesn't parse")
            }
            return writeSettings(entries)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("vehicle_settings.xml doesn't parse: ${e.message}", e)
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun transformStringPref(name: String, value: String): String {
        if (name != SyncDestinationStore.KEY_SYNC_DESTINATIONS_JSON) return value
        val root = try {
            JSONObject(value)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "vehicle_settings.xml sync destinations don't parse: ${e.message}",
                e,
            )
        }
        stripDestinationArray(root.optJSONArray("spreadsheet"))
        stripDestinationArray(root.optJSONArray("photo"))
        return root.toString()
    }

    private fun stripDestinationArray(array: JSONArray?) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (!obj.has("configJson")) continue
            val raw = obj.optString("configJson", "")
            if (raw.isBlank()) continue
            val parsed = try {
                JSONObject(raw)
            } catch (e: Exception) {
                throw IllegalArgumentException("configJson doesn't parse: ${e.message}", e)
            }
            for (key in CONFIG_JSON_TOKEN_KEYS) {
                if (parsed.has(key)) parsed.put(key, "")
            }
            obj.put("configJson", parsed.toString())
        }
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.TEXT) sb.append(parser.text)
        }
        return sb.toString()
    }

    private fun readSet(parser: XmlPullParser): List<String> {
        val values = mutableListOf<String>()
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_TAG && parser.name == "set") break
            if (event == XmlPullParser.START_TAG && parser.name == "string") {
                values.add(readText(parser))
            }
        }
        return values
    }

    private fun writeSettings(entries: List<SettingsEntry>): ByteArray {
        val xml = ByteArrayOutputStream()
        val serializer = Xml.newSerializer()
        serializer.setOutput(xml, "UTF-8")
        serializer.startDocument("UTF-8", true)
        serializer.text("\n")
        serializer.startTag(null, "map")
        serializer.text("\n")
        for (entry in entries) {
            when (entry) {
                is SettingsEntry.Str -> {
                    serializer.startTag(null, "string")
                    serializer.attribute(null, "name", entry.name)
                    serializer.text(entry.value)
                    serializer.endTag(null, "string")
                }
                is SettingsEntry.Typed -> {
                    serializer.startTag(null, entry.tag)
                    serializer.attribute(null, "name", entry.name)
                    serializer.attribute(null, "value", entry.value)
                    serializer.endTag(null, entry.tag)
                }
                is SettingsEntry.StringSet -> {
                    serializer.startTag(null, "set")
                    serializer.attribute(null, "name", entry.name)
                    entry.values.forEach { value ->
                        serializer.startTag(null, "string")
                        serializer.text(value)
                        serializer.endTag(null, "string")
                    }
                    serializer.endTag(null, "set")
                }
            }
            serializer.text("\n")
        }
        serializer.endTag(null, "map")
        serializer.endDocument()
        return xml.toByteArray()
    }

    private sealed interface SettingsEntry {
        data class Str(val name: String, val value: String) : SettingsEntry
        data class Typed(val tag: String, val name: String, val value: String) : SettingsEntry
        data class StringSet(val name: String, val values: List<String>) : SettingsEntry
    }
}
