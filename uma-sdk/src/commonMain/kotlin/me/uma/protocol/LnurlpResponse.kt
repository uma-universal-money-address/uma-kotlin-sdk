package me.uma.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Response from VASP2 to the [LnurlpRequest].
 *
 * @property callback The URL which should be used by VASP1 for the [PayRequest].
 * @property minSendable The minimum amount that the receiver can receive in millisatoshis.
 * @property maxSendable The maximum amount that the receiver can receive in millisatoshis.
 * @property metadata Encoded metadata that can be used to verify the invoice later. See the
 *     [LUD-06 Spec](https://github.com/lnurl/luds/blob/luds/06.md).
 * @property currencies The list of currencies that the receiver accepts.
 * @property requiredPayerData The data that the sender must send to the receiver to identify themselves.
 * @property compliance The compliance data from the receiver, including TR status, kyc info, etc.
 * @property umaVersion The version of the UMA protocol that VASP2 has chosen for this transaction based on its own
 *     support and VASP1's specified preference in the LnurlpRequest. For the version negotiation flow, see
 * 	   https://static.swimlanes.io/87f5d188e080cb8e0494e46f80f2ae74.png
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LnurlpResponse(
    val callback: String,
    val minSendable: Long,
    val maxSendable: Long,
    val metadata: String,
    val currencies: List<Currency>,
    @SerialName("payerData")
    val requiredPayerData: CounterPartyDataOptions,
    val compliance: LnurlComplianceResponse,
    val umaVersion: String,
    @EncodeDefault
    val tag: String = "payRequest",
) {
    fun toJson() = Json.encodeToString(this)
}
