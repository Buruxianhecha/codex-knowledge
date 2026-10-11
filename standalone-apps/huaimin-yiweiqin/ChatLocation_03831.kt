
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
        require(valid(p.latitude, p.longitude))
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
            if (!valid(lat,lon)) return null
            ChatLocationPoint(lat, lon,
                json["name"]?.jsonPrimitive?.content.orEmpty().take(120)
                    .ifBlank { "选定位置" },
                json["address"]?.jsonPrimitive?.content.orEmpty().take(320))
        }.getOrNull()
    }
    private fun valid(lat: Double, lon: Double) =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
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
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val provider = when {
        fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> return null
    }
    val old = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        ?.takeIf { System.currentTimeMillis() - it.time in 0L..300_000L }
    if (Build.VERSION.SDK_INT < 30) return old
    return withTimeoutOrNull(12_000L) {
        suspendCancellableCoroutine<Location?> { cont ->
            val cancel = CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            try {
                manager.getCurrentLocation(provider, cancel, context.mainExecutor) { fix ->
                    if (cont.isActive) cont.resume(fix)
                }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    } ?: old
}

private fun addressPoint(a: Address): ChatLocationPoint? {
    if (!a.hasLatitude() || !a.hasLongitude()) return null
    return ChatLocationPoint(a.latitude, a.longitude,
        a.featureName ?: a.thoroughfare ?: a.locality ?: "地图选点",
        a.getAddressLine(0).orEmpty())
}

@Suppress("DEPRECATION")
private suspend fun findPlaces(context: Context, q: String, lat: Double?, lon: Double?):
    List<ChatLocationPoint> = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext emptyList()
    runCatching {
        val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)
        val result = if (q.isBlank() && lat != null && lon != null)
            geocoder.getFromLocation(lat, lon, 5)
        else if (q.isNotBlank()) geocoder.getFromLocationName(q.take(90), 8)
        else null
        result.orEmpty().mapNotNull(::addressPoint)
            .distinctBy { it.latitude to it.longitude }
    }.getOrDefault(emptyList())
}

@Composable
private fun StaticLocationMap(
    point: ChatLocationPoint?,
    modifier: Modifier = Modifier,
    onTap: ((Double, Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val onTapLatest by rememberUpdatedState(onTap)
    val map = remember(context) {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(if (point == null) 3.0 else 16.0)
            controller.setCenter(
                if (point == null) GeoPoint(20.0, 0.0)
                else GeoPoint(point.latitude,point.longitude))
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
            view.controller.setCenter(GeoPoint(point.latitude,point.longitude))
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
    var point by remember { mutableStateOf<ChatLocationPoint?>(null) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf(emptyList<ChatLocationPoint>()) }
    var loading by remember { mutableStateOf(true) }
    var hint by remember { mutableStateOf("正在定位…") }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading = true
        val fix = withContext(Dispatchers.IO) { deviceLocation(context) }
        loading = false
        if (fix == null) {
            hint = "无法获取当前位置，可搜索地点或在地图上点击选点"
        } else {
            val current = ChatLocationPoint(fix.latitude,fix.longitude,
                "当前位置","定位精度约 " + fix.accuracy.toInt() + " 米")
            point = current
            matches = listOf(current) + findPlaces(context,"",fix.latitude,fix.longitude)
            hint = "选中地图上的地点，或在下方搜索"
        }
    }
    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        delay(550)
        matches = findPlaces(context,query,null,null)
        if (matches.isEmpty()) hint = "未搜到地点，可直接在地图上点选"
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=onDismiss) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    Text("发送位置",fontWeight=FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Button(onClick={ point?.let(onSend) },enabled=point!=null && !loading) {
                        Text("发送")
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    StaticLocationMap(point,Modifier.fillMaxSize()) { lat,lon ->
                        point = ChatLocationPoint(lat,lon,"地图选点",
                            String.format(Locale.US,"%.6f, %.6f",lat,lon))
                        query = ""
                    }
                    Text("© OpenStreetMap contributors",Modifier.align(Alignment.BottomStart)
                        .background(Color.White.copy(alpha=0.85f)).padding(5.dp),
                        fontSize=10.sp,color=Color.DarkGray)
                    IconButton(onClick={ refresh++ },modifier=Modifier.align(Alignment.BottomEnd)
                        .padding(12.dp).background(MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(12.dp))) {
                        Icon(Icons.Rounded.MyLocation,contentDescription="回到当前位置")
                    }
                }
                OutlinedTextField(value=query,onValueChange={ query=it },
                    label={Text("搜索地点、道路或建筑")},
                    modifier=Modifier.fillMaxWidth().padding(horizontal=15.dp,vertical=8.dp),
                    singleLine=true)
                Text(if (loading) "正在定位…" else hint,
                    Modifier.padding(start=18.dp,bottom=5.dp),
                    fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=216.dp)) {
                    items(matches) { candidate ->
                        Row(Modifier.fillMaxWidth().clickable { point=candidate }
                            .padding(horizontal=16.dp,vertical=9.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocationOn,null,tint=MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(candidate.name,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(candidate.address,fontSize=11.sp,
                                    maxLines=1,overflow=TextOverflow.Ellipsis,
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
        Box(Modifier.fillMaxWidth().height(68.dp)
            .background(Color(0xFFBDD6E9),RoundedCornerShape(10.dp))) {
            Icon(Icons.Rounded.LocationOn,null,Modifier.align(Alignment.Center),
                tint=Color(0xFF24865D))
            Text("点击查看地图",Modifier.align(Alignment.BottomCenter).padding(bottom=4.dp),
                fontSize=11.sp,color=Color(0xFF263953))
        }
    }
}

private fun openLocationNavigation(context: Context,p: ChatLocationPoint) {
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
