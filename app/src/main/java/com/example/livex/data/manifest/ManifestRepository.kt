package com.example.livex.data.manifest

import com.example.livex.di.IoDispatcher
import com.example.livex.di.UserAgent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

/** Downloads the raw text of an HLS playlist or DASH MPD. */
interface ManifestRepository {
    suspend fun fetch(url: String): String
}

class OkHttpManifestRepository @Inject constructor(
    private val client: OkHttpClient,
    @param:UserAgent private val userAgent: String,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ManifestRepository {

    override suspend fun fetch(url: String): String = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            // Always hit the network: the shared client has a disk cache, and a cached copy of a
            // live playlist would hide exactly the changes "Refresh" is meant to show.
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        // execute() blocks, which is why this runs on the IO dispatcher, never on Main.
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            response.body.string()
        }
    }
}
