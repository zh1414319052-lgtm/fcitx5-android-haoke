/* SPDX-License-Identifier: GPL-3.0-only */
package org.fcitx.fcitx5.android.updates;

import org.fcitx.fcitx5.android.BuildConfig;

/** Fixed website trust pin; the release certificate is injected from public identity metadata. */
final class HaokeUpdateConfig {
    private HaokeUpdateConfig() {}
    static HaokeUpdateVerifier.Config create() {
        return new HaokeUpdateVerifier.Config("d000c19470bc74b6", BuildConfig.APPLICATION_ID,
                "stable", BuildConfig.HAOKE_CERT_SHA256,
                java.util.Collections.singletonMap("haoke-updates-000ef7a45a5a58d5",
                        "+8cSV1C+t+6GipgPi3a2zjZ4T3W/Z+tZDKzzO4tQXOY="));
    }
}
