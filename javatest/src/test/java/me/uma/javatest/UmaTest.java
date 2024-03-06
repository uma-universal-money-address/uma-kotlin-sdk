package me.uma.javatest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.uma.protocol.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import kotlin.coroutines.Continuation;
import me.uma.InMemoryNonceCache;
import me.uma.InMemoryPublicKeyCache;
import me.uma.InvalidNonceException;
import me.uma.SyncUmaInvoiceCreator;
import me.uma.UmaInvoiceCreator;
import me.uma.UmaProtocolHelper;
import me.uma.UmaRequester;

public class UmaTest {
    UmaProtocolHelper umaProtocolHelper = new UmaProtocolHelper(new InMemoryPublicKeyCache(), new TestUmaRequester());
    private static final String PUBKEY_HEX = "04419c5467ea563f0010fd614f85e885ac99c21b8e8d416241175fdd5efd2244fe907e2e6fa3dd6631b1b17cd28798da8d882a34c4776d44cc4090781c7aadea1b";
    private static final String PRIVKEY_HEX = "77e891f0ecd265a3cda435eaa73792233ebd413aeb0dbb66f2940babfc9a2667";

    private static final String CERT = "-----BEGIN CERTIFICATE-----\n" +
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
                request, new PubKeyResponse(publicKeyBytes(), publicKeyBytes()),
                new InMemoryNonceCache(1L)));
        System.out.println(request);
    }

    @Test
    public void testGetLnurlpResponse() throws Exception {
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
                        new Currency(
                                "USD",
                                "US Dollar",
                                "$",
                                34_150,
                                new CurrencyConvertible(1, 10_000_000),
                                2
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
        assertTrue(umaProtocolHelper.verifyLnurlpResponseSignature(
                parsedResponse, new PubKeyResponse(publicKeyBytes(), publicKeyBytes()),
                new InMemoryNonceCache(1L)));
    }

    @Test
    public void testGetPayReqResponseSync() throws Exception {
        PayRequest request = new PayRequest(
                "USD",
                "USD",
                100,
                PayerData.createPayerData("$alice@vasp1.com"),
                CounterPartyData.createCounterPartyDataOptions(
                        Map.of(
                                "name", false,
                                "email", false,
                                "identity", true,
                                "compliance", true
                        )
                )
        );
        PayReqResponse response = umaProtocolHelper.getPayReqResponseSync(
                request,
                new TestSyncUmaInvoiceCreator(),
                "metadata",
                "USD",
                2,
                12345L,
                0L,
                List.of(),
                null,
                "",
                privateKeyBytes(),
                PayeeData.createPayeeData(null, "$bob@vasp2.com")
        );
        assertNotNull(response);
        assertEquals("lnbc12345", response.getEncodedInvoice());
        System.out.println(response);
        assertTrue(umaProtocolHelper.verifyPayReqResponseSignature(
                response, new PubKeyResponse(publicKeyBytes(), publicKeyBytes()),
                "$alice@vasp1.com", new InMemoryNonceCache(1L)));
    }

    @Test
    public void testGetPayReqResponseFuture() throws Exception {
        PayRequest request = new PayRequest(
                "USD",
                "USD",
                100,
                PayerData.createPayerData("$alice@vasp1.com")
        );
        PayReqResponse response = umaProtocolHelper.getPayReqResponseFuture(
                request,
                new TestUmaInvoiceCreator(),
                "metadata",
                "USD",
                2,
                12345L,
                0L,
                List.of(),
                null,
                "",
                privateKeyBytes()
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
        nonceCache.checkAndSaveNonce(request.getNonce(), 2L);

        Exception exception = assertThrows(InvalidNonceException.class, () -> {
            umaProtocolHelper.verifyUmaLnurlpQuerySignature(
                    request, new PubKeyResponse(publicKeyBytes(), publicKeyBytes()), nonceCache);
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
                    request, new PubKeyResponse(publicKeyBytes(), publicKeyBytes()), nonceCache);
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
        nonceCache.checkAndSaveNonce(request.getNonce(), 2L);
        nonceCache.purgeNoncesOlderThan(3L);

        assertTrue(umaProtocolHelper.verifyUmaLnurlpQuerySignature(
                request, new PubKeyResponse(publicKeyBytes(), publicKeyBytes()), nonceCache));
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
        assertTrue(umaProtocolHelper.verifyPostTransactionCallbackSignature(
                callback, new PubKeyResponse(publicKeyBytes(), publicKeyBytes()),
                new InMemoryNonceCache(1L)));
    }

    @Test
    public void serializeAndDeserializePubKeyResponse() {
        PubKeyResponse keysOnlyResponse =
                new PubKeyResponse(UmaTest.hexToBytes("02d5fe"), UmaTest.hexToBytes("123456"));
        String json = keysOnlyResponse.toJson();
        PubKeyResponse parsedResponse = umaProtocolHelper.parseAsPubKeyResponse(json);
        assertNotNull(parsedResponse);
        assertEquals(keysOnlyResponse, parsedResponse);

        PubKeyResponse certsOnlyResponse =
                new PubKeyResponse(CERT, CERT);
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
    public CompletableFuture<String> createUmaInvoice(long amountMsats, @NotNull String metadata) {
        return CompletableFuture.completedFuture("lnbc12345");
    }
}

class TestSyncUmaInvoiceCreator implements SyncUmaInvoiceCreator {
    @NotNull
    @Override
    public String createUmaInvoice(long amountMsats, @NotNull String metadata) {
        return "lnbc12345";
    }
}
