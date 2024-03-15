package me.uma.utils

import kotlinx.serialization.json.Json

val serialFormat = Json {
    ignoreUnknownKeys = true
    isLenient = true
}