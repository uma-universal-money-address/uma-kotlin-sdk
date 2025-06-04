@file:OptIn(ExperimentalUnsignedTypes::class)

package me.uma.crypto

object Secp256k1 {
    fun signEcdsa(message: ByteArray, privateKey: ByteArray): ByteArray {
        return me.uma.crypto.internal
          .signEcdsa(message.toUByteArray().toList(), privateKey.toUByteArray().toList())
          .toByteArray()
    }

    fun verifyEcdsa(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return me.uma.crypto.internal.verifyEcdsa(
          message.toUByteArray().toList(),
          signature.toUByteArray().toList(),
          publicKey.toUByteArray().toList(),
        )
    }

    fun encryptEcies(message: ByteArray, publicKey: ByteArray): ByteArray {
        return me.uma.crypto.internal
          .encryptEcies(message.toUByteArray().toList(), publicKey.toUByteArray().toList())
          .toByteArray()
    }

    fun decryptEcies(message: ByteArray, privateKey: ByteArray): ByteArray {
        return me.uma.crypto.internal
          .decryptEcies(message.toUByteArray().toList(), privateKey.toUByteArray().toList())
          .toByteArray()
    }

    fun generateKeyPair(): KeyPair {
        val keyPair = me.uma.crypto.internal.generateKeypair()
        return KeyPair(keyPair.getPublicKey().toByteArray(), keyPair.getPrivateKey().toByteArray())
    }
}

data class KeyPair(val publicKey: ByteArray, val privateKey: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyPair

        if (!publicKey.contentEquals(other.publicKey)) return false
        if (!privateKey.contentEquals(other.privateKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}
