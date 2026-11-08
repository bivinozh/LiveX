package com.example.livex.data.greeting

/** In-memory fake: set [greeting] or [error] to drive each scenario. */
class FakeGreetingRepository(
    var greeting: String = "Hello Test!",
    var error: Exception? = null,
) : GreetingRepository {

    var callCount = 0
        private set

    override suspend fun getGreeting(): String {
        callCount++
        error?.let { throw it }
        return greeting
    }
}
