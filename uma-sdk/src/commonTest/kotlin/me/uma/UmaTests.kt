package me.uma

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.uma.crypto.Secp256k1
import me.uma.crypto.hexToByteArray
import me.uma.protocol.KycStatus
import me.uma.protocol.PayRequestV0
import me.uma.protocol.PayRequestV1
import me.uma.protocol.TravelRuleFormat
import me.uma.protocol.compliance
import me.uma.protocol.createCounterPartyDataOptions
import me.uma.utils.serialFormat
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class UmaTests {
    val keys = Secp256k1.generateKeyPair()

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `test create and parse payreq in receiving amount`() = runTest {
        val travelRuleInfo = "travel rule info"
        val payreq = UmaProtocolHelper().getPayRequest(
            receiverEncryptionPubKey = keys.publicKey,
            sendingVaspPrivateKey = keys.privateKey,
            receivingCurrencyCode = "USD",
            amount = 100,
            isAmountInReceivingCurrency = true,
            payerIdentifier = "test@test.com",
            payerKycStatus = KycStatus.VERIFIED,
            utxoCallback = "https://example.com/utxo",
            travelRuleInfo = "travel rule info",
            travelRuleFormat = TravelRuleFormat("someFormat", "1.0"),
            requestedPayeeData = createCounterPartyDataOptions(
                "email" to true,
                "name" to false,
                "compliance" to true,
            ),
            receiverUmaVersion = "1.0",
        )
        assertTrue(payreq is PayRequestV1)
        assertEquals("USD", payreq.receivingCurrencyCode())
        assertEquals("USD", payreq.sendingCurrencyCode())
        val json = payreq.toJson()
        val jsonObject = serialFormat.decodeFromString(JsonObject.serializer(), json)
        assertEquals("100.USD", jsonObject["amount"]?.jsonPrimitive?.content)
        assertEquals("USD", jsonObject["convert"]?.jsonPrimitive?.content)
        val decodedPayReq = UmaProtocolHelper().parseAsPayRequest(json)
        assertEquals(payreq, decodedPayReq)

        val encryptedTravelRuleInfo =
            decodedPayReq.payerData?.compliance()?.encryptedTravelRuleInfo ?: fail("travel rule info not found")
        assertEquals(
            travelRuleInfo,
            String(Secp256k1.decryptEcies(encryptedTravelRuleInfo.hexToByteArray(), keys.privateKey)),
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `test create and parse payreq in msats`() = runTest {
        val payreq = UmaProtocolHelper().getPayRequest(
            receiverEncryptionPubKey = keys.publicKey,
            sendingVaspPrivateKey = keys.privateKey,
            receivingCurrencyCode = "USD",
            amount = 100,
            isAmountInReceivingCurrency = false,
            payerIdentifier = "test@test.com",
            payerKycStatus = KycStatus.VERIFIED,
            utxoCallback = "https://example.com/utxo",
            receiverUmaVersion = "1.0",
        )
        assertTrue(payreq is PayRequestV1)
        assertNull(payreq.sendingCurrencyCode())
        assertEquals("USD", payreq.receivingCurrencyCode())
        val json = payreq.toJson()
        val jsonObject = serialFormat.decodeFromString(JsonObject.serializer(), json)
        assertEquals("100", jsonObject["amount"]?.jsonPrimitive?.content)
        assertEquals("USD", jsonObject["convert"]?.jsonPrimitive?.content)
        val decodedPayReq = UmaProtocolHelper().parseAsPayRequest(json)
        assertEquals(payreq, decodedPayReq)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `test create and parse payreq umav0`() = runTest {
        val payreq = UmaProtocolHelper().getPayRequest(
            receiverEncryptionPubKey = keys.publicKey,
            sendingVaspPrivateKey = keys.privateKey,
            receivingCurrencyCode = "USD",
            amount = 100,
            isAmountInReceivingCurrency = false,
            payerIdentifier = "test@test.com",
            payerKycStatus = KycStatus.VERIFIED,
            utxoCallback = "https://example.com/utxo",
            receiverUmaVersion = "0.3",
        )
        assertTrue(payreq is PayRequestV0)
        assertNull(payreq.sendingCurrencyCode())
        assertEquals("USD", payreq.receivingCurrencyCode())
        val json = payreq.toJson()
        val decodedPayReq = UmaProtocolHelper().parseAsPayRequest(json)
        assertEquals(payreq, decodedPayReq)
    }
}
