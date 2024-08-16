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
fun Int.lengthOffset() = this + 1

fun Int.valueOffset() = this + 2

fun ByteBuffer.putString(tag: Int, value: String): ByteBuffer {
    return put(tag.toByte())
        .put(value.length.toByte())
        .put(value.toByteArray())
}

fun ByteBuffer.putNumber(tag: Int, value: Number): ByteBuffer {
    put(tag.toByte()) // insert tag
    return when (value) {
        is Int -> {
            when (value) {
                in Byte.MIN_VALUE.toInt()..Byte.MAX_VALUE.toInt() -> {
                    put(Byte.SIZE_BYTES.toByte()).put(value.toByte())
                }
                in Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt() -> {
                    put(Short.SIZE_BYTES.toByte()).putShort(value.toShort())
                }
                else -> put(Int.SIZE_BYTES.toByte()).putInt(value)
            }
        }
        is Short -> {
            when (value) {
                in Byte.MIN_VALUE..Byte.MAX_VALUE -> {
                    put(Byte.SIZE_BYTES.toByte()).put(value.toByte())
                }
                else -> put(Short.SIZE_BYTES.toByte()).putShort(value)
            }
        }
        is Byte -> put(Byte.SIZE_BYTES.toByte()).put(value.toByte())
        is Float -> put(Float.SIZE_BYTES.toByte()).putFloat(value)
        is Double -> put(Double.SIZE_BYTES.toByte()).putDouble(value)
        is Long -> put(Long.SIZE_BYTES.toByte()).putLong(value)
        else -> throw IllegalArgumentException("Unsupported type: ${value::class.simpleName}")
    }
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


fun ByteArray.getNumber(offset: Int, length: Int): Int {
    val buffer = ByteBuffer.wrap(slice(offset..<offset + length).toByteArray())
    return when (length) {
        1 -> this[offset].toInt()
        2 -> buffer.getShort().toInt()
        4 -> buffer.getInt()
        else -> this[offset].toInt()
    }
}

fun ByteArray.getFLoat(offset: Int, length: Int): Float {
    // TODO throw error for wrong sized item
    val buffer = ByteBuffer.wrap(slice(offset..<offset + length).toByteArray())
    return buffer.getFloat()
}

fun ByteArray.getBoolean(offset: Int): Boolean = this[offset].toInt() == 1

fun ByteArray.getString(offset: Int, length: Int): String {
    val decodedResult = String(
        slice(offset..<offset + length).toByteArray(),
    )
    return decodedResult
}

fun ByteArray.getTLV(
    offset: Int, length: Int, tlvDecode: (ByteArray) -> TLVCodeable,
): TLVCodeable {
    return tlvDecode(slice(offset..<offset + length).toByteArray())
}

fun ByteArray.getByteCodeable(
    offset: Int, length: Int, byteDecode: (ByteArray) -> ByteCodeable,
): ByteCodeable {
    return byteDecode(slice(offset..<offset + length).toByteArray())
}
