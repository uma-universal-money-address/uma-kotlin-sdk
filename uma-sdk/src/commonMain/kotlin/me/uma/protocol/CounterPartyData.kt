@file:JvmName("CounterPartyData")

package me.uma.protocol

import kotlinx.serialization.Serializable

@Serializable
data class CounterPartyDataOption(
    val mandatory: Boolean,
)

typealias CounterPartyDataOptions = Map<String, CounterPartyDataOption>

fun createCounterPartyDataOptions(map: Map<String, Boolean>): CounterPartyDataOptions {
    return map.mapValues { CounterPartyDataOption(it.value) }
}

fun createCounterPartyDataOptions(vararg pairs: Pair<String, Boolean>): CounterPartyDataOptions {
    return createCounterPartyDataOptions(pairs.toMap())
}

/**
 * Common keys used in counterparty data exchanges between VASPs.
 */
object CounterPartyDataKeys {
    /** The UMA address of the counterparty */
    const val IDENTIFIER = "identifier"

    /** The full name of the counterparty */
    const val NAME = "name"

    /** The email address of the counterparty */
    const val EMAIL = "email"

    /** Compliance-related data including KYC status, UTXOs, and travel rule information */
    const val COMPLIANCE = "compliance"

    /** The counterparty's date of birth, in ISO 8601 format */
    const val BIRTH_DATE = "birthDate"

    /** The counterparty's nationality, in ISO 3166-1 alpha-2 format */
    const val NATIONALITY = "nationality"

    /** The counterparty's country of residence, in ISO 3166-1 alpha-2 format */
    const val COUNTRY_OF_RESIDENCE = "countryOfResidence"

    /** The counterparty's phone number, in E.164 format */
    const val PHONE_NUMBER = "phoneNumber"
}
