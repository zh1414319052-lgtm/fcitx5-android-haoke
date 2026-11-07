/* SPDX-License-Identifier: GPL-3.0-only */
package org.fcitx.fcitx5.android.updates;

import org.bouncycastle.util.encoders.Base64;
import org.json.JSONObject;
import org.junit.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Locale;
import java.util.TimeZone;
import static org.junit.Assert.*;

/** Exact public stable feed captured after registering this product, verified at capture time. */
public class HaokeProductFeedTest {
    @Test public void registeredProductNoneFeedVerifiesWithActualIdentity() throws Exception {
        byte[] bytes;
        try (InputStream in = getClass().getResourceAsStream("/updates/fcitx5-stable-none-20261107.json")) {
            assertNotNull(in); bytes = in.readAllBytes();
        }
        JSONObject envelope = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        JSONObject payload = new JSONObject(new String(Base64.decode(envelope.getString("payload")), StandardCharsets.UTF_8));
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        date.setTimeZone(TimeZone.getTimeZone("UTC"));
        long capturedNow = date.parse(payload.getString("issuedAt")).getTime() + 1000;
        HaokeUpdateVerifier.Config config = new HaokeUpdateVerifier.Config("d000c19470bc74b6",
            "org.fcitx.fcitx5.android.haoke", "stable",
            "a455f0d17ec4f31972c9a1dc7d9e3c8f5a7c31dd1e101765d25827780a917bf3",
            Collections.singletonMap("haoke-updates-000ef7a45a5a58d5", "+8cSV1C+t+6GipgPi3a2zjZ4T3W/Z+tZDKzzO4tQXOY="));
        HaokeUpdateVerifier.Receipt result = HaokeUpdateVerifier.verify(bytes, config,
            new HaokeUpdateVerifier.Device(11301, 34, "arm64-v8a"), null, capturedNow);
        assertEquals("none", result.availability);
        assertEquals(0, result.checkpoint.revision);
        assertNull(result.release);
    }
}
