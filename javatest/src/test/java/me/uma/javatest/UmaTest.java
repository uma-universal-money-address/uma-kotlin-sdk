package me.uma.javatest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import kotlin.coroutines.Continuation;
import me.uma.InMemoryPublicKeyCache;
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
    private static String PUBKEY_HEX = "02061e5634646e60cfbe2ca42e2be920b4deb749f0159ed7c428cdd8e3ea69c133";
    private static String PRIVKEY_HEX = "0e120a3c9ff18d295c6452cbb7ee3bb0f3d9c34f4db9e62293d2773f338a3b9d";


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
                "$bob@vasp2.com",
                "https://vasp.com",
                true);
        assertNotNull(lnurlpUrl);
        System.out.println(lnurlpUrl);
        LnurlpRequest request = umaProtocolHelper.parseLnurlpRequest(lnurlpUrl);
        assertNotNull(request);
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
                new TestUmaInvoiceCreator(),
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
