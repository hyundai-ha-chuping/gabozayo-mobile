package com.gabozayo.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val Amber = Color(0xFFF2A100)

// ───────── 내 위치 ─────────
fun hasLocPerm(ctx: Context) =
    ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
fun lastLocation(ctx: Context): Location? = try {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
} catch (e: Exception) { null }

@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
fun refreshLocation(ctx: Context) {
    if (Geo.manual) return
    try {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val l = object : LocationListener {
            override fun onLocationChanged(l: Location) { if (!Geo.manual) Geo.loc = l }
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
            @Deprecated("old API") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        }
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { lm.isProviderEnabled(it) }
            .forEach { lm.requestSingleUpdate(it, l, Looper.getMainLooper()) }
    } catch (e: Exception) { }
}

/** 위치가 조금 바뀔 때마다 API 를 다시 부르지 않도록 좌표를 반올림한 키 */
fun locKey(l: Location?, digits: Int = 3): String =
    if (l == null) "" else "%.${digits}f,%.${digits}f".format(Locale.US, l.latitude, l.longitude)

fun distKm(p: Place): Double? {
    val l = Geo.eff ?: return null
    if (p.lat == 0.0 && p.lng == 0.0) return null
    return distM(l.latitude, l.longitude, p.lat, p.lng) / 1000.0
}

fun dText(p: Place): String {
    val k = distKm(p) ?: return ""
    return if (k < 1) "${(k * 1000).toInt()}m" else if (k < 10) "%.1fkm".format(k) else "${k.toInt()}km"
}

fun placeSub(p: Place): String = listOf(dText(p), p.region()).filter { it.isNotBlank() }.joinToString(" · ")

// ───────── 지도 앱 / 공유 ─────────
private fun view(u: String) = Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun openUrl(ctx: Context, u: String) {
    try { ctx.startActivity(view(u)) } catch (e: Exception) { Toast.makeText(ctx, "열 수 없어요", Toast.LENGTH_SHORT).show() }
}

fun openMapApp(ctx: Context, app: String, p: Place) {
    val n = Uri.encode(p.n)
    val (app1, web) = when (app) {
        "네이버지도" -> "nmap://route/car?dlat=${p.lat}&dlng=${p.lng}&dname=$n&appname=com.gabozayo.app" to "https://map.naver.com/p/search/$n"
        "카카오맵" -> "kakaomap://route?ep=${p.lat},${p.lng}&by=CAR" to "https://map.kakao.com/link/to/$n,${p.lat},${p.lng}"
        else -> "google.navigation:q=${p.lat},${p.lng}&mode=d" to "https://www.google.com/maps/dir/?api=1&destination=${p.lat},${p.lng}&travelmode=driving"
    }
    try { ctx.startActivity(view(app1)) }
    catch (e: ActivityNotFoundException) {
        try { ctx.startActivity(view(web)) } catch (e2: Exception) { Toast.makeText(ctx, "지도를 열 수 없어요", Toast.LENGTH_SHORT).show() }
    }
}

fun copyText(ctx: Context, t: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("gabozayo", t))
}

fun shareTo(ctx: Context, kind: String, p: Place, toast: (String) -> Unit) {
    val text = "${p.n} https://map.naver.com/p/search/${Uri.encode(p.n)}"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        when (kind) {
            "링크 복사" -> { copyText(ctx, text); toast("링크를 복사했어요") }
            "카카오톡" -> ctx.startActivity(send.setPackage("com.kakao.talk"))
            "문자" -> ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else -> ctx.startActivity(Intent.createChooser(send, "공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } catch (e: ActivityNotFoundException) { toast("$kind 앱을 열 수 없어요") }
}

// ───────── 차량 (계정에서 불러오기 / 목적지 전송) ─────────
data class Car(val name: String, val plate: String, val code: String)

/**
 * 폰 → 차량 목적지 전송. 차량 앱(JSON 약속은 차량 프로젝트 Model.kt 와 같아요)
 *   보내기: {"id","name","address","lat","lng","route","ts"}
 *   받기  : 차량이 쓴 상태 {"id","ack","message","ts"}  ack = received / searching / ready / guiding / failed
 */
interface CarBridge {
    suspend fun send(p: Place, car: Car, route: String = ""): Result<String>   // 성공하면 전송 번호(id)
    suspend fun status(id: String): Result<String>                              // 차량의 ack 값, 아직 없으면 ""
    /** 차량 내비가 계산한 (남은 시간 초, 남은 거리 m). 아직 없으면 null */
    suspend fun trip(id: String): Result<Pair<Int, Int>?>
    suspend fun cancel(car: Car): Result<Unit>
    /** 찜한 곳 목록을 차량 앱에 보내요 (차량 화면의 "찜한 곳") */
    suspend fun pushSaved(places: List<Place>): Result<Unit>
    /** 우리 아이 관심사 순위를 차량 앱에 보내요 */
    suspend fun pushInterests(items: List<com.gabozayo.core.InterestItem>): Result<Unit>
    /** 차량 홈 화면용 추천 장소 · 축제 · 아이 목록 */
    suspend fun pushHome(recs: List<Pair<Place, String>>, events: List<Place>, kids: List<com.gabozayo.core.KidItem>): Result<Unit>

    // ── 이야기 (서버에 저장하고, 지우면 서버에서도 지워요) ──
    suspend fun loadStories(): Result<List<com.gabozayo.core.StoryItem>>
    suspend fun putStory(s: com.gabozayo.core.StoryItem): Result<Unit>
    suspend fun deleteStory(id: Long): Result<Unit>
    suspend fun setStoryLike(id: Long, n: Int): Result<Unit>
    suspend fun putStoryComment(sid: Long, c: com.gabozayo.core.StoryComment): Result<Unit>
    suspend fun deleteStoryComment(sid: Long, cid: Long): Result<Unit>
    /** 내 연결 코드의 서버 데이터를 전부 지워요 */
    suspend fun deleteAllData(): Result<Unit>
}

/** 이 휴대폰의 연결 코드(6자리). 처음 한 번 만들고 계속 써요. 차량 앱에서 이 코드를 입력해요 */
fun familyCode(): String = Store.str("family_code", "").ifBlank { com.gabozayo.core.FamilyApi.newCode().also { Store.putStr("family_code", it) } }

/** 차량과 실제로 주고받아요 (Firebase 중계 + 6자리 코드) */
object FirebaseCarBridge : CarBridge {
    private val api by lazy { com.gabozayo.core.FamilyApi(BuildConfig.FIREBASE_DB_URL) }
    private fun need() { check(api.isConfigured) { "Firebase 주소가 없어요 (local.properties 의 FIREBASE_DB_URL)" } }
    private fun routeKey(name: String) = when (name) { "최소 시간" -> "fastest"; "최단 거리" -> "shortest"; "무료도로" -> "free"; else -> "recommend" }

    override suspend fun send(p: Place, car: Car, route: String): Result<String> = runCatching {
        need()
        val id = System.currentTimeMillis().toString()
        api.sendDest(familyCode(), com.gabozayo.core.SentDest(id, p.n, p.a, p.lat, p.lng, routeKey(route), System.currentTimeMillis()))
        id
    }

    override suspend fun status(id: String): Result<String> = runCatching {
        need()
        api.load(familyCode())?.ack?.takeIf { it.id == id }?.ack ?: ""
    }

    override suspend fun trip(id: String): Result<Pair<Int, Int>?> = runCatching {
        need()
        api.load(familyCode())?.ack?.takeIf { it.id == id && it.etaSec > 0 }?.let { it.etaSec to it.distM }
    }

    override suspend fun cancel(car: Car) = runCatching { Unit }

    override suspend fun pushSaved(places: List<Place>): Result<Unit> = runCatching {
        need()
        api.pushSaved(familyCode(), places.map { com.gabozayo.core.SavedItem(it.id, it.n, it.a, it.region(), it.tag, it.lat, it.lng, it.img) })
    }

    override suspend fun pushInterests(items: List<com.gabozayo.core.InterestItem>): Result<Unit> = runCatching {
        need()
        api.pushInterests(familyCode(), items)
    }

    private val detailCache = HashMap<Int, PlaceDetail?>()
    /** 차량 상세 화면에 보여줄 TourAPI 상세 (한 번 받으면 다시 받지 않아요. 실패하면 빈 값 → 차량 화면에서 그 칸은 숨겨져요) */
    private suspend fun enrich(p: Place, why: String): com.gabozayo.core.SavedItem {
        val d = if (detailCache.containsKey(p.id)) detailCache[p.id] else Tour.detail(p).getOrNull().also { detailCache[p.id] = it }
        fun row(k: String) = d?.rows?.firstOrNull { it.first == k }?.second.orEmpty()
        return com.gabozayo.core.SavedItem(
            p.id, p.n, p.a, p.region(), p.tag, p.lat, p.lng, p.img, why,
            hours = row("운영시간").ifBlank { row("기간") }.ifBlank { p.period }, age = row("추천 연령"),
            overview = d?.overview.orEmpty().take(160),
            facilities = d?.facilities?.map { it.substringAfter(' ') } ?: emptyList(), imgCount = d?.images?.size ?: 0, images = d?.images?.filter { it.isNotBlank() }?.take(6) ?: emptyList(),
        )
    }

    override suspend fun pushHome(recs: List<Pair<Place, String>>, events: List<Place>, kids: List<com.gabozayo.core.KidItem>): Result<Unit> = runCatching {
        need()
        val r = recs.take(9).map { (p, why) -> enrich(p, why) }
        val e = events.take(5).map { enrich(it, it.period) }
        api.pushHome(familyCode(), r, e, kids)
    }

    override suspend fun loadStories(): Result<List<com.gabozayo.core.StoryItem>> = runCatching { need(); api.loadStories(familyCode()) }
    override suspend fun putStory(s: com.gabozayo.core.StoryItem): Result<Unit> = runCatching { need(); api.putStory(familyCode(), s) }
    override suspend fun deleteStory(id: Long): Result<Unit> = runCatching { need(); api.deleteStory(familyCode(), id) }
    override suspend fun setStoryLike(id: Long, n: Int): Result<Unit> = runCatching { need(); api.setStoryLike(familyCode(), id, n) }
    override suspend fun putStoryComment(sid: Long, c: com.gabozayo.core.StoryComment): Result<Unit> = runCatching { need(); api.putStoryComment(familyCode(), sid, c) }
    override suspend fun deleteStoryComment(sid: Long, cid: Long): Result<Unit> = runCatching { need(); api.deleteStoryComment(familyCode(), sid, cid) }
    override suspend fun deleteAllData(): Result<Unit> = runCatching { need(); api.deleteAll(familyCode()) }

    private var lastBeat = 0L
    private var beatChangedAt = 0L
    val deviceId: String by lazy { Store.str("device_id", "").ifBlank { java.util.UUID.randomUUID().toString().also { Store.putStr("device_id", it) } } }

    /** 연결 코드를 서버에 등록해요. 다른 가족이 같은 코드를 이미 쓰고 있으면 새 코드를 뽑아요 */
    suspend fun ensureCode(): Result<String> = runCatching {
        need()
        var code = familyCode()
        repeat(6) {
            val doc = api.load(code)
            if (doc == null || doc.owner.isBlank() || doc.owner == deviceId) { api.register(code, deviceId); return@runCatching code }
            code = com.gabozayo.core.FamilyApi.newCode(); Store.putStr("family_code", code)
        }
        error("연결 코드를 만들지 못했어요")
    }

    /**
     * 차량이 지금 켜져 있고 이 코드로 연결돼 있는지. 차량이 몇 초마다 올리는 신호(carBeat)가 최근 20초 안에 바뀌었을 때만 "연결됨"이에요.
     * (예전에 한 번 연결했다는 기록만으로는 연결됨으로 보지 않아요. 확인 못 하면 null)
     */
    suspend fun carConnected(): Boolean? = runCatching {
        need()
        val beat = api.load(familyCode())?.carBeat ?: 0L
        val now = android.os.SystemClock.elapsedRealtime()
        when {
            beat == 0L -> { lastBeat = 0L; beatChangedAt = 0L; false }
            lastBeat == 0L -> { lastBeat = beat; beatChangedAt = 0L; false }   // 처음 본 값: 변하는지 다음 확인 때 봐요
            beat != lastBeat -> { lastBeat = beat; beatChangedAt = now; true }
            else -> beatChangedAt != 0L && now - beatChangedAt < 20_000L
        }
    }.getOrNull()
}

fun carBridge(): CarBridge = FirebaseCarBridge

@Composable
fun Cars(cars: MutableList<Car>, back: () -> Unit) {
    val ctx = LocalContext.current
    val code = familyCode()
    val on = cars.isNotEmpty()
    val ready = BuildConfig.FIREBASE_DB_URL.startsWith("https://")
    Column(Modifier.fillMaxSize()) {
        Bar("차량 연결", back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), Arrangement.spacedBy(14.dp)) {
            if (!ready) {
                Text("Firebase 주소가 없어요. local.properties 의 FIREBASE_DB_URL 을 채워 주세요", fontSize = 13.sp, color = Red)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Bl).padding(22.dp), Arrangement.spacedBy(6.dp)) {
                Text("내 연결 코드", color = OnBl, fontSize = 12.sp)
                Text(code.chunked(3).joinToString(" "), color = OnBl, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                Text("차량 앱의 숫자판에 이 6자리를 입력하면 바로 연결돼요", color = OnBl, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().card().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Chp), Alignment.Center) { Icon(IcCar, null, Modifier.size(22.dp), tint = BlTx) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (on) "차량 연결됨" else "차량 연결을 기다리는 중", fontWeight = FontWeight.Bold, color = Nv)
                    Text(if (on) "찜한 곳이 차량 앱에 보여요" else "차량에 코드를 입력하면 연결돼요", fontSize = 12.sp, color = Sb)
                }
                if (!on) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            if (on) OutlinedButton({ CarSync.tick++; Toast.makeText(ctx, "찜 목록을 차량으로 보내요", Toast.LENGTH_SHORT).show() }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("찜 목록 지금 보내기") }
        }
    }
}

// ───────── 가보자요 (목적지를 내 차량으로 보내기) ─────────
private class RouteOpt(val name: String, val desc: String)
private val routeOpts = listOf(
    RouteOpt("추천", "시간과 거리를 함께 고려해요"),
    RouteOpt("최소 시간", "가장 빨리 도착해요"),
    RouteOpt("최단 거리", "가장 짧은 길로 가요"),
    RouteOpt("무료도로", "통행료 없이 가요"),
)

@Composable
fun NaviSend(p: Place, cars: List<Car>, back: () -> Unit, onCars: () -> Unit) {
    val scope = rememberCoroutineScope()
    var sel by remember { mutableStateOf(cars.firstOrNull()) }
    var ri by remember { mutableIntStateOf(0) }
    var stage by remember { mutableIntStateOf(0) }      // 0 고르기 · 1 전송
    var step by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var trip by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(stage, attempt) {
        if (stage != 1) return@LaunchedEffect
        val car = sel ?: return@LaunchedEffect
        step = 0; failed = false
        val bridge = carBridge()
        val id = bridge.send(p, car, routeOpts[ri].name).getOrElse { failed = true; return@LaunchedEffect }
        step = 1
        // 차량이 돌려주는 진행 상태(ack)에 맞춰 단계를 올려요
        var waited = 0
        while (waited < 300_000) {
            delay(500.milliseconds); waited += 500
            bridge.trip(id).getOrNull()?.let { trip = it }
            when (bridge.status(id).getOrNull()) {
                "received", "searching" -> step = 2                      // 차량이 받았고 내비에 경로를 요청해요
                "ready", "guiding" -> {
                    step = 3
                    // 도착 예정 시간이 조금 늦게 올 수 있어서 잠깐 더 기다려요
                    repeat(8) { if (trip == null) { bridge.trip(id).getOrNull()?.let { t -> trip = t }; if (trip == null) delay(1000.milliseconds) } }
                    return@LaunchedEffect
                } // 경로가 준비됐어요
                "failed" -> { failed = true; return@LaunchedEffect }
            }
        }
        if (step < 3) failed = true
    }
    val labels = listOf("휴대폰에서 목적지를 보내는 중", "차량이 목적지를 받았어요", "차량 내비에 경로를 요청하는 중", "경로가 준비됐어요 · 내비에서 안내해요")
    Column(Modifier.fillMaxSize()) {
        Bar("가보자요", { if (stage == 1) stage = 0 else back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Bl).padding(16.dp)) {
                Text("목적지", color = OnBl, fontSize = 11.sp)
                Text(p.n, color = OnBl, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(placeSub(p), color = OnBl, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (cars.isEmpty()) {
                Column(Modifier.fillMaxWidth().card().padding(16.dp), Arrangement.spacedBy(8.dp)) {
                    Text("연결된 차량이 없어요", fontWeight = FontWeight.Bold, color = Nv)
                    Text("계정의 차량을 연결하면 목적지를 보낼 수 있어요", fontSize = 12.sp, color = Sb)
                    Button(onCars, shape = RoundedCornerShape(10.dp)) { Text("차량 연결하기", fontSize = 13.sp) }
                }
            } else if (stage == 0) {
                Text("보낼 차량", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Nv)
                cars.forEach { c ->
                    val on = sel == c
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sf).border(if (on) 2.dp else 1.dp, if (on) Bl else Ln, RoundedCornerShape(16.dp)).clickable { sel = c }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Chp), Alignment.Center) { Icon(IcCar, null, Modifier.size(22.dp), tint = BlTx) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Bold, color = Nv, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${c.plate} · 연결됨", fontSize = 12.sp, color = Sb, maxLines = 1)
                        }
                        RadioButton(on, { sel = c })
                    }
                }
                Text("경로 선택", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Nv)
                routeOpts.forEachIndexed { i, r ->
                    val on = ri == i
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sf).border(if (on) 2.dp else 1.dp, if (on) Bl else Ln, RoundedCornerShape(16.dp)).clickable { ri = i }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontSize = 12.sp, color = if (on) BlTx else Sb, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(r.desc, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Nv, maxLines = 1)
                        }
                        Spacer(Modifier.width(6.dp))
                        RadioButton(on, { ri = i })
                    }
                }
                Text("예상값이에요 · 최종 경로는 차량 내비에서 확정돼요", fontSize = 10.sp, color = Sb, maxLines = 1)
                Button({ stage = 1 }, Modifier.fillMaxWidth().height(52.dp), enabled = sel != null, shape = RoundedCornerShape(14.dp)) {
                    Text("${sel?.name ?: "차량"}으로 보내기", fontWeight = FontWeight.Bold, maxLines = 1)
                }
                TextButton(onCars, Modifier.fillMaxWidth()) { Text("다른 차량 연결하기", fontSize = 12.sp) }
            } else {
                Row(Modifier.fillMaxWidth().card().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Chp), Alignment.Center) { Icon(IcCar, null, Modifier.size(22.dp), tint = BlTx) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sel?.name ?: "", fontWeight = FontWeight.Bold, color = Nv, maxLines = 1)
                        Text("${routeOpts[ri].name} 경로로 보내요", fontSize = 12.sp, color = Sb, maxLines = 1)
                    }
                    TextButton({ stage = 0 }) { Text("경로 변경", fontSize = 12.sp, maxLines = 1) }
                }
                Column(Modifier.fillMaxWidth().card().padding(16.dp), Arrangement.spacedBy(12.dp)) {
                    labels.forEachIndexed { i, l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(22.dp), Alignment.Center) {
                                when {
                                    failed && i == step -> Text("!", color = Red, fontWeight = FontWeight.Bold)
                                    i < step || (i == labels.lastIndex && step == labels.lastIndex) -> Text("✓", color = BlTx, fontWeight = FontWeight.Bold)
                                    i == step -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else -> Text("○", color = Ln)
                                }
                            }
                            Text("  $l", fontSize = 13.sp, color = if (i <= step) Nv else Sb, fontWeight = if (i == step) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                    trip?.let { (sec, m) ->
                        val min = ((sec + 59) / 60).coerceAtLeast(1)
                        Text("도착까지 약 ${if (min >= 60) "${min / 60}시간 ${min % 60}분" else "${min}분"} · ${"%.1f".format(m / 1000.0)}km", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BlTx)
                    }
                    if (failed) Text("전송에 실패했어요. 차량 연결 상태를 확인해 주세요.", fontSize = 12.sp, color = Red)
                }
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                    OutlinedButton({ scope.launch { sel?.let { carBridge().cancel(it) }; back() } }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("전송 취소", fontSize = 12.sp) }
                    Button({ attempt++ }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("다시 보내기", fontSize = 12.sp) }
                }
            }
        }
    }
}

// ───────── 후기 ─────────
fun stars(n: Int) = "★".repeat(n) + "☆".repeat(5 - n)

@Composable
fun ReviewDialog(name: String, onDismiss: () -> Unit, onSave: (Int, String) -> Unit) {
    var star by remember { mutableIntStateOf(5) }
    var t by remember { mutableStateOf("") }
    AlertDialog(onDismiss, title = { Text("후기 쓰기", fontSize = 16.sp) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(name, fontSize = 12.sp, color = Sb, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row { (1..5).forEach { s -> Text(if (s <= star) "★" else "☆", Modifier.clickable { star = s }.padding(2.dp), Amber, 28.sp) } }
            KField(t, { t = it }, Modifier.fillMaxWidth().height(120.dp), "다녀온 느낌을 적어 주세요", singleLine = false)
        }
    }, confirmButton = { TextButton({ onSave(star, t.trim()) }, enabled = t.isNotBlank()) { Text("등록") } },
        dismissButton = { TextButton(onDismiss) { Text("취소") } })
}

@Composable
fun Reviews(p: Place, list: List<Review>, back: () -> Unit, onWrite: (Int, String) -> Unit) {
    var sort by remember { mutableStateOf("최신순") }
    var star by remember { mutableIntStateOf(0) }
    var wr by remember { mutableStateOf(false) }
    val avg = if (list.isEmpty()) 0.0 else list.map { it.star }.average()
    val shown = list.filter { star == 0 || it.star == star }.let { l ->
        when (sort) { "별점 높은순" -> l.sortedByDescending { it.star }; "별점 낮은순" -> l.sortedBy { it.star }; else -> l.sortedByDescending { it.date } }
    }
    Column(Modifier.fillMaxSize()) {
        Bar("후기 · ${p.n}", back) { TextButton({ wr = true }) { Text("후기 쓰기", fontSize = 12.sp, maxLines = 1) } }
        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("✍️", fontSize = 40.sp)
                Text("아직 후기가 없어요.\n첫 후기를 남겨 보세요!", fontSize = 12.sp, color = Sb, textAlign = TextAlign.Center, lineHeight = 18.sp)
                Button({ wr = true }, shape = RoundedCornerShape(20.dp)) { Text("후기 쓰기", fontSize = 12.sp) }
            }
        } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth().card().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.1f".format(avg), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                        Text(stars(avg.roundToInt()), color = Amber, fontSize = 13.sp, maxLines = 1)
                        Text("${list.size}개 후기", fontSize = 11.sp, color = Sb, maxLines = 1)
                    }
                    Column(Modifier.weight(1f), Arrangement.spacedBy(5.dp)) {
                        (5 downTo 1).forEach { s ->
                            val n = list.count { it.star == s }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$s", Modifier.width(14.dp), fontSize = 11.sp)
                                LinearProgressIndicator(progress = { n / list.size.toFloat() }, Modifier.weight(1f).height(8.dp).clip(CircleShape), color = Amber, trackColor = Ln)
                                Text("$n", Modifier.width(26.dp), fontSize = 11.sp, color = Sb, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) { listOf("최신순", "별점 높은순", "별점 낮은순").forEach { Chip(it, sort == it) { sort = it } } } }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                    Chip("전체 ${list.size}", star == 0) { star = 0 }
                    (5 downTo 1).forEach { s -> Chip("★$s (${list.count { it.star == s }})", star == s) { star = s } }
                }
            }
            if (shown.isEmpty()) item { Text("해당 별점의 후기가 없어요", Modifier.fillMaxWidth().padding(top = 30.dp), Sb, 12.sp, textAlign = TextAlign.Center) }
            items(shown) { r ->
                Column(Modifier.fillMaxWidth().card().padding(14.dp), Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(stars(r.star), color = Amber, fontSize = 13.sp, maxLines = 1); Text(r.date, fontSize = 11.sp, color = Sb, maxLines = 1) }
                    Text(r.who, fontSize = 11.sp, color = Sb, maxLines = 1); Text(r.text, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
    }
    if (wr) ReviewDialog(p.n, { wr = false }) { s, t -> onWrite(s, t); wr = false }
}

/** 설정에서 값을 바꾸면 tick 을 올려서 찜 목록을 차량으로 다시 보내요 */
object CarSync { var tick by mutableIntStateOf(0) }

// 서버 이야기 ↔ 화면 모델
fun com.gabozayo.core.StoryItem.toPost(me: String) = Post(id, "익명의 가보자", kind, title, body, like, 0, by == me, date)
fun com.gabozayo.core.StoryComment.toComment(sid: Long, me: String) = Comment(id, sid, text, date, by == me)
