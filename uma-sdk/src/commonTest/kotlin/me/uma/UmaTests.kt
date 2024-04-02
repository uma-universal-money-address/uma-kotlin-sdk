package me.uma

import me.uma.crypto.Secp256k1
import me.uma.crypto.hexToByteArray
import me.uma.protocol.*
import me.uma.utils.serialFormat
import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalCoroutinesApi::class)
class UmaTests {
    val keys = Secp256k1.generateKeyPair()

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

    @Test
    fun `test isUmaLnurlpQuery future-proofing`() {
        val umaLnurlpQuery =
            "https://example.com/.well-known/lnurlp/\$bob?vaspDomain=example.com&nonce=123&signature=123&" +
                "isSubjectToTravelRule=true&timestamp=123&umaVersion=100.0"
        assertEquals(true, UmaProtocolHelper().isUmaLnurlpQuery(umaLnurlpQuery))
    }

    @Test
    fun `test serialization nulls`() = runTest {
        // Missing nodePubKey and encryptedTravelRuleInfo:
        val jsonCompliancePayerData = """
            {
                "utxos": ["utxo1", "utxo2"],
                "kycStatus": "VERIFIED",
                "utxoCallback": "utxoCallback",
                "signature": "signature",
                "signatureNonce": "1234",
                "signatureTimestamp": 1234567,
                "travelRuleFormat": null
            }
        """.trimIndent()

        val compliancePayerData = serialFormat.decodeFromString<CompliancePayerData>(jsonCompliancePayerData)
        assertEquals(
            CompliancePayerData(
                utxos = listOf("utxo1", "utxo2"),
                kycStatus = KycStatus.VERIFIED,
                utxoCallback = "utxoCallback",
                signature = "signature",
                signatureNonce = "1234",
                signatureTimestamp = 1234567,
                encryptedTravelRuleInfo = null,
                nodePubKey = null,
            ),
            compliancePayerData,
        )
    }
}
