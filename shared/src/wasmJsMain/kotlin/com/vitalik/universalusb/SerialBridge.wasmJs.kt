@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.vitalik.universalusb

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

actual class SerialController(
    private val onDataReceived: (String) -> Unit
) {
    private var connectedState by mutableStateOf(false)
    private val scope = CoroutineScope(Dispatchers.Main)

    actual val isConnected: Boolean
        get() = connectedState

    actual fun connect() {
        initJsBridge()
        startWebSerialConnect()

        scope.launch {
            while (true) {
                delay(50)

                val currentStatus = checkJsConnectionStatus()
                if (connectedState != currentStatus) {
                    connectedState = currentStatus
                    if (currentStatus) {
                        onDataReceived("Система: Успішно підключено через Web Serial API (115200 baud)")
                    }
                }

                var msg = popJsLogMessage()
                while (msg.isNotEmpty()) {
                    onDataReceived(msg)
                    msg = popJsLogMessage()
                }

                if (!currentStatus && connectedState) break
            }
        }
    }

    actual fun disconnect() {
        connectedState = false
        closeWebSerialPort()
        onDataReceived("Система: Відключено")
    }

    actual fun sendData(data: String) {
        if (!connectedState) return
        writeWebSerialData(data)
    }
}

@Composable
actual fun rememberSerialController(onDataReceived: (String) -> Unit): SerialController {
    return remember { SerialController(onDataReceived) }
}

// ==================== БЕЗПЕЧНИЙ WASM-JS INTEROP ====================

private fun initJsBridge(): Unit = js("""{
    if (!window._serialLogs) window._serialLogs = [];
    if (!window._serialBuffer) window._serialBuffer = "";
    if (window._serialConnected === undefined) window._serialConnected = false;
}""")

private fun startWebSerialConnect(): Unit = js("""{
    (async () => {
        if (!('serial' in navigator)) {
            window._serialLogs.push("Помилка: Браузер не підтримує Web Serial API");
            window._serialConnected = false;
            return;
        }
        try {
            const port = await navigator.serial.requestPort();
            await port.open({ baudRate: 115200 });
            window._webSerialPort = port;
            window._serialConnected = true;

            const decoder = new TextDecoderStream();
            
            // 🛑 ФІНАЛЬНЕ ВИПРАВЛЕННЯ: pipeTo повертає Promise. Коли порт закривається, 
            // цей Promise відхиляється. Його обов'язково треба перехопити через .catch()
            window._webSerialPipePromise = port.readable.pipeTo(decoder.writable).catch(function(e) {});

            const reader = decoder.readable.getReader();
            window._webSerialReader = reader;
            window._serialBuffer = "";

            try {
                while (true) {
                    const { value, done } = await reader.read();
                    if (done) break;
                    if (value) {
                        window._serialBuffer += value;
                        let lines = window._serialBuffer.split('\n');
                        window._serialBuffer = lines.pop();

                        for (let line of lines) {
                            let cleanLine = line.replace('\r', '').trim();
                            if (cleanLine.length > 0) {
                                window._serialLogs.push(cleanLine);
                            }
                        }
                    }
                }
            } catch (readErr) {
                // Ігноруємо помилки переривання потоку
            } finally {
                try { reader.releaseLock(); } catch(e) {}
            }
        } catch (err) {
            window._serialConnected = false;
            if (err.name !== 'NotFoundError') {
                window._serialLogs.push("Помилка: " + err.message);
            }
        }
    })().catch(function(e) {});
}""")

private fun checkJsConnectionStatus(): Boolean = js("""
    window._serialConnected === true
""")

private fun popJsLogMessage(): String = js("""
    (window._serialLogs && window._serialLogs.length > 0) ? window._serialLogs.shift() : ""
""")

private fun closeWebSerialPort(): Unit = js("""{
    (async () => {
        window._serialConnected = false;
        try {
            if (window._webSerialReader) {
                await window._webSerialReader.cancel().catch(function(e){});
                window._webSerialReader = null;
            }
        } catch(e) {}
        
        try {
            // Очікуємо безпечного закриття pipe, щоб не було неперехоплених відхилень
            if (window._webSerialPipePromise) {
                await window._webSerialPipePromise;
                window._webSerialPipePromise = null;
            }
        } catch(e) {}
        
        try {
            if (window._webSerialPort) {
                await window._webSerialPort.close().catch(function(e){});
                window._webSerialPort = null;
            }
        } catch(e) {}
    })().catch(function(e) {}); // 🛑 Захист на випадок помилки самої функції закриття
}""")

private fun writeWebSerialData(data: String): Unit = js("""{
    (async () => {
        try {
            if (window._webSerialPort && window._webSerialPort.writable && window._serialConnected) {
                const encoder = new TextEncoder();
                const writer = window._webSerialPort.writable.getWriter();
                await writer.write(encoder.encode(data));
                writer.releaseLock();
            }
        } catch (e) {
            if (window._serialLogs) window._serialLogs.push("Помилка відправки: " + e.message);
        }
    })().catch(function(e) {});
}""")