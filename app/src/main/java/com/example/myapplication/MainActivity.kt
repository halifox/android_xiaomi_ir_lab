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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            catalog, device!!, brand!!, remote!!, transmitter, ::goBack,
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
                headlineContent = { Text(device.name, fontWeight = FontWeight.Medium) },
                supportingContent = { Text("device=${device.deviceId}") },
                trailingContent = { Text("选择") },
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
                headlineContent = { Text(brand.name, fontWeight = FontWeight.Medium) },
                supportingContent = { Text("brand=${brand.brandId} · 优先级 ${brand.priority}") },
                trailingContent = { Text("选择") },
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
                headlineContent = { Text(remote.modelId, fontWeight = FontWeight.Medium) },
                supportingContent = {
                    Text(
                        remote.unavailableReason
                            ?: "${remote.source.uppercase()} · ${formatFrequency(remote.frequency)} · ${remote.commandCount} 项",
                    )
                },
                trailingContent = { Text(if (remote.isSendable) "可发送" else "仅浏览") },
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
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state = load("remote:${device.deviceId}:${summary.modelId}") {
        catalog.loadRemote(device, summary)
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
        if (remote.commands.isEmpty()) {
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
                    if (onBack != null) {
                        Button(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text("返回")
                        }
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
