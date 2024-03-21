package me.uma

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import me.uma.crypto.Secp256k1
import me.uma.protocol.CompliancePayerData
import me.uma.protocol.KycStatus
import me.uma.protocol.PayerDataOptions
import me.uma.protocol.TravelRuleFormat
import me.uma.utils.serialFormat
import kotlinx.serialization.decodeFromString

@OptIn(ExperimentalCoroutinesApi::class)
class UmaTests {
    val keys = Secp256k1.generateKeyPair()

    @Test
    fun `test serialize PayerDataOptions`() = runTest {
        val payerDataOptions = PayerDataOptions(
            nameRequired = false,
            emailRequired = false,
            complianceRequired = true,
        )
        val json = payerDataOptions.toJson()
        assertEquals(
            payerDataOptions,
            serialFormat.decodeFromString(PayerDataOptions.serializer(), json),
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `test create and parse payreq`() = runTest {
        val travelRuleInfo = "travel rule info"
        val payreq = UmaProtocolHelper().getPayRequest(
            receiverEncryptionPubKey = keys.publicKey,
            sendingVaspPrivateKey = keys.privateKey,
            currencyCode = "USD",
            amount = 100,
            payerIdentifier = "test@test.com",
            payerKycStatus = KycStatus.VERIFIED,
            utxoCallback = "https://example.com/utxo",
            travelRuleInfo = "travel rule info",
            travelRuleFormat = TravelRuleFormat("someFormat", "1.0"),
        )
        val json = payreq.toJson()
        val decodedPayReq = UmaProtocolHelper().parseAsPayRequest(json)
        assertEquals(payreq, decodedPayReq)

        val encryptedTravelRuleInfo =
            decodedPayReq.payerData.compliance?.encryptedTravelRuleInfo ?: fail("travel rule info not found")
        assertEquals(
            travelRuleInfo,
            String(Secp256k1.decryptEcies(encryptedTravelRuleInfo.hexToByteArray(), keys.privateKey)),
        )
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
