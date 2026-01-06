package me.uma

import io.ktor.utils.io.core.toByteArray
import me.uma.crypto.Secp256k1
import me.uma.crypto.hexToByteArray
import me.uma.protocol.*
import me.uma.utils.serialFormat
import org.junit.jupiter.api.assertThrows
import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val BECH32_REFERENCE_STR =
    "uma1qqxzgen0daqxyctj9e3k7mgpy33nwcesxanx2cedvdnrqvpdxsenzced8ycnve3dxe3nzvmxvv6xyd3evcusyqsraqp3vqqr24f5gqgf24fj" +
        "q3r0d3kxzuszqyjqxqgzqszqqr6zgqzszqgxrd3k7mtsd35kzmnrv5arztr9d4skjmp6xqkxuctdv5arqpcrxqhrxzcg2ez4yj2xf9z5grqu" +
        "dp68gurn8ghj7etcv9khqmr99e3k7mf0vdskcmrzv93kkeqfwd5kwmnpw36hyeg73rn40"

@OptIn(ExperimentalCoroutinesApi::class)
class UmaTests {
    val keys = Secp256k1.generateKeyPair()

    @Test
    fun `test create invoice currency`() = runTest {
        val data = listOf(
            listOf("text/uma-invoice", "invoiceUUID"),
            listOf("text/plain", "otherInformations"),
        )
        val encoded1 = Json.encodeToString(data)

        val original = Json.decodeFromString<List<List<String>>>(encoded1)
        println(original.size)
        val invoiceCurrency =
            InvoiceCurrency(
                "usd",
                "us dollars",
                "$",
                10,
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
    fun `correctly serialized timestamps in invoices`() = runTest {
        val timestamp = System.currentTimeMillis()
        val invoice = createInvoice(timestamp)
        val serializedInvoice = serialFormat.encodeToString(invoice)
        val result = serialFormat.decodeFromString<Invoice>(serializedInvoice)
        assertEquals(result.expiration, timestamp)
    }

    @Test
    fun `deserializing an Invoice with missing required fields triggers error`() = runTest {
        val exception =
            assertThrows<UmaException> {
                // missing receiverUma, invoiceUUID, and Amount
                val malformedBech32str =
                    "uma1qvtqqq642dzqzz242vsygmmvd3shyqspyspszqsyqsqq7sjqq5qszpsmvdhk6urvd9skucm98gcjcetdv95kcw3s93hx" +
                        "zmt98gcqwqes9ceskzzkg4fyj3jfg4zqc8rgw368que69uhk27rpd4cxcefwvdhk6tmrv9kxccnpvd4kgztnd9nkuct5" +
                        "w4ex2mxcdff"
                Invoice.fromBech32(malformedBech32str)
            }
        assertEquals(
            "missing required fields: [amount, invoiceUUID, receiverUma]",
            exception.message,
        )
    }

    @Test
    fun `test encode invoice as bech32`() = runTest {
        val invoice = createInvoice()
        val bech32str =
            try {
                invoice.toBech32()
            } catch (e: IndexOutOfBoundsException) {
                ""
            }
        assertEquals("uma", bech32str.slice(0..2))
        assertEquals(BECH32_REFERENCE_STR, bech32str)

        val decodedInvoice = Invoice.fromBech32(bech32str)
        validateInvoice(invoice, decodedInvoice)
    }

    @Test
    fun `test decode bech32 invoice from incoming string`() {
        // sourced from python
        val decodedInvoice = Invoice.fromBech32(BECH32_REFERENCE_STR)
        assertEquals("\$foo@bar.com", decodedInvoice.receiverUma)
        assertEquals("c7c07fec-cf00-431c-916f-6c13fc4b69f9", decodedInvoice.invoiceUUID)
        assertEquals(1000, decodedInvoice.amount)
        assertEquals(1000000L, decodedInvoice.expiration)
        assertEquals(true, decodedInvoice.isSubjectToTravelRule)
        assertEquals("0.3", decodedInvoice.umaVersions)
        assertEquals(KycStatus.VERIFIED, decodedInvoice.kycStatus)
        assertEquals("https://example.com/callback", decodedInvoice.callback)
        assertEquals(InvoiceCurrency("USD", "US Dollar", "$", 2), decodedInvoice.receivingCurrency)
    }

    @Test
    fun `test verify invoice signature`() {
        val invoice = UmaProtocolHelper().getInvoice(
            receiverUma = "\$foo@bar.com",
            invoiceUUID = "c7c07fec-cf00-431c-916f-6c13fc4b69f9",
            amount = 1000,
            receivingCurrency = InvoiceCurrency(code = "USD", name = "US Dollar", symbol = "$", decimals = 2),
            expiration = 1000000,
            isSubjectToTravelRule = true,
            requiredPayerData = mapOf(
                CounterPartyDataKeys.NAME to CounterPartyDataOption(false),
                CounterPartyDataKeys.EMAIL to CounterPartyDataOption(false),
                CounterPartyDataKeys.COMPLIANCE to CounterPartyDataOption(true),
            ),
            commentCharsAllowed = null,
            senderUma = null,
            invoiceLimit = null,
            kycStatus = KycStatus.VERIFIED,
            callback = "https://example.com/callback",
            privateSigningKey = keys.privateKey,
        )
        assertTrue(UmaProtocolHelper().verifyUmaInvoice(invoice, PubKeyResponse(keys.publicKey, keys.publicKey)))
    }

    @Test
    fun `test create and parse payreq in receiving amount`() = runTest {
        val travelRuleInfo = "travel rule info"
        val payreq =
            UmaProtocolHelper().getPayRequest(
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
                    CounterPartyDataKeys.EMAIL to true,
                    CounterPartyDataKeys.NAME to false,
                    CounterPartyDataKeys.COMPLIANCE to true,
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
        val payreq =
            UmaProtocolHelper().getPayRequest(
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
        val payreq =
            UmaProtocolHelper().getPayRequest(
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
    fun `test parse Lnurlp URL with invalid user`() {
        val umaLnurlpQuery =
            "https://example.com/.well-known/lnurlp/\$bob(?vaspDomain=example.com&nonce=123&signature=123&" +
                "isSubjectToTravelRule=true&timestamp=123&umaVersion=1.0"
        assertThrows<UmaException> {
            UmaProtocolHelper().parseLnurlpRequest(umaLnurlpQuery)
        }
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
        val jsonCompliancePayerData =
            """
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

    @Test
    fun `test deserialization of missing utxos and utxoCallback in payer data`() = runTest {
        // Missing nodePubKey and encryptedTravelRuleInfo:
        val jsonCompliancePayerData =
            """
            {
                "kycStatus": "VERIFIED",
                "signature": "signature",
                "signatureNonce": "1234",
                "signatureTimestamp": 1234567,
                "travelRuleFormat": null
            }
            """.trimIndent()

        val compliancePayerData = serialFormat.decodeFromString<CompliancePayerData>(jsonCompliancePayerData)
        assertEquals(
            CompliancePayerData(
                utxos = emptyList(),
                kycStatus = KycStatus.VERIFIED,
                utxoCallback = "",
                signature = "signature",
                signatureNonce = "1234",
                signatureTimestamp = 1234567,
                encryptedTravelRuleInfo = null,
                nodePubKey = null,
            ),
            compliancePayerData,
        )
    }

    @Test
    fun `test deserialization of missing utxoCallback in payee data`() = runTest {
        // Missing utxoCallback:
        val jsonCompliancePayeeData =
            """
            {
                "utxos": [],
                "signature": "signature",
                "signatureNonce": "1234",
                "signatureTimestamp": 1234567
            }
            """.trimIndent()

        val compliancePayerData = serialFormat.decodeFromString<CompliancePayeeData>(jsonCompliancePayeeData)
        assertEquals(
            CompliancePayeeData(
                utxos = emptyList(),
                nodePubKey = null,
                utxoCallback = "",
                signature = "signature",
                signatureNonce = "1234",
                signatureTimestamp = 1234567,
                backingSignatures = null,
            ),
            compliancePayerData,
        )
    }

    private fun createInvoice(timestamp: Long? = null): Invoice {
        val requiredPayerData =
            mapOf(
                CounterPartyDataKeys.NAME to CounterPartyDataOption(false),
                CounterPartyDataKeys.EMAIL to CounterPartyDataOption(false),
                CounterPartyDataKeys.COMPLIANCE to CounterPartyDataOption(true),
            )
        val invoiceCurrency =
            InvoiceCurrency(
                code = "USD",
                name = "US Dollar",
                symbol = "$",
                decimals = 2,
            )

        return Invoice(
            receiverUma = "\$foo@bar.com",
            invoiceUUID = "c7c07fec-cf00-431c-916f-6c13fc4b69f9",
            amount = 1000L,
            receivingCurrency = invoiceCurrency,
            expiration = timestamp ?: 1000000L,
            isSubjectToTravelRule = true,
            requiredPayerData = requiredPayerData,
            commentCharsAllowed = null,
            senderUma = null,
            maxNumPayments = null,
            umaVersions = "0.3",
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
        assertEquals(preEncodedInvoice.maxNumPayments, decodedInvoice.maxNumPayments)
        assertEquals(preEncodedInvoice.umaVersions, decodedInvoice.umaVersions)
        assertEquals(preEncodedInvoice.kycStatus, decodedInvoice.kycStatus)
        assertEquals(preEncodedInvoice.callback, decodedInvoice.callback)
        assertEquals(preEncodedInvoice.requiredPayerData, decodedInvoice.requiredPayerData)
        assertEquals(preEncodedInvoice.receivingCurrency, decodedInvoice.receivingCurrency)
    }

    @Test
    fun `test serialize and deserialize SettlementAsset`() = runTest {
        val asset = SettlementAsset(
            identifier = "BTC",
            multipliers = mapOf("USD" to 34150.0, "EUR" to 29000.0)
        )
        val serialized = serialFormat.encodeToString(asset)
        val deserialized = serialFormat.decodeFromString<SettlementAsset>(serialized)
        assertEquals(asset, deserialized)
        assertEquals("BTC", deserialized.identifier)
        assertEquals(34150.0, deserialized.multipliers["USD"])
        assertEquals(29000.0, deserialized.multipliers["EUR"])
    }

    @Test
    fun `test serialize and deserialize SettlementOption`() = runTest {
        val option = SettlementOption(
            settlementLayer = "ln",
            assets = listOf(
                SettlementAsset("BTC", mapOf("USD" to 34150.0)),
                SettlementAsset("ETH", mapOf("USD" to 2500.0))
            )
        )
        val serialized = serialFormat.encodeToString(option)
        val deserialized = serialFormat.decodeFromString<SettlementOption>(serialized)
        assertEquals(option, deserialized)
        assertEquals("ln", deserialized.settlementLayer)
        assertEquals(2, deserialized.assets.size)
        assertEquals("BTC", deserialized.assets[0].identifier)
        assertEquals("ETH", deserialized.assets[1].identifier)
    }

    @Test
    fun `test serialize and deserialize SettlementInfo`() = runTest {
        val info = SettlementInfo(
            layer = "ln",
            assetIdentifier = "BTC"
        )
        val serialized = serialFormat.encodeToString(info)
        val deserialized = serialFormat.decodeFromString<SettlementInfo>(serialized)
        assertEquals(info, deserialized)
        assertEquals("ln", deserialized.layer)
        assertEquals("BTC", deserialized.assetIdentifier)
    }

    @Test
    fun `test LnurlpResponse with settlementOptions`() = runTest {
        val settlementOptions = listOf(
            SettlementOption(
                settlementLayer = "ln",
                assets = listOf(SettlementAsset("BTC", mapOf("USD" to 34150.0)))
            ),
            SettlementOption(
                settlementLayer = "spark",
                assets = listOf(SettlementAsset("USDC", mapOf("USD" to 1.0)))
            )
        )

        val lnurlpUrl = UmaProtocolHelper().getSignedLnurlpRequestUrl(
            keys.privateKey,
            "\$bob@vasp2.com",
            "https://vasp.com",
            true
        )
        val request = UmaProtocolHelper().parseLnurlpRequest(lnurlpUrl)
        assertNotNull(request)

        val lnurlpResponse = UmaProtocolHelper().getLnurlpResponse(
            request,
            keys.privateKey,
            true,
            "https://vasp2.com/callback",
            "encoded metadata",
            1,
            10_000_000,
            createCounterPartyDataOptions(
                CounterPartyDataKeys.NAME to false,
                CounterPartyDataKeys.EMAIL to false
            ),
            listOf(
                createCurrency(
                    code = "USD",
                    name = "US Dollar",
                    symbol = "$",
                    millisatoshiPerUnit = 34_150.0,
                    decimals = 2,
                    minSendable = 1,
                    maxSendable = 10_000_000,
                    senderUmaVersion = "1.0"
                )
            ),
            KycStatus.VERIFIED,
            null,
            null,
            settlementOptions
        )

        assertNotNull(lnurlpResponse)
        val umaResponse = lnurlpResponse.asUmaResponse()
        assertNotNull(umaResponse)
        assertNotNull(umaResponse.settlementOptions)
        assertEquals(2, umaResponse.settlementOptions?.size)
        assertEquals("ln", umaResponse.settlementOptions?.get(0)?.settlementLayer)
        assertEquals("spark", umaResponse.settlementOptions?.get(1)?.settlementLayer)

        val json = lnurlpResponse.toJson()
        val parsedResponse = UmaProtocolHelper().parseAsLnurlpResponse(json)
        assertNotNull(parsedResponse)
        val parsedUmaResponse = parsedResponse.asUmaResponse()
        assertNotNull(parsedUmaResponse?.settlementOptions)
        assertEquals(umaResponse.settlementOptions, parsedUmaResponse?.settlementOptions)
    }

    @Test
    fun `test PayRequest with settlementInfo`() = runTest {
        val settlementInfo = SettlementInfo(
            layer = "spark",
            assetIdentifier = "USDC"
        )

        val payreq = UmaProtocolHelper().getPayRequest(
            receiverEncryptionPubKey = keys.publicKey,
            sendingVaspPrivateKey = keys.privateKey,
            receivingCurrencyCode = "USD",
            amount = 100,
            isAmountInReceivingCurrency = true,
            payerIdentifier = "test@test.com",
            payerKycStatus = KycStatus.VERIFIED,
            utxoCallback = "https://example.com/utxo",
            receiverUmaVersion = "1.0",
            settlementInfo = settlementInfo
        )

        assertTrue(payreq is PayRequestV1)
        assertEquals(settlementInfo, payreq.settlementInfo())

        val json = payreq.toJson()
        val decodedPayReq = UmaProtocolHelper().parseAsPayRequest(json)
        assertTrue(decodedPayReq is PayRequestV1)
        assertEquals(settlementInfo, decodedPayReq.settlementInfo())
        assertEquals(payreq, decodedPayReq)
    }

    @Test
    fun `test PayRequest without settlementInfo defaults to null`() = runTest {
        val payreq = UmaProtocolHelper().getPayRequest(
            receiverEncryptionPubKey = keys.publicKey,
            sendingVaspPrivateKey = keys.privateKey,
            receivingCurrencyCode = "USD",
            amount = 100,
            isAmountInReceivingCurrency = true,
            payerIdentifier = "test@test.com",
            payerKycStatus = KycStatus.VERIFIED,
            utxoCallback = "https://example.com/utxo",
            receiverUmaVersion = "1.0"
        )

        assertTrue(payreq is PayRequestV1)
        assertNull(payreq.settlementInfo())

        val json = payreq.toJson()
        val decodedPayReq = UmaProtocolHelper().parseAsPayRequest(json)
        assertNull(decodedPayReq.settlementInfo())
    }

    @Test
    fun `test PayReqResponse exchangeFees field compatibility`() = runTest {
        val paymentInfo = V1PayReqResponsePaymentInfo(
            currencyCode = "USD",
            decimals = 2,
            multiplier = 34150.0,
            exchangeFees = 100L,
            amount = 1000L
        )

        assertEquals(100L, paymentInfo.exchangeFees)
        assertEquals(100L, paymentInfo.exchangeFeesMillisatoshi)

        val serialized = serialFormat.encodeToString(paymentInfo)
        val deserialized = serialFormat.decodeFromString<V1PayReqResponsePaymentInfo>(serialized)
        assertEquals(100L, deserialized.exchangeFees)
        assertEquals(100L, deserialized.exchangeFeesMillisatoshi)
    }

    @Test
    fun `test createUmaInvoiceForSettlementLayer defaults to createUmaInvoice`() = runTest {
        val invoiceCreator = object : UmaInvoiceCreator {
            override fun createUmaInvoice(amountMsats: Long, metadata: String, receiverIdentifier: String?) =
                java.util.concurrent.CompletableFuture.completedFuture("lnbc_default_$amountMsats")
        }

        val result = invoiceCreator.createUmaInvoiceForSettlementLayer(
            amount = 1000L,
            metadata = "test metadata",
            receiverIdentifier = "test@receiver.com",
            settlementInfo = SettlementInfo("ln", "BTC")
        ).await()

        assertEquals("lnbc_default_1000", result)
    }

    @Test
    fun `test createUmaInvoiceForSettlementLayer can be overridden`() = runTest {
        val invoiceCreator = object : UmaInvoiceCreator {
            override fun createUmaInvoice(amountMsats: Long, metadata: String, receiverIdentifier: String?) =
                java.util.concurrent.CompletableFuture.completedFuture("lnbc_default_$amountMsats")

            override fun createUmaInvoiceForSettlementLayer(
                amount: Long,
                metadata: String,
                receiverIdentifier: String?,
                settlementInfo: SettlementInfo?,
            ) = if (settlementInfo != null && settlementInfo.layer == "spark") {
                java.util.concurrent.CompletableFuture.completedFuture(
                    "spark_invoice_${settlementInfo.assetIdentifier}_$amount",
                )
            } else {
                createUmaInvoice(amount, metadata, receiverIdentifier)
            }
        }

        val resultSpark = invoiceCreator.createUmaInvoiceForSettlementLayer(
            amount = 1000L,
            metadata = "test metadata",
            receiverIdentifier = "test@receiver.com",
            settlementInfo = SettlementInfo("spark", "USDC")
        ).await()

        assertEquals("spark_invoice_USDC_1000", resultSpark)

        val resultLightning = invoiceCreator.createUmaInvoiceForSettlementLayer(
            amount = 2000L,
            metadata = "test metadata",
            receiverIdentifier = "test@receiver.com",
            settlementInfo = SettlementInfo("ln", "BTC")
        ).await()

        assertEquals("lnbc_default_2000", resultLightning)
    }
}
