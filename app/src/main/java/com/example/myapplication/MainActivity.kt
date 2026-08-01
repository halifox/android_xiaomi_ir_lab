package com.example.myapplication

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { GreeRemoteScreen() } }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun GreeRemoteScreen() {
    val context = LocalContext.current
    val profilesResult = remember { runCatching { RemoteCatalog.loadAll(context) } }
    val profiles = profilesResult.getOrDefault(emptyList())
    val irManager = remember { context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager }
    var profileIndex by remember { mutableIntStateOf(0) }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    val profile = profiles.getOrNull(profileIndex)
    val fallbackState = remember {
        AcRemoteState(
            false, 26, AcProtocolConventions.MODES[0],
            AcProtocolConventions.FAN_SPEEDS[0], 0, emptyMap(),
        )
    }
    var state by remember(profile?.getId()) { mutableStateOf(profile?.createInitialState() ?: fallbackState) }
    var functionId by remember(profile?.getId()) { mutableIntStateOf(AcProtocolConventions.FUNCTION_POWER) }
    var lastStatus by remember { mutableStateOf("等待发送") }
    val hasIrEmitter = irManager?.hasIrEmitter() == true
    val canTransmit = hasIrEmitter && profile?.isSendable() == true
    val deviceBadge = when {
        !hasIrEmitter -> "无红外发射器"
        profile?.isSendable() != true -> "配置不可发送"
        else -> "红外就绪"
    }
    val temperatures = profile?.getSupportedTemperatures(state.getMode()).orEmpty()
    val fans = profile?.getSupportedFanSpeeds(state.getMode()).orEmpty()

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState()).padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("格力空调", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        profile?.let { "${it.getAssetName()} · ${it.getId()} · ${it.getEngineLabel()}" }
                            ?: profilesResult.exceptionOrNull()?.let { "码库读取失败：${it.message}" }
                            ?: "没有找到空调配置",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (canTransmit) Color(0xFFE4F7EA) else MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        deviceBadge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (canTransmit) Color(0xFF176B35) else MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                    )
                }
            }

            Section("空调配置（${profiles.size}）") {
                Column {
                    OutlinedButton(
                        onClick = { profileMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(profile?.let { "${profileIndex + 1}/${profiles.size}  ${it.getId()} · 格式 ${it.getFormatId()}" } ?: "选择配置")
                    }
                    DropdownMenu(
                        expanded = profileMenuExpanded,
                        onDismissRequest = { profileMenuExpanded = false },
                    ) {
                        profiles.forEachIndexed { index, item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("${index + 1}. ${item.getId()} · 格式 ${item.getFormatId()}")
                                        Text(
                                            "品牌 ${item.getBrand()} · ${item.getEngineLabel()}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    profileIndex = index
                                    profileMenuExpanded = false
                                    lastStatus = "已切换 ${item.getId()}"
                                },
                            )
                        }
                    }
                    Text(
                        "新增其他品牌时，将同结构 JSON 放入 assets 即会自动加入列表。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (state.getPower()) state.getMode().getLabel() else "已关机")
                    Text(
                        if (temperatures.isEmpty()) "--" else "${state.getTemperature()}°",
                        fontSize = 68.sp,
                        lineHeight = 76.sp,
                        fontWeight = FontWeight.Light,
                    )
                    Text(
                        "${state.getFanSpeed().getLabel()}风 · 风向 ${state.getUdWindMode()}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }

            if (temperatures.isNotEmpty()) Section("温度") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            val index = temperatures.indexOf(state.getTemperature()).coerceAtLeast(0)
                            state = state.withTemperature(temperatures[(index - 1).coerceAtLeast(0)])
                            functionId = AcProtocolConventions.FUNCTION_TEMPERATURE_DOWN
                        },
                        modifier = Modifier.size(64.dp), shape = CircleShape,
                    ) { Text("−", fontSize = 28.sp) }
                    Text("${state.getTemperature()} ℃", fontSize = 28.sp, fontWeight = FontWeight.Medium)
                    OutlinedButton(
                        onClick = {
                            val index = temperatures.indexOf(state.getTemperature()).coerceAtLeast(0)
                            state = state.withTemperature(temperatures[(index + 1).coerceAtMost(temperatures.lastIndex)])
                            functionId = AcProtocolConventions.FUNCTION_TEMPERATURE_UP
                        },
                        modifier = Modifier.size(64.dp), shape = CircleShape,
                    ) { Text("+", fontSize = 26.sp) }
                }
            }

            Section("模式") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    profile?.getSupportedModes()?.forEach { mode ->
                        FilterChip(
                            selected = state.getMode() == mode,
                            onClick = {
                                val nextTemps = profile.getSupportedTemperatures(mode)
                                val nextFans = profile.getSupportedFanSpeeds(mode)
                                state = state.withMode(
                                    mode,
                                    state.getTemperature().takeIf { it in nextTemps } ?: nextTemps.firstOrNull() ?: state.getTemperature(),
                                    state.getFanSpeed().takeIf { it in nextFans } ?: nextFans.firstOrNull() ?: state.getFanSpeed(),
                                )
                                functionId = AcProtocolConventions.FUNCTION_MODE
                            },
                            label = { Text(mode.getLabel()) },
                        )
                    }
                }
            }

            if (fans.isNotEmpty()) Section("风速") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fans.forEach { fan ->
                        FilterChip(
                            selected = state.getFanSpeed() == fan,
                            onClick = {
                                state = state.withFanSpeed(fan)
                                functionId = AcProtocolConventions.FUNCTION_FAN_SPEED
                            },
                            label = { Text(fan.getLabel()) },
                        )
                    }
                }
            }

            profile?.getUdWindModes()?.takeIf { it.isNotEmpty() }?.let { windModes ->
                Section("上下风向") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        windModes.forEach { value ->
                            FilterChip(
                                selected = state.getUdWindMode() == value,
                                onClick = {
                                    state = state.withUdWindMode(value)
                                    functionId = if (value == 0) AcProtocolConventions.FUNCTION_UD_SWING
                                    else AcProtocolConventions.FUNCTION_UD_FIX
                                },
                                label = { Text(if (value == 0) "扫风" else "档位 $value") },
                            )
                        }
                    }
                }
            }

            profile?.getExtraFunctions()?.takeIf { it.isNotEmpty() }?.let { functions ->
                Section("JSON 扩展功能") {
                    functions.forEach { function ->
                        val spec = profile.getFunctionSpecs()[function.getFid()]
                        val states = spec?.getStates().orEmpty().ifEmpty { listOf(0, 1) }
                        val current = state.getExtraStates()[function.getFid()] ?: spec?.getDefaultState() ?: 0
                        val enabledForMode = spec?.getModes()?.isEmpty() != false || state.getMode().getModeLetter() in spec.getModes()
                        DynamicFunctionRow(
                            label = function.getDisplayName(),
                            states = states,
                            current = current,
                            enabled = enabledForMode,
                            onChange = { value ->
                                state = state.withExtraState(function.getFid(), value)
                                functionId = function.getFid()
                            },
                        )
                    }
                }
            }

            if (profile != null && !profile.isSendable()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "该项是 type=1 旧式固定码，JSON 中没有 1002/300–302，能够自由选择，但无法由 JSON/LuaJ 解码发送。",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Button(
                onClick = {
                    val next = state.withPower(!state.getPower())
                    runCatching {
                        IrTransmitter.transmit(irManager, profile!!, next, AcProtocolConventions.FUNCTION_POWER)
                    }.onSuccess {
                        state = next
                        lastStatus = if (next.getPower()) "已发送开机" else "已发送关机"
                    }.onFailure { showTransmitError(context, it) }
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                enabled = canTransmit,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.getPower()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                ),
            ) { Text(if (state.getPower()) "关机" else "开机", fontSize = 18.sp) }

            OutlinedButton(
                onClick = {
                    val next = state.withPower(true)
                    runCatching { IrTransmitter.transmit(irManager, profile!!, next, functionId) }
                        .onSuccess {
                            state = next
                            lastStatus = "已发送 ${profile?.getId()} · functionId=$functionId"
                        }
                        .onFailure { showTransmitError(context, it) }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = canTransmit,
                shape = RoundedCornerShape(18.dp),
            ) { Text("发送当前 JSON/Lua 状态") }

            Text(
                lastStatus,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DynamicFunctionRow(
    label: String,
    states: List<Int>,
    current: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    if (states.size == 2 && states.containsAll(listOf(0, 1))) {
        ToggleRow(label, current == 1, enabled) { onChange(if (it) 1 else 0) }
    } else {
        val index = states.indexOf(current).coerceAtLeast(0)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onChange(states[(index - 1).coerceAtLeast(0)]) }, enabled = enabled && index > 0) { Text("−") }
                Text(formatFunctionState(current))
                OutlinedButton(onClick = { onChange(states[(index + 1).coerceAtMost(states.lastIndex)]) }, enabled = enabled && index < states.lastIndex) { Text("+") }
            }
        }
    }
}

private fun formatFunctionState(value: Int): String = if (value in 30..1440 && value % 30 == 0) {
    "%02d:%02d".format(value / 60, value % 60)
} else value.toString()

/** Compose 事件的错误反馈；红外编码和发送均由 Java 层完成。 */
private fun showTransmitError(context: Context, error: Throwable) {
    error.printStackTrace()
    Toast.makeText(context, "发送失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}
