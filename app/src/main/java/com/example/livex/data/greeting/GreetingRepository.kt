package com.example.livex.data.greeting

import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

interface GreetingRepository {
    suspend fun getGreeting(): String
}

class DefaultGreetingRepository @Inject constructor() : GreetingRepository {
    override suspend fun getGreeting(): String {
        // TODO: replace with a real data source.
        delay(500.milliseconds)
        return "Hello Android!"
    }
}
