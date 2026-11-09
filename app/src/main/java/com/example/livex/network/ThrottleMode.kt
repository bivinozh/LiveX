package com.example.livex.network

/** Simulated network conditions for the throttle simulator. */
enum class ThrottleMode(val label: String, val bitsPerSecond: Long?) {
    UNLIMITED("Unlimited", null),

    /** Enough for 720p/1080p on most ladders. */
    MBPS_5("5 Mbps", 5_000_000),

    /** Mid ladder: ABR settles on a ~480p-720p rendition. */
    MBPS_2("2 Mbps", 2_000_000),

    /** Weak mobile / congested Wi-Fi: lowest renditions, stalls if the buffer was built at a higher bitrate. */
    KBPS_800("800 kbps", 800_000),

    /** Good periods of 1.5-6 Mbps for 3-6 s, then a complete drop for 1-3 s, repeating. */
    UNSTABLE("Unstable (drops)", null),
}
