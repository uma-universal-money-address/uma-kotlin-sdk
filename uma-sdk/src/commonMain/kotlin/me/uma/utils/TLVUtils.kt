package me.uma.utils

interface TLVCodeable {
    fun toTLV(): ByteArray
}

interface ByteCodeable {
    fun toBytes(): ByteArray
}
