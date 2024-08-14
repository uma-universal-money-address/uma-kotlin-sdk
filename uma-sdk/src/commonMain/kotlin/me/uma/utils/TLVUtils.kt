package me.uma.utils

import java.nio.ByteBuffer

interface TLVCodeable {
    fun toTLV(): ByteArray
}

interface ByteCodeable {
    fun toBytes(): ByteArray
}

/**
 * utilities for accessing offsets of 'value' and 'len' (length) when encoding and decoding
 */
inline fun Int.lengthOffset() = this + 1

inline fun Int.valueOffset() = this + 2

fun ByteBuffer.putString(tag: Int, value: String): ByteBuffer {
    return put(tag.toByte())
        .put(value.length.toByte())
        .put(value.toByteArray())
}

// @TODO handle int16+
fun ByteBuffer.putNumber(tag: Int, value: Int): ByteBuffer {
    return put(tag.toByte())
        .put(1)
        .put(value.toByte())
}

fun ByteBuffer.putBoolean(tag: Int, value: Boolean): ByteBuffer {
    return put(tag.toByte())
        .put(1)
        .put(if (value) 1 else 0)
}

fun ByteBuffer.putByteArray(tag: Int, value: ByteArray): ByteBuffer =
    put(tag.toByte())
        .put(value.size.toByte())
        .put(value)

fun ByteBuffer.putByteCodeable(tag: Int, value: ByteCodeable): ByteBuffer {
    val encodedBytes = value.toBytes()
    return put(tag.toByte())
        .put(encodedBytes.size.toByte())
        .put(encodedBytes)
}

fun ByteBuffer.putTLVCodeable(tag: Int, value: TLVCodeable): ByteBuffer {
    val encodedBytes = value.toTLV()
    return put(tag.toByte())
        .put(encodedBytes.size.toByte())
        .put(encodedBytes)
}


fun ByteArray.getNumber(offset: Int): Int {
    return this[offset].toInt()
}

fun ByteArray.getBoolean(offset: Int): Boolean = this[offset].toInt() == 1

fun ByteArray.getString(offset: Int, length: Int): String {
    val decodedResult = String(
        slice(offset..<offset+length).toByteArray()
    )
    return decodedResult
}

fun ByteArray.getTLV(
    offset: Int, length: Int, tlvDecode: (ByteArray) -> TLVCodeable
): TLVCodeable {
    return tlvDecode(slice(offset..< offset+length).toByteArray())
}

fun ByteArray.getByteCodeable(
    offset: Int, length: Int, byteDecode: (ByteArray) -> ByteCodeable
): ByteCodeable {
    return byteDecode(slice(offset..< offset+length).toByteArray())
}
