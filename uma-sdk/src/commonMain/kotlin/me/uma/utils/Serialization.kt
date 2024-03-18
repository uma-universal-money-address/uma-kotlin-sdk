package me.uma.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import me.uma.protocol.Currency

val module = SerializersModule {
    polymorphic(Currency::class,  Currency.CurrencyV1::class, Currency.CurrencyV1.serializer())
    polymorphic(Currency::class,  Currency.CurrencyV0::class, Currency.CurrencyV0.serializer())
}

val serialFormat = Json {
    ignoreUnknownKeys = true
    isLenient = true
    serializersModule = module
}
