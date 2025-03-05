package me.uma

import me.uma.generated.ErrorCode
import me.uma.utils.serialFormat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

const val MAJOR_VERSION = 1
const val MINOR_VERSION = 0
const val UMA_VERSION_STRING = "$MAJOR_VERSION.$MINOR_VERSION"
val backCompatVersions = listOf("0.3")

/**
 * The supported major versions of this UMA SDK.
 *
 * NOTE: In the future, we may want to support multiple major versions in the same SDK, but for now, this keeps
 * things simple.
 */
fun supportedMajorVersions() = setOf(MAJOR_VERSION) + backCompatVersions.map { Version.parse(it).major }

data class Version(val major: Int, val minor: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        return when {
            major > other.major -> 1
            major < other.major -> -1
            minor > other.minor -> 1
            minor < other.minor -> -1
            else -> 0
        }
    }

    override fun toString(): String {
        return "$major.$minor"
    }

    companion object {
        fun parse(versionString: String): Version {
            val parts = versionString.split(".")
            if (parts.size != 2) {
                throw UmaException("Invalid version string: $versionString", ErrorCode.INVALID_INPUT)
            }
            return try {
                Version(parts[0].toInt(), parts[1].toInt())
            } catch (e: NumberFormatException) {
                throw UmaException("Invalid version string: $versionString", ErrorCode.INVALID_INPUT, e)
            }
        }
    }
}

class UnsupportedVersionException(
    val unsupportedVersion: String,
    val supportedMajorVersions: Set<Int> = supportedMajorVersions(),
) : UmaException(
        "Unsupported version: $unsupportedVersion. Supported major versions: $supportedMajorVersions",
        ErrorCode.UNSUPPORTED_UMA_VERSION
    ) {
    fun toLnurlpResponseJson(): String {
        return buildJsonObject {
            put("reason", "Unsupported version: $unsupportedVersion.")
            put("supportedMajorVersions", serialFormat.encodeToJsonElement(supportedMajorVersions))
            put("unsupportedVersion", unsupportedVersion)
        }.toString()
    }

    override fun getAdditionalParams(): Map<String, String> {
        return mapOf(
            "supportedMajorVersions" to serialFormat.encodeToString(supportedMajorVersions),
            "unsupportedVersion" to unsupportedVersion,
        )
    }
}

fun isVersionSupported(versionString: String): Boolean {
    val version =
        try {
            Version.parse(versionString)
        } catch (e: UmaException) {
            return false
        }
    return supportedMajorVersions().contains(version.major)
}

fun selectHighestSupportedVersion(otherVaspSupportedMajorVersions: List<Int>): String? {
    val highestSupportedMajorVersion =
        otherVaspSupportedMajorVersions.filter {
            supportedMajorVersions().contains(it)
        }.maxOrNull() ?: return null
    return getHighestSupportedVersionForMajorVersion(highestSupportedMajorVersion)
}

private fun getHighestSupportedVersionForMajorVersion(majorVersion: Int): String? {
    if (majorVersion == MAJOR_VERSION) return UMA_VERSION_STRING

    return backCompatVersions.find { Version.parse(it).major == majorVersion }
}
