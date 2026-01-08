package me.uma.protocol

import kotlinx.serialization.Serializable

/**
 * Represents a specific asset available on a settlement layer.
 *
 * @property identifier Asset identifier (e.g., "BTC" for Lightning, bech32m token ID for Spark).
 * @property multipliers A map from currency code to the exchange rate in the smallest unit of the asset
 *     per the smallest unit of the currency. For example, for Lightning (BTC), the multiplier for USD
 *     would be the number of millisatoshis per cent.
 */
@Serializable
data class SettlementAsset(
    val identifier: String,
    val multipliers: Map<String, Double>,
)

/**
 * Represents a complete settlement configuration available for payments.
 *
 * @property settlementLayer Layer identifier (e.g., "ln" for Lightning, "spark" for Spark).
 * @property assets List of available assets on this settlement layer.
 */
@Serializable
data class SettlementOption(
    val settlementLayer: String,
    val assets: List<SettlementAsset>,
)

/**
 * Represents the sender's chosen settlement method in a [PayRequest].
 *
 * @property layer Selected settlement layer (e.g., "ln" for Lightning, "spark" for Spark).
 * @property assetIdentifier Selected asset identifier on that layer (e.g., "BTC" for Lightning).
 */
@Serializable
data class SettlementInfo(
    val layer: String,
    val assetIdentifier: String,
)
