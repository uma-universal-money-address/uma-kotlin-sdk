package me.uma

import io.ktor.utils.io.core.toByteArray
import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.uma.crypto.Secp256k1
import me.uma.crypto.hexToByteArray
import me.uma.protocol.*
import me.uma.utils.serialFormat
import org.w3c.dom.css.Counter
import kotlinx.coroutines.flow.callbackFlow

@OptIn(ExperimentalCoroutinesApi::class)
class UmaTests {
    val keys = Secp256k1.generateKeyPair()

    @Test
    fun `test create invoice currency`() = runTest {
        val invoiceCurrency = InvoiceCurrency(
            "usd",
            "us dollars",
            "$",
            10
        )
        val encoded = serialFormat.encodeToString(invoiceCurrency)
        val result = serialFormat.decodeFromString<InvoiceCurrency>(encoded)
        assertEquals("usd", result.code)
        assertEquals("us dollars", result.name)
        assertEquals("$", result.symbol)
        assertEquals(10, result.decimals)
    }

    @Test
    fun `test create invoice`() = runTest {
        val invoice = createInvoice()
        val serializedInvoice = serialFormat.encodeToString(invoice)
        val result = serialFormat.decodeFromString<Invoice>(serializedInvoice)
        validateInvoice(invoice, result)
    }

    @Test
    fun `test encode invoice as bech32`() = runTest {
        val invoice = createInvoice()
        val bech32str = try {
            invoice.toBech32()
        } catch (e: IndexOutOfBoundsException) {
            ""
        }
        assertEquals("uma", bech32str.slice(0..2))

        val decodedInvoice = Invoice.fromBech32(bech32str)
        validateInvoice(invoice, decodedInvoice)

    }

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

    private fun createInvoice(
    ): Invoice {
        val requiredPayerData = mapOf(
            "name" to CounterPartyDataOption(false),
            "email" to CounterPartyDataOption(false),
            "compliance" to CounterPartyDataOption(true),
        )
        val invoiceCurrency = InvoiceCurrency(
            code = "USD",
            name = "US Dollar",
            symbol = "$",
            decimals = 2,
        )
        return Invoice(
            receiverUma = "\$foo@bar.com",
            invoiceUUID = "c7c07fec-cf00-431c-916f-6c13fc4b69f9",
            amount = 1000,
            receivingCurrency = invoiceCurrency,
            expiration = 1000000,
            isSubjectToTravelRule = true,
            requiredPayerData = requiredPayerData,
            commentCharsAllowed = 30,
            senderUma = "\$other@uma.com",
            invoiceLimit = 100,
            umaVersion = "0.3",
            kycStatus = KycStatus.VERIFIED,
            callback = "https://example.com/callback",
            signature = "signature".toByteArray(),
        )
    }

    private fun validateInvoice(preEncodedInvoice: Invoice, decodedInvoice: Invoice) {
        assertEquals(preEncodedInvoice.receiverUma, decodedInvoice.receiverUma)
        assertEquals(preEncodedInvoice.invoiceUUID, decodedInvoice.invoiceUUID)
        assertEquals(preEncodedInvoice.amount, decodedInvoice.amount)
        assertEquals(preEncodedInvoice.expiration, decodedInvoice.expiration)
        assertEquals(preEncodedInvoice.isSubjectToTravelRule, decodedInvoice.isSubjectToTravelRule)
        assertEquals(preEncodedInvoice.commentCharsAllowed, decodedInvoice.commentCharsAllowed)
        assertEquals(preEncodedInvoice.senderUma, decodedInvoice.senderUma)
        assertEquals(preEncodedInvoice.invoiceLimit, decodedInvoice.invoiceLimit)
        assertEquals(preEncodedInvoice.umaVersion, decodedInvoice.umaVersion)
        assertEquals(preEncodedInvoice.kycStatus, decodedInvoice.kycStatus)
        assertEquals(preEncodedInvoice.callback, decodedInvoice.callback)
        assertEquals(preEncodedInvoice.requiredPayerData, decodedInvoice.requiredPayerData)
        assertEquals(preEncodedInvoice.receivingCurrency, decodedInvoice.receivingCurrency)
    }
}
