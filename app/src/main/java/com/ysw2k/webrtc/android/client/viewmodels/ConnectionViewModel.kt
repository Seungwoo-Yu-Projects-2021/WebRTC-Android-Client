package com.ysw2k.webrtc.android.client.viewmodels

import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ysw2k.webrtc.android.client.models.RecyclerViewDataModel
import org.webrtc.PeerConnection

class ConnectionViewModel: ViewModel() {
    private val _peerConnectionMap by lazy {
        MutableLiveData<Map<String, PeerConnection>>(emptyMap())
    }

    val peerConnectionMap: LiveData<Map<String, PeerConnection>> by this::_peerConnectionMap

    @MainThread
    fun setPeerConnectionMap(data: Map<String, PeerConnection>) {
        _peerConnectionMap.value = data
    }

    fun postPeerConnectionMap(data: Map<String, PeerConnection>) {
        _peerConnectionMap.postValue(data)
    }

    private val _recyclerViewDataMap by lazy {
        MutableLiveData<Map<String, RecyclerViewDataModel>>(emptyMap())
    }

    val recyclerViewDataMap: LiveData<Map<String, RecyclerViewDataModel>> by this::_recyclerViewDataMap

    @MainThread
    fun setRecyclerViewDataMap(data: Map<String, RecyclerViewDataModel>) {
        _recyclerViewDataMap.value = data
    }

    fun postRecyclerViewDataMap(data: Map<String, RecyclerViewDataModel>) {
        _recyclerViewDataMap.postValue(data)
    }
}