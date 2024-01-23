package me.uma

// NonceCache is an interface for a caching of nonces used in signatures. This is used to prevent replay attacks.
// Implementations of this interface should be thread-safe.
interface NonceCache {
    // Checks if the given nonce has been used before, and if not, saves it.
    // If the nonce has been used before, or if timestamp is too old, returns an error.
    fun checkAndSaveNonce(nonce: String, timestamp: Long)

    // Purges all nonces older than the given timestamp. This allows the cache to be pruned.
    fun purgeNoncesOlderThan(timestamp: Long)
}

class InvalidNonceException(message: String) : Exception(message)

// InMemoryNonceCache is an in-memory implementation of NonceCache.
// It is not recommended to use this in production, as it will not persist across restarts. You likely want to implement
// your own NonceCache that persists to a database of some sort.
class InMemoryNonceCache(private var oldestValidTimestamp: Long) : NonceCache {
    private val cache = mutableMapOf<String, Long>()

    override fun checkAndSaveNonce(nonce: String, timestamp: Long) {
        if (timestamp < oldestValidTimestamp) {
            throw InvalidNonceException("Timestamp too old")
        }
        if (cache.contains(nonce)) {
            throw InvalidNonceException("Nonce already used")
        } else {
            cache[nonce] = timestamp
        }
    }

    override fun purgeNoncesOlderThan(timestamp: Long) {
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < timestamp) {
                iterator.remove()
            }
        }
        oldestValidTimestamp = timestamp
    }
}
