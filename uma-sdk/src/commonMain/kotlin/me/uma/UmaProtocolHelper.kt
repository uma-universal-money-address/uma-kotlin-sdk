@file:OptIn(ExperimentalStdlibApi::class)

package me.uma

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.random.nextULong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.uma.crypto.Secp256k1
import me.uma.protocol.*
import me.uma.utils.isDomainLocalhost

/**
 * A helper class for interacting with the UMA protocol. It provides methods for creating and verifying UMA requests
 * and responses.
 */
class UmaProtocolHelper @JvmOverloads constructor(
    private val publicKeyCache: PublicKeyCache = InMemoryPublicKeyCache(),
    private val umaRequester: UmaRequester = KtorUmaRequester(),
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Fetches the public keys of a VASP or returns the cached keys if they are still valid.
     *
     * In Kotlin, prefer using [fetchPublicKeysForVasp] instead.
     *
     * @param vaspDomain The domain of the VASP whose public keys are being fetched.
     * @return The [PubKeyResponse] containing the public keys of the VASP.
     */
    @Throws(Exception::class)
    fun fetchPublicKeysForVaspFuture(vaspDomain: String): Future<PubKeyResponse> = coroutineScope.future {
        fetchPublicKeysForVasp(vaspDomain)
    }

    /**
     * Fetches the public keys of a VASP or returns the cached keys if they are still valid.
     *
     * This method is synchronous and should only be used in cases where the caller is already on a background thread.
     * In Kotlin, prefer using [fetchPublicKeysForVasp] instead.
     *
     * @param vaspDomain The domain of the VASP whose public keys are being fetched.
     * @return The [PubKeyResponse] containing the public keys of the VASP.
     */
    @Throws(Exception::class)
    fun fetchPublicKeysForVaspSync(vaspDomain: String): PubKeyResponse = runBlocking {
        fetchPublicKeysForVasp(vaspDomain)
    }

    /**
     * Fetches the public keys of a VASP or returns the cached keys if they are still valid.
     *
     * @param vaspDomain The domain of the VASP whose public keys are being fetched.
     * @return The [PubKeyResponse] containing the public keys of the VASP.
     */
    @JvmName("KotlinOnly-fetchPublicKeysForVaspSuspended")
    suspend fun fetchPublicKeysForVasp(vaspDomain: String): PubKeyResponse {
        val cached = publicKeyCache.getPublicKeysForVasp(vaspDomain)
        if (cached != null) {
            return cached
        }

        val scheme = if (isDomainLocalhost(vaspDomain)) "http" else "https"
        val response = umaRequester.makeGetRequest("$scheme://$vaspDomain/.well-known/lnurlpubkey")
        val pubKeyResponse = Json.decodeFromString<PubKeyResponse>(response)
        publicKeyCache.addPublicKeysForVasp(vaspDomain, pubKeyResponse)
        return pubKeyResponse
    }

    private fun generateNonce(): String {
        return Random.nextULong().toString()
    }

    /**
     * Creates a signed UMA [LnurlpRequest].
     *
     * @param signingPrivateKey The signing private key of the VASP that is sending the payment. This will be used to
     *     sign the request.
     * @param receiverAddress The address of the user at the receiving VASP that is receiving the payment
     *     (i.e. $bob@vasp2.com).
     * @param senderVaspDomain The domain of the VASP that is sending the payment. It will be used by the receiving VASP
     *     to fetch the public keys of the sending VASP.
     * @param isSubjectToTravelRule Indicates whether the sending VASP is a financial institution that requires travel rule
     *     information.
     * @param umaVersion The version of the UMA protocol that the sending VASP prefers to use for this transaction. This
     *     parameter should only be overridden in cases where the receiving VASP does not support the default version
     *     for this SDK. For the version negotiation flow, see
     *     https://static.swimlanes.io/87f5d188e080cb8e0494e46f80f2ae74.png
     */
    @JvmOverloads
    @Throws(Exception::class)
    fun getSignedLnurlpRequestUrl(
        signingPrivateKey: ByteArray,
        receiverAddress: String,
        senderVaspDomain: String,
        isSubjectToTravelRule: Boolean,
        umaVersion: String = UMA_VERSION_STRING,
    ): String {
        val nonce = generateNonce()
        val timestamp = System.currentTimeMillis() / 1000
        val unsignedRequest = LnurlpRequest(
            receiverAddress = receiverAddress,
            vaspDomain = senderVaspDomain,
            isSubjectToTravelRule = isSubjectToTravelRule,
            nonce = nonce,
            timestamp = timestamp,
            signature = "",
            umaVersion = umaVersion,
        )
        val signature = signPayload(unsignedRequest.signablePayload(), signingPrivateKey)
        return unsignedRequest.signedWith(signature).encodeToUrl()
    }

    /**
     * @return true if the given URL is a valid UMA Lnurlp query, false otherwise.
     */
    fun isUmaLnurlpQuery(url: String): Boolean {
        return try {
            parseLnurlpRequest(url)
            true
        } catch (e: UnsupportedVersionException) {
            true
        } catch (e: Exception) {
            false
        }
    }

    fun parseLnurlpRequest(url: String) = LnurlpRequest.decodeFromUrl(url)

    /**
     * Verifies the signature on an UMA Lnurlp query based on the public key of the VASP making the request.
     *
     * @param query The signed [LnurlpRequest] to verify.
     * @param pubKeyResponse The [PubKeyResponse] that contains the public key of the receiver.
     * @param nonceCache The persistent [NonceCache] implementation that will cache previously seen nonces.
     * @return true if the signature is valid, false otherwise.
     * @throws InvalidNonceException if the nonce has already been used/timestamp is too old.
     */
    @Throws(InvalidNonceException::class)
    fun verifyUmaLnurlpQuerySignature(
        query: LnurlpRequest,
        pubKeyResponse: PubKeyResponse,
        nonceCache: NonceCache,
    ): Boolean {
        nonceCache.checkAndSaveNonce(query.nonce, query.timestamp)
        return verifySignature(query.signablePayload(), query.signature, pubKeyResponse.signingPubKey)
    }

    /**
     * Creates a signed UMA [LnurlpResponse].
     *
     * @param query The [LnurlpRequest] sent by the sender.
     * @param privateKeyBytes The signing private key of the VASP that is receiving the payment. This will be used to
     *     sign the response.
     * @param requiresTravelRuleInfo Indicates whether the receiving VASP is a financial institution which requires
     *     travel rule information.
     * @param callback The URL that the sending VASP will call to retrieve an invoice via a [PayRequest].
     * @param encodedMetadata The metadata that will be added to the invoice's metadata hash field.
     * @param minSendableSats The minimum amount of sats that the sender can send.
     * @param maxSendableSats The maximum amount of sats that the sender can send.
     * @param payerDataOptions The data that the sender must send to the receiver to identify themselves.
     * @param currencyOptions The list of currencies that the receiver accepts, along with their conversion rates.
     * @param receiverKycStatus Indicates whether VASP2 has KYC information about the receiver.
     * @return The [LnurlpResponse] that should be sent to the sender for the given [LnurlpRequest].
     * @throws IllegalArgumentException if the receiverAddress is not in the format of "user@domain".
     */
    fun getLnurlpResponse(
        query: LnurlpRequest,
        privateKeyBytes: ByteArray,
        requiresTravelRuleInfo: Boolean,
        callback: String,
        encodedMetadata: String,
        minSendableSats: Long,
        maxSendableSats: Long,
        payerDataOptions: PayerDataOptions,
        currencyOptions: List<Currency>,
        receiverKycStatus: KycStatus,
    ): LnurlpResponse {
        val complianceResponse =
            getSignedLnurlpComplianceResponse(query, privateKeyBytes, requiresTravelRuleInfo, receiverKycStatus)
        val umaVersion = minOf(Version.parse(query.umaVersion), Version.parse(UMA_VERSION_STRING)).toString()
        return LnurlpResponse(
            callback = callback,
            minSendable = minSendableSats * 1000,
            maxSendable = maxSendableSats * 1000,
            metadata = encodedMetadata,
            currencies = currencyOptions,
            requiredPayerData = payerDataOptions,
            compliance = complianceResponse,
            umaVersion = umaVersion,
        )
    }

    private fun getSignedLnurlpComplianceResponse(
        query: LnurlpRequest,
        privateKeyBytes: ByteArray,
        requiresTravelRuleInfo: Boolean,
        receiverKycStatus: KycStatus,
    ): LnurlComplianceResponse {
        val nonce = generateNonce()
        val timestamp = System.currentTimeMillis() / 1000
        val complianceResponse = LnurlComplianceResponse(
            kycStatus = receiverKycStatus,
            isSubjectToTravelRule = requiresTravelRuleInfo,
            signature = "",
            signatureNonce = nonce,
            signatureTimestamp = timestamp,
            receiverIdentifier = query.receiverAddress,
        )
        val signature = signPayload(complianceResponse.signablePayload(), privateKeyBytes)
        return complianceResponse.signedWith(signature)
    }

    fun parseAsLnurlpResponse(response: String): LnurlpResponse {
        return Json.decodeFromString(response)
    }

    /**
     * Verifies the signature on an UMA Lnurlp response based on the public key of the VASP making the request.
     *
     * @param response The signed [LnurlpResponse] sent by the receiver.
     * @param pubKeyResponse The [PubKeyResponse] that contains the public key of the receiver.
     * @param nonceCache The persistent [NonceCache] implementation that will cache previously seen nonces.
     * @return true if the signature is valid, false otherwise.
     * @throws InvalidNonceException if the nonce has already been used/timestamp is too old.
     */
    @Throws(InvalidNonceException::class)
    fun verifyLnurlpResponseSignature(
        response: LnurlpResponse,
        pubKeyResponse: PubKeyResponse,
        nonceCache: NonceCache,
    ): Boolean {
        nonceCache.checkAndSaveNonce(response.compliance.signatureNonce, response.compliance.signatureTimestamp)
        return verifySignature(
            response.compliance.signablePayload(),
            response.compliance.signature,
            pubKeyResponse.signingPubKey,
        )
    }

    /**
     * Creates a signed UMA [PayRequest].
     *
     * @param receiverEncryptionPubKey The public key of the receiver that will be used to encrypt the travel rule
     *     information.
     * @param sendingVaspPrivateKey The private key of the VASP that is sending the payment. This will be used to sign
     *     the request.
     * @param currencyCode The code of the currency that the receiver will receive for this payment.
     * @param amount The amount of the payment in the smallest unit of the specified currency (i.e. cents for USD).
     * @param payerIdentifier The identifier of the sender. For example, $alice@vasp1.com
     * @param payerKycStatus Indicates whether VASP1 has KYC information about the sender.
     * @param utxoCallback The URL that the receiver will call to send UTXOs of the channel that the receiver used to
     *   receive the payment once it completes.
     * @param travelRuleInfo The travel rule information. This will be encrypted before sending to the receiver.
     * @param payerUtxos The list of UTXOs of the sender's channels that might be used to fund the payment.
     * @param payerNodePubKey If known, the public key of the sender's node. If supported by the receiving VASP's
     *     compliance provider, this will be used to pre-screen the sender's UTXOs for compliance purposes.
     * @param payerName The name of the sender (optional).
     * @param payerEmail The email of the sender (optional).
     * @param travelRuleFormat An optional standardized format of the travel rule information (e.g. IVMS). Null
     *    indicates raw json or a custom format.
     * @return The [PayRequest] that should be sent to the receiver.
     */
    @JvmOverloads
    fun getPayRequest(
        receiverEncryptionPubKey: ByteArray,
        sendingVaspPrivateKey: ByteArray,
        currencyCode: String,
        amount: Long,
        payerIdentifier: String,
        payerKycStatus: KycStatus,
        utxoCallback: String,
        travelRuleInfo: String? = null,
        payerUtxos: List<String>? = null,
        payerNodePubKey: String? = null,
        payerName: String? = null,
        payerEmail: String? = null,
        travelRuleFormat: TravelRuleFormat? = null,
    ): PayRequest {
        val compliancePayerData = getSignedCompliancePayerData(
            receiverEncryptionPubKey,
            sendingVaspPrivateKey,
            payerIdentifier,
            travelRuleInfo,
            payerKycStatus,
            payerUtxos,
            payerNodePubKey,
            utxoCallback,
            travelRuleFormat,
        )
        val payerData = PayerData(
            identifier = payerIdentifier,
            name = payerName,
            email = payerEmail,
            compliance = compliancePayerData,
        )
        return PayRequest(
            payerData = payerData,
            currencyCode = currencyCode,
            amount = amount,
        )
    }

    private fun getSignedCompliancePayerData(
        receiverEncryptionPubKey: ByteArray,
        sendingVaspPrivateKey: ByteArray,
        payerIdentifier: String,
        travelRuleInfo: String?,
        payerKycStatus: KycStatus,
        payerUtxos: List<String>?,
        payerNodePubKey: String?,
        utxoCallback: String,
        travelRuleFormat: TravelRuleFormat?,
    ): CompliancePayerData {
        val nonce = generateNonce()
        val timestamp = System.currentTimeMillis() / 1000
        val unsignedCompliancePayerData = CompliancePayerData(
            utxos = payerUtxos ?: emptyList(),
            nodePubKey = payerNodePubKey,
            encryptedTravelRuleInfo = travelRuleInfo?.let { encryptTravelRuleInfo(receiverEncryptionPubKey, it) },
            kycStatus = payerKycStatus,
            signature = "",
            signatureNonce = nonce,
            signatureTimestamp = timestamp,
            utxoCallback = utxoCallback,
            travelRuleFormat = travelRuleFormat,
        )
        val signablePayload = "$payerIdentifier|$nonce|$timestamp".encodeToByteArray()
        val signature = signPayload(signablePayload, sendingVaspPrivateKey)
        return unsignedCompliancePayerData.signedWith(signature)
    }

    private fun encryptTravelRuleInfo(receiverEncryptionPubKey: ByteArray, travelRuleInfoJson: String): String {
        return Secp256k1.encryptEcies(travelRuleInfoJson.encodeToByteArray(), receiverEncryptionPubKey).toHexString()
    }

    /**
     * Parses a json requestBody into a [PayRequest].
     *
     * @param request The json requestBody sent by the sender.
     * @return The [PayRequest] sent by the sender.
     * @throws IllegalArgumentException if the requestBody is not a valid [PayRequest].
     * @throws SerializationException if the requestBody is not a valid json.
     */
    @Throws(IllegalArgumentException::class)
    fun parseAsPayRequest(request: String): PayRequest {
        return Json.decodeFromString(request)
    }

    /**
     * Verifies the signature of the [PayRequest] sent by the sender.
     *
     * @param payReq The [PayRequest] sent by the sender.
     * @param pubKeyResponse The [PubKeyResponse] that contains the public key of the sender.
     * @param nonceCache The persistent [NonceCache] implementation that will cache previously seen nonces.
     * @return true if the signature is valid, false otherwise.
     * @throws InvalidNonceException if the nonce has already been used/timestamp is too old.
     */
    @Throws(InvalidNonceException::class)
    fun verifyPayReqSignature(
        payReq: PayRequest,
        pubKeyResponse: PubKeyResponse,
        nonceCache: NonceCache,
    ): Boolean {
        val compliance = payReq.payerData.compliance ?: return false
        nonceCache.checkAndSaveNonce(compliance.signatureNonce, compliance.signatureTimestamp)
        return verifySignature(
            payReq.signablePayload(),
            compliance.signature,
            pubKeyResponse.signingPubKey,
        )
    }

    /**
     * Creates an uma pay request response with an encoded invoice.
     *
     * In Kotlin, prefer using [getPayReqResponse] instead.
     *
     * @param query The [PayRequest] sent by the sender.
     * @param invoiceCreator The [UmaInvoiceCreator] that will be used to create the invoice.
     * @param metadata The metadata that will be added to the invoice's metadata hash field.
     * @param currencyCode The code of the currency that the receiver will receive for this payment.
     * @param currencyDecimals The number of digits after the decimal point for the receiving currency. For example,
     *     USD has 2 decimal places. This should align with the `decimals` field returned for the chosen currency in the
     *     LNURLP response.
     * @param conversionRate The conversion rate. It is the numer of milli-satoshis per the smallest unit of the
     *     specified currency (for example: cents in USD). This rate is committed to by the receiving VASP until the
     *     invoice expires.
     * @param receiverFeesMillisats The fees charged (in millisats) by the receiving VASP for this transaction. This
     *     is separate from the [conversionRate].
     * @param receiverChannelUtxos The list of UTXOs of the receiver's channels that might be used to fund the payment.
     * @param receiverNodePubKey If known, the public key of the receiver's node. If supported by the sending VASP's
     *     compliance provider, this will be used to pre-screen the receiver's UTXOs for compliance purposes.
     * @param utxoCallback The URL that the receiving VASP will call to send UTXOs of the channel that the receiver
     *     used to receive the payment once it completes.
     * @return A [CompletableFuture] [PayReqResponse] that should be returned to the sender for the given [PayRequest].
     */
    @Throws(IllegalArgumentException::class, CancellationException::class)
    fun getPayReqResponseFuture(
        query: PayRequest,
        invoiceCreator: UmaInvoiceCreator,
        metadata: String,
        currencyCode: String,
        currencyDecimals: Int,
        conversionRate: Double,
        receiverFeesMillisats: Long,
        receiverChannelUtxos: List<String>,
        receiverNodePubKey: String?,
        utxoCallback: String,
    ): CompletableFuture<PayReqResponse> = coroutineScope.future {
        getPayReqResponse(
            query,
            invoiceCreator,
            metadata,
            currencyCode,
            currencyDecimals,
            conversionRate,
            receiverFeesMillisats,
            receiverChannelUtxos,
            receiverNodePubKey,
            utxoCallback,
        )
    }

    /**
     * Creates an uma pay request response with an encoded invoice.
     *
     * This method is synchronous and should only be used in cases where the caller is already on a background thread.
     * In Kotlin, prefer using [getPayReqResponse] instead.
     *
     * @param query The [PayRequest] sent by the sender.
     * @param invoiceCreator The [UmaInvoiceCreator] that will be used to create the invoice.
     * @param metadata The metadata that will be added to the invoice's metadata hash field.
     * @param currencyCode The code of the currency that the receiver will receive for this payment.
     * @param currencyDecimals The number of digits after the decimal point for the receiving currency. For example,
     *     USD has 2 decimal places. This should align with the `decimals` field returned for the chosen currency in the
     *     LNURLP response.
     * @param conversionRate The conversion rate. It is the number of milli-satoshis per the smallest unit of the
     *     specified currency (for example: cents in USD). This rate is committed to by the receiving VASP until the
     *     invoice expires.
     * @param receiverFeesMillisats The fees charged (in millisats) by the receiving VASP for this transaction. This
     *     is separate from the [conversionRate].
     * @param receiverChannelUtxos The list of UTXOs of the receiver's channels that might be used to fund the payment.
     * @param receiverNodePubKey If known, the public key of the receiver's node. If supported by the sending VASP's
     *     compliance provider, this will be used to pre-screen the receiver's UTXOs for compliance purposes.
     * @param utxoCallback The URL that the receiving VASP will call to send UTXOs of the channel that the receiver
     *     used to receive the payment once it completes.
     * @return A [PayReqResponse] that should be returned to the sender for the given [PayRequest].
     */
    @Throws(Exception::class, IllegalArgumentException::class, CancellationException::class)
    fun getPayReqResponseSync(
        query: PayRequest,
        invoiceCreator: SyncUmaInvoiceCreator,
        metadata: String,
        currencyCode: String,
        currencyDecimals: Int,
        conversionRate: Double,
        receiverFeesMillisats: Long,
        receiverChannelUtxos: List<String>,
        receiverNodePubKey: String?,
        utxoCallback: String,
    ): PayReqResponse = runBlocking {
        val futureInvoiceCreator = object : UmaInvoiceCreator {
            override fun createUmaInvoice(amountMsats: Long, metadata: String): CompletableFuture<String> {
                return coroutineScope.future { invoiceCreator.createUmaInvoice(amountMsats, metadata) }
            }
        }
        getPayReqResponse(
            query,
            futureInvoiceCreator,
            metadata,
            currencyCode,
            currencyDecimals,
            conversionRate,
            receiverFeesMillisats,
            receiverChannelUtxos,
            receiverNodePubKey,
            utxoCallback,
        )
    }

    /**
     * Creates an uma pay request response with an encoded invoice.
     *
     * @param query The [PayRequest] sent by the sender.
     * @param invoiceCreator The [UmaInvoiceCreator] that will be used to create the invoice.
     * @param metadata The metadata that will be added to the invoice's metadata hash field.
     * @param currencyCode The code of the currency that the receiver will receive for this payment.
     * @param currencyDecimals The number of digits after the decimal point for the receiving currency. For example,
     *     USD has 2 decimal places. This should align with the `decimals` field returned for the chosen currency in the
     *     LNURLP response.
     * @param conversionRate The conversion rate. It is the numer of milli-satoshis per the smallest unit of the
     *     specified currency (for example: cents in USD). This rate is committed to by the receiving VASP until the
     *     invoice expires.
     * @param receiverFeesMillisats The fees charged (in millisats) by the receiving VASP for this transaction. This
     *     is separate from the [conversionRate].
     * @param receiverChannelUtxos The list of UTXOs of the receiver's channels that might be used to fund the payment.
     * @param receiverNodePubKey If known, the public key of the receiver's node. If supported by the sending VASP's
     *     compliance provider, this will be used to pre-screen the receiver's UTXOs for compliance purposes.
     * @param utxoCallback The URL that the receiving VASP will call to send UTXOs of the channel that the receiver
     *     used to receive the payment once it completes.
     * @return The [PayReqResponse] that should be returned to the sender for the given [PayRequest].
     */
    @JvmName("KotlinOnly-getPayReqResponseSuspended")
    suspend fun getPayReqResponse(
        query: PayRequest,
        invoiceCreator: UmaInvoiceCreator,
        metadata: String,
        currencyCode: String,
        currencyDecimals: Int,
        conversionRate: Double,
        receiverFeesMillisats: Long,
        receiverChannelUtxos: List<String>,
        receiverNodePubKey: String?,
        utxoCallback: String,
    ): PayReqResponse {
        val encodedPayerData = Json.encodeToString(query.payerData)
        val metadataWithPayerData = "$metadata$encodedPayerData"
        val invoice = invoiceCreator.createUmaInvoice(
            amountMsats = (query.amount.toDouble() * conversionRate + receiverFeesMillisats).roundToLong(),
            metadata = metadataWithPayerData,
        ).await()
        return PayReqResponse(
            encodedInvoice = invoice,
            compliance = PayReqResponseCompliance(
                utxos = receiverChannelUtxos,
                nodePubKey = receiverNodePubKey,
                utxoCallback = utxoCallback,
            ),
            paymentInfo = PayReqResponsePaymentInfo(
                currencyCode = currencyCode,
                decimals = currencyDecimals,
                multiplier = conversionRate,
                exchangeFeesMillisatoshi = receiverFeesMillisats,
            ),
        )
    }

    fun parseAsPayReqResponse(response: String): PayReqResponse {
        return Json.decodeFromString(response)
    }

    @Throws(Exception::class)
    private fun signPayload(payload: ByteArray, privateKey: ByteArray): String {
        return Secp256k1.signEcdsa(payload, privateKey).toHexString()
    }

    @Throws(Exception::class)
    private fun verifySignature(payload: ByteArray, signature: String, publicKey: ByteArray): Boolean {
        return Secp256k1.verifyEcdsa(payload, signature.hexToByteArray(), publicKey)
    }

    fun getVaspDomainFromUmaAddress(identifier: String): String {
        val atIndex = identifier.indexOf('@')
        if (atIndex == -1) {
            throw IllegalArgumentException("Invalid identifier: $identifier. Must be of format \$user@domain.com")
        }
        return identifier.substring(atIndex + 1)
    }
}

interface UmaInvoiceCreator {
    /**
     * Creates an invoice with the given amount and encoded LNURL metadata.
     *
     * @param amountMsats The amount of the invoice in millisatoshis.
     * @param metadata The metadata that will be added to the invoice's metadata hash field.
     * @return The encoded BOLT-11 invoice that should be returned to the sender for the given [PayRequest] wrapped in a
     *     [CompletableFuture].
     */
    fun createUmaInvoice(amountMsats: Long, metadata: String): CompletableFuture<String>
}

interface SyncUmaInvoiceCreator {
    /**
     * Synchronously creates an invoice with the given amount and encoded LNURL metadata.
     *
     * This method is synchronous and should only be used in cases where the caller is already on a background thread.
     *
     * @param amountMsats The amount of the invoice in millisatoshis.
     * @param metadata The metadata that will be added to the invoice's metadata hash field.
     * @return The encoded BOLT-11 invoice that should be returned to the sender for the given [PayRequest].
     */
    fun createUmaInvoice(amountMsats: Long, metadata: String): String
}
