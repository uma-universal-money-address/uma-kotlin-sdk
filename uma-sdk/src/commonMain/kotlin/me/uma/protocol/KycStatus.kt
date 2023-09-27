package me.uma.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.uma.utils.EnumSerializer

@Serializable(with = KycStatusSerializer::class)
enum class KycStatus(val rawValue: String) {

    UNKNOWN("UNKNOWN"),

    NOT_VERIFIED("NOT_VERIFIED"),

    PENDING("PENDING"),

    VERIFIED("VERIFIED"),
    ;
    fun toJson() = Json.encodeToString(this)
}

object KycStatusSerializer :
    EnumSerializer<KycStatus>(
        KycStatus::class,
        { rawValue ->
            KycStatus.entries.firstOrNull { it.rawValue == rawValue } ?: KycStatus.UNKNOWN
        },
    )
