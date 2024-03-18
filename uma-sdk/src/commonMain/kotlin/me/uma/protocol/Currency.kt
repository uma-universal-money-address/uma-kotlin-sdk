package me.uma.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

sealed interface Currency {
    /**
     * The currency code, eg. "USD".
     */
    val code: String

    /**
     * The full currency name, eg. "US Dollars".
     */
    val name: String

    /**
     * The symbol of the currency, eg. "$".
     */
    val symbol: String

    /**
     * Estimated millisats per smallest "unit" of this currency (eg. 1 cent in USD).
     */
    val millisatoshiPerUnit: Double

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
    val decimals: Int
}


@Serializable
data class CurrencyV1(
    override val code: String,
    override val name: String,
    override val symbol: String,
    @SerialName("multiplier")
    override val millisatoshiPerUnit: Double,
    /**
     * The minimum and maximum amounts that can be sent in this currency and converted from SATs by the receiver.
     */
    val convertible: CurrencyConvertible,
    override val decimals: Int,
) : Currency

@Serializable
data class CurrencyV0(
    override val code: String,
    override val name: String,
    override val symbol: String,
    @SerialName("multiplier")
    override val millisatoshiPerUnit: Double,
    /**
     * Minimum amount that can be sent in this currency. This is in the smallest unit of the currency
     * (eg. cents for USD).
     */
    val minSendable: Long,

    /**
     * Maximum amount that can be sent in this currency. This is in the smallest unit of the currency
     * (eg. cents for USD).
     */
    val maxSendable: Long,
    override val decimals: Int,
) : Currency

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

object CurrencySerializer : JsonContentPolymorphicSerializer<Currency>(Currency::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        "minSendable" in element.jsonObject -> CurrencyV0.serializer()
        else -> CurrencyV1.serializer()
    }
}
