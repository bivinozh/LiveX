package com.example.livex.data.stream

import com.example.livex.data.SampleStreams
import com.example.livex.data.Stream
import javax.inject.Inject

/** Source of the stream catalog. Suspending so it can later come from a backend. */
interface StreamRepository {
    suspend fun getStreams(): List<Stream>
}

class DefaultStreamRepository @Inject constructor() : StreamRepository {
    override suspend fun getStreams(): List<Stream> = SampleStreams.all
}
