@file:JvmName("CounterPartyData")

package me.uma.protocol

import io.ktor.utils.io.core.toByteArray
import me.uma.utils.ByteCodeable
import kotlinx.serialization.Serializable

@Serializable
data class CounterPartyDataOption(
    val mandatory: Boolean,
)

typealias CounterPartyDataOptions = Map<String, CounterPartyDataOption>

data class CounterPartyDataOptionsWrapper(
    val options: CounterPartyDataOptions
) : ByteCodeable {
    override fun toBytes(): ByteArray {
        val optionsString = options.map { (key, option) ->
            "${key}:${ if (option.mandatory) 1 else 0}"
        }.joinToString(",")
        return optionsString.toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromBytes(bytes: ByteArray): CounterPartyDataOptionsWrapper {
            val optionsString = String(bytes)
            return CounterPartyDataOptionsWrapper(
                optionsString.split(",").mapNotNull {
                    val options = it.split(':')
                    if (options.size == 2) {
                        options[0] to CounterPartyDataOption(options[1] == "1")
                    } else null
                }.toMap()
            )
        }
    }
}

fun createCounterPartyDataOptions(map: Map<String, Boolean>): CounterPartyDataOptions {
    return map.mapValues { CounterPartyDataOption(it.value) }
}

fun createCounterPartyDataOptions(vararg pairs: Pair<String, Boolean>): CounterPartyDataOptions {
    return createCounterPartyDataOptions(pairs.toMap())
}
