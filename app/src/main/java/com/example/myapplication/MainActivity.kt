package com.example.myapplication

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 应用入口；Compose 只负责四级列表页面和交互状态。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { UniversalRemoteApp() } }
    }
}

/** 四级页面的当前位置。 */
private enum class Page { DEVICES, BRANDS, REMOTES, CONTROLS }

/** 后台加载结果，避免在主线程读取和解密 assets。 */
private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val error: Throwable) : LoadState<Nothing>
}

/** 组织设备、品牌、型号和遥控器四级页面。 */
@Composable
private fun UniversalRemoteApp() {
    val context = LocalContext.current
    val catalog = remember { RemoteCatalog(context) }
    val irManager = remember {
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    }
    val transmitter = remember(irManager) { FixedIrTransmitter(irManager) }
    val kkAcTransmitter = remember(irManager) {
        KkAcTransmitter(irManager, KkAcNativeBridge())
    }
    var page by remember { mutableStateOf(Page.DEVICES) }
    var device by remember { mutableStateOf<DeviceDefinition?>(null) }
    var brand by remember { mutableStateOf<BrandDefinition?>(null) }
    var remote by remember { mutableStateOf<RemoteSummary?>(null) }

    fun goBack() {
        when (page) {
            Page.DEVICES -> Unit
            Page.BRANDS -> {
                device = null
                page = Page.DEVICES
            }
            Page.REMOTES -> {
                brand = null
                page = Page.BRANDS
            }
            Page.CONTROLS -> {
                remote = null
                page = Page.REMOTES
            }
        }
    }

    BackHandler(enabled = page != Page.DEVICES) { goBack() }
    when (page) {
        Page.DEVICES -> DeviceListPage(catalog) {
            device = it
            page = Page.BRANDS
        }
        Page.BRANDS -> BrandListPage(catalog, device!!, ::goBack) {
            brand = it
            page = Page.REMOTES
        }
        Page.REMOTES -> RemoteListPage(catalog, device!!, brand!!, ::goBack) {
            remote = it
            page = Page.CONTROLS
        }
        Page.CONTROLS -> ControlListPage(
            catalog, device!!, brand!!, remote!!, transmitter, kkAcTransmitter, ::goBack,
        )
    }
}

/** 一级设备类型列表。 */
@Composable
private fun DeviceListPage(catalog: RemoteCatalog, onSelect: (DeviceDefinition) -> Unit) {
    val state = load("devices") { catalog.loadDevices() }
    ListPage(title = "选择设备类型", onBack = null, state = state) { devices ->
        if (devices.isEmpty()) item(key = "empty_devices") {
            ListItem(
                headlineContent = { Text("没有设备类型") },
                supportingContent = { Text("devices.json 未提供设备") },
            )
        }
        items(devices, key = { it.deviceId }) { device ->
            ListItem(
                headlineContent = { Text(device.name) },
                modifier = Modifier.clickable { onSelect(device) },
            )
            HorizontalDivider()
        }
    }
}

/** 二级设备品牌列表。 */
@Composable
private fun BrandListPage(
    catalog: RemoteCatalog,
    device: DeviceDefinition,
    onBack: () -> Unit,
    onSelect: (BrandDefinition) -> Unit,
) {
    val state = load("brands:${device.deviceId}") { catalog.loadBrands(device) }
    ListPage(title = device.name, onBack = onBack, state = state) { brands ->
        if (brands.isEmpty()) item(key = "empty_brands") {
            ListItem(
                headlineContent = { Text("暂无品牌") },
                supportingContent = { Text("该设备的品牌索引为空") },
            )
        }
        items(brands, key = { it.stableId }) { brand ->
            ListItem(
                headlineContent = { Text(brand.name) },
                modifier = Modifier.clickable { onSelect(brand) },
            )
            HorizontalDivider()
        }
    }
}

/** 三级品牌遥控器型号列表。 */
@Composable
private fun RemoteListPage(
    catalog: RemoteCatalog,
    device: DeviceDefinition,
    brand: BrandDefinition,
    onBack: () -> Unit,
    onSelect: (RemoteSummary) -> Unit,
) {
    val state = load("remotes:${brand.stableId}") { catalog.loadRemotes(device, brand) }
    ListPage(title = "${brand.name}遥控器", onBack = onBack, state = state) { remotes ->
        if (remotes.isEmpty()) item(key = "empty_remotes") {
            ListItem(
                headlineContent = { Text("暂无遥控器") },
                supportingContent = { Text("品牌文件没有引用任何型号") },
            )
        }
        items(remotes, key = { it.modelId }) { remote ->
            ListItem(
                headlineContent = { Text(remote.modelId) },
                modifier = Modifier.clickable { onSelect(remote) },
            )
            HorizontalDivider()
        }
    }
}

/** 四级遥控器控制项列表。 */
@Composable
private fun ControlListPage(
    catalog: RemoteCatalog,
    device: DeviceDefinition,
    brand: BrandDefinition,
    summary: RemoteSummary,
    transmitter: FixedIrTransmitter,
    kkAcTransmitter: KkAcTransmitter,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state = load("remote:${device.deviceId}:${summary.modelId}") {
        catalog.loadRemote(device, summary)
    }
    val loadedRemote = (state as? LoadState.Ready)?.value
    var acState by remember(loadedRemote?.modelId) {
        mutableStateOf(loadedRemote?.acConfiguration?.createInitialState())
    }
    ListPage(title = summary.modelId, onBack = onBack, state = state) { remote ->
        item(key = "remote_info") {
            ListItem(
                headlineContent = { Text(brand.name, fontWeight = FontWeight.Medium) },
                supportingContent = {
                    Text(
                        remote.unavailableReason
                            ?: "${remote.source.uppercase()} · ${formatFrequency(remote.frequency)}",
                    )
                },
                trailingContent = { Text(if (transmitter.hasEmitter()) "红外就绪" else "无红外硬件") },
            )
            HorizontalDivider()
        }
        val configuration = remote.acConfiguration
        val currentAcState = acState
        if (configuration != null && currentAcState != null) {
            acControlItems(
                configuration = configuration,
                state = currentAcState,
                onChange = { next, functionId ->
                    val normalized = configuration.normalizeState(next, functionId)
                    acState = normalized
                    runCatching { kkAcTransmitter.transmit(remote, normalized, functionId) }
                        .onSuccess {
                            Toast.makeText(context, "空调状态已发送", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { showError(context, it) }
                },
            )
        } else if (remote.commands.isEmpty()) {
            item(key = "empty_controls") {
                ListItem(
                    headlineContent = { Text("没有控制项") },
                    supportingContent = { Text(remote.unavailableReason ?: "型号文件为空") },
                )
            }
        } else {
            items(remote.commands, key = { it.key }) { command ->
                RemoteControlListItem(
                    command = command,
                    hardwareReady = transmitter.hasEmitter(),
                    onSend = {
                        runCatching { transmitter.transmit(remote, command) }
                            .onSuccess { Toast.makeText(context, "已发送：${command.title}", Toast.LENGTH_SHORT).show() }
                            .onFailure { showError(context, it) }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

/** 向 LazyColumn 添加可复用的 KK type=2 空调状态控制项。 */
private fun androidx.compose.foundation.lazy.LazyListScope.acControlItems(
    configuration: AcConfiguration,
    state: AcState,
    onChange: (AcState, Int) -> Unit,
) {
    item(key = "ac_power") {
        ToggleControlListItem("电源", state.power == 0, true) { checked ->
            onChange(state.withPower(if (checked) 0 else 1), 1)
        }
        HorizontalDivider()
    }
    item(key = "ac_mode") {
        val options = configuration.modes.associate { it.value to it.name }
        EnumControlListItem("运行模式", state.mode, options, true) { selected ->
            val next = configuration.mode(selected)
            val temperature = if (state.temperature in next.temperatures) {
                state.temperature
            } else {
                configuration.defaultTemperature(next)
            }
            val fan = if (state.fanSpeed in next.fanSpeeds) {
                state.fanSpeed
            } else {
                next.fanSpeeds.firstOrNull() ?: -1
            }
            onChange(state.withMode(next.value, temperature, fan), 2)
        }
        HorizontalDivider()
    }
    val mode = configuration.mode(state.mode)
    item(key = "ac_temperature") {
        StepControlListItem(
            title = "目标温度",
            value = if (state.temperature >= 0) "${state.temperature} ℃" else "当前模式不可调",
            values = mode.temperatures,
            current = state.temperature,
            enabled = mode.temperatures.isNotEmpty(),
        ) { selected ->
            val functionId = if (selected > state.temperature) 3 else 4
            onChange(state.withTemperature(selected), functionId)
        }
        HorizontalDivider()
    }
    item(key = "ac_fan") {
        EnumControlListItem(
            title = "风速",
            selected = state.fanSpeed,
            options = mode.fanSpeeds.associateWith(::fanLabel),
            enabled = mode.fanSpeeds.isNotEmpty(),
        ) { selected ->
            onChange(state.withFanSpeed(selected), 5)
        }
        HorizontalDivider()
    }
    item(key = "ac_wind") {
        EnumControlListItem(
            title = "上下风向",
            selected = state.windDirection,
            options = configuration.windDirections.associateWith(::windLabel),
            enabled = configuration.windDirections.isNotEmpty(),
        ) { selected ->
            onChange(state.withWindDirection(selected), if (selected == 0) 6 else 7)
        }
        HorizontalDivider()
    }
    items(configuration.extraFunctions, key = { "ac_extra:${it.functionId}" }) { function ->
        val current = state.extraStates[function.functionId] ?: function.defaultState
        val enabled = function.isAvailable(state.power, state.mode)
        if (function.states.size == 2 && function.states.containsAll(listOf(0, 1))) {
            ToggleControlListItem(function.name, current == 1, enabled) { checked ->
                onChange(
                    state.withExtraState(function.functionId, if (checked) 1 else 0),
                    function.functionId,
                )
            }
        } else if (function.states.size > 4 && isOrdered(function.states)) {
            StepControlListItem(
                title = function.name,
                value = formatExtraState(function.functionId, current),
                values = function.states,
                current = current,
                enabled = enabled,
            ) { selected ->
                onChange(state.withExtraState(function.functionId, selected), function.functionId)
            }
        } else {
            EnumControlListItem(
                title = function.name,
                selected = current,
                options = function.states.associateWith { formatExtraState(function.functionId, it) },
                enabled = enabled,
            ) { selected ->
                onChange(state.withExtraState(function.functionId, selected), function.functionId)
            }
        }
        HorizontalDivider()
    }
}

/** 使用图标切换按钮表示开关型状态。 */
@Composable
private fun ToggleControlListItem(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(if (checked) "开启" else "关闭") },
        trailingContent = {
            FilledTonalIconToggleButton(
                checked = checked,
                onCheckedChange = onChange,
                enabled = enabled,
            ) {
                Icon(
                    imageVector = if (checked) Icons.Default.Check else Icons.Default.Clear,
                    contentDescription = if (checked) "关闭$title" else "开启$title",
                )
            }
        },
    )
}

/** 使用减、加两个按钮表示温度、定时或连续档位。 */
@Composable
private fun StepControlListItem(
    title: String,
    value: String,
    values: List<Int>,
    current: Int,
    enabled: Boolean = true,
    onChange: (Int) -> Unit,
) {
    val index = values.indexOf(current)
    val previous = values.getOrNull(index - 1)
    val next = values.getOrNull(index + 1)
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(value) },
        trailingContent = {
            Row {
                FilledTonalIconButton(
                    onClick = { previous?.let(onChange) },
                    enabled = enabled && previous != null,
                    modifier = Modifier.semantics { contentDescription = "降低$title" },
                ) {
                    Text("−")
                }
                FilledTonalIconButton(
                    onClick = { next?.let(onChange) },
                    enabled = enabled && next != null,
                    modifier = Modifier.semantics { contentDescription = "提高$title" },
                ) {
                    Text("+")
                }
            }
        },
    )
}

/** 少量枚举显示为并列图标按钮，超过四项时使用下拉选择。 */
@Composable
private fun EnumControlListItem(
    title: String,
    selected: Int,
    options: Map<Int, String>,
    enabled: Boolean = true,
    onChange: (Int) -> Unit,
) {
    val currentLabel = options[selected] ?: selected.toString()
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(currentLabel) },
        trailingContent = {
            if (options.size <= 4) {
                Row {
                    options.forEach { (value, label) ->
                        FilledTonalIconToggleButton(
                            checked = value == selected,
                            onCheckedChange = { if (it) onChange(value) },
                            enabled = enabled,
                        ) {
                            Text(shortLabel(label))
                        }
                    }
                }
            } else {
                EnumDropdown(currentLabel, options, enabled, onChange)
            }
        },
    )
}

/** 枚举值较多时使用锚定下拉菜单，避免横向控件溢出。 */
@Composable
private fun EnumDropdown(
    currentLabel: String,
    options: Map<Int, String>,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text(currentLabel)
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "展开选项")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onChange(value)
                    },
                )
            }
        }
    }
}

/** 将统一风速编号转换为界面名称。 */
private fun fanLabel(value: Int): String = when (value) {
    0 -> "自动"
    1 -> "低风"
    2 -> "中风"
    3 -> "高风"
    else -> "当前模式不可调"
}

/** 将扩展状态格式化为开关、定时或普通数值。 */
private fun formatExtraState(functionId: Int, value: Int): String = when {
    value == 0 -> "关闭 / 0"
    value == 1 -> "开启 / 1"
    functionId == 9 || functionId == 10 -> "%02d:%02d".format(value / 60, value % 60)
    value in 30..1440 && value % 10 == 0 -> "%02d:%02d".format(value / 60, value % 60)
    else -> value.toString()
}

/** 将风向编号转换为界面名称。 */
private fun windLabel(value: Int): String = if (value == 0) "扫风" else "档位 $value"

/** 判断数值集合是否适合使用前后步进按钮。 */
private fun isOrdered(values: List<Int>): Boolean =
    values.size > 1 && values.zipWithNext().all { (first, second) -> second > first }

/** 将枚举名称压缩为图标按钮内可辨认的短标签。 */
private fun shortLabel(value: String): String = when (value) {
    "自动" -> "自"
    "低风" -> "低"
    "中风" -> "中"
    "高风" -> "高"
    "扫风" -> "扫"
    else -> value.take(2)
}

/** 可被所有固定码设备复用的一行遥控器控制。 */
@Composable
private fun RemoteControlListItem(
    command: RemoteCommand,
    hardwareReady: Boolean,
    onSend: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(command.title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(command.description) },
        trailingContent = {
            Button(onClick = onSend, enabled = command.isEnabled && hardwareReady) {
                Text(if (command.isEnabled) "发送" else "不可用")
            }
        },
    )
}

/**
 * 提供统一 Scaffold、加载态和 LazyColumn 容器。
 *
 * <p>成功状态的 content 接收 LazyListScope，因此四级页面均直接使用 ListItem。</p>
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <T> ListPage(
    title: String,
    onBack: (() -> Unit)?,
    state: LoadState<T>,
    content: androidx.compose.foundation.lazy.LazyListScope.(T) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上一页")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                LoadState.Loading -> item(key = "loading") {
                    ListItem(
                        headlineContent = { Text("正在加载") },
                        leadingContent = { CircularProgressIndicator() },
                    )
                }
                is LoadState.Failed -> item(key = "error") {
                    ListItem(
                        headlineContent = { Text("数据加载失败") },
                        supportingContent = { Text(state.error.message ?: state.error.javaClass.simpleName) },
                    )
                }
                is LoadState.Ready -> content(state.value)
            }
        }
    }
}

/** 在 IO 线程执行指定页面的数据加载，并按 key 自动重新加载。 */
@Composable
private fun <T> load(key: String, block: () -> T): LoadState<T> {
    var state by remember(key) { mutableStateOf<LoadState<T>>(LoadState.Loading) }
    LaunchedEffect(key) {
        state = LoadState.Loading
        state = try {
            LoadState.Ready(withContext(Dispatchers.IO) { block() })
        } catch (error: Throwable) {
            LoadState.Failed(error)
        }
    }
    return state
}

/** 将 Hz 格式化为列表使用的 kHz 文本。 */
private fun formatFrequency(frequency: Int): String = if (frequency > 0) {
    "%.2f kHz".format(frequency / 1000.0)
} else {
    "频率缺失"
}

/** 向用户展示编码、硬件或权限错误。 */
private fun showError(context: Context, error: Throwable) {
    error.printStackTrace()
    val message = if (error is SecurityException) {
        "系统拒绝 TRANSMIT_IR 权限"
    } else {
        error.message ?: "未知错误"
    }
    Toast.makeText(context, "发送失败：$message", Toast.LENGTH_LONG).show()
}
