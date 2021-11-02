package com.ysw2k.webrtc.android.client.models

import org.webrtc.MediaStreamTrack
import org.webrtc.RtpTransceiver

private class RecyclerViewDataAccumulator {
    companion object {
        private var index = 0

        fun getIndex() = index++
    }
}

data class RecyclerViewDataModel(
    val index: Int = RecyclerViewDataAccumulator.getIndex(),
    val localUser: Boolean = false
) {
    var viewIndex: Int? = null
    var transceivers: Array<RtpTransceiver> = emptyArray()
    var localUserTracks: Array<MediaStreamTrack> = emptyArray()
}