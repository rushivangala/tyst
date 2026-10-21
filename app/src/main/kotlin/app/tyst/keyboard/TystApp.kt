package app.tyst.keyboard

import android.app.Application
import android.content.Context
import app.tyst.keyboard.data.Graph

class TystApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        graph.start()
    }
}

val Context.graph: Graph get() = (applicationContext as TystApp).graph
