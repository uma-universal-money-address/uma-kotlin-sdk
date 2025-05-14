package me.uma.crypto

/** wrapper class for bech32 conversion functions */
object Bech32 {
    fun encodeBech32(hrp: String, data: ByteArray): String {
        return me.uma.crypto.internal.encodeBech32(hrp, data.toByteList())
    }

    fun decodeBech32(bech32str: String): Bech32Data {
        val data = me.uma.crypto.internal.decodeBech32(bech32str)
        return Bech32Data(data.hrp, data.data.toByteArray())
    }

    private fun ByteArray.toByteList() = map { it.toUByte() }

    private fun List<UByte>.toByteArray() = map { it.toByte() }.toByteArray()

    data class Bech32Data(val hrp: String, val data: ByteArray)
}
