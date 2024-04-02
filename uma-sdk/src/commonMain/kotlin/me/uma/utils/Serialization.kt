package me.uma.utils

import me.uma.protocol.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

val module = SerializersModule {
    polymorphic(Currency::class, CurrencyV1::class, CurrencyV1.serializer())
    polymorphic(Currency::class, CurrencyV0::class, CurrencyV0.serializer())
    polymorphic(PayRequest::class, PayRequestV1::class, PayRequestV1Serializer)
    polymorphic(PayRequest::class, PayRequestV0::class, PayRequestV0.serializer())
    polymorphic(PayReqResponse::class, PayReqResponseV1::class, PayReqResponseV1.serializer())
    polymorphic(PayReqResponse::class, PayReqResponseV0::class, PayReqResponseV0.serializer())
}

@OptIn(ExperimentalSerializationApi::class)
val serialFormat = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    serializersModule = module
}
