package com.ysw2k.webrtc.android.client

import android.Manifest
import android.content.Context
import android.os.BatteryManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import com.ysw2k.webrtc.android.client.adapters.ConnectionRecyclerViewAdapter
import com.ysw2k.webrtc.android.client.databinding.ActivityMainBinding
import com.ysw2k.webrtc.android.client.models.RecyclerViewDataModel
import com.ysw2k.webrtc.android.client.models.ThermalZoneModel
import com.ysw2k.webrtc.android.client.utils.LinearLayoutManagerWrapper
import com.ysw2k.webrtc.android.client.viewmodels.ConnectionViewModel
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.*
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.floor

private class PeerConnectionObserver(
    _socket: Socket,
    _remoteId: String,
    _connectionViewModel: ConnectionViewModel,
) : Observer {
    lateinit var self: PeerConnection
    private val socket: Socket = _socket
    private val remoteId: String = _remoteId
    private val connectionViewModel: ConnectionViewModel = _connectionViewModel

    override fun onSignalingChange(p0: SignalingState?) {
        println("$remoteId onSignalingChange $p0")
    }

    override fun onIceConnectionChange(p0: IceConnectionState?) {
        println("$remoteId onIceConnectionChange $p0")
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
        println("$remoteId onIceConnectionReceivingChange $p0")
    }

    override fun onIceGatheringChange(state: IceGatheringState?) {
        println("$remoteId onIceGatheringChange $state")

        if (state == IceGatheringState.COMPLETE) {
            self.transceivers.forEach {
                if (it.sender.track()?.kind() == null) {
                    println("RTCRtpSender it.sender.track()?.kind() == null")
                } else {
                    println("RTCRtpSender ${it.sender.track()!!.kind()}")
                }
            }
        }
    }

    override fun onIceCandidate(candidate: IceCandidate?) {
        println("$remoteId onIceCandidate $candidate")

        if (candidate != null) {
            socket.emit(
                "transfer-candidate",
                JSONObject()
                    .put("senderId", socket.id())
                    .put("receiverId", remoteId)
                    .put(
                        "candidate",
                        JSONObject().put("sdp", candidate.sdp)
                            .put("sdpMLineIndex", candidate.sdpMLineIndex)
                            .put("sdpMid", candidate.sdpMid)
                    )

            )
        }
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
        println("$remoteId onIceCandidatesRemoved $p0")
    }

    override fun onAddStream(p0: MediaStream?) {
        println("$remoteId onAddStream $p0")
    }

    override fun onRemoveStream(p0: MediaStream?) {
        println("$remoteId onRemoveStream $p0")
    }

    override fun onDataChannel(p0: DataChannel?) {
        println("$remoteId onDataChannel $p0")
    }

    override fun onRenegotiationNeeded() {
        println("$remoteId onRenegotiationNeeded")
    }

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
        println("$remoteId onAddTrack $p0 $p1")
    }

    override fun onTrack(transceiver: RtpTransceiver?) {
        println("$remoteId onTrack $transceiver")
        if (transceiver != null) {
            val recyclerViewDataMap = (connectionViewModel.recyclerViewDataMap.value ?: return)
                .toMutableMap()
            val recyclerViewData = recyclerViewDataMap[remoteId] ?: return
            recyclerViewData.transceivers = recyclerViewData.transceivers.toMutableList().apply {
                add(transceiver)
            }.toTypedArray()

            println("transceiver.mediaType ${transceiver.mediaType}")
            println("transceiver.receiver.track()!!.kind() ${transceiver.receiver.track()!!.kind()}")

            recyclerViewDataMap[remoteId] = recyclerViewData
            connectionViewModel.postRecyclerViewDataMap(recyclerViewDataMap)
        }
    }
}

private open class EmptySdpObserver(_remoteId: String): SdpObserver {
    private val remoteId: String = _remoteId

    override fun onCreateSuccess(p0: SessionDescription?) {
        println("$remoteId onCreateSuccess $p0")
    }

    override fun onSetSuccess() {
        println("$remoteId onSetSuccess")
    }

    override fun onCreateFailure(p0: String?) {
        println("$remoteId onCreateFailure $p0")
    }

    override fun onSetFailure(p0: String?) {
        println("$remoteId onSetFailure $p0")
    }
}

class MainActivity : AppCompatActivity() {
    private val socket: Socket by lazy {
        IO.socket("https://dev-local.mymativ.com:3000")
    }

    private val baseWebRTCConfiguration: RTCConfiguration =
        RTCConfiguration(arrayListOf(
            IceServer.builder("turn:coturn.mymativ.com:5349")
                .setUsername("test")
                .setPassword("test123")
                .setTlsCertPolicy(TlsCertPolicy.TLS_CERT_POLICY_SECURE)
                .createIceServer()
        )).apply {
            sdpSemantics = SdpSemantics.UNIFIED_PLAN
        }

    private val eglRootBase: EglBase by lazy {
        EglBase.create()
    }

    private lateinit var binding: ActivityMainBinding
    private val connectionViewModel: ConnectionViewModel by viewModels()
    private val recyclerViewAdapter: ConnectionRecyclerViewAdapter by lazy {
        ConnectionRecyclerViewAdapter(connectionViewModel, eglRootBase)
    }
    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                HardwareVideoEncoderFactory(
                    eglRootBase.eglBaseContext,
                    true,
                    true,
                )
            )
            .setVideoDecoderFactory(
                HardwareVideoDecoderFactory(
                    eglRootBase.eglBaseContext,
                )
            )
            .createPeerConnectionFactory()
    }
    private lateinit var localVideoCapturer: CameraVideoCapturer
    private lateinit var localAudioSource: AudioSource
    private lateinit var localVideoSource: VideoSource
    private lateinit var localAudioTrack: AudioTrack
    private lateinit var localVideoTrack: VideoTrack

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.activityRecyclerView.adapter = recyclerViewAdapter
        binding.activityRecyclerView.layoutManager = LinearLayoutManagerWrapper(applicationContext)

        TedPermission.with(this)
            .setPermissionListener(object: PermissionListener {
                override fun onPermissionGranted() {
                    println("ayaya")
                    init()
                }

                override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                    finishAndRemoveTask()
                }
            })
            .setDeniedMessage("asdaffadsfdfs")
            .setPermissions(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
            )
            .check()
    }

    private fun init() {
        socket.connect()

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        socket.on("on-create") {
            val data = it[0].let { block -> block as Int }

            println("on-create: $data")
        }

        socket.on("on-join") {
            val data = it[0].let { block -> block as JSONArray? }

            println("on-join: $data")

            if (data == null) {
                socket.emit("create-room", arrayOf(1))
            } else {
                sendOffers(Array(data.length()) { index ->
                    data.getString(index)
                })
            }
        }

        socket.on("on-received-offer") {
            val data = it[0] as JSONObject

            println("on-received-offer: $data")
            val senderId = data.getString("senderId")
            val peerConnection = getOrCreatePeerConnection(senderId)
            // peerConnection.addTrack(localAudioTrack)
            peerConnection.addTrack(localVideoTrack)
            peerConnection.setRemoteDescription(
                EmptySdpObserver(senderId),
                SessionDescription(
                    SessionDescription.Type.OFFER,
                    data.getString("sdp")
                ),
            )

            peerConnection.createAnswer(
                object: EmptySdpObserver(senderId) {
                    override fun onCreateSuccess(p0: SessionDescription?) {
                        super.onCreateSuccess(p0!!)
                        peerConnection.setLocalDescription(
                            EmptySdpObserver(senderId),
                            SessionDescription(p0.type, p0.description),
                        )

                        socket.emit(
                            "transfer-answer",
                            JSONObject().put("sdp", p0.description)
                                .put("senderId", socket.id())
                                .put("receiverId", senderId)
                        )
                    }
                },
                MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
                }
            )
        }

        socket.on("on-received-answer") {
            val data = it[0] as JSONObject

            println("on-received-answer: $data")
            val senderId = data.getString("senderId")
            val peerConnection = getOrCreatePeerConnection(senderId)
            peerConnection.setRemoteDescription(
                EmptySdpObserver(senderId),
                SessionDescription(
                    SessionDescription.Type.ANSWER,
                    data.getString("sdp")
                ),
            )
        }

        socket.on("on-received-candidate") {
            val data = it[0] as JSONObject

            println("on-received-candidate: $data")
            val peerConnection = getOrCreatePeerConnection(data.getString("senderId"))
            val candidateJSONObject = data.getJSONObject("candidate")

            if (candidateJSONObject.has("sdp")) {
                peerConnection.addIceCandidate(IceCandidate(
                    candidateJSONObject.getString("sdpMid"),
                    candidateJSONObject.getInt("sdpMLineIndex"),
                    candidateJSONObject.getString("sdp"),
                ))
            } else {
                peerConnection.addIceCandidate(IceCandidate(
                    candidateJSONObject.getString("sdpMid"),
                    candidateJSONObject.getInt("sdpMLineIndex"),
                    candidateJSONObject.getString("candidate"),
                ))
            }
        }

        socket.on("on-user-disconnect") {
            val data = it[0].let { block -> block as String }

            println("on-user-disconnect: $data")
            removePeerConnection(data)
        }

        socket.on(Socket.EVENT_DISCONNECT) {
            println("disconnect")
            removeAllPeerConnection()
        }

        socket.on(Socket.EVENT_CONNECT) {
            configureLocalEnvironment()
            socket.emit("join-room", 0)
            reportDeviceStatistic()
            activateReportLooper()
        }

        connectionViewModel.recyclerViewDataMap.observe(this) {
            runOnUiThread {
                recyclerViewAdapter.notifyItemInserted(1)
                println("recyclerViewAdapter.itemCount ${recyclerViewAdapter.itemCount}")
            }
        }
    }

    private fun configureLocalEnvironment() {
        captureLocalMedia(binding.root.context)
        localAudioTrack = peerConnectionFactory.createAudioTrack(
            "${socket.id()}-audio",
            localAudioSource
        )
        localVideoTrack = peerConnectionFactory.createVideoTrack(
            "${socket.id()}-video",
            localVideoSource
        )

        val recyclerViewData = connectionViewModel.recyclerViewDataMap.value!!.toMutableMap()
        recyclerViewData[socket.id()] = RecyclerViewDataModel(localUser = true).apply {
            localUserTracks = arrayOf(localVideoTrack, localAudioTrack)
        }
        connectionViewModel.postRecyclerViewDataMap(recyclerViewData)

        runOnUiThread {
            recyclerViewAdapter.notifyItemInserted(0)
            println("recyclerViewAdapter.itemCount ${recyclerViewAdapter.itemCount}")
        }
    }

    private fun getVideoCapturer(context: Context): CameraVideoCapturer =
        Camera2Enumerator(context).run {
            return deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw Exception()
        }

    private fun getOrCreatePeerConnection(remoteId: String): PeerConnection {
        val oldPeerConnection = connectionViewModel.peerConnectionMap.value?.get(remoteId)

        if (oldPeerConnection != null) { return oldPeerConnection }

        val observer = PeerConnectionObserver(socket, remoteId, connectionViewModel)
        val newPeerConnection = peerConnectionFactory.createPeerConnection(
            baseWebRTCConfiguration,
            observer
        )!!

        observer.self = newPeerConnection

        val peerConnectionMap = connectionViewModel.peerConnectionMap.value!!.toMutableMap()
        peerConnectionMap[remoteId] = newPeerConnection
        connectionViewModel.postPeerConnectionMap(peerConnectionMap)

        val recyclerViewData = connectionViewModel.recyclerViewDataMap.value!!.toMutableMap()
        recyclerViewData[remoteId] = RecyclerViewDataModel()
        connectionViewModel.postRecyclerViewDataMap(recyclerViewData)
        return newPeerConnection
    }

    private fun captureLocalMedia(
        context: Context
    ) {
        val surfaceTextureHelper = SurfaceTextureHelper.create(
            Thread.currentThread().name,
            eglRootBase.eglBaseContext,
        )
        if (!::localAudioSource.isInitialized) {
            localAudioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        }

        if (!::localVideoSource.isInitialized) {
            localVideoSource = peerConnectionFactory.createVideoSource(false)
        }

        if (!::localVideoCapturer.isInitialized) {
            localVideoCapturer = getVideoCapturer(context)
        }
        localVideoCapturer.initialize(
            surfaceTextureHelper,
            context,
            localVideoSource.capturerObserver
        )
        localVideoCapturer.startCapture(1280, 720, 30)
    }

    private fun removePeerConnection(remoteId: String) {
        val peerConnection = connectionViewModel.peerConnectionMap.value?.get(remoteId) ?: return
        peerConnection.close()
        connectionViewModel.postPeerConnectionMap(
            connectionViewModel.peerConnectionMap.value!!.filter {
                it.key !== remoteId
            }
        )
        connectionViewModel.postRecyclerViewDataMap(
            connectionViewModel.recyclerViewDataMap.value!!.filter {
                it.key !== remoteId
            }
        )
    }

    private fun removeAllPeerConnection() {
        val peerConnectionMap = connectionViewModel.peerConnectionMap.value ?: return
        peerConnectionMap.forEach {
            it.value.close()
        }
        connectionViewModel.postPeerConnectionMap(emptyMap())
    }

    private fun sendOffers(userIds: Array<String>) {
        userIds.iterator().forEach { senderId ->
            val peerConnection = getOrCreatePeerConnection(senderId)
            // peerConnection.addTrack(localAudioTrack)
            peerConnection.addTrack(localVideoTrack)
            peerConnection.createOffer(
                object: EmptySdpObserver(senderId) {
                    override fun onCreateSuccess(p0: SessionDescription?) {
                        super.onCreateSuccess(p0!!)
                        peerConnection.setLocalDescription(
                            EmptySdpObserver(senderId),
                            SessionDescription(p0.type, p0.description),
                        )

                        socket.emit(
                            "transfer-offer",
                            JSONObject().put("sdp", p0.description)
                                .put("senderId", socket.id())
                                .put("receiverId", senderId)
                        )
                    }
                },
                MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
                }
            )
        }
    }

    private fun activateReportLooper() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay((5 * 60 * 1000L))
                reportDeviceStatistic()
            }
        }
    }

    private fun reportDeviceStatistic() {
        val data = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("currentBatteryStatus", getBatteryLevel())

            val array = JSONArray().apply {
                getThermalZones().forEach {
                    val item = JSONObject().apply {
                        put("name", it.name)
                        put("value", it.value)
                    }
                    put(item)
                }
            }
            put("currentDeviceTemperature", array)
        }

        socket.emit("report-statistics", data)
    }

    private fun getBatteryLevel(): Int {
        return (applicationContext.getSystemService(BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun getThermalZones(): Array<ThermalZoneModel> {
        val data = Array(100) {
            val value = getThermalValue(it)
            println("value ${value}")
            if (value > 0.0f) {
                val type = getThermalType(it)
                println("type ${type}")
                if (type != null) {
                    return@Array ThermalZoneModel().apply {
                        name = type
                        this.value = value
                    }
                }
            }

            null
        }.filterNotNull()

        println(data)
        data.forEach {
            println("${it.name} ${it.value}")
        }

        return data.toTypedArray()
    }

    private fun getThermalValue(index: Int): Float {
        try {
            val data = Runtime.getRuntime().exec("cat sys/class/thermal/thermal_zone$index/temp").let { process ->
                process.waitFor()

                BufferedReader(InputStreamReader(process.inputStream)).let { reader ->
                    val loaded = reader.readLine()
                    reader.close()
                    process.destroy()

                    loaded?.toFloat() ?: 0.0f
                }
            }
            if (data > 0.0f) {
                when {
                    data > 10000.0f -> {
                        return floor(data / 100) / 10
                    }
                    data > 1000.0f -> {
                        return floor(data / 10) / 10
                    }
                    data > 100.0f -> {
                        return floor(data / 100)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return 0.0f
    }

    private fun getThermalType(index: Int): String? {
        try {
            return Runtime.getRuntime().exec("cat sys/class/thermal/thermal_zone$index/type").let { process ->
                process.waitFor()

                BufferedReader(InputStreamReader(process.inputStream)).let { reader ->
                    val data = reader.readLine()
                    reader.close()
                    process.destroy()

                    data
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }
}