package me.uma.protocol

import kotlinx.serialization.Serializable

@Serializable
data class UtxoWithAmount(
    val utxo: String,
    val amountMsats: Long,
)
