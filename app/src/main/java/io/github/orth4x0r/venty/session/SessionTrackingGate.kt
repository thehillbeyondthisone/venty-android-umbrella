package io.github.orth4x0r.venty.session

/** Privacy boundary: start collecting only after an observed heater-off state. */
internal class SessionTrackingGate {
    var paused = false
        private set
    private var waitingForOff = true

    fun setPaused(value: Boolean) {
        if (paused != value) waitingForOff = true
        paused = value
    }

    fun resetConnection() { waitingForOff = true }

    fun allows(heating: Boolean): Boolean {
        if (paused) return false
        if (!heating) waitingForOff = false
        return !waitingForOff
    }
}
