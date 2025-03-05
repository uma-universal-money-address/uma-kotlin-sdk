package me.uma

import me.uma.generated.ErrorCode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

open class UmaException(
    message: String,
    private val code: ErrorCode,
    cause: Throwable? = null,
) : Exception(message, cause) {
    open fun getAdditionalParams(): Map<String, String> = emptyMap()

    fun toJSON(): String {
        return buildJsonObject {
            put("status", "ERROR")
            put("reason", message)
            put("code", code.name)
            getAdditionalParams().forEach { (key, value) -> put(key, value) }
        }.toString()
    }

    fun toHttpStatusCode(): Int = code.httpStatusCode
}
