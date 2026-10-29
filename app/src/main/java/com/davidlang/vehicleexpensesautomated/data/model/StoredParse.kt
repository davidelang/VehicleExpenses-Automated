package com.davidlang.vehicleexpensesautomated.data.model

/** Valid value, blank (today's meaning), or corrupt stored text that must not be rewritten. */
sealed class StoredParse<out T> {
    data class Valid<T>(val value: T) : StoredParse<T>()
    class Blank<T> : StoredParse<T>()
    data class Corrupt<T>(val raw: String) : StoredParse<T>()
}
