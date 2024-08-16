package me.uma.protocol

import me.uma.utils.ByteCodeable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import me.uma.utils.EnumSerializer
import me.uma.utils.serialFormat

@Serializable(with = KycStatusSerializer::class)
enum class KycStatus(val rawValue: String) {

    UNKNOWN("UNKNOWN"),

    NOT_VERIFIED("NOT_VERIFIED"),

    PENDING("PENDING"),

    VERIFIED("VERIFIED"),
    ;
    fun toJson() = serialFormat.encodeToString(this)

    companion object {
        fun fromRawValue(rawValue: String) = when(rawValue) {
                "UNKNOWN" -> UNKNOWN
                "NOT_VERIFIED" -> NOT_VERIFIED
                "PENDING" -> PENDING
                "VERIFIED" -> VERIFIED
                else -> UNKNOWN
            }
    }
}

object KycStatusSerializer :
    EnumSerializer<KycStatus>(
        KycStatus::class,
        { rawValue ->
            KycStatus.entries.firstOrNull { it.rawValue == rawValue } ?: KycStatus.UNKNOWN
        },
    )
