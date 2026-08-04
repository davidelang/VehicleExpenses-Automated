package com.davidlang.vehicleexpensesautomated.data.email

/**
 * Shared DTO for email → fuel intake (Wave 1 / Track B).
 * Matches sandbox `lib/parsed-fuel-receipt.js` contract.
 */
data class ParsedFuelReceipt(
    val cost: Double,
    val gallons: Double,
    val timestampMs: Long,
    val locationText: String,
    val currency: String = "USD",
    val brand: String = "Shell",
    val messageKey: String,
    val timestampLocal: String? = null,
    val siteId: String? = null,
    val pump: String? = null,
    val product: String? = null,
)
