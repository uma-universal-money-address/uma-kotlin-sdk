package me.uma.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Currency(
    /**
     * The currency code, eg. "USD".
     */
    val code: String,

    /**
     * The full currency name, eg. "US Dollars".
     */
    val name: String,

    /**
     * The symbol of the currency, eg. "$".
     */
    val symbol: String,

    /**
     * Estimated millisats per smallest "unit" of this currency (eg. 1 cent in USD).
     */
    @SerialName("multiplier")
    val millisatoshiPerUnit: Double,

    /**
     * The minimum and maximum amounts that can be sent in this currency and converted from SATs by the receiver.
     */
    val convertible: CurrencyConvertible,

    /**
     * The number of digits after the decimal point for display on the sender side, and to add clarity
     * around what the "smallest unit" of the currency is. For example, in USD, by convention, there are 2 digits for
     * cents - $5.95. In this case, `decimals` would be 2. Note that the multiplier is still always in the smallest
     * unit (cents). In addition to display purposes, this field can be used to resolve ambiguity in what the multiplier
     * means. For example, if the currency is "BTC" and the multiplier is 1000, really we're exchanging in SATs, so
     * `decimals` would be 8.
     *
     * For details on edge cases and examples, see https://github.com/uma-universal-money-address/protocol/blob/main/umad-04-lnurlp-response.md.
     */
    val decimals: Int,
) {
    fun toJson() = Json.encodeToString(this)
}

/**
 * The `convertible` field of the [Currency] object.
 */
@Serializable
data class CurrencyConvertible(
    /**
     * Minimum amount that can be sent in this currency. This is in the smallest unit of the currency
     * (eg. cents for USD).
     */
    val min: Long,
    /**
     * Maximum amount that can be sent in this currency. This is in the smallest unit of the currency
     * (eg. cents for USD).
     */
    val max: Long,
)
