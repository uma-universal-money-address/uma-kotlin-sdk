package me.uma.protocol

import me.uma.utils.serialFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * Post-payment callbacks exchanged between VASPs.
 *
 * @property utxos UTXOs of the VASP that is sending the callback.
 * @property vaspDomain Domain name of the VASP sending the callback. Used to
 *     fetch keys for signature validation.
 * @property signature The signature of the VASP sending the callback on the
 *     [signablePayload].
 * @property signatureNonce The nonce used in the signature.
 * @property signatureTimestamp The timestamp used in the signature.
 * @property transactionStatus The status of the transaction.
 * @property errorCode The error code of the transaction. This should be one
 *     of the values in the [ErrorCode] enum. This should only be set if
 *     [transactionStatus] is [TransactionStatus.FAILED].
 * @property errorReason The reason for the failure. This should only be set if
 *     [transactionStatus] is [TransactionStatus.FAILED].
 */

@Serializable
data class PostTransactionCallback @JvmOverloads constructor(
    val utxos: List<UtxoWithAmount>,
    val vaspDomain: String,
    val signature: String,
    val signatureNonce: String,
    val signatureTimestamp: Long,
    val transactionStatus: TransactionStatus? = null,
    val errorCode: String? = null,
    val errorReason: String? = null,
) {
    fun signablePayload(): ByteArray {
        return "$signatureNonce|$signatureTimestamp".encodeToByteArray()
    }

    fun signedWith(signature: String) = copy(signature = signature)

    fun toJson() = serialFormat.encodeToString(this)
}
