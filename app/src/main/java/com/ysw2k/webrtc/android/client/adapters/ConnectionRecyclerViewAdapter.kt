package com.ysw2k.webrtc.android.client.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ysw2k.webrtc.android.client.databinding.RecyclerViewItemBinding
import com.ysw2k.webrtc.android.client.models.RecyclerViewDataModel
import com.ysw2k.webrtc.android.client.viewmodels.ConnectionViewModel
import org.webrtc.*
import java.util.*

class ConnectionRendererEvents(_userId: String): RendererCommon.RendererEvents {
    private val userId = _userId

    override fun onFirstFrameRendered() {
        println("$userId onFirstFrameRendered")
    }

    override fun onFrameResolutionChanged(p0: Int, p1: Int, p2: Int) {
        println("$userId onFrameResolutionChanged $p0 $p1 $p2")
    }
}

class ConnectionRecyclerViewAdapter(
   _connectionViewModel: ConnectionViewModel,
   _eglRootBase: EglBase,
): RecyclerView.Adapter<ConnectionRecyclerViewHolder>() {
    private val connectionViewModel = _connectionViewModel
    private val eglRootBase = _eglRootBase

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ConnectionRecyclerViewHolder =
        ConnectionRecyclerViewHolder(
            RecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(holder: ConnectionRecyclerViewHolder, position: Int) {
        val recyclerViewDataMap = connectionViewModel.recyclerViewDataMap.value ?: return
        val userId = recyclerViewDataMap.keys.elementAt(position)
        val recyclerViewData = recyclerViewDataMap[userId] ?: return

        if (
            holder.bind(
                recyclerViewData,
                eglRootBase,
                ConnectionRendererEvents(userId),
                position,
            )
        ) {
            println("binding finished")
            connectionViewModel.postRecyclerViewDataMap(recyclerViewDataMap)
        } else {
            println("binding failed")
        }
    }

    override fun getItemCount(): Int = connectionViewModel.recyclerViewDataMap.value?.size ?: 0
}

class ConnectionRecyclerViewHolder(
    _recyclerViewItemBinding: RecyclerViewItemBinding,
) : RecyclerView.ViewHolder(_recyclerViewItemBinding.root) {
    private val recyclerViewItemBinding = _recyclerViewItemBinding

    private lateinit var historicalVideoId: String
    var viewBoundWithStream = false

    init {
        recyclerViewItemBinding.viewItem.release()
    }

    fun bind(
        recyclerViewData: RecyclerViewDataModel,
        eglBase: EglBase,
        rendererEvents: ConnectionRendererEvents,
        position: Int,
    ): Boolean {
        println("recyclerViewItemBinding.viewItem")

        if (recyclerViewData.localUser) {
            val videoTrack = nominateVideoTrack(recyclerViewData) as VideoTrack

            if (::historicalVideoId.isInitialized && historicalVideoId == videoTrack.id()) {
                println("binding failure 1")
                return false
            }

            if (attachVideo(videoTrack, eglBase, rendererEvents)) {
                recyclerViewData.viewIndex = position
                return true
            } else {
                println("binding failure 2")
            }
        } else {
            println("remote user")
            println("recyclerViewData.transceivers.size ${recyclerViewData.transceivers.size}")
            val videoTransceiver = nominateVideoTransceiver(recyclerViewData) ?: return false
            println("videoTransceiver $videoTransceiver")
            val videoTrack = nominateVideoTrack(videoTransceiver = videoTransceiver) as VideoTrack
            println("videoTransceiver $videoTrack")

            println("videoTrack id ${videoTrack.id()}")

            if (::historicalVideoId.isInitialized && historicalVideoId == videoTrack.id()) {
                println("binding failure 3")
                return false
            }

            if (viewBoundWithStream != !videoTransceiver.isStopped) {
                if (!videoTransceiver.isStopped) {
                    if (attachVideo(videoTrack, eglBase, rendererEvents)) {
                        recyclerViewData.viewIndex = position
                        return true
                    } else {
                        println("binding failure 4")
                    }
                } else {
                    if (detachVideo()) {
                        recyclerViewData.viewIndex = null
                        return true
                    } else {
                        println("binding failure 5")
                    }
                }
            }
        }

        println("binding failure 6")
        return false
    }

    private fun nominateVideoTransceiver(
        recyclerViewData: RecyclerViewDataModel,
    ): RtpTransceiver? = recyclerViewData.transceivers.find { predicate ->
        predicate.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO
    }

    private fun nominateVideoTrack(
        recyclerViewData: RecyclerViewDataModel? = null,
        videoTransceiver: RtpTransceiver? = null,
    ): VideoTrack? {
        if (recyclerViewData == null) {
            if (videoTransceiver == null) { return null }

            return (videoTransceiver.receiver.track() ?: return null) as VideoTrack
        } else {
            return (recyclerViewData.localUserTracks.find {
                it.kind().lowercase(Locale.ENGLISH).contains("video")
            } ?: return null) as VideoTrack
        }
    }

    private fun attachVideo(
        videoTrack: VideoTrack,
        eglBase: EglBase,
        rendererEvents: ConnectionRendererEvents,
    ): Boolean {
        if (viewBoundWithStream) {
            detachVideo()
        }
        if (videoTrack.state() === MediaStreamTrack.State.ENDED) { return false }

        historicalVideoId = videoTrack.id()
        println("historicalVideoId $historicalVideoId")

        recyclerViewItemBinding.viewItem.setEnableHardwareScaler(true)
        recyclerViewItemBinding.viewItem.init(eglBase.eglBaseContext, rendererEvents)
        videoTrack.addSink(recyclerViewItemBinding.viewItem)
        viewBoundWithStream = true

        return true
    }

    private fun detachVideo(): Boolean {
        if (!viewBoundWithStream) { return false }

        recyclerViewItemBinding.viewItem.release()
        viewBoundWithStream = false

        return true
    }
}