package com.vitalik.universalusb

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App() {
    val logs = remember { mutableStateListOf<String>() }

    val serialController = rememberSerialController { message ->
        logs.add(message)
    }

    // Початкові значення слайдерів RGB
    var redValue by remember { mutableFloatStateOf(110f) }
    var greenValue by remember { mutableFloatStateOf(73f) }
    var blueValue by remember { mutableFloatStateOf(85f) }

    val isConnected = serialController.isConnected
    val listState = rememberLazyListState()

    // Автоматична прокрутка терміналу до останнього повідомлення
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    fun sendCommand(cmd: String) {
        serialController.sendData("$cmd\n")
        logs.add("TX: $cmd")
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF6F4F8) // Світлий фон
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ================= 1. ВЕРХНЯ ПАНЕЛЬ (ЗАГОЛОВОК І СТАТУС) =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "USB UART (115200)",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = if (isConnected) Color(0xFF388E3C) else Color(0xFFD32F2F),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = if (isConnected) "ПІДКЛЮЧЕНО" else "ВІДКЛЮЧЕНО",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) Color(0xFF388E3C) else Color(0xFFD32F2F)
                        )
                    }
                }

                // КНОПКИ ПІДКЛЮЧИТИ / ВІДКЛЮЧИТИ
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { serialController.connect() },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(23.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5B4B9B) // Фіолетовий
                        )
                    ) {
                        Text("Підключити", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { serialController.disconnect() },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(23.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB71C1C) // Червоний
                        )
                    ) {
                        Text("Відключити", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))

                // ================= 2. БЛОК "ОРГАНИ КЕРУВАННЯ МК" =================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFEDE7F6) // Ніжно-бузковий фон картки
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "ОРГАНИ КЕРУВАННЯ МК",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4A3982)
                        )

                        // КНОПКИ УВІМКНУТИ / ВИМКНУТИ
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { sendCommand("CMD_ON") },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(21.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B4B9B))
                            ) {
                                Text("Увімкнути", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { sendCommand("CMD_OFF") },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(21.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B4B9B))
                            ) {
                                Text("Вимкнути", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 🔴 СЛАЙДЕР 1: ЧЕРВОНИЙ (R)
                        ColorSlider(
                            label = "Червоний (R)",
                            value = redValue,
                            color = Color(0xFFD32F2F),
                            enabled = isConnected,
                            onValueChange = { newValue ->
                                val newInt = newValue.toInt()
                                if (newInt != redValue.toInt()) {
                                    redValue = newValue
                                    sendCommand("PWM_R_$newInt")
                                } else {
                                    redValue = newValue
                                }
                            }
                        )

                        // 🟢 СЛАЙДЕР 2: ЗЕЛЕНИЙ (G)
                        ColorSlider(
                            label = "Зелений (G)",
                            value = greenValue,
                            color = Color(0xFF388E3C),
                            enabled = isConnected,
                            onValueChange = { newValue ->
                                val newInt = newValue.toInt()
                                if (newInt != greenValue.toInt()) {
                                    greenValue = newValue
                                    sendCommand("PWM_G_$newInt")
                                } else {
                                    greenValue = newValue
                                }
                            }
                        )

                        // 🔵 СЛАЙДЕР 3: СИНІЙ (B)
                        ColorSlider(
                            label = "Синій (B)",
                            value = blueValue,
                            color = Color(0xFF1976D2),
                            enabled = isConnected,
                            onValueChange = { newValue ->
                                val newInt = newValue.toInt()
                                if (newInt != blueValue.toInt()) {
                                    blueValue = newValue
                                    sendCommand("PWM_B_$newInt")
                                } else {
                                    blueValue = newValue
                                }
                            }
                        )
                    }
                }

                // ================= 3. ЧОРНИЙ ТЕРМІНАЛ КОНСОЛІ =================
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF121212)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { log ->
                            // Стилізація кольорів повідомлень
                            val textColor = when {
                                log.startsWith("TX:") -> Color(0xFF64B5F6)        // Блакитний для відправлених
                                log.startsWith("Система:") -> Color(0xFFFFB74D)   // Помаранчевий для системних
                                log.contains("Помилка", ignoreCase = true) -> Color(0xFFEF5350) // Червоний
                                else -> Color(0xFF81C784)                         // Зелений для вхідних з МК
                            }

                            Text(
                                text = log,
                                color = textColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// Компонент кастомного кольорового слайдера
@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    color: Color,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text(
            text = "$label: ${value.toInt()}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.2f),
                disabledThumbColor = Color.Gray,
                disabledActiveTrackColor = Color.LightGray.copy(alpha = 0.4f)
            )
        )
    }
}