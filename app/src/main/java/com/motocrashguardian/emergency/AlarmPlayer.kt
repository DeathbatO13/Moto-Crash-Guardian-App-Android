package com.motocrashguardian.emergency

class AlarmPlayer(private val output: AlarmOutput) {
    private var playing = false

    @Synchronized
    fun start() {
        if (playing) return
        output.start()
        playing = true
    }

    @Synchronized
    fun stop() {
        if (!playing) return
        output.stop()
        playing = false
    }
}

interface AlarmOutput {
    fun start()

    fun stop()
}
