package me.uma

import me.uma.protocol.PubKeyResponse

interface PublicKeyCache {
    fun getPublicKeysForVasp(vaspDomain: String): PubKeyResponse?

    fun addPublicKeysForVasp(vaspDomain: String, pubKeyResponse: PubKeyResponse)

    fun removePublicKeysForVasp(vaspDomain: String)

    fun clear()
}

class InMemoryPublicKeyCache : PublicKeyCache {
    private val cache = mutableMapOf<String, PubKeyResponse>()

    override fun getPublicKeysForVasp(vaspDomain: String): PubKeyResponse? {
        val pubKeyResponse = cache[vaspDomain]
        return if (pubKeyResponse?.expirationTimestamp == null ||
            pubKeyResponse.expirationTimestamp < System.currentTimeMillis() / 1000
        ) {
            cache.remove(vaspDomain)
            null
        } else {
            pubKeyResponse
        }
    }

    override fun addPublicKeysForVasp(vaspDomain: String, pubKeyResponse: PubKeyResponse) {
        if (pubKeyResponse.expirationTimestamp != null &&
            pubKeyResponse.expirationTimestamp > System.currentTimeMillis() / 1000
        ) {
            cache[vaspDomain] = pubKeyResponse
        }
    }

    override fun removePublicKeysForVasp(vaspDomain: String) {
        cache.remove(vaspDomain)
    }

    override fun clear() {
        cache.clear()
    }
}
