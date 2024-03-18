package me.uma.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import me.uma.protocol.Currency
import me.uma.protocol.CurrencyV0
import me.uma.protocol.CurrencyV1
import me.uma.protocol.PayReqResponse
import me.uma.protocol.PayReqResponseV0
import me.uma.protocol.PayReqResponseV1
import me.uma.protocol.PayRequest
import me.uma.protocol.PayRequestV0
import me.uma.protocol.PayRequestV1
import me.uma.protocol.PayRequestV1Serializer

val module = SerializersModule {
    polymorphic(Currency::class, CurrencyV1::class, CurrencyV1.serializer())
    polymorphic(Currency::class, CurrencyV0::class, CurrencyV0.serializer())
    polymorphic(PayRequest::class, PayRequestV1::class, PayRequestV1Serializer)
    polymorphic(PayRequest::class, PayRequestV0::class, PayRequestV0.serializer())
    polymorphic(PayReqResponse::class, PayReqResponseV1::class, PayReqResponseV1.serializer())
    polymorphic(PayReqResponse::class, PayReqResponseV0::class, PayReqResponseV0.serializer())
}

val serialFormat = Json {
    ignoreUnknownKeys = true
    isLenient = true
    serializersModule = module
}
