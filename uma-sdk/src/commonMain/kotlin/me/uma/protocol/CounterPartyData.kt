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

fun createCounterPartyDataOptions(map: Map<String, Boolean>): CounterPartyDataOptions {
    return map.mapValues { CounterPartyDataOption(it.value) }
}

fun createCounterPartyDataOptions(vararg pairs: Pair<String, Boolean>): CounterPartyDataOptions {
    return createCounterPartyDataOptions(pairs.toMap())
}
