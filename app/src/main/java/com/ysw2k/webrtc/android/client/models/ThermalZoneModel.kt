package com.ysw2k.webrtc.android.client.models

private class ThermalZoneAccumulator {
    companion object {
        private var index = 0

        fun getIndex() = index++
    }
}

data class ThermalZoneModel(
    val index: Int = ThermalZoneAccumulator.getIndex(),
) {
    var name: String? = null
    var value: Float? = null
}