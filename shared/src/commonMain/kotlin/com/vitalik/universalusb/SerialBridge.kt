package com.vitalik.universalusb

import androidx.compose.runtime.Composable

expect class SerialController {
    val isConnected: Boolean
    fun connect()
    fun disconnect()
    fun sendData(data: String)
}

@Composable
expect fun rememberSerialController(onDataReceived: (String) -> Unit): SerialController