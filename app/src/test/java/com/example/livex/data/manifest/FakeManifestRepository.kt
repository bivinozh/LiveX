package com.example.livex.data.manifest

import java.io.IOException

/** Serves canned manifest text per URL and records which URLs were requested. */
class FakeManifestRepository(
    val responses: MutableMap<String, String> = mutableMapOf(),
    var error: Exception? = null,
) : ManifestRepository {

    val requestedUrls = mutableListOf<String>()

    override suspend fun fetch(url: String): String {
        requestedUrls += url
        error?.let { throw it }
        return responses[url] ?: throw IOException("HTTP 404 for $url")
    }
}
