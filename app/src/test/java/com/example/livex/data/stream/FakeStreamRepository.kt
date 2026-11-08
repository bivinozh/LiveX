package com.example.livex.data.stream

import com.example.livex.data.SampleStreams
import com.example.livex.data.Stream

/** In-memory fake: set [streams] or [error] to drive each scenario. */
class FakeStreamRepository(
    var streams: List<Stream> = SampleStreams.all,
    var error: Exception? = null,
) : StreamRepository {

    var callCount = 0
        private set

    override suspend fun getStreams(): List<Stream> {
        callCount++
        error?.let { throw it }
        return streams
    }
}
