package com.example.livex

import android.app.Application
import com.example.livex.qoe.QoePipeline
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LiveXApplication : Application() {

    @Inject
    lateinit var qoePipeline: QoePipeline

    override fun onCreate() {
        super.onCreate()
        // Subscribe before any player exists: the event bus doesn't replay, so a late start loses events.
        qoePipeline.start()
    }
}
