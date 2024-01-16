package me.uma.javatest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import kotlin.coroutines.Continuation;
import me.uma.InMemoryPublicKeyCache;
import me.uma.SyncUmaInvoiceCreator;
import me.uma.UmaInvoiceCreator;
import me.uma.UmaProtocolHelper;
import me.uma.UmaRequester;
import me.uma.protocol.Currency;
import me.uma.protocol.KycStatus;
import me.uma.protocol.LnurlpRequest;
import me.uma.protocol.LnurlpResponse;
import me.uma.protocol.PayReqResponse;
import me.uma.protocol.PayRequest;
import me.uma.protocol.PayerData;
import me.uma.protocol.PayerDataOptions;
import me.uma.protocol.PubKeyResponse;

public class UmaTest {
    UmaProtocolHelper umaProtocolHelper = new UmaProtocolHelper(new InMemoryPublicKeyCache(), new TestUmaRequester());
    private static String PUBKEY_HEX = "04f2998ab056897ddb91b5e6fad1e4bb6c4b7dda427409f667d0f4694b553e4feeeb08936c2993f7b931f6a3fa7e846f11165fae222de5e4a55c12def21a7c9fcf";
    private static String PRIVKEY_HEX = "10fbbee8f689b207bb22df2dfa27827ae9ae02e265980ea09ef5101ed5fb508f";


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
        assertTrue(umaProtocolHelper.verifyUmaLnurlpQuerySignature(request, new PubKeyResponse(publicKeyBytes(), publicKeyBytes())));
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
                new PayerDataOptions(false, false, true),
                List.of(
                        new Currency(
                                "USD",
                                "US Dollar",
                                "$",
                                34_150,
                                1,
                                10_000_000,
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
                parsedResponse, new PubKeyResponse(publicKeyBytes(), publicKeyBytes())));
    }

    @Test
    public void testGetPayReqResponseSync() throws Exception {
        PayRequest request = new PayRequest(
                "USD",
                100,
                new PayerData("$alice@vasp1.com")
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
                ""
        );
        assertNotNull(response);
        assertEquals("lnbc12345", response.getEncodedInvoice());
        System.out.println(response);
    }

    @Test
    public void testGetPayReqResponseFuture() throws Exception {
        PayRequest request = new PayRequest(
                "USD",
                100,
                new PayerData("$alice@vasp1.com")
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
                ""
        ).get();
        assertNotNull(response);
        assertEquals("lnbc12345", response.getEncodedInvoice());
        System.out.println(response);
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
