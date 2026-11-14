package com.gabozayo.app

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

private fun typeColor(t: Int): Int = when (t) {
    12 -> 0xFF2B8A5E.toInt()   // 관광지
    14 -> 0xFF7A4FD6.toInt()   // 문화시설
    15 -> 0xFFE5484D.toInt()   // 행사
    28 -> 0xFFF08A00.toInt()   // 레포츠
    else -> 0xFFA86B00.toInt()
}

private fun typeEmoji(t: Int) = when (t) { 12 -> "🌳"; 14 -> "🏛"; 15 -> "🎪"; 28 -> "⚽"; else -> "📍" }

/** 물방울 모양 핀. 선택하면 크고 진하게 */
private fun pinBitmap(density: Float, color: Int, emoji: String, selected: Boolean): Bitmap {
    val s = if (selected) 1.25f else 1f
    val w = (38 * density * s).toInt(); val h = (50 * density * s).toInt()
    val bmp = Bitmap.createBitmap(w, h + (4 * density).toInt(), Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    val r = w / 2f - 2 * density
    val cx = w / 2f; val cy = r + 2 * density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // 그림자
    paint.color = 0x33000000; c.drawOval(cx - r * 0.5f, h - 2 * density, cx + r * 0.5f, h + 3 * density, paint)
    // 몸통
    paint.color = color
    val path = Path().apply { moveTo(cx, h.toFloat()); lineTo(cx - r * 0.78f, cy + r * 0.62f); lineTo(cx + r * 0.78f, cy + r * 0.62f); close() }
    c.drawPath(path, paint); c.drawCircle(cx, cy, r, paint)
    // 테두리 + 안쪽 흰 원
    paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f * density; paint.color = android.graphics.Color.WHITE; c.drawCircle(cx, cy, r, paint)
    paint.style = Paint.Style.FILL; paint.color = android.graphics.Color.WHITE; c.drawCircle(cx, cy, r * 0.66f, paint)
    paint.color = android.graphics.Color.BLACK; paint.textSize = r * 0.85f; paint.textAlign = Paint.Align.CENTER
    c.drawText(emoji, cx, cy + paint.textSize * 0.34f, paint)
    return bmp
}

private fun meBitmap(density: Float, color: Int): Bitmap {
    val d = (56 * density).toInt()
    val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = (color and 0x00FFFFFF) or 0x33000000; c.drawCircle(d / 2f, d / 2f, d / 2f, p)
    p.color = android.graphics.Color.WHITE; c.drawCircle(d / 2f, d / 2f, 10 * density, p)
    p.color = color; c.drawCircle(d / 2f, d / 2f, 7.5f * density, p)
    return bmp
}

/** 앱 안에서 보는 지도 (OpenStreetMap 타일 · API 키 필요 없음). 네이버/카카오 지도 SDK 로 바꾸고 싶으면 이 화면만 교체하면 돼요. */
@Composable
fun NearMap(back: () -> Unit, open: (Int) -> Unit) {
    val ctx = LocalContext.current
    val density = ctx.resources.displayMetrics.density
    val here = Geo.eff
    val dark = Th.dark
    val places = (Geo.near + Geo.events).distinctBy { it.id }.filter { it.lat != 0.0 || it.lng != 0.0 }
    var selId by remember { mutableStateOf<Int?>(null) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    val listState = rememberLazyListState()

    remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
        }
    }
    val pinCache = remember { HashMap<String, BitmapDrawable>() }
    fun pin(t: Int, sel: Boolean) = pinCache.getOrPut("$t$sel") { BitmapDrawable(ctx.resources, pinBitmap(density, typeColor(t), typeEmoji(t), sel)) }
    val meIcon = remember(dark) { BitmapDrawable(ctx.resources, meBitmap(density, if (dark) 0xFFC98A10.toInt() else 0xFFA86B00.toInt())) }

    fun fitAll(m: MapView) {
        val pts = places.map { GeoPoint(it.lat, it.lng) } + listOfNotNull(here?.let { GeoPoint(it.latitude, it.longitude) })
        if (pts.size >= 2) m.post { m.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts).increaseByScale(1.35f), true) }
        else pts.firstOrNull()?.let { m.controller.setCenter(it); m.controller.setZoom(14.0) }
    }

    // 지도 마커 ↔ 아래 카드 연동
    LaunchedEffect(selId) {
        val i = places.indexOfFirst { it.id == selId }
        if (i >= 0) {
            listState.animateScrollToItem(i)
            mapRef?.controller?.animateTo(GeoPoint(places[i].lat, places[i].lng))
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        AndroidView(
            factory = { c ->
                MapView(c).apply {
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    isTilesScaledToDpi = true
                    controller.setZoom(14.0)
                    controller.setCenter(here?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(36.5, 127.8))
                    if (here == null) controller.setZoom(7.0)
                    onResume(); mapRef = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.setTileSource(TileSourceFactory.MAPNIK)
                // 키가 필요 없는 OSM 기본 타일. 지도는 다크 모드여도 항상 밝은 원래 색으로 보여줘요
                map.overlayManager.tilesOverlay.setColorFilter(null)
                map.overlays.clear()
                places.forEach { p ->
                    map.overlays.add(Marker(map).apply {
                        position = GeoPoint(p.lat, p.lng); title = p.n
                        icon = pin(p.type, p.id == selId)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setInfoWindow(null)
                        setOnMarkerClickListener { _, _ -> selId = p.id; true }
                    })
                }
                here?.let { l ->
                    map.overlays.add(Marker(map).apply {
                        position = GeoPoint(l.latitude, l.longitude); icon = meIcon
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); setInfoWindow(null)
                        setOnMarkerClickListener { _, _ -> true }
                    })
                }
                map.invalidate()
            },
            onRelease = { it.onDetach(); mapRef = null },
        )
        LaunchedEffect(mapRef, places.size, here == null) { mapRef?.let { fitAll(it) } }

        // 위쪽: 뒤로가기 + 제목
        Row(Modifier.align(Alignment.TopStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Sf).clickable(onClick = back), Alignment.Center) { Text("‹", fontSize = 26.sp, color = Nv) }
            Column(Modifier.shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Sf).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("내 근처 가볼만한 곳", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Nv, maxLines = 1)
                Text(if (Geo.area.isNotBlank()) "${Geo.area} 기준 · ${places.size}곳" else "${places.size}곳", fontSize = 11.sp, color = Sb, maxLines = 1)
            }
        }
        // 오른쪽: 내 위치 / 전체 보기
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MapBtn("◎") { here?.let { mapRef?.controller?.animateTo(GeoPoint(it.latitude, it.longitude)); mapRef?.controller?.setZoom(15.0) } }
            MapBtn("⤢") { mapRef?.let { fitAll(it) } }
        }
        if (places.isEmpty()) Text(
            when { here == null && Geo.outside -> "한국 밖 위치예요. 홈에서 지역을 골라 주세요"; Geo.nearMsg.isNotBlank() -> Geo.nearMsg; Geo.nearLoading -> "불러오는 중…"; else -> "표시할 장소가 없어요" },
            Modifier.align(Alignment.Center).shadow(6.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Sf).padding(14.dp), Sb, 13.sp,
        )
        // 아래: 가로 카드
        if (places.isNotEmpty()) LazyRow(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            state = listState, contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom,
        ) {
            itemsIndexed(places, key = { _, p -> p.id }) { _, p ->
                val on = p.id == selId
                Column(
                    Modifier.width(300.dp).shadow(8.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Sf)
                        .border(if (on) BorderStroke(2.dp, Bl) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(20.dp))
                        .clickable { if (on) open(p.id) else selId = p.id }.padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumb(p.img, Modifier.size(60.dp).clip(RoundedCornerShape(14.dp))); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${typeEmoji(p.type)} ${p.typeLabel()}", fontSize = 11.sp, color = Color(typeColor(p.type)), fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(p.n, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Nv, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(placeSub(p), fontSize = 11.sp, color = Sb, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    // 선택한 카드에만 아래에 넓은 버튼이 나타나요
                    if (on) Box(
                        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bl).clickable { open(p.id) }.padding(vertical = 10.dp),
                        Alignment.Center,
                    ) { Text("자세히 보기 ›", fontSize = 13.sp, color = OnBl, fontWeight = FontWeight.ExtraBold, maxLines = 1) }
                }
            }
        }
        Text("© OpenStreetMap contributors", Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 2.dp), Sb, 8.sp)
    }
}

@Composable
private fun MapBtn(label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Sf).clickable(onClick = onClick), Alignment.Center) { Text(label, fontSize = 20.sp, color = BlTx) }
}
