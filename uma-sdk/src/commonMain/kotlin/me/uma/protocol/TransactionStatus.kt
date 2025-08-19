package me.uma.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class TransactionStatus {
    /**
     * Recipient received the payment.
     */
    COMPLETED,

    /**
     * Payment failed due to a post-transaction error.
     */
    FAILED,
}
