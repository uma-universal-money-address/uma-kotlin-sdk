package me.uma.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
val serialFormat = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}
