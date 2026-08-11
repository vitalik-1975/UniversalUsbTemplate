package com.vitalik.universalusb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*

private const val ACTION_USB_PERMISSION = "com.vitalik.universalusb.USB_PERMISSION"

actual class SerialController(
    private val context: Context,
    private val onDataReceived: (String) -> Unit
) {
    private var connectedState by mutableStateOf(false)
    private var usbSerialPort: UsbSerialPort? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    actual val isConnected: Boolean
        get() = connectedState

    actual fun connect() {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)

        if (availableDrivers.isEmpty()) {
            onDataReceived("Помилка: USB-UART пристрої не знайдені. Перевірте OTG кабель.")
            return
        }

        val driver = availableDrivers[0]
        val device = driver.device

        // 1. ПЕРЕВІРЯЄМО ДОЗВІЛ
        if (!manager.hasPermission(device)) {
            onDataReceived("Система: Запит дозволу на доступ до USB...")

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION), flags
            )
            // Викликаємо системне вікно згоди
            manager.requestPermission(device, permissionIntent)
            return // Зупиняємось і чекаємо на BroadcastReceiver
        }

        // Якщо дозвіл вже був наданий раніше, підключаємось одразу
        openConnection(manager, driver.ports[0])
    }

    fun openConnection(manager: UsbManager, port: UsbSerialPort) {
        try {
            val connection = manager.openDevice(port.device)
            if (connection == null) {
                onDataReceived("Помилка: Не вдалося відкрити з'єднання з пристроєм")
                return
            }

            port.open(connection)
            // Налаштовуємо параметри: 115200 baud, 8 data bits, 1 stop bit, no parity
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            usbSerialPort = port
            connectedState = true
            onDataReceived("Система: Успішно підключено (115200 baud)")

            startReading()
        } catch (e: Exception) {
            onDataReceived("Помилка: ${e.message}")
            disconnect()
        }
    }

    private fun startReading() {
        readJob = scope.launch {
            val buffer = ByteArray(1024)
            var accumulatedString = ""

            while (isActive && connectedState) {
                try {
                    val len = usbSerialPort?.read(buffer, 100) ?: 0
                    if (len > 0) {
                        val data = String(buffer, 0, len)
                        accumulatedString += data

                        if (accumulatedString.contains("\n")) {
                            val lines = accumulatedString.split("\n")
                            accumulatedString = lines.last() // Залишаємо хвіст без \n

                            lines.dropLast(1).forEach { line ->
                                val cleanLine = line.replace("\r", "").trim()
                                if (cleanLine.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        onDataReceived(cleanLine)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onDataReceived("Помилка читання: ${e.message}")
                        disconnect()
                    }
                    break
                }
            }
        }
    }

    actual fun disconnect() {
        try {
            readJob?.cancel()
            usbSerialPort?.close()
        } catch (e: Exception) {
            // Ігноруємо помилки при закритті
        } finally {
            usbSerialPort = null
            connectedState = false
            onDataReceived("Система: Відключено")
        }
    }

    actual fun sendData(data: String) {
        if (!connectedState || usbSerialPort == null) return
        try {
            usbSerialPort?.write(data.toByteArray(), 200)
        } catch (e: Exception) {
            onDataReceived("Помилка відправки: ${e.message}")
        }
    }
}

@Composable
actual fun rememberSerialController(onDataReceived: (String) -> Unit): SerialController {
    val context = LocalContext.current
    val controller = remember { SerialController(context, onDataReceived) }

    DisposableEffect(Unit) {
        // 2. СЛУХАЄМО ВІДПОВІДЬ КОРИСТУВАЧА НА ЗАПИТ ДОЗВОЛУ
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                onDataReceived("Система: Дозвіл отримано! Підключаємось...")
                                val manager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
                                val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
                                // Знаходимо драйвер саме для того пристрою, на який дали дозвіл
                                val driver = availableDrivers.find { d -> d.device.deviceName == it.deviceName }
                                if (driver != null) {
                                    controller.openConnection(manager, driver.ports[0])
                                }
                            }
                        } else {
                            onDataReceived("Помилка: Ви відхилили доступ до USB-пристрою")
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        // Реєструємо ресівер з урахуванням нових вимог безпеки Android 13+ (Tiramisu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
            controller.disconnect()
        }
    }

    return controller
}