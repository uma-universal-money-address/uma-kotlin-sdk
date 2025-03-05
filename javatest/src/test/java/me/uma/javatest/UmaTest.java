package me.uma.javatest;

import kotlin.coroutines.Continuation;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElementKt;
import kotlinx.serialization.json.JsonObject;
import me.uma.*;
import me.uma.protocol.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;
import static me.uma.protocol.CurrencyUtils.createCurrency;
import static me.uma.utils.SerializationKt.getSerialFormat;

public class UmaTest {
    PublicKeyCache publicKeyCache = new InMemoryPublicKeyCache();
    UmaProtocolHelper umaProtocolHelper = new UmaProtocolHelper(publicKeyCache, new TestUmaRequester());
    private static final String PUBKEY_HEX = "04419c5467ea563f0010fd614f85e885ac99c21b8e8d416241175fdd5efd2244fe907e2e6fa3dd6631b1b17cd28798da8d882a34c4776d44cc4090781c7aadea1b";
    private static final String PRIVKEY_HEX = "77e891f0ecd265a3cda435eaa73792233ebd413aeb0dbb66f2940babfc9a2667";
    private static final String encodedPayReqMetadata = "[[\"text/uma-invoice\",\"invoiceUUID\"],[\"text/plain\",\"otherInformations\"]]";

    private static final String CERT_CHAIN = "-----BEGIN CERTIFICATE-----\n" +
            "MIIB1zCCAXygAwIBAgIUGN3ihBj1RnKoeTM/auDFnNoThR4wCgYIKoZIzj0EAwIw\n" +
            "QjELMAkGA1UEBhMCVVMxEzARBgNVBAgMCmNhbGlmb3JuaWExDjAMBgNVBAcMBWxv\n" +
            "cyBhMQ4wDAYDVQQKDAVsaWdodDAeFw0yNDAzMDUyMTAzMTJaFw0yNDAzMTkyMTAz\n" +
            "MTJaMEIxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApjYWxpZm9ybmlhMQ4wDAYDVQQH\n" +
            "DAVsb3MgYTEOMAwGA1UECgwFbGlnaHQwVjAQBgcqhkjOPQIBBgUrgQQACgNCAARB\n" +
            "nFRn6lY/ABD9YU+F6IWsmcIbjo1BYkEXX91e/SJE/pB+Lm+j3WYxsbF80oeY2o2I\n" +
            "KjTEd21EzECQeBx6reobo1MwUTAdBgNVHQ4EFgQUU87LnQdiP6XIE6LoKU1PZnbt\n" +
            "bMwwHwYDVR0jBBgwFoAUU87LnQdiP6XIE6LoKU1PZnbtbMwwDwYDVR0TAQH/BAUw\n" +
            "AwEB/zAKBggqhkjOPQQDAgNJADBGAiEAvsrvoeo3rbgZdTHxEUIgP0ArLyiO34oz\n" +
            "NlwL4gk5GpgCIQCvRx4PAyXNV9T6RRE+3wFlqwluOc/pPOjgdRw/wpoNPQ==\n" +
            "-----END CERTIFICATE-----\n" +
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICdjCCAV6gAwIBAgIUAekCcU1Qhjo2Y6L2Down9BLdfdUwDQYJKoZIhvcNAQEL\n" +
            "BQAwNDELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAmNhMQwwCgYDVQQHDANsb3MxCjAI\n" +
            "BgNVBAoMAWEwHhcNMjQwMzA4MDEwNTU3WhcNMjUwMzA4MDEwNTU3WjBAMQswCQYD\n" +
            "VQQGEwJVUzELMAkGA1UECAwCY2ExDDAKBgNVBAcMA2xvczEKMAgGA1UECgwBYTEK\n" +
            "MAgGA1UECwwBYTBWMBAGByqGSM49AgEGBSuBBAAKA0IABJ11ZAQKylgIzZmuI5NE\n" +
            "+DyZ9BUDZhxUPSxTxl+s1am+Lxzr9D7wlwOiiqCYHFWpL6lkCmJcCC06P3RyzXIT\n" +
            "KmyjQjBAMB0GA1UdDgQWBBRXgW6xGB3+mTSSUKlhSiu3LS+TKTAfBgNVHSMEGDAW\n" +
            "gBTFmyv7+YDpK0WAOHJYAzjynmWsMDANBgkqhkiG9w0BAQsFAAOCAQEAFVAA3wo+\n" +
            "Hi/k+OWO/1CFqIRV/0cA8F05sBMiKVA11xB6I1y54aUV4R0jN76fOiN1jnZqTRnM\n" +
            "G8rZUfQgE/LPVbb1ERHQfd8yaeI+TerKdPkMseu/jnvI+dDJfQdsY7iaa7NPO0dm\n" +
            "t8Nz75cYW8kYuDaq0Hb6uGsywf9LGO/VjrDhyiRxmZ1Oq4JxQmLuh5SDcPfqHTR3\n" +
            "VbMC1b7eVXaA9O2qYS36zv8cCUSUl5sOSwM6moaFN+xLtVNJ6ZhKPNS2Gd8znhzZ\n" +
            "AQZcDDpXBO6ORNbhVk5A3X6eQX4Ek1HBTa3pcSUQomYAA9TIuVzL6DSot5GWS8Ek\n" +
            "usLY8crt6ys3KQ==\n" +
            "-----END CERTIFICATE-----";

    @Test
    public void testFetchPublicKeySync() throws Exception {
        PubKeyResponse pubKeys = umaProtocolHelper.fetchPublicKeysForVaspSync("https://vasp.com");
        assertNotNull(pubKeys);
        System.out.println(pubKeys);
    }

    @Test
    public void testFetchPublicKeyFuture() throws Exception {
        PubKeyResponse pubKeys = umaProtocolHelper.fetchPublicKeysForVaspFuture("https://vasp.com").get();
        assertNotNull(pubKeys);
        System.out.println(pubKeys);
    }

    @Test
    public void testGetLnurlpRequest() throws Exception {
        String lnurlpUrl = umaProtocolHelper.getSignedLnurlpRequestUrl(
                privateKeyBytes(),
                "$bob@uma-test.yourvasp.com",
                /* senderVaspDomain */ "myvasp.com",
                /* isSubjectToTravelRule */ true
        );
        assertNotNull(lnurlpUrl);
        System.out.println(lnurlpUrl);
        LnurlpRequest request = umaProtocolHelper.parseLnurlpRequest(lnurlpUrl);
        assertNotNull(request);
        assertTrue(umaProtocolHelper.verifyUmaLnurlpQuerySignature(
                requireNonNull(request.asUmaRequest()), new PubKeyResponse(publicKeyBytes(), publicKeyBytes()),
                new InMemoryNonceCache(1L)));
        System.out.println(request);
    }

    @Test
    public void testGetLnurlpResponse_umaV1() throws Exception {
        String lnurlpUrl = umaProtocolHelper.getSignedLnurlpRequestUrl(
                privateKeyBytes(),
                "$bob@vasp2.com",
                "https://vasp.com",
                true);
        LnurlpRequest request = umaProtocolHelper.parseLnurlpRequest(lnurlpUrl);
        assertNotNull(request);

        LnurlpResponse lnurlpResponse = umaProtocolHelper.getLnurlpResponse(
                request,
                privateKeyBytes(),
                true,
                "https://vasp2.com/callback",
                "encoded metadata",
                1,
                10_000_000,
                CounterPartyData.createCounterPartyDataOptions(
                        Map.of(
                                "name", false,
                                "email", false
                        )
                ),
                List.of(
                        createCurrency(
                                "USD",
                                "US Dollar",
                                "$",
                                34_150,
                                2,
                                1,
                                10_000_000,
                                "1.0"
                        )
                ),
                KycStatus.VERIFIED
        );
        assertNotNull(lnurlpResponse);
        String responseJson = lnurlpResponse.toJson();
        System.out.println(responseJson);
        LnurlpResponse parsedResponse = umaProtocolHelper.parseAsLnurlpResponse(responseJson);
        assertNotNull(parsedResponse);
        assertEquals(lnurlpResponse, parsedResponse);
        assertNotNull(parsedResponse.asUmaResponse());
        assertEquals(1, parsedResponse.asUmaResponse().getCurrencies().get(0).minSendable());
        assertEquals(10_000_000, parsedResponse.asUmaResponse().getCurrencies().get(0).maxSendable());
        CounterPartyDataOption complianceOption = parsedResponse.asUmaResponse().getRequiredPayerData().get("compliance");
        assertNotNull(complianceOption);
        assertTrue(complianceOption.getMandatory());
        CounterPartyDataOption identifierOption = parsedResponse.asUmaResponse().getRequiredPayerData().get("identifier");
        assertNotNull(identifierOption);
        assertTrue(identifierOption.getMandatory());
        assertTrue(umaProtocolHelper.verifyLnurlpResponseSignature(
                requireNonNull(parsedResponse.asUmaResponse()), new PubKeyResponse(publicKeyBytes(), publicKeyBytes()),
                new InMemoryNonceCache(1L)));
    }

    @Test
    public void testGetLnurlpResponse_umaV0() throws Exception {
        String lnurlpUrl = umaProtocolHelper.getSignedLnurlpRequestUrl(
                privateKeyBytes(),
                "$bob@vasp2.com",
                "https://vasp.com",
                true);
        LnurlpRequest request = umaProtocolHelper.parseLnurlpRequest(lnurlpUrl);
        assertNotNull(request);
        LnurlpResponse lnurlpResponse = umaProtocolHelper.getLnurlpResponse(
                request,
                privateKeyBytes(),
                true,
                "https://vasp2.com/callback",
                "encoded metadata",
                1,
                10_000_000,
                CounterPartyData.createCounterPartyDataOptions(
                        Map.of(
                                "name", false,
                                "email", false,
                                "identity", true,
                                "compliance", true
                        )
                ),
                List.of(
                        createCurrency(
                                "USD",
                                "US Dollar",
                                "$",
                                34_150,
                                2,
                                1,
                                10_000_000,
                                "0.3"
                        )
                ),
                KycStatus.VERIFIED
        );
        assertNotNull(lnurlpResponse);
        String responseJson = lnurlpResponse.toJson();
        System.out.println(responseJson);
        LnurlpResponse parsedResponse = umaProtocolHelper.parseAsLnurlpResponse(responseJson);
        assertNotNull(parsedResponse);
        assertEquals(lnurlpResponse, parsedResponse);
        assertNotNull(parsedResponse.asUmaResponse());
        assertEquals(1, parsedResponse.asUmaResponse().getCurrencies().get(0).minSendable());
        assertEquals(10_000_000, parsedResponse.asUmaResponse().getCurrencies().get(0).maxSendable());
        assertTrue(umaProtocolHelper.verifyLnurlpResponseSignature(
                parsedResponse.asUmaResponse(), new PubKeyResponse(publicKeyBytes(), publicKeyBytes()),
                new InMemoryNonceCache(1L)));
    }

    @Test
    public void testSignAndVerifyLnurlpResponseWithBackingSignature() throws Exception {
        String lnurlpUrl = umaProtocolHelper.getSignedLnurlpRequestUrl(
                privateKeyBytes(),
                "$bob@vasp2.com",
                "https://vasp.com",
                true);
        LnurlpRequest request = umaProtocolHelper.parseLnurlpRequest(lnurlpUrl);
        assertNotNull(request);
        LnurlpResponse lnurlpResponse = umaProtocolHelper.getLnurlpResponse(
                request,
                privateKeyBytes(),
                true,
                "https://vasp2.com/callback",
                "encoded metadata",
                1,
                10_000_000,
                CounterPartyData.createCounterPartyDataOptions(
                        Map.of(
                                "name", false,
                                "email", false,
                                "identity", true,
                                "compliance", true
                        )
                ),
                List.of(
                        createCurrency(
                                "USD",
                                "US Dollar",
                                "$",
                                34_150,
                                2,
                                1,
                                10_000_000,
                                "0.3"
                        )
                ),
                KycStatus.VERIFIED
        );
        assertNotNull(lnurlpResponse);
        String backingDomain = "backingvasp.com";
        UmaLnurlpResponse umaResponse = lnurlpResponse.asUmaResponse();
        assertNotNull(umaResponse);
        UmaLnurlpResponse responseWithBackingSignature = umaResponse.appendBackingSignature(privateKeyBytes(), backingDomain);
        String responseJson = responseWithBackingSignature.toJson();
        LnurlpResponse parsedResponse = umaProtocolHelper.parseAsLnurlpResponse(responseJson);
        assertNotNull(parsedResponse);
        UmaLnurlpResponse parsedUmaResponse = parsedResponse.asUmaResponse();
        assertNotNull(parsedUmaResponse);
        assertNotNull(parsedUmaResponse.getBackingSignatures());
        assertEquals(1, parsedUmaResponse.getBackingSignatures().size());
        Long publicKeyCacheExpiry = System.currentTimeMillis() / 1000 + 10000;
        PubKeyResponse backingVaspPubKeyResponse = new PubKeyResponse(publicKeyBytes(), publicKeyBytes(), publicKeyCacheExpiry);
        publicKeyCache.addPublicKeysForVasp(backingDomain, backingVaspPubKeyResponse);
        assertTrue(umaProtocolHelper.verifyLnurlpResponseBackingSignaturesSync(parsedUmaResponse));
    }

    @Test
    public void testGetPayRequest_umaV1() throws Exception {
        PayRequest request = umaProtocolHelper.getPayRequest(
                publicKeyBytes(),
                privateKeyBytes(),
                "USD",
                100L,
                true,
                "$alice@vasp1.com",
                KycStatus.VERIFIED,
                "",
                null,
                null,
                null,
                "payerName",
                "payerEmail",
                null,
                null,
                "comment",
                "sample-uuid-string",
                "1.0"
        );
        assertNotNull(request);
        System.out.println(request);
        assertTrue(umaProtocolHelper.verifyPayReqSignature(
                request, new PubKeyResponse(publicKeyBytes(), publicKeyBytes()),
                new InMemoryNonceCache(1L)));
        String requestJson = request.toJson();
        PayRequest parsedRequest = umaProtocolHelper.parseAsPayRequest(requestJson);
        assertNotNull(parsedRequest);
        assertEquals(request, parsedRequest);
    }

    @Test
    public void testGetPayRequest_umaV0() throws Exception {
        PayRequest request = umaProtocolHelper.getPayRequest(
                publicKeyBytes(),
                privateKeyBytes(),
                "USD",
                100L,
                true,
                "$alice@vasp1.com",
                KycStatus.VERIFIED,
                "",
                null,
                null,
                null,
                "payerName",
                "payerEmail",
                null,
                null,
                "comment",
                "sample-uuid-string",
                "0.3"
        );
        assertNotNull(request);
        System.out.println(request);
        assertTrue(umaProtocolHelper.verifyPayReqSignature(
                request, new PubKeyResponse(publicKeyBytes(), publicKeyBytes()),
                new InMemoryNonceCache(1L)));
        String requestJson = request.toJson();
        PayRequest parsedRequest = umaProtocolHelper.parseAsPayRequest(requestJson);
        assertNotNull(parsedRequest);
        assertEquals(request, parsedRequest);
    }

    @Test
    public void testSignAndVerifyPayReqBackingSignatures() throws Exception {
        PayRequest request = umaProtocolHelper.getPayRequest(
                publicKeyBytes(),
                privateKeyBytes(),
                "USD",
                1000L,
                true,
                "$alice@vasp1.com",
                KycStatus.VERIFIED,
                "/api/lnurl/utxocallback?txid=1234"
        );
        String backingDomain = "backingvasp.com";
        PayRequest requestWithBackingSignatures = request.appendBackingSignature(privateKeyBytes(), backingDomain);
        String requestJson = requestWithBackingSignatures.toJson();
        PayRequest parsedRequest = umaProtocolHelper.parseAsPayRequest(requestJson);
        assertNotNull(parsedRequest);
        JsonObject payerData = parsedRequest.getPayerData();
        assertNotNull(payerData);
        CompliancePayerData complianceData = getSerialFormat().decodeFromJsonElement(
                CompliancePayerData.Companion.serializer(),
                payerData.get("compliance")
        );
        assertNotNull(complianceData);
        assertNotNull(complianceData.getBackingSignatures());
        assertEquals(1, complianceData.getBackingSignatures().size());
        Long publicKeyCacheExpiry = System.currentTimeMillis() / 1000 + 10000;
        PubKeyResponse backingVaspPubKeyResponse = new PubKeyResponse(publicKeyBytes(), publicKeyBytes(), publicKeyCacheExpiry);
        publicKeyCache.addPublicKeysForVasp(backingDomain, backingVaspPubKeyResponse);
        assertTrue(umaProtocolHelper.verifyPayReqBackingSignaturesSync(parsedRequest));
    }

    @Test
    public void testGetPayReqResponseSync_umaV1() throws Exception {
        PayRequest request = umaProtocolHelper.getPayRequest(
                publicKeyBytes(),
                privateKeyBytes(),
                "USD",
                100L,
                true,
                "$alice@vasp1.com",
                KycStatus.VERIFIED,
                ""
        );
        PayReqResponse response = umaProtocolHelper.getPayReqResponseSync(
                request,
                new TestSyncUmaInvoiceCreator(),
                encodedPayReqMetadata,
                "USD",
                2,
                12345.0,
                5L,
                List.of(),
                null,
                "",
                privateKeyBytes(),
                PayeeData.createPayeeData(null, "$bob@vasp2.com"),
                null,
                null,
                "1.0"
        );
        assertNotNull(response);
        assertEquals("lnbc12345", response.getEncodedInvoice());
        System.out.println(response);
        assertTrue(umaProtocolHelper.verifyPayReqResponseSignature(
                response, new PubKeyResponse(publicKeyBytes(), publicKeyBytes()),
                "$alice@vasp1.com", new InMemoryNonceCache(1L)));
        String responseJson = response.toJson();
        JsonObject json = Json.Default.decodeFromString(JsonObject.Companion.serializer(), responseJson);
        JsonObject paymentInfo = JsonElementKt.getJsonObject(json.get("converted"));
        assertEquals(5, JsonElementKt.getInt(JsonElementKt.getJsonPrimitive(paymentInfo.get("fee"))));
        PayReqResponse parsedResponse = umaProtocolHelper.parseAsPayReqResponse(responseJson);
        assertNotNull(parsedResponse);
        assertEquals(response, parsedResponse);
    }

    @Test
    public void testGetPayReqResponseSync_umaV0() throws Exception {
        PayRequest request = umaProtocolHelper.getPayRequest(
                publicKeyBytes(),
                privateKeyBytes(),
                "USD",
                100L,
                true,
                "$alice@vasp1.com",
                KycStatus.VERIFIED,
                ""
        );
        PayReqResponse response = umaProtocolHelper.getPayReqResponseSync(
                request,
                new TestSyncUmaInvoiceCreator(),
                encodedPayReqMetadata,
                "USD",
                2,
                12345.0,
                0L,
                List.of(),
                null,
                "",
                privateKeyBytes(),
                PayeeData.createPayeeData(null, "$bob@vasp2.com"),
                null,
                null,
                "0.3"
        );
        assertNotNull(response);
        assertEquals("lnbc12345", response.getEncodedInvoice());
        System.out.println(response);
        assertTrue(umaProtocolHelper.verifyPayReqResponseSignature(
                response, new PubKeyResponse(publicKeyBytes(), publicKeyBytes()),
                "$alice@vasp1.com", new InMemoryNonceCache(1L)));
        String responseJson = response.toJson();
        PayReqResponse parsedResponse = umaProtocolHelper.parseAsPayReqResponse(responseJson);
        assertNotNull(parsedResponse);
        assertEquals(response, parsedResponse);
    }

    @Test
    public void testSignAndVerifyPayReqResponseBackingSignatures() throws Exception {
        PayRequest request = umaProtocolHelper.getPayRequest(
                publicKeyBytes(),
                privateKeyBytes(),
                "USD",
                100L,
                true,
                "$alice@vasp1.com",
                KycStatus.VERIFIED,
                "/api/lnurl/utxocallback?txid=1234"
        );
        PayReqResponse response = umaProtocolHelper.getPayReqResponseSync(
                request,
                new TestSyncUmaInvoiceCreator(),
                encodedPayReqMetadata,
                "USD",
                2,
                24150.0,
                100000L,
                List.of("abcdef12345"),
                null,
                "/api/lnurl/utxocallback?txid=1234",
                privateKeyBytes(),
                PayeeData.createPayeeData(null, "$bob@vasp2.com"),
                null,
                null,
                "1.0"
        );
        String backingDomain = "backingvasp.com";
        PayReqResponse signedResponse = response.appendBackingSignature(privateKeyBytes(), backingDomain, "$alice@vasp1.com", "$bob@vasp2.com");
        String responseJson = signedResponse.toJson();
        PayReqResponse parsedResponse = umaProtocolHelper.parseAsPayReqResponse(responseJson);
        assertNotNull(parsedResponse);
        JsonObject payeeData = parsedResponse.payeeData();
        assertNotNull(payeeData);
        CompliancePayeeData complianceData = getSerialFormat().decodeFromJsonElement(
                CompliancePayeeData.Companion.serializer(),
                payeeData.get("compliance")
        );
        assertNotNull(complianceData);
        assertNotNull(complianceData.getBackingSignatures());
        assertEquals(1, complianceData.getBackingSignatures().size());
        Long publicKeyCacheExpiry = System.currentTimeMillis() / 1000 + 10000;
        PubKeyResponse backingVaspPubKeyResponse = new PubKeyResponse(publicKeyBytes(), publicKeyBytes(), publicKeyCacheExpiry);
        publicKeyCache.addPublicKeysForVasp(backingDomain, backingVaspPubKeyResponse);
        assertTrue(umaProtocolHelper.verifyPayReqResponseBackingSignaturesSync(parsedResponse, "$alice@vasp1.com"));
    }

    @Test
    public void testGetPayReqResponseFuture() throws Exception {
        PayRequest request = umaProtocolHelper.getPayRequest(
                publicKeyBytes(),
                privateKeyBytes(),
                "USD",
                100L,
                true,
                "$alice@vasp1.com",
                KycStatus.VERIFIED,
                ""
        );
        PayReqResponse response = umaProtocolHelper.getPayReqResponseFuture(
                request,
                new TestUmaInvoiceCreator(),
                encodedPayReqMetadata,
                "USD",
                2,
                12345.0,
                0L,
                List.of(),
                null,
                "",
                privateKeyBytes(),
                PayeeData.createPayeeData(null, "$bob@vasp2.com")
        ).get();
        assertNotNull(response);
        assertEquals("lnbc12345", response.getEncodedInvoice());
        System.out.println(response);
        assertTrue(umaProtocolHelper.verifyPayReqResponseSignature(
                response, new PubKeyResponse(publicKeyBytes(), publicKeyBytes()),
                "$alice@vasp1.com", new InMemoryNonceCache(1L)));
    }

    @Test
    public void testVerifyUmaLnurlpQuerySignature_duplicateNonce() throws Exception {
        String lnurlpUrl = umaProtocolHelper.getSignedLnurlpRequestUrl(
                privateKeyBytes(),
                "$bob@vasp2.com",
                "https://vasp.com",
                true);
        LnurlpRequest request = umaProtocolHelper.parseLnurlpRequest(lnurlpUrl);
        assertNotNull(request);

        InMemoryNonceCache nonceCache = new InMemoryNonceCache(1L);
        nonceCache.checkAndSaveNonce(requireNonNull(request.getNonce()), 2L);

        Exception exception = assertThrows(InvalidNonceException.class, () -> {
            umaProtocolHelper.verifyUmaLnurlpQuerySignature(
                    requireNonNull(request.asUmaRequest()), new PubKeyResponse(publicKeyBytes(), publicKeyBytes()), nonceCache);
        });
        assertEquals("Nonce already used", exception.getMessage());
    }

    @Test
    public void testVerifyUmaLnurlpQuerySignature_oldSignature() throws Exception {
        String lnurlpUrl = umaProtocolHelper.getSignedLnurlpRequestUrl(
                privateKeyBytes(),
                "$bob@vasp2.com",
                "https://vasp.com",
                true);
        LnurlpRequest request = umaProtocolHelper.parseLnurlpRequest(lnurlpUrl);
        assertNotNull(request);

        InMemoryNonceCache nonceCache = new InMemoryNonceCache(System.currentTimeMillis() / 1000 + 1000);

        Exception exception = assertThrows(InvalidNonceException.class, () -> {
            umaProtocolHelper.verifyUmaLnurlpQuerySignature(
                    requireNonNull(request.asUmaRequest()), new PubKeyResponse(publicKeyBytes(), publicKeyBytes()),
                    nonceCache);
        });
        assertEquals("Timestamp too old", exception.getMessage());
    }

    @Test
    public void testVerifyUmaLnurlpQuerySignature_purgeOlderNoncesAndStoreNonce() throws Exception {
        String lnurlpUrl = umaProtocolHelper.getSignedLnurlpRequestUrl(
                privateKeyBytes(),
                "$bob@vasp2.com",
                "https://vasp.com",
                true);
        LnurlpRequest request = umaProtocolHelper.parseLnurlpRequest(lnurlpUrl);
        assertNotNull(request);

        InMemoryNonceCache nonceCache = new InMemoryNonceCache(1L);
        nonceCache.checkAndSaveNonce(requireNonNull(request.getNonce()), 2L);
        nonceCache.purgeNoncesOlderThan(3L);

        assertTrue(umaProtocolHelper.verifyUmaLnurlpQuerySignature(
                requireNonNull(request.asUmaRequest()), new PubKeyResponse(publicKeyBytes(), publicKeyBytes()), nonceCache));
    }

    @Test
    public void testSignAndVerifyLnurlpRequestWithBackingSignature() throws Exception {
        String lnurlpUrl = umaProtocolHelper.getSignedLnurlpRequestUrl(
                privateKeyBytes(),
                "$bob@vasp2.com",
                "https://vasp.com",
                true);
        LnurlpRequest request = umaProtocolHelper.parseLnurlpRequest(lnurlpUrl);
        assertNotNull(request);
        byte[] backingVaspPrivateKey = privateKeyBytes();
        String backingDomain = "backingvasp.com";
        UmaLnurlpRequest requestWithBackingSignature = request.asUmaRequest().appendBackingSignature(backingVaspPrivateKey, backingDomain);
        String encodedUrl = requestWithBackingSignature.encodeToUrl();
        LnurlpRequest parsedRequest = umaProtocolHelper.parseLnurlpRequest(encodedUrl);
        assertNotNull(parsedRequest);
        assertNotNull(parsedRequest.getBackingSignatures());
        assertEquals(1, parsedRequest.getBackingSignatures().size());
        Long publicKeyCacheExpiry = System.currentTimeMillis() / 1000 + 10000;
        PubKeyResponse backingVaspPubKeyResponse = new PubKeyResponse(publicKeyBytes(), publicKeyBytes(), publicKeyCacheExpiry);
        publicKeyCache.addPublicKeysForVasp(backingDomain, backingVaspPubKeyResponse);
        assertTrue(umaProtocolHelper.verifyUmaLnurlpQuerySignature(requireNonNull(request.asUmaRequest()), new PubKeyResponse(publicKeyBytes(), publicKeyBytes()), new InMemoryNonceCache(1L)));
        assertTrue(umaProtocolHelper.verifyUmaLnurlpQueryBackingSignaturesSync(
                requireNonNull(parsedRequest.asUmaRequest())
        ));
    }

    @Test
    public void testGetAndVerifyPostTransactionCallback() throws Exception {
        PostTransactionCallback callback = umaProtocolHelper.getPostTransactionCallback(
                Arrays.asList(
                        new UtxoWithAmount("utxo1", 1000),
                        new UtxoWithAmount("utxo2", 2000)
                ),
                /* vaspDomain */ "myvasp.com",
                /* signingPrivateKey */ privateKeyBytes()
        );
        assertNotNull(callback);
        System.out.println(callback);
        String json = callback.toJson();
        PostTransactionCallback parsedCallback = umaProtocolHelper.parseAsPostTransactionCallback(json);
        assertTrue(umaProtocolHelper.verifyPostTransactionCallbackSignature(
                parsedCallback, new PubKeyResponse(publicKeyBytes(), publicKeyBytes()),
                new InMemoryNonceCache(1L)));
    }

    @Test
    public void serializeAndDeserializePubKeyResponse() throws UmaException {
        PubKeyResponse keysOnlyResponse =
                new PubKeyResponse(UmaTest.hexToBytes("02d5fe"), UmaTest.hexToBytes("123456"));
        String json = keysOnlyResponse.toJson();
        PubKeyResponse parsedResponse = umaProtocolHelper.parseAsPubKeyResponse(json);
        assertNotNull(parsedResponse);
        assertEquals(keysOnlyResponse, parsedResponse);

        PubKeyResponse certsOnlyResponse = new PubKeyResponse(CERT_CHAIN, CERT_CHAIN);
        json = certsOnlyResponse.toJson();
        parsedResponse = umaProtocolHelper.parseAsPubKeyResponse(json);
        assertNotNull(parsedResponse);
        assertEquals(certsOnlyResponse, parsedResponse);
        assertArrayEquals(UmaTest.hexToBytes(PUBKEY_HEX), parsedResponse.getSigningPublicKey());
        assertArrayEquals(UmaTest.hexToBytes(PUBKEY_HEX), parsedResponse.getEncryptionPublicKey());
    }

    static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }

    private byte[] privateKeyBytes() {
        return hexToBytes(UmaTest.PRIVKEY_HEX);
    }

    private byte[] publicKeyBytes() {
        return hexToBytes(UmaTest.PUBKEY_HEX);
    }
}

class TestUmaRequester implements UmaRequester {
    @Nullable
    @Override
    public Object makeGetRequest(@NotNull String url, @NotNull Continuation<? super String> $completion) {
        if (url.contains("lnurlpubkey")) {
            PubKeyResponse response = new PubKeyResponse(
                    UmaTest.hexToBytes("02d5fe"),
                    UmaTest.hexToBytes("123456"));
            return response.toJson();
        }
        return null;
    }
}

class TestUmaInvoiceCreator implements UmaInvoiceCreator {
    @NotNull
    @Override
    public CompletableFuture<String> createUmaInvoice(
            long amountMsats,
            @NotNull String metadata,
            @Nullable String receiverIdentifier
    ) {
        return CompletableFuture.completedFuture("lnbc12345");
    }
}

class TestSyncUmaInvoiceCreator implements SyncUmaInvoiceCreator {
    @NotNull
    @Override
    public String createUmaInvoice(
            long amountMsats,
            @NotNull String metadata,
            @Nullable String receiverIdentifier
    ) {
        return "lnbc12345";
    }
}
