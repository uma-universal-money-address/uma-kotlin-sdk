package me.uma

import kotlin.test.Test
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import me.uma.generated.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals

class UmaExceptionTests {
    @Test
    fun testBaseUmaError() {
        val exception = UmaException("test reason", ErrorCode.INTERNAL_ERROR)
        val json = exception.toJSON()
        val errorMap: Map<String, JsonElement> = Json.decodeFromString(json)
        assertEquals("ERROR", errorMap["status"]?.jsonPrimitive?.content)
        assertEquals("test reason", errorMap["reason"]?.jsonPrimitive?.content)
        assertEquals("INTERNAL_ERROR", errorMap["code"]?.jsonPrimitive?.content)
        assertEquals(500, exception.toHttpStatusCode())
    }

    @Test
    fun testInvalidSignatureError() {
        val exception = UmaException("Bad signature", ErrorCode.INVALID_SIGNATURE)
        val json = exception.toJSON()
        val errorMap: Map<String, JsonElement> = Json.decodeFromString(json)
        assertEquals("ERROR", errorMap["status"]?.jsonPrimitive?.content)
        assertEquals("Bad signature", errorMap["reason"]?.jsonPrimitive?.content)
        assertEquals("INVALID_SIGNATURE", errorMap["code"]?.jsonPrimitive?.content)
        assertEquals(401, exception.toHttpStatusCode())
    }

    @Test
    fun testUnsupportedVersionError() {
        val exception = UnsupportedVersionException("1.2", setOf(0, 1))
        val json = exception.toJSON()
        val errorMap: Map<String, JsonElement> = Json.decodeFromString(json)
        assertEquals("ERROR", errorMap["status"]?.jsonPrimitive?.content)
        assertEquals(
          "Unsupported version: 1.2. Supported major versions: [0, 1]",
          errorMap["reason"]?.jsonPrimitive?.content,
        )
        assertEquals("UNSUPPORTED_UMA_VERSION", errorMap["code"]?.jsonPrimitive?.content)
        assertEquals("[0,1]", errorMap["supportedMajorVersions"]?.jsonPrimitive?.content)
        assertEquals("1.2", errorMap["unsupportedVersion"]?.jsonPrimitive?.content)
        assertEquals(412, exception.toHttpStatusCode())
    }
}
