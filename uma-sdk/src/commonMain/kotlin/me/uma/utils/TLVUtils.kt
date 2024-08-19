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

fun MutableList<ByteArray>.putString(tag: Int, value: String?): MutableList<ByteArray> {
    value?.let {
        val byteStr = value.toByteArray(Charsets.UTF_8)
        add(
            ByteBuffer.allocate(2 + byteStr.size)
                .put(tag.toByte())
                .put(byteStr.size.toByte())
                .put(byteStr)
                .array()
        )
    }
    return this
}

fun MutableList<ByteArray>.putNumber(tag: Int, value: Number?): MutableList<ByteArray> {
   if (value == null) return this
    val tlvBuffer = { numberSize: Int ->
        ByteBuffer
            .allocate(2 + numberSize)
            .put(tag.toByte())
            .put(numberSize.toByte())
    }
    add(
        when (value) {
            is Int -> {
                when (value) {
                    in Byte.MIN_VALUE.toInt()..Byte.MAX_VALUE.toInt() -> {
                        tlvBuffer(Byte.SIZE_BYTES).put(value.toByte())
                    }
                    in Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt() -> {
                        tlvBuffer(Short.SIZE_BYTES).putShort(value.toShort())
                    }
                    else -> {
                        tlvBuffer(Int.SIZE_BYTES).putInt(value)
                    }
                }
            }
            is Short -> {
                when (value) {
                    in Byte.MIN_VALUE..Byte.MAX_VALUE -> {
                        tlvBuffer(Byte.SIZE_BYTES).put(value.toByte())
                    }
                    else -> tlvBuffer(Short.SIZE_BYTES).putShort(value.toShort())
                }
            }
            is Byte -> tlvBuffer(Byte.SIZE_BYTES).put(value.toByte())
            is Float -> tlvBuffer(Float.SIZE_BYTES).putFloat(value)
            is Double -> tlvBuffer(Double.SIZE_BYTES).putDouble(value)
            is Long -> tlvBuffer(Long.SIZE_BYTES).putLong(value)
            else -> throw IllegalArgumentException("Unsupported type: ${value::class.simpleName}")
        }.array()
    )
    return this
}

fun MutableList<ByteArray>.putBoolean(tag: Int, value: Boolean): MutableList<ByteArray> {
    add(
        ByteBuffer.allocate(2 + 1)
            .put(tag.toByte())
            .put(1)
            .put(if(value) 1 else 0)
            .array()
    )
    return this
}

fun MutableList<ByteArray>.putByteArray(tag: Int, value: ByteArray?): MutableList<ByteArray> {
    value?.let {
        add(
            ByteBuffer.allocate(2 + value.size)
                .put(tag.toByte())
                .put(value.size.toByte())
                .put(value)
                .array()
        )
    }
    return this
}

fun MutableList<ByteArray>.putByteCodeable(tag: Int, value: ByteCodeable?): MutableList<ByteArray> {
    value?.let {
        val encodedBytes = it.toBytes()
        add(
            ByteBuffer.allocate(2 + encodedBytes.size)
                .put(tag.toByte())
                .put(encodedBytes.size.toByte())
                .put(encodedBytes)
                .array()
        )
    }
    return this
}

fun MutableList<ByteArray>.putTLVCodeable(tag: Int, value: TLVCodeable): MutableList<ByteArray> {
    val encodedBytes = value.toTLV()
    add(
        ByteBuffer.allocate(2 + encodedBytes.size)
            .put(tag.toByte())
            .put(encodedBytes.size.toByte())
            .put(encodedBytes)
            .array()
    )
    return this
}

fun MutableList<ByteArray>.array(): ByteArray {
    val buffer = ByteBuffer.allocate(sumOf { it.size })
    forEach(buffer::put)
    return buffer.array()
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
