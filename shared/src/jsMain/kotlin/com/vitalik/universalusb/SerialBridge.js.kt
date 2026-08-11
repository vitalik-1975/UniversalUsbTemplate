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
        closeWebSerialPort()
        connectedState = false
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

// ==================== JS INTEROP З БЕЗПЕЧНОЮ ОБРОБКОЮ PROMISES ====================

private fun initJsBridge(): Dynamic = js("""
    if (!window._serialLogs) window._serialLogs = [];
    if (!window._serialBuffer) window._serialBuffer = "";
    if (window._serialConnected === undefined) window._serialConnected = false;
""")

private fun startWebSerialConnect(): Dynamic = js("""
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
            port.readable.pipeTo(decoder.writable);
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
                // Захист від помилок розриву потоку читання
            } finally {
                try { reader.releaseLock(); } catch(e) {}
            }
        } catch (err) {
            window._serialConnected = false;
            if (err.name !== 'NotFoundError') {
                window._serialLogs.push("Помилка: " + err.message);
            }
        }
    })().catch(e => console.log("Handled JS rejection:", e));
""")

private fun checkJsConnectionStatus(): Boolean = js("""
    window._serialConnected === true
""") as Boolean

private fun popJsLogMessage(): String = js("""
    (window._serialLogs && window._serialLogs.length > 0) ? window._serialLogs.shift() : ""
""") as String

private fun closeWebSerialPort(): Dynamic = js("""
    (async () => {
        try {
            window._serialConnected = false;
            if (window._webSerialReader) {
                try { await window._webSerialReader.cancel(); } catch(e) {}
                window._webSerialReader = null;
            }
            if (window._webSerialPort) {
                try { await window._webSerialPort.close(); } catch(e) {}
                window._webSerialPort = null;
            }
        } catch (e) {
            console.log("Disconnect info:", e);
        }
    })().catch(e => console.log("Handled close rejection:", e));
""")

private fun writeWebSerialData(data: String): Dynamic = js("""
    (async () => {
        try {
            if (window._webSerialPort && window._webSerialPort.writable) {
                const encoder = new TextEncoder();
                const writer = window._webSerialPort.writable.getWriter();
                await writer.write(encoder.encode(data));
                writer.releaseLock();
            }
        } catch (e) {
            if (window._serialLogs) window._serialLogs.push("Помилка відправки: " + e.message);
        }
    })().catch(e => console.log("Handled write rejection:", e));
""")