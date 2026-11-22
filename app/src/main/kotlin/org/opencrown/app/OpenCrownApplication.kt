package org.opencrown.app

import android.app.Application
import org.opencrown.app.data.AppGraph

class OpenCrownApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
