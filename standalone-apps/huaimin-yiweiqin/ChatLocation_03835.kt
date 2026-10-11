
package com.cleo.cleos.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.content.ActivityNotFoundException
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale
import kotlin.coroutines.resume

/** Static location content is kept in the original chat-message text and backups. */
internal data class ChatLocationPoint(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val address: String
)

internal object ChatLocationPayload {
    private const val PREFIX = "HUAIMIN_LOCATION_V1:"
    fun encode(p: ChatLocationPoint): String {
        require(isValid(p.latitude, p.longitude))
        return PREFIX + buildJsonObject {
            put("lat", p.latitude)
            put("lon", p.longitude)
            put("name", p.name.take(120))
            put("address", p.address.take(320))
        }.toString()
    }
    fun decode(raw: String): ChatLocationPoint? {
        if (!raw.startsWith(PREFIX) || raw.length > 1600) return null
        return runCatching {
            val json = Json.parseToJsonElement(raw.removePrefix(PREFIX)).jsonObject
            val lat = json["lat"]?.jsonPrimitive?.doubleOrNull ?: return null
            val lon = json["lon"]?.jsonPrimitive?.doubleOrNull ?: return null
            if (!isValid(lat,lon)) return null
            ChatLocationPoint(lat, lon,
                json["name"]?.jsonPrimitive?.content.orEmpty().take(120)
                    .ifBlank { "选定位置" },
                json["address"]?.jsonPrimitive?.content.orEmpty().take(320))
        }.getOrNull()
    }
    internal fun isValid(lat: Double, lon: Double) =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
}

/** Pure policy used by location selection and JVM regression tests. */
internal object LocationQuality {
    fun usableCache(ageMs: Long, accuracyMeters: Float): Boolean =
        ageMs in 0..1_800_000L &&
            accuracyMeters.isFinite() && accuracyMeters in 0f..3000f
    fun immediatelyUsable(ageMs: Long, accuracyMeters: Float): Boolean =
        ageMs in 0..120_000L &&
            accuracyMeters.isFinite() && accuracyMeters in 0f..750f
}
private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Suppress("MissingPermission")
private suspend fun deviceLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    // A disabled Android master-location switch is different from no GPS satellite fix.
    if (!manager.isLocationEnabled) return null
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val enabled = buildList {
        if (runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false))
            add(LocationManager.NETWORK_PROVIDER)
        if (fine && runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false))
            add(LocationManager.GPS_PROVIDER)
    }
    // Try both GPS and network caches. Older builds examined only GPS, which is
    // often unavailable indoors even when the phone already has a network fix.
    val cacheProviders = enabled + LocationManager.PASSIVE_PROVIDER
    val cached = cacheProviders.distinct().mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.filter { loc ->
        LocationQuality.usableCache(
            System.currentTimeMillis() - loc.time,
            if (loc.hasAccuracy()) loc.accuracy else 3000f
        ) && ChatLocationPayload.isValid(loc.latitude, loc.longitude)
    }.minWithOrNull(
        compareBy<Location> { if (it.hasAccuracy()) it.accuracy else 3000f }
            .thenByDescending { it.time }
    )
    val age = cached?.let { System.currentTimeMillis() - it.time }
    if (cached != null && LocationQuality.immediatelyUsable(
            age ?: Long.MAX_VALUE, cached.accuracy
        )) return cached
    if (Build.VERSION.SDK_INT < 30 || enabled.isEmpty()) return cached

    // Race GPS and NETWORK, accept the first valid live fix, and stop the other
    // request. A single GPS getCurrentLocation() can wait indefinitely indoors.
    return coroutineScope {
        val found = Channel<Location>(Channel.CONFLATED)
        val requests = enabled.map { provider ->
            launch {
                val candidate = withTimeoutOrNull(6500L) {
                    suspendCancellableCoroutine<Location?> { continuation ->
                        val cancellation = CancellationSignal()
                        continuation.invokeOnCancellation { cancellation.cancel() }
                        try {
                            manager.getCurrentLocation(
                                provider, cancellation, context.mainExecutor
                            ) { location ->
                                if (continuation.isActive) continuation.resume(location)
                            }
                        } catch (_: Exception) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                }
                if (candidate != null && ChatLocationPayload.isValid(
                        candidate.latitude, candidate.longitude
                    )) found.trySend(candidate)
            }
        }
        val live = withTimeoutOrNull(7000L) { found.receive() }
        requests.forEach { it.cancel() }
        found.close()
        live ?: cached
    }
}

private fun addressPoint(a: Address): ChatLocationPoint? {
    if (!a.hasLatitude() || !a.hasLongitude()) return null
    return ChatLocationPoint(a.latitude, a.longitude,
        a.featureName ?: a.thoroughfare ?: a.locality ?: "地图选点",
        a.getAddressLine(0).orEmpty())
}

/**
 * Use the OS geocoding service. On Android 13+ use asynchronous Geocoder APIs
 * to avoid a blocking binder call that can leave the UI stuck on "locating".
 * If the device has no geocoder, searching literal "latitude,longitude" still works.
 */
@Suppress("DEPRECATION")
private suspend fun findPlaces(context: Context, q: String, lat: Double?, lon: Double?):
    List<ChatLocationPoint> {
    val coordinates = q.trim().split(Regex("[,，\\s]+")).filter { it.isNotBlank() }
    if (coordinates.size == 2) {
        val y = coordinates[0].toDoubleOrNull()
        val x = coordinates[1].toDoubleOrNull()
        if (y != null && x != null && ChatLocationPayload.isValid(y,x)) {
            return listOf(ChatLocationPoint(y, x, "坐标选点",
                String.format(Locale.US, "%.6f, %.6f", y, x)))
        }
    }
    if (!Geocoder.isPresent()) return emptyList()
    val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)
    val list: List<Address> = if (Build.VERSION.SDK_INT >= 33) {
        withTimeoutOrNull(4000L) {
            suspendCancellableCoroutine<List<Address>> { continuation ->
                val callback = object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses)
                    }
                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                }
                try {
                    if (q.isBlank() && lat != null && lon != null)
                        geocoder.getFromLocation(lat, lon, 5, callback)
                    else if (q.isNotBlank())
                        geocoder.getFromLocationName(q.take(90), 8, callback)
                    else continuation.resume(emptyList())
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(emptyList())
                }
            }
        } ?: emptyList()
    } else {
        runCatching {
            withContext(Dispatchers.IO) {
                if (q.isBlank() && lat != null && lon != null)
                    geocoder.getFromLocation(lat, lon, 5).orEmpty()
                else if (q.isNotBlank()) geocoder.getFromLocationName(q.take(90), 8).orEmpty()
                else emptyList()
            }
        }.getOrDefault(emptyList())
    }
    return list.mapNotNull(::addressPoint).distinctBy { it.latitude to it.longitude }
}

@Composable
private fun StaticLocationMap(
    point: ChatLocationPoint?,
    modifier: Modifier = Modifier,
    onTap: ((Double, Double) -> Unit)? = null,
    initialCenter: ChatLocationPoint? = null,
) {
    val context = LocalContext.current
    val onTapLatest by rememberUpdatedState(onTap)
    val startingPoint = point ?: initialCenter
    var latestPin by remember { mutableStateOf<Pair<Double,Double>?>(null) }
    val map = remember(context) {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(if (startingPoint == null) 3.0 else 16.0)
            controller.setCenter(
                if (startingPoint == null) GeoPoint(20.0, 0.0)
                else GeoPoint(startingPoint.latitude,startingPoint.longitude))
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    AndroidView(factory = { map }, modifier = modifier, update = { view ->
        view.overlays.clear()
        if (onTapLatest != null) {
            view.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    if (p != null) onTapLatest?.invoke(p.latitude,p.longitude)
                    return p != null
                }
                override fun longPressHelper(p: GeoPoint?): Boolean {
                    if (p != null) onTapLatest?.invoke(p.latitude,p.longitude)
                    return p != null
                }
            }))
        }
        if (point != null) {
            val key = point.latitude to point.longitude
            if (latestPin != key) {
                latestPin = key
                if (view.zoomLevelDouble < 13.0) view.controller.setZoom(16.0)
                view.controller.setCenter(GeoPoint(point.latitude,point.longitude))
            }
            view.overlays.add(Marker(view).apply {
                position = GeoPoint(point.latitude,point.longitude)
                title = point.name
                setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM)
            })
        }
        view.invalidate()
    })
}

@Composable
internal fun ChatLocationPicker(onDismiss: () -> Unit, onSend: (ChatLocationPoint) -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    var point by remember { mutableStateOf<ChatLocationPoint?>(null) }
    var userPicked by remember { mutableStateOf(false) }
    var manualMode by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf(emptyList<ChatLocationPoint>()) }
    var loading by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("准备定位…") }
    var refresh by remember { mutableIntStateOf(0) }
    // This is ONLY an optional manual-map starting view, never treated as GPS.
    val manualStart = remember {
        ChatLocationPoint(31.2304, 121.4737, "上海地图初始视角", "这不是你的位置，请点地图选点")
    }
    val permission = hasLocationPermission(context)
    val locationEnabled = runCatching { manager.isLocationEnabled }.getOrDefault(false)

    LaunchedEffect(refresh) {
        loading = true
        try {
            val fix = withTimeoutOrNull(8500L) { deviceLocation(context) }
            if (fix != null) {
                val found = ChatLocationPoint(fix.latitude, fix.longitude,
                    "当前位置", "定位精度约 " + fix.accuracy.toInt() + " 米")
                if (!userPicked) {
                    point = found
                    manualMode = true
                    matches = listOf(found)
                }
                hint = "已定位，点击地图或搜索其他地点，再确认发送"
            } else {
                hint = when {
                    !hasLocationPermission(context) ->
                        "未获定位授权；可手动在地图选点，或在系统权限中授权"
                    !runCatching { manager.isLocationEnabled }.getOrDefault(false) ->
                        "手机系统定位开关未打开；请开启定位后点右侧定位按钮"
                    else -> "定位超时。可以重试、搜索地点或手动在地图选点"
                }
                if (point == null) manualMode = true
            }
        } catch (e: Exception) {
            hint = "定位服务不可用：" + (e.message ?: "请检查权限或尝试地图选点")
            if (point == null) manualMode = true
        } finally {
            loading = false
        }
    }
    LaunchedEffect(point?.latitude,point?.longitude,query) {
        val selected = point ?: return@LaunchedEffect
        if (query.isNotBlank()) return@LaunchedEffect
        val nearby = findPlaces(context,"",selected.latitude,selected.longitude)
        matches = (listOf(selected) + nearby).distinctBy { it.latitude to it.longitude }
    }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) return@LaunchedEffect
        delay(500)
        searching = true
        try {
            val results = findPlaces(context,q,null,null)
            matches = results
            hint = if (results.isEmpty())
                "未找到地点；手机可能没有地址搜索服务。可输入「纬度,经度」或在地图上点选"
                else "找到 " + results.size + " 个地点，请点选后发送"
        } catch (_: Exception) {
            hint = "地点搜索暂不可用；可在地图上手动点选"
        } finally {
            searching = false
        }
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=onDismiss) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    Text("发送位置",fontWeight=FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Button(onClick={ point?.let(onSend) },enabled=point != null) {
                        Text("发送")
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (point != null || manualMode) {
                        StaticLocationMap(
                            point = point,
                            modifier = Modifier.fillMaxSize(),
                            onTap = { lat,lon ->
                                userPicked = true
                                point = ChatLocationPoint(lat,lon,"地图选点",
                                    String.format(Locale.US,"%.6f, %.6f",lat,lon))
                                query = ""
                                hint = "已选中地图位置。请确认地点后点击发送"
                            },
                            initialCenter = manualStart,
                        )
                        Text("© OpenStreetMap contributors",Modifier.align(Alignment.BottomStart)
                            .background(Color.White.copy(alpha=0.85f)).padding(5.dp),
                            fontSize=10.sp,color=Color.DarkGray)
                        if (point == null) {
                            Text("手动选点模式 · 初始视角上海，并非当前位置",
                                Modifier.align(Alignment.TopCenter)
                                    .padding(10.dp)
                                    .background(Color.White.copy(alpha=0.90f),
                                        RoundedCornerShape(8.dp))
                                    .padding(horizontal=10.dp,vertical=6.dp),
                                fontSize=12.sp,color=Color(0xFF35465D))
                        }
                    } else {
                        Box(Modifier.fillMaxSize().background(Color(0xFFE6EEF8))) {
                            Column(Modifier.align(Alignment.Center),
                                horizontalAlignment=Alignment.CenterHorizontally) {
                                if (loading) CircularProgressIndicator()
                                Text(if (loading) "正在尝试网络定位和 GPS…"
                                    else "尚未获取当前位置",Modifier.padding(16.dp))
                                TextButton(onClick={ manualMode = true }) {
                                    Text("改用手动地图选点")
                                }
                            }
                        }
                    }
                    IconButton(onClick={
                        refresh++
                    },modifier=Modifier.align(Alignment.BottomEnd)
                        .padding(12.dp).background(MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(12.dp))) {
                        Icon(Icons.Rounded.MyLocation,contentDescription="重新获取手机当前位置")
                    }
                }
                if (preferChinaMap()) {
                    // Domestic fallback for OSM tiles or system Geocoder being unavailable.
                    // AMap URI is a user-invoked browser/app link, not an invisible upload.
                    TextButton(
                        onClick = {
                            val target = point?.let(ChinaMapLink::marker)
                                ?: ChinaMapLink.search(query.ifBlank { "附近" })
                            if (!openChinaMap(context, target)) {
                                hint = "未找到能打开的地图应用；可继续用坐标或手动选点"
                            }
                        },
                        modifier = Modifier.padding(start=12.dp),
                    ) {
                        Text(if (point != null) "在高德地图查看选中位置"
                            else "用高德地图查看国内地点（免费外部打开）")
                    }
                }
                if (!permission || !locationEnabled) {
                    TextButton(onClick = {
                        runCatching {
                            val action = if (!hasLocationPermission(context))
                                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.parse("package:" + context.packageName))
                            else Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            context.startActivity(action)
                        }
                    },modifier=Modifier.padding(start=12.dp)) {
                        Text(if (!permission) "检查应用定位权限 / 系统位置设置"
                            else "打开手机系统定位设置")
                    }
                }
                OutlinedTextField(value=query,onValueChange={ query=it },
                    label={Text("搜索地点、道路、建筑或输入纬度,经度")},
                    modifier=Modifier.fillMaxWidth().padding(horizontal=15.dp,vertical=8.dp),
                    singleLine=true)
                Text(
                    if (loading) "正在定位（最多等待约 8 秒）…"
                    else if (searching) "正在搜索地点…"
                    else hint,
                    Modifier.padding(start=18.dp,bottom=5.dp),
                    fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=216.dp)) {
                    items(matches) { candidate ->
                        Row(Modifier.fillMaxWidth().clickable {
                            userPicked = true
                            manualMode = true
                            point = candidate
                            hint = "已选择「" + candidate.name + "」，可点击发送"
                        }.padding(horizontal=16.dp,vertical=9.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocationOn,null,tint=MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(candidate.name,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(candidate.address,fontSize=11.sp,maxLines=1,
                                    overflow=TextOverflow.Ellipsis,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (point==candidate) Text("✓",color=MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatLocationCard(
    point: ChatLocationPoint,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(Modifier.widthIn(max=280.dp)
        .background(Color(0xFFD6E6FA),RoundedCornerShape(16.dp))
        .combinedClickable(onClick=onClick,onLongClick=onLongClick)
        .padding(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocationOn,null,tint=Color(0xFF24865D))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(point.name,color=Color(0xFF263953),fontWeight=FontWeight.SemiBold,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(point.address,color=Color(0xFF50647E),fontSize=11.sp,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(104.dp)
            .background(Color(0xFFBDD6E9),RoundedCornerShape(10.dp))) {
            StaticLocationMap(point, Modifier.fillMaxSize())
            // Map preview itself is read-only. Tap still opens the full detail
            // instead of accidentally moving the pin in chat.
            Box(Modifier.matchParentSize()
                .combinedClickable(onClick=onClick,onLongClick=onLongClick))
            Text("查看地图",Modifier.align(Alignment.BottomCenter)
                .background(Color.White.copy(alpha=0.78f),RoundedCornerShape(8.dp))
                .padding(horizontal=7.dp,vertical=3.dp),
                fontSize=11.sp,color=Color(0xFF263953))
        }
    }
}

/**
 * China-first keyless map handoff (NOT an embedded AMap SDK).
 * Official AMap marker URI accepts WGS84 explicitly; keeping original
 * coordinates prevents GCJ02 shifts when a photo/location is shared.
 */
internal object ChinaMapLink {
    fun marker(p: ChatLocationPoint): String =
        "https://uri.amap.com/marker?position=" +
            p.longitude + "," + p.latitude +
            "&name=" + Uri.encode(p.name) +
            "&coordinate=wgs84&src=huaimin&callnative=1"

    fun search(keyword: String): String =
        "https://uri.amap.com/search?keyword=" +
            Uri.encode(keyword.take(100)) +
            "&view=map&src=huaimin&callnative=1"
}

private fun openChinaMap(context: Context, uri: String): Boolean =
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(uri)))
        true
    }.getOrDefault(false)

private fun preferChinaMap(): Boolean =
    Locale.getDefault().country.equals("CN",ignoreCase=true)

private fun openLocationNavigation(context: Context,p: ChatLocationPoint) {
    if (preferChinaMap() && openChinaMap(context, ChinaMapLink.marker(p))) return
    val uri=Uri.parse("geo:" + p.latitude + "," + p.longitude + "?q=" +
        p.latitude + "," + p.longitude + "(" + Uri.encode(p.name) + ")")
    if (runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW,uri))
        }.isFailure) {
        val web=Uri.parse("https://www.openstreetmap.org/?mlat=" + p.latitude +
            "&mlon=" + p.longitude + "#map=16/" + p.latitude + "/" + p.longitude)
        if (runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,web)) }.isFailure)
            Toast.makeText(context,"未找到地图应用",Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun ChatLocationViewer(p: ChatLocationPoint,onDismiss: () -> Unit) {
    val context=LocalContext.current
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=onDismiss) {
                        Icon(Icons.Rounded.Close,contentDescription="关闭")
                    }
                    Text("位置详情",fontWeight=FontWeight.SemiBold)
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    StaticLocationMap(p,Modifier.fillMaxSize())
                    Text("© OpenStreetMap contributors",Modifier.align(Alignment.BottomStart)
                        .background(Color.White.copy(alpha=0.83f)).padding(5.dp),
                        fontSize=10.sp,color=Color.DarkGray)
                }
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(p.name,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
                    Text(p.address.ifBlank {
                        "坐标 " + p.latitude + ", " + p.longitude
                    },fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick={openLocationNavigation(context,p)},
                        modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Navigation,null)
                        Spacer(Modifier.width(8.dp))
                        Text("导航到这里")
                    }
                }
            }
        }
    }
}
