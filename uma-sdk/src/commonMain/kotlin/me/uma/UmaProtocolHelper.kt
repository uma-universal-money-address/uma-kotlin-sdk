@file:OptIn(ExperimentalStdlibApi::class)

package me.uma

import me.uma.crypto.Secp256k1
import me.uma.protocol.*
import me.uma.utils.isDomainLocalhost
import me.uma.utils.serialFormat
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

/**
 * A helper class for interacting with the UMA protocol. It provides methods for creating and verifying UMA requests
 * and responses.
 */
@OptIn(ExperimentalStdlibApi::class)
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
        val pubKeyResponse = parseAsPubKeyResponse(response)
        publicKeyCache.addPublicKeysForVasp(vaspDomain, pubKeyResponse)
        return pubKeyResponse
    }

    fun parseAsPubKeyResponse(response: String): PubKeyResponse {
        return serialFormat.decodeFromString(response)
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
        ).asUmaRequest() ?: throw IllegalArgumentException("Invalid LnurlpRequest")
        val signature = signPayload(unsignedRequest.signablePayload(), signingPrivateKey)
        return unsignedRequest.signedWith(signature).encodeToUrl()
    }

    /**
     * @return true if the given URL is a valid UMA Lnurlp query, false otherwise.
     */
    fun isUmaLnurlpQuery(url: String): Boolean {
        return try {
            val request = parseLnurlpRequest(url)
            request.asUmaRequest() != null
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
        query: UmaLnurlpRequest,
        pubKeyResponse: PubKeyResponse,
        nonceCache: NonceCache,
    ): Boolean {
        nonceCache.checkAndSaveNonce(query.nonce, query.timestamp)
        return verifySignature(
            query.signablePayload(),
            query.signature,
            pubKeyResponse.getSigningPublicKey(),
        )
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
     * @param commentCharsAllowed The number of characters that the sender can include in the comment field of the pay
     *    request.
     * @param nostrPubkey An optional nostr pubkey used for nostr zaps (NIP-57). If set, it should be a valid
     *    BIP-340 public key in hex format.
     * @return The [LnurlpResponse] that should be sent to the sender for the given [LnurlpRequest].
     * @throws IllegalArgumentException if the receiverAddress is not in the format of "user@domain".
     */
    @JvmOverloads
    fun getLnurlpResponse(
        query: LnurlpRequest,
        privateKeyBytes: ByteArray,
        requiresTravelRuleInfo: Boolean,
        callback: String,
        encodedMetadata: String,
        minSendableSats: Long,
        maxSendableSats: Long,
        payerDataOptions: CounterPartyDataOptions?,
        currencyOptions: List<Currency>?,
        receiverKycStatus: KycStatus?,
        commentCharsAllowed: Int? = null,
        nostrPubkey: String? = null,
    ): LnurlpResponse {
        val umaRequest = query.asUmaRequest() ?: return LnurlpResponse(
            callback = callback,
            minSendable = minSendableSats * 1000,
            maxSendable = maxSendableSats * 1000,
            metadata = encodedMetadata,
            currencies = currencyOptions,
            requiredPayerData = payerDataOptions,
            compliance = null,
            umaVersion = null,
        )
        requireNotNull(receiverKycStatus) { "Receiver KYC status is required for UMA" }
        requireNotNull(currencyOptions) { "Currency options are required for UMA" }
        val complianceResponse =
            getSignedLnurlpComplianceResponse(query, privateKeyBytes, requiresTravelRuleInfo, receiverKycStatus)
        val umaVersion = minOf(Version.parse(umaRequest.umaVersion), Version.parse(UMA_VERSION_STRING)).toString()
        val currencies = if (Version.parse(umaVersion).major < 1) {
            currencyOptions.map {
                if (it is CurrencyV1) {
                    CurrencyV0(
                        code = it.code,
                        name = it.name,
                        symbol = it.symbol,
                        millisatoshiPerUnit = it.millisatoshiPerUnit,
                        minSendable = it.convertible.min,
                        maxSendable = it.convertible.max,
                        decimals = it.decimals,
                    )
                } else {
                    it
                }
            }
        } else {
            currencyOptions
        }
        val umaPayerDataOptions = payerDataOptions?.toMutableMap() ?: mutableMapOf()
        umaPayerDataOptions.putIfAbsent("compliance", CounterPartyDataOption(true))
        umaPayerDataOptions.putIfAbsent("identifier", CounterPartyDataOption(true))
        return LnurlpResponse(
            callback = callback,
            minSendable = minSendableSats * 1000,
            maxSendable = maxSendableSats * 1000,
            metadata = encodedMetadata,
            currencies = currencies,
            requiredPayerData = umaPayerDataOptions,
            compliance = complianceResponse,
            umaVersion = umaVersion,
            commentCharsAllowed = commentCharsAllowed,
            nostrPubkey = nostrPubkey,
            allowsNostr = nostrPubkey != null,
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
        return serialFormat.decodeFromString(response)
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
        response: UmaLnurlpResponse,
        pubKeyResponse: PubKeyResponse,
        nonceCache: NonceCache,
    ): Boolean {
        nonceCache.checkAndSaveNonce(response.compliance.signatureNonce, response.compliance.signatureTimestamp)
        return verifySignature(
            response.compliance.signablePayload(),
            response.compliance.signature,
            pubKeyResponse.getSigningPublicKey(),
        )
    }

    /**
     * Creates a signed UMA [PayRequest].
     *
     * @param receiverEncryptionPubKey The public key of the receiver that will be used to encrypt the travel rule
     *     information.
     * @param sendingVaspPrivateKey The private key of the VASP that is sending the payment. This will be used to sign
     *     the request.
     * @param receivingCurrencyCode The code of the currency that the receiver will receive for this payment.
     * @param amount The amount that the receiver will receive in either the smallest unit of the receiving currency
     *     (if `isAmountInReceivingCurrency` is True), or in msats (if false).
     * @param isAmountInReceivingCurrency Whether the amount field is specified in the smallest unit of the receiving
     *     currency or in msats (if false).
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
     * @param requestedPayeeData The data that the sender requests the receiver to send to identify themselves.
     * @param comment A comment that the sender would like to include with the payment. This can only be included
     *    if the receiver included the `commentAllowed` field in the lnurlp response. The length of the comment must be
     *    less than or equal to the value of `commentAllowed`.
     * @param receiverUmaVersion The UMA version of the receiver VASP. This information can be obtained from the [LnurlpResponse]
     * @return The [PayRequest] that should be sent to the receiver.
     */
    @JvmOverloads
    fun getPayRequest(
        receiverEncryptionPubKey: ByteArray,
        sendingVaspPrivateKey: ByteArray,
        receivingCurrencyCode: String,
        amount: Long,
        isAmountInReceivingCurrency: Boolean,
        payerIdentifier: String,
        payerKycStatus: KycStatus,
        utxoCallback: String,
        travelRuleInfo: String? = null,
        payerUtxos: List<String>? = null,
        payerNodePubKey: String? = null,
        payerName: String? = null,
        payerEmail: String? = null,
        travelRuleFormat: TravelRuleFormat? = null,
        requestedPayeeData: CounterPartyDataOptions? = null,
        comment: String? = null,
        invoiceUUID: String? = null,
        receiverUmaVersion: String = UMA_VERSION_STRING,
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
        val payerData = createPayerData(
            identifier = payerIdentifier,
            name = payerName,
            email = payerEmail,
            compliance = compliancePayerData,
        )
        if (Version.parse(receiverUmaVersion).major < 1) {
            return PayRequestV0(
                currencyCode = receivingCurrencyCode,
                amount = amount,
                payerData = payerData,
            )
        } else {
            return PayRequestV1(
                sendingCurrencyCode = if (isAmountInReceivingCurrency) receivingCurrencyCode else null,
                payerData = payerData,
                receivingCurrencyCode = receivingCurrencyCode,
                amount = amount,
                requestedPayeeData = requestedPayeeData,
                comment = comment,
                invoiceUUID = invoiceUUID
            )
        }
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
            encryptedTravelRuleInfo = travelRuleInfo?.let {
                encryptTravelRuleInfo(
                    receiverEncryptionPubKey,
                    it,
                )
            },
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
        return Secp256k1.encryptEcies(
            travelRuleInfoJson.encodeToByteArray(),
            receiverEncryptionPubKey,
        ).toHexString()
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
        return serialFormat.decodeFromString(PayRequestSerializer, request)
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
    fun verifyPayReqSignature(payReq: PayRequest, pubKeyResponse: PubKeyResponse, nonceCache: NonceCache): Boolean {
        if (!payReq.isUmaRequest()) return false
        val compliance = payReq.payerData?.compliance() ?: return false
        nonceCache.checkAndSaveNonce(compliance.signatureNonce, compliance.signatureTimestamp)
        return verifySignature(
            payReq.signablePayload(),
            compliance.signature,
            pubKeyResponse.getSigningPublicKey(),
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
     * @param receivingCurrencyCode The code of the currency that the receiver will receive for this payment.
     * @param receivingCurrencyDecimals The number of digits after the decimal point for the receiving currency. For example,
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
     * @param receivingVaspPrivateKey The signing private key of the VASP that is receiving the payment. This will be
     *      used to sign the response.
     * @param payeeData The data requested by the sending VASP about the receiver.
     * @param disposable This field may be used by a WALLET to decide whether the initial LNURL link will be stored
     *      locally for later reuse or erased. If disposable is null, it should be interpreted as true, so if SERVICE
     *      intends its LNURL links to be stored it must return `disposable: false`. UMA should always return
     *      `disposable: false`. See LUD-11.
     * @param successAction An action that the wallet should take once the payment is complete. See LUD-09.
     * @param senderUmaVersion The UMA version of the sender VASP. This information can be obtained from the [LnurlpRequest].
     * @return A [CompletableFuture] [PayReqResponse] that should be returned to the sender for the given [PayRequest].
     */
    @JvmOverloads
    @Throws(IllegalArgumentException::class, CancellationException::class)
    fun getPayReqResponseFuture(
        query: PayRequest,
        invoiceCreator: UmaInvoiceCreator,
        metadata: String,
        receivingCurrencyCode: String?,
        receivingCurrencyDecimals: Int?,
        conversionRate: Double?,
        receiverFeesMillisats: Long?,
        receiverChannelUtxos: List<String>?,
        receiverNodePubKey: String?,
        utxoCallback: String?,
        receivingVaspPrivateKey: ByteArray?,
        payeeData: PayeeData? = null,
        disposable: Boolean? = null,
        successAction: Map<String, String>? = null,
        senderUmaVersion: String = UMA_VERSION_STRING,
    ): CompletableFuture<PayReqResponse> = coroutineScope.future {
        getPayReqResponse(
            query,
            invoiceCreator,
            metadata,
            receivingCurrencyCode,
            receivingCurrencyDecimals,
            conversionRate,
            receiverFeesMillisats,
            receiverChannelUtxos,
            receiverNodePubKey,
            utxoCallback,
            receivingVaspPrivateKey,
            payeeData,
            disposable,
            successAction,
            senderUmaVersion,
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
     * @param receivingCurrencyCode The code of the currency that the receiver will receive for this payment.
     * @param receivingCurrencyDecimals The number of digits after the decimal point for the receiving currency. For example,
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
     * @param receivingVaspPrivateKey The signing private key of the VASP that is receiving the payment. This will be
     *      used to sign the response.
     * @param payeeData The data requested by the sending VASP about the receiver.
     * @param disposable This field may be used by a WALLET to decide whether the initial LNURL link will be stored
     *      locally for later reuse or erased. If disposable is null, it should be interpreted as true, so if SERVICE
     *      intends its LNURL links to be stored it must return `disposable: false`. UMA should always return
     *      `disposable: false`. See LUD-11.
     * @param successAction An action that the wallet should take once the payment is complete. See LUD-09.
     * @param senderUmaVersion The UMA version of the sender VASP. This information can be obtained from the [LnurlpRequest].
     * @return A [PayReqResponse] that should be returned to the sender for the given [PayRequest].
     */
    @JvmOverloads
    @Throws(Exception::class, IllegalArgumentException::class, CancellationException::class)
    fun getPayReqResponseSync(
        query: PayRequest,
        invoiceCreator: SyncUmaInvoiceCreator,
        metadata: String,
        receivingCurrencyCode: String?,
        receivingCurrencyDecimals: Int?,
        conversionRate: Double?,
        receiverFeesMillisats: Long?,
        receiverChannelUtxos: List<String>?,
        receiverNodePubKey: String?,
        utxoCallback: String?,
        receivingVaspPrivateKey: ByteArray?,
        payeeData: PayeeData? = null,
        disposable: Boolean? = null,
        successAction: Map<String, String>? = null,
        senderUmaVersion: String = UMA_VERSION_STRING,
    ): PayReqResponse = runBlocking {
        val futureInvoiceCreator = object : UmaInvoiceCreator {
            override fun createUmaInvoice(
                amountMsats: Long,
                metadata: String,
                receiverIdentifier: String?,
            ): CompletableFuture<String> {
                return coroutineScope.future {
                    invoiceCreator.createUmaInvoice(amountMsats, metadata, receiverIdentifier)
                }
            }
        }
        getPayReqResponse(
            query,
            futureInvoiceCreator,
            metadata,
            receivingCurrencyCode,
            receivingCurrencyDecimals,
            conversionRate,
            receiverFeesMillisats,
            receiverChannelUtxos,
            receiverNodePubKey,
            utxoCallback,
            receivingVaspPrivateKey,
            payeeData,
            disposable,
            successAction,
            senderUmaVersion,
        )
    }

    /**
     * Creates an uma pay request response with an encoded invoice.
     *
     * @param query The [PayRequest] sent by the sender.
     * @param invoiceCreator The [UmaInvoiceCreator] that will be used to create the invoice.
     * @param metadata The metadata that will be added to the invoice's metadata hash field.
     * @param receivingCurrencyCode The code of the currency that the receiver will receive for this payment.
     * @param receivingCurrencyDecimals The number of digits after the decimal point for the receiving currency. For example,
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
     * @param receivingVaspPrivateKey The signing private key of the VASP that is receiving the payment. This will be
     *      used to sign the response.
     * @param payeeData The data requested by the sending VASP about the receiver.
     * @param disposable This field may be used by a WALLET to decide whether the initial LNURL link will be stored
     *      locally for later reuse or erased. If disposable is null, it should be interpreted as true, so if SERVICE
     *      intends its LNURL links to be stored it must return `disposable: false`. UMA should always return
     *      `disposable: false`. See LUD-11.
     * @param successAction An action that the wallet should take once the payment is complete. See LUD-09.
     * @param senderUmaVersion The UMA version of the sender VASP. This information can be obtained from the [LnurlpRequest].
     * @return The [PayReqResponse] that should be returned to the sender for the given [PayRequest].
     */
    @JvmName("KotlinOnly-getPayReqResponseSuspended")
    suspend fun getPayReqResponse(
        query: PayRequest,
        invoiceCreator: UmaInvoiceCreator,
        metadata: String,
        receivingCurrencyCode: String?,
        receivingCurrencyDecimals: Int?,
        conversionRate: Double?,
        receiverFeesMillisats: Long?,
        receiverChannelUtxos: List<String>?,
        receiverNodePubKey: String?,
        utxoCallback: String?,
        receivingVaspPrivateKey: ByteArray?,
        payeeData: PayeeData? = null,
        disposable: Boolean? = null,
        successAction: Map<String, String>? = null,
        senderUmaVersion: String = UMA_VERSION_STRING,
    ): PayReqResponse {
        val encodedPayerData = query.payerData?.let(serialFormat::encodeToString) ?: ""
        val encodedInvoiceUUID = query.invoiceUUID()?.let(serialFormat::encodeToString) ?: ""
        val metadataWithPayerData = "$metadata$encodedPayerData$encodedInvoiceUUID"
        if (query.sendingCurrencyCode() != null && query.sendingCurrencyCode() != receivingCurrencyCode) {
            throw IllegalArgumentException(
                "Currency code in the pay request must match the receiving currency if not null.",
            )
        }
        val requiredUmaFields: Map<String, Any?> = mapOf(
            "receivingCurrencyCode" to receivingCurrencyCode,
            "receivingCurrencyDecimals" to receivingCurrencyDecimals,
            "conversionRate" to conversionRate,
            "receiverFeesMillisats" to receiverFeesMillisats,
            "payerData identifier" to query.payerData?.identifier(),
            "payeeData identifier" to payeeData?.identifier(),
            "receivingVaspPrivateKey" to receivingVaspPrivateKey,
        )
        if (query.isUmaRequest()) {
            val missingFields = requiredUmaFields.filterValues { it == null }.keys
            if (missingFields.isNotEmpty()) {
                throw IllegalArgumentException("Missing required fields for UMA: $missingFields")
            }
        }
        val isAmountInMsats = query.sendingCurrencyCode() == null
        val receivingCurrencyAmount = if (isAmountInMsats) {
            ((query.amount.toDouble() - (receiverFeesMillisats ?: 0)) / (conversionRate ?: 1.0)).roundToLong()
        } else {
            query.amount
        }
        val invoice = invoiceCreator.createUmaInvoice(
            amountMsats = if (isAmountInMsats) {
                query.amount
            } else {
                (query.amount.toDouble() * (conversionRate ?: 1.0) + (receiverFeesMillisats ?: 0)).roundToLong()
            },
            metadata = metadataWithPayerData,
            receiverIdentifier = payeeData?.identifier(),
        ).await()
        val mutablePayeeData = payeeData?.toMutableMap() ?: mutableMapOf()
        if (query.isUmaRequest()) {
            mutablePayeeData["compliance"] = serialFormat.encodeToJsonElement(
                getSignedCompliancePayeeData(
                    receiverChannelUtxos ?: emptyList(),
                    receiverNodePubKey,
                    receivingVaspPrivateKey!!,
                    payerIdentifier = query.payerData!!.identifier()!!,
                    payeeIdentifier = payeeData?.identifier()
                        ?: throw IllegalArgumentException("Payee identifier is required for UMA"),
                    utxoCallback ?: "",
                ),
            )
        }
        val hasPaymentInfo =
            receivingCurrencyCode != null && receivingCurrencyDecimals != null && conversionRate != null
        if (Version.parse(senderUmaVersion).major < 1) {
            if (!hasPaymentInfo) {
                throw IllegalArgumentException("Payment info is required for UMAv0")
            }
            return PayReqResponseV0(
                encodedInvoice = invoice,
                compliance = PayReqResponseCompliance(
                    utxos = receiverChannelUtxos ?: emptyList(),
                    nodePubKey = receiverNodePubKey,
                    utxoCallback = utxoCallback ?: "",
                ),
                paymentInfo = V0PayReqResponsePaymentInfo(
                    currencyCode = receivingCurrencyCode!!,
                    decimals = receivingCurrencyDecimals!!,
                    multiplier = conversionRate!!,
                    exchangeFeesMillisatoshi = receiverFeesMillisats ?: 0,
                ),
            )
        }
        return PayReqResponseV1(
            encodedInvoice = invoice,
            payeeData = if (query.isUmaRequest()) JsonObject(mutablePayeeData) else null,
            paymentInfo = if (hasPaymentInfo) {
                V1PayReqResponsePaymentInfo(
                    currencyCode = receivingCurrencyCode!!,
                    decimals = receivingCurrencyDecimals!!,
                    multiplier = conversionRate!!,
                    exchangeFeesMillisatoshi = receiverFeesMillisats ?: 0,
                    amount = receivingCurrencyAmount,
                )
            } else {
                null
            },
            disposable = disposable,
            successAction = successAction,
        )
    }

    private fun getSignedCompliancePayeeData(
        receiverChannelUtxos: List<String>,
        receiverNodePubKey: String?,
        receivingVaspPrivateKey: ByteArray,
        payerIdentifier: String,
        payeeIdentifier: String,
        utxoCallback: String,
    ): CompliancePayeeData {
        val nonce = generateNonce()
        val timestamp = System.currentTimeMillis() / 1000
        val unsignedCompliancePayeeData = CompliancePayeeData(
            utxos = receiverChannelUtxos,
            nodePubKey = receiverNodePubKey,
            utxoCallback = utxoCallback,
            signature = "",
            signatureNonce = nonce,
            signatureTimestamp = timestamp,
        )
        val signablePayload = "$payerIdentifier|$payeeIdentifier|$nonce|$timestamp".encodeToByteArray()
        val signature = signPayload(signablePayload, receivingVaspPrivateKey)
        return unsignedCompliancePayeeData.signedWith(signature)
    }

    fun parseAsPayReqResponse(response: String): PayReqResponse {
        return serialFormat.decodeFromString(PayReqResponseSerializer, response)
    }

    /**
     * Verifies the signature of the [PayReqResponse] sent by the receiver.
     *
     * @param payReqResponse The [PayReqResponse] sent by the receiver.
     * @param pubKeyResponse The [PubKeyResponse] that contains the public key of the receiver.
     * @param payerIdentifier The identifier of the sender. For example, $alice@vasp1.com
     * @param nonceCache The persistent [NonceCache] implementation that will cache previously seen nonces.
     * @return true if the signature is valid, false otherwise.
     * @throws InvalidNonceException if the nonce has already been used/timestamp is too old.
     */
    @Throws(InvalidNonceException::class)
    fun verifyPayReqResponseSignature(
        payReqResponse: PayReqResponse,
        pubKeyResponse: PubKeyResponse,
        payerIdentifier: String,
        nonceCache: NonceCache,
    ): Boolean {
        if (payReqResponse !is PayReqResponseV1) return true
        if (!payReqResponse.isUmaResponse()) return false
        val compliance = payReqResponse.payeeData?.payeeCompliance() ?: return false
        nonceCache.checkAndSaveNonce(compliance.signatureNonce, compliance.signatureTimestamp)
        return verifySignature(
            payReqResponse.signablePayload(payerIdentifier),
            compliance.signature,
            pubKeyResponse.getSigningPublicKey(),
        )
    }

    /**
     * Creates a signed [PostTransactionCallback].
     *
     * @param utxos UTXOs of the VASP sending the callback.
     * @param vaspDomain Domain name of the VASP sending the callback.
     * @param vaspPrivateKey The private signing key of the VASP sending the callback. Used to sign the message.
     * @return the [PostTransactionCallback] to be sent to the counterparty.
     */
    fun getPostTransactionCallback(
        utxos: List<UtxoWithAmount>,
        vaspDomain: String,
        signingPrivateKey: ByteArray,
    ): PostTransactionCallback {
        val nonce = generateNonce()
        val timestamp = System.currentTimeMillis() / 1000
        val unsignedCallback = PostTransactionCallback(
            utxos = utxos,
            vaspDomain = vaspDomain,
            signature = "",
            signatureNonce = nonce,
            signatureTimestamp = timestamp,
        )
        val signature = signPayload(unsignedCallback.signablePayload(), signingPrivateKey)
        return unsignedCallback.signedWith(signature)
    }

    /**
     * Verifies the signature of the [PostTransactionCallback] sent by the counterparty.
     *
     * @param postTransactionCallback The [PostTransactionCallback] sent by the counterparty.
     * @param pubKeyResponse The [PubKeyResponse] that contains the public key of the counterparty.
     * @param nonceCache The persistent [NonceCache] implementation that will cache previously seen nonces.
     * @return true if the signature is valid, false otherwise.
     * @throws InvalidNonceException if the nonce has already been used/timestamp is too old.
     */
    @Throws(InvalidNonceException::class)
    fun verifyPostTransactionCallbackSignature(
        postTransactionCallback: PostTransactionCallback,
        pubKeyResponse: PubKeyResponse,
        nonceCache: NonceCache,
    ): Boolean {
        nonceCache.checkAndSaveNonce(
            postTransactionCallback.signatureNonce,
            postTransactionCallback.signatureTimestamp,
        )
        return verifySignature(
            postTransactionCallback.signablePayload(),
            postTransactionCallback.signature,
            pubKeyResponse.getSigningPublicKey(),
        )
    }

    fun parseAsPostTransactionCallback(callback: String): PostTransactionCallback {
        return serialFormat.decodeFromString(callback)
    }

    @Throws(Exception::class)
    private fun signPayload(payload: ByteArray, privateKey: ByteArray): String {
        return Secp256k1.signEcdsa(payload, privateKey).toHexString()
    }

    @Throws(Exception::class)
    private fun verifySignature(payload: ByteArray, signature: String, publicKey: ByteArray): Boolean {
        return Secp256k1.verifyEcdsa(payload, signature.hexToByteArray(), publicKey)
    }

    @Throws(Exception::class)
    private fun verifySignature(payload: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return Secp256k1.verifyEcdsa(payload, signature, publicKey)
    }

    fun getVaspDomainFromUmaAddress(identifier: String): String {
        val atIndex = identifier.indexOf('@')
        if (atIndex == -1) {
            throw IllegalArgumentException("Invalid identifier: $identifier. Must be of format \$user@domain.com")
        }
        return identifier.substring(atIndex + 1)
    }

    fun verifyUmaInvoice(invoice: Invoice, pubKeyResponse: PubKeyResponse): Boolean {
        return invoice.signature?.let { signature ->
            verifySignature(
                invoice.toSignablePayload(),
                signature,
                pubKeyResponse.getSigningPublicKey(),
            )
        } ?: false
    }

    fun getInvoice(
        receiverUma: String,
        invoiceUUID: String,
        amount: Long,
        receivingCurrency: InvoiceCurrency,
        expiration: Long,
        isSubjectToTravelRule: Boolean,
        commentCharsAllowed: Int? = null,
        senderUma: String? = null,
        invoiceLimit: Int? = null,
        callback: String,
        privateSigningKey: ByteArray,
        kycStatus: KycStatus? = null,
        requiredPayerData: CounterPartyDataOptions? = null,
    ): Invoice {
        return Invoice(
            receiverUma = receiverUma,
            invoiceUUID = invoiceUUID,
            amount = amount,
            receivingCurrency = receivingCurrency,
            expiration = expiration,
            isSubjectToTravelRule = isSubjectToTravelRule,
            umaVersion = UMA_VERSION_STRING,
            commentCharsAllowed = commentCharsAllowed,
            senderUma = senderUma,
            invoiceLimit = invoiceLimit,
            callback = callback,
            kycStatus = kycStatus,
            requiredPayerData = requiredPayerData,
        ).apply {
            signature = Secp256k1.signEcdsa(toSignablePayload(), privateSigningKey)
        }
    }
}

interface UmaInvoiceCreator {
    /**
     * Creates an invoice with the given amount and encoded LNURL metadata.
     *
     * @param amountMsats The amount of the invoice in millisatoshis.
     * @param metadata The metadata that will be added to the invoice's metadata hash field.
     * @param receiverIdentifier Optional identifier of the receiver.
     * @return The encoded BOLT-11 invoice that should be returned to the sender for the given [PayRequest] wrapped in a
     *     [CompletableFuture].
     */
    fun createUmaInvoice(amountMsats: Long, metadata: String, receiverIdentifier: String?): CompletableFuture<String>
}

interface SyncUmaInvoiceCreator {
    /**
     * Synchronously creates an invoice with the given amount and encoded LNURL metadata.
     *
     * This method is synchronous and should only be used in cases where the caller is already on a background thread.
     *
     * @param amountMsats The amount of the invoice in millisatoshis.
     * @param metadata The metadata that will be added to the invoice's metadata hash field.
     * @param receiverIdentifier Optional identifier of the receiver.
     * @return The encoded BOLT-11 invoice that should be returned to the sender for the given [PayRequest].
     */
    fun createUmaInvoice(amountMsats: Long, metadata: String, receiverIdentifier: String?): String
}
