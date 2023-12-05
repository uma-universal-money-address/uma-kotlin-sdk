package me.uma.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Currency @JvmOverloads constructor(
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
    val millisatoshiPerUnit: Long,

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

    /**
     * Number of digits after the decimal point for display on the sender side. For example,
     * in USD, by convention, there are 2 digits for cents - $5.95. in this case, `decimals`
     * would be 2. Note that the multiplier is still always in the smallest unit (cents). This field
     * is only for display purposes. The sender should assume zero if this field is omitted, unless
     * they know the proper display format of the target currency.
     */
    val decimals: Int? = null,
) {
    fun toJson() = Json.encodeToString(this)
}
