package me.uma.protocol

import me.uma.utils.ByteCodeable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import me.uma.utils.EnumSerializer
import me.uma.utils.serialFormat
import okio.Utf8

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

data class KycStatusWrapper(val status: KycStatus): ByteCodeable {
    override fun toBytes(): ByteArray {
        return status.rawValue.toByteArray()
    }

    companion object {
        fun fromBytes(bytes: ByteArray): KycStatusWrapper {
            return KycStatusWrapper(
                KycStatus.fromRawValue(bytes.toString(Charsets.UTF_8))
            )
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
