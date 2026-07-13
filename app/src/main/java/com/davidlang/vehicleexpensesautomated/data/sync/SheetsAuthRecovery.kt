package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Intent
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException

object SheetsAuthRecovery {

    const val NEED_REMOTE_CONSENT_MESSAGE = "Approve Sheets access on the next screen"

    fun extractRecoveryIntent(throwable: Throwable): Intent? {
        var current: Throwable? = throwable
        while (current != null) {
            when (current) {
                is UserRecoverableAuthIOException -> return current.intent
                is UserRecoverableAuthException -> return current.intent
            }
            current = current.cause
        }
        return null
    }

    fun wrapIfRecoverable(throwable: Throwable): Throwable {
        val intent = extractRecoveryIntent(throwable) ?: return throwable
        return SheetsRecoverableAuthException(intent)
    }

    fun userMessage(throwable: Throwable): String = when (throwable) {
        is SheetsRecoverableAuthException -> throwable.message ?: NEED_REMOTE_CONSENT_MESSAGE
        else -> {
            val intent = extractRecoveryIntent(throwable)
            if (intent != null) NEED_REMOTE_CONSENT_MESSAGE
            else throwable.message ?: "Sheets operation failed"
        }
    }

    fun needsRemoteConsent(throwable: Throwable): Boolean =
        throwable is SheetsRecoverableAuthException || extractRecoveryIntent(throwable) != null
}

/** Thrown when Google Sheets needs in-app consent; launch [recoveryIntent] then retry once. */
class SheetsRecoverableAuthException(
    val recoveryIntent: Intent,
    override val message: String = SheetsAuthRecovery.NEED_REMOTE_CONSENT_MESSAGE,
) : Exception(message)