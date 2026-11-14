package com.gabozayo.app

import android.Manifest
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 색상 토큰(Bg, Sf, Bl, Nv, Sb, Ln …)은 Theme.kt 에 있어요 (라이트/다크 자동 전환)
val NoPref = Color(0xFF8793A8)

/** 아이 취향. color = 취향 색상, kw = 추천 검색 키워드 */
class Like(val emoji: String, val label: String, private val base: Color, val kw: String, val group: String = "") {
    /** 다크 모드에서는 조금 밝게 */
    val color: Color get() = if (Th.dark) lerp(base, Color.White, 0.28f) else base
}
val likeGroups = listOf("자연·동물", "체험·배움", "놀이·액티비티", "탈것·공룡", "축제·공연")
val likes = listOf(
    Like("🦖", "공룡", Color(0xFF2E8B57), "공룡", "탈것·공룡"),
    Like("🐘", "동물", Color(0xFFE07B1A), "동물원", "자연·동물"),
    Like("🚂", "기차", Color(0xFF4C5FD5), "기차", "탈것·공룡"),
    Like("🏖", "물놀이", Color(0xFF11A39C), "워터파크", "놀이·액티비티"),
    Like("🎡", "놀이공원", Color(0xFFD6336C), "놀이공원", "놀이·액티비티"),
    Like("🌳", "숲·꽃", Color(0xFF7BA428), "수목원", "자연·동물"),
    Like("🔭", "과학·우주선", Color(0xFF5B3FC4), "과학관", "체험·배움"),
    Like("🎨", "만들기 체험", Color(0xFFC98A00), "체험마을", "체험·배움"),
    Like("🛷", "눈썰매", Color(0xFF2BA3D8), "눈썰매", "놀이·액티비티"),
    // 아래는 새로 추가 (저장된 번호가 바뀌지 않도록 뒤에 붙였어요)
    Like("🐞", "곤충", Color(0xFF8A9A1B), "곤충", "자연·동물"),
    Like("🦀", "바다·갯벌", Color(0xFF1F8FBF), "갯벌체험", "자연·동물"),
    Like("🍓", "농장 체험", Color(0xFFE5484D), "체험농장", "자연·동물"),
    Like("🏛", "박물관", Color(0xFF8B5E3C), "박물관", "체험·배움"),
    Like("🏯", "역사·문화", Color(0xFFB4472E), "역사", "체험·배움"),
    Like("🖼", "미술·전시", Color(0xFF9B59B6), "미술관", "체험·배움"),
    Like("⛺", "캠핑", Color(0xFF2F855A), "캠핑", "놀이·액티비티"),
    Like("🧸", "키즈카페", Color(0xFFEC7FA9), "키즈카페", "놀이·액티비티"),
    Like("🎪", "공연·축제", Color(0xFFDD6B20), "축제", "축제·공연"),
)
fun kidColor(prefs: Map<String, List<Int>>, k: String): Color = prefs[k]?.firstOrNull()?.let { likes.getOrNull(it)?.color } ?: NoPref
fun kidEmoji(prefs: Map<String, List<Int>>, k: String): String = prefs[k]?.firstOrNull()?.let { likes.getOrNull(it)?.emoji } ?: "🙂"

/** 위치를 못 잡거나 에뮬레이터일 때 쓰는 지역 목록 (위도 to 경도) */
val cities = listOf(
    "서울" to (37.5665 to 126.9780), "부산" to (35.1796 to 129.0756), "인천" to (37.4563 to 126.7052),
    "대구" to (35.8714 to 128.6014), "대전" to (36.3504 to 127.3845), "광주" to (35.1595 to 126.8526),
    "울산" to (35.5384 to 129.3114), "세종" to (36.4800 to 127.2890), "수원" to (37.2636 to 127.0286),
    "춘천" to (37.8813 to 127.7298), "강릉" to (37.7519 to 128.8761), "전주" to (35.8242 to 127.1480),
    "경주" to (35.8562 to 129.2247), "여수" to (34.7604 to 127.6622), "제주" to (33.4996 to 126.5312),
)

/** 지역을 직접 고르면 그 지역의 시·도를 같이 기억해요 */
val citySido = mapOf("서울" to "서울", "부산" to "부산", "인천" to "인천", "대구" to "대구", "대전" to "대전", "광주" to "광주", "울산" to "울산", "세종" to "세종",
    "수원" to "경기", "춘천" to "강원", "강릉" to "강원", "전주" to "전북", "경주" to "경북", "여수" to "전남", "제주" to "제주")

fun setManualCity(n: String) {
    val ll = cities.firstOrNull { it.first == n }?.second ?: return
    Geo.manual = true
    Geo.loc = Location("manual").apply { latitude = ll.first; longitude = ll.second }
    Geo.area = n; Geo.sido = citySido[n].orEmpty(); Geo.near = emptyList(); Geo.nearMsg = ""; Geo.wx = null
    Store.putStr("manual_city", n)
}

fun useRealLocation(ctx: Context) {
    Geo.manual = false; Geo.area = ""; Geo.sido = ""; Geo.near = emptyList(); Geo.nearMsg = ""; Geo.wx = null
    Store.putStr("manual_city", "")
    Geo.loc = lastLocation(ctx); refreshLocation(ctx); Geo.tick++
}

val tabs = listOf("home", "saved", "story", "my")

fun ico(d: String): ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
val IcHome = ico("M3 11l9-8 9 8v10H3z")
val IcHeart = ico("M12 21C5 15.5 3 12 3 8.8C3 6.2 5 4.3 7.5 4.3C9.5 4.3 11.2 5.4 12 7C12.8 5.4 14.5 4.3 16.5 4.3C19 4.3 21 6.2 21 8.8C21 12 19 15.5 12 21Z")
val IcChat = ico("M4 5h16v11H9l-5 4z")
val IcUser = ico("M12 4C14.2 4 16 5.8 16 8C16 10.2 14.2 12 12 12C9.8 12 8 10.2 8 8C8 5.8 9.8 4 12 4ZM4 21C4 17 7.6 14.5 12 14.5C16.4 14.5 20 17 20 21")

fun Modifier.card() = this.clip(RoundedCornerShape(24.dp)).background(Sf)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 시작 화면은 시스템이 그려주는 하나만 쓰고, 1.2초 동안 보여준 뒤 바로 로그인 화면으로 가요
        val splash = installSplashScreen()
        val t0 = android.os.SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { android.os.SystemClock.uptimeMillis() - t0 < 1200 }
        super.onCreate(savedInstanceState)
        Store.init(this)
        Th.load(this)
        SearchStats.load()
        Store.places().forEach { Geo.register(it) }
        Store.str("manual_city", "").takeIf { it.isNotBlank() }?.let { setManualCity(it) }
        setContent { AppTheme { App() } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val stack = remember { mutableStateListOf("login") }
    val saved = remember { mutableStateListOf<Int>().apply { addAll(Store.ids("saved")) } }
    val kids = remember { mutableStateListOf<String>().apply { addAll(Store.kids().map { it.first }) } }
    val prefs = remember {
        mutableStateMapOf<String, SnapshotStateList<Int>>().apply {
            Store.kids().forEach { (k, v) -> put(k, mutableStateListOf<Int>().apply { addAll(v) }) }
        }
    }
    val cars = remember { mutableStateListOf<Car>() }
    val viewed = remember { mutableStateListOf<Int>().apply { addAll(Store.ids("viewed")) } }
    val recent = remember { mutableStateListOf<String>().apply { addAll(Store.strs("recent")) } }
    // 예전 테스트 빌드에 남은 글(id 1~12)은 불러오지 않아요
    val posts = remember { mutableStateListOf<Post>().apply { addAll(Store.posts().filter { it.id !in 1L..12L }) } }
    val comments = remember { mutableStateListOf<Comment>().apply { addAll(Store.comments().filter { it.postId !in 1L..12L }) } }
    val liked = remember { mutableStateListOf<Long>().apply { addAll(Store.liked().filter { it !in 1L..12L }) } }
    val reviews = remember { mutableStateListOf<Review>().apply { addAll(Store.reviews()) } }
    var q by remember { mutableStateOf("") }
    val cur = stack.last()
    val r = cur.split("/")
    fun go(x: String) { stack.add(x) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun tab(x: String) { stack.clear(); stack.add(x) }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    val scope = rememberCoroutineScope()
    val pendingStories = remember { mutableSetOf<Long>() }   // 서버에 아직 올라가지 않은 글·댓글 id (그동안은 서버 목록으로 덮어쓰지 않아요)
    fun fav(id: Int) { if (saved.contains(id)) saved.remove(id) else saved.add(id) }
    // 찜 목록을 차량 앱에 보내요 (바뀔 때, 버튼을 누를 때, 그리고 30초마다 다시)
    // 차량 홈 화면용 추천 장소: 홈 화면과 같은 방식(아이 취향 + 현재 지역)으로 찾아 두고 차량에 보내요
    var carRecs by remember { mutableStateOf<List<Pair<Place, String>>>(emptyList()) }
    val carRecKey = kids.joinToString(",") + prefs.entries.joinToString(";") { it.key + it.value } + Geo.sido + (Geo.eff?.let { "%.2f,%.2f".format(it.latitude, it.longitude) } ?: "")
    LaunchedEffect(carRecKey) {
        val here = Geo.eff
        if (here == null) { carRecs = emptyList(); return@LaunchedEffect }
        // 아이마다 그 아이가 고른 취향으로 따로 찾아요 (차량에서 아이를 누르면 그 아이 취향 추천이 나와요)
        val out = mutableListOf<Pair<Place, String>>()
        val familyTop = kids.flatMap { prefs[it] ?: emptyList() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }.filter { it in likes.indices }
        for (k in kids.toList()) {
            val mine = (prefs[k] ?: emptyList()).filter { it in likes.indices }.take(3)
            if (mine.isEmpty()) continue
            var got = 0
            Tour.recommend(mine.map { it to likes[it].kw }, here.latitude, here.longitude, Geo.sido)
                .onSuccess { list -> list.take(3).forEach { (p, li) -> out += p to "$k 취향 · ${likes[li].label}"; got++ } }
                .onFailure { android.util.Log.w("Gabozayo", "추천 실패($k): ${it.message}") }
            // 그 아이 취향으로 이 지역에서 못 찾으면, 우리 가족이 많이 고른 취향으로 대신 보여 줘요 (이유 문구에 그렇다고 밝혀요)
            if (got == 0 && familyTop.isNotEmpty()) {
                Tour.recommend(familyTop.map { it to likes[it].kw }, here.latitude, here.longitude, Geo.sido)
                    .onSuccess { list -> list.take(3).forEach { (p, li) -> out += p to "$k 가족 추천 · ${likes[li].label}" } }
                    .onFailure { android.util.Log.w("Gabozayo", "가족 추천 실패($k): ${it.message}") }
            }
        }
        carRecs = out
    }
    LaunchedEffect(saved.toList().joinToString(","), CarSync.tick, carRecKey, carRecs.size, Geo.events.size) {
        while (true) {
            carBridge().pushHome(carRecs, Geo.events.take(5), kids.map { k -> com.gabozayo.core.KidItem(k, (prefs[k] ?: emptyList()).mapNotNull { likes.getOrNull(it)?.label }) })
            carBridge().pushSaved(saved.mapNotNull { Geo.cache[it] })
            // 아이들이 고른 취향을 많이 고른 순서로 5개까지 보내요
            val rank = kids.flatMap { prefs[it] ?: emptyList() }.groupingBy { it }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
                .mapNotNull { e -> likes.getOrNull(e.key)?.let { com.gabozayo.core.InterestItem(it.emoji, it.label, e.value) } }.take(5)
            carBridge().pushInterests(rank)
            delay(30_000.milliseconds)
        }
    }
    // 차량이 코드를 입력해서 연결됐는지 5초마다 확인해서 cars 에 반영해요
    LaunchedEffect(Unit) {
        FirebaseCarBridge.ensureCode()   // 연결 코드를 서버에 등록해요 (겹치면 새로 뽑아요)
        while (true) {
            when (FirebaseCarBridge.carConnected()) {
                true -> if (cars.isEmpty()) cars.add(Car("내 차량", "", familyCode()))
                false -> cars.clear()
                null -> {}
            }
            delay(5_000.milliseconds)
        }
    }
    fun submit(v: String) {
        val k = v.trim(); if (k.isBlank()) return
        q = k; recent.remove(k); recent.add(0, k); while (recent.size > 8) recent.removeAt(recent.lastIndex)
        SearchStats.record(k); back(); go("result")
    }
    // ── 이야기: 서버에 저장하고, 지우면 서버에서도 지워요 ──
    fun addPost(k: String, t: String, b: String) {
        val me = FirebaseCarBridge.deviceId
        val p = Post(System.currentTimeMillis(), "익명의 가보자", k, t, b, 0, 0, true, todayStr())
        posts.add(0, p); pendingStories.add(p.id); back()
        scope.launch {
            carBridge().putStory(com.gabozayo.core.StoryItem(p.id, k, t, b, p.date, 0, me))
                .onSuccess { toast("글이 올라갔어요") }
                .onFailure { posts.removeAll { x -> x.id == p.id }; toast("서버에 올리지 못했어요. 연결을 확인해 주세요") }
            pendingStories.remove(p.id)
        }
    }
    fun delPost(p: Post, then: () -> Unit = {}) {
        scope.launch {
            carBridge().deleteStory(p.id)
                .onSuccess { posts.removeAll { x -> x.id == p.id }; comments.removeAll { c -> c.postId == p.id }; liked.remove(p.id); then(); toast("글을 삭제했어요") }
                .onFailure { toast("삭제하지 못했어요. 연결을 확인해 주세요") }
        }
    }
    fun toggleLike(p: Post) {
        val i = posts.indexOfFirst { it.id == p.id }; if (i < 0) return
        val was = p.id in liked
        val n = (p.like + if (was) -1 else 1).coerceAtLeast(0)
        if (was) liked.remove(p.id) else liked.add(p.id)
        posts[i] = posts[i].copy(like = n)
        scope.launch {
            carBridge().setStoryLike(p.id, n).onFailure {
                val j = posts.indexOfFirst { x -> x.id == p.id }; if (j >= 0) posts[j] = posts[j].copy(like = p.like)
                if (was) liked.add(p.id) else liked.remove(p.id)
                toast("좋아요를 저장하지 못했어요")
            }
        }
    }
    fun addComment(p: Post, t: String) {
        val me = FirebaseCarBridge.deviceId
        val c = Comment(System.currentTimeMillis(), p.id, t, todayStr()); comments.add(c); pendingStories.add(c.id)
        scope.launch {
            carBridge().putStoryComment(p.id, com.gabozayo.core.StoryComment(c.id, t, c.date, me))
                .onFailure { comments.removeAll { x -> x.id == c.id }; toast("댓글을 올리지 못했어요. 연결을 확인해 주세요") }
            pendingStories.remove(c.id)
        }
    }
    fun delComment(c: Comment) {
        scope.launch {
            carBridge().deleteStoryComment(c.postId, c.id)
                .onSuccess { comments.removeAll { x -> x.id == c.id } }
                .onFailure { toast("댓글을 지우지 못했어요. 연결을 확인해 주세요") }
        }
    }
    /** 내 데이터 모두 삭제: 서버(찜·아이·추천·연결 기록·이야기)를 먼저 지우고, 성공하면 폰 데이터도 지워요 */
    fun resetAll() {
        scope.launch {
            carBridge().deleteAllData()
                .onSuccess {
                    saved.clear(); viewed.clear(); kids.clear(); prefs.clear(); posts.clear(); comments.clear(); liked.clear(); reviews.clear(); recent.clear(); cars.clear()
                    SearchStats.clear(); Store.wipe(); Th.set("system"); useRealLocation(ctx)
                    toast("내 데이터를 모두 삭제했어요"); tab("login")
                }
                .onFailure { toast("서버 데이터를 지우지 못했어요. 연결을 확인하고 다시 시도해 주세요") }
        }
    }
    fun addReview(p: Place, star: Int, text: String) { reviews.add(0, Review(p.id, p.n, star, todayStr(), text)); toast("후기를 남겼어요") }
    BackHandler(stack.size > 1) { back() }

    // 서버의 이야기를 15초마다 받아와요 (같은 코드를 쓰는 기기끼리 같은 글을 봐요)
    LaunchedEffect(Unit) {
        while (true) {
            val me = FirebaseCarBridge.deviceId
            if (!Store.bool("storiesSynced", false)) {
                // 예전 버전에서 기기에만 있던 내 글·댓글을 한 번만 서버로 옮겨요
                posts.filter { it.mine }.forEach { p -> carBridge().putStory(com.gabozayo.core.StoryItem(p.id, p.kind, p.title, p.body, p.date, p.like, me)) }
                comments.filter { it.mine }.forEach { c -> carBridge().putStoryComment(c.postId, com.gabozayo.core.StoryComment(c.id, c.text, c.date, me)) }
                Store.putBool("storiesSynced", true)
            }
            carBridge().loadStories().onSuccess { list ->
                val sp = list.map { it.toPost(me) }
                val newPosts = (sp + posts.filter { it.id in pendingStories && sp.none { x -> x.id == it.id } }).sortedByDescending { it.id }
                if (newPosts != posts.toList()) { posts.clear(); posts.addAll(newPosts) }
                val sc = list.flatMap { st -> st.comments.map { it.toComment(st.id, me) } }
                val newComments = sc + comments.filter { it.id in pendingStories && sc.none { x -> x.id == it.id } }
                if (newComments != comments.toList()) { comments.clear(); comments.addAll(newComments) }
                liked.removeAll { id -> newPosts.none { it.id == id } }
            }
            delay(15_000.milliseconds)
        }
    }

    // 기기에 저장
    LaunchedEffect(Unit) {
        launch { snapshotFlow { saved.toList() to viewed.toList() }.collect { (s, v) -> Store.putIds("saved", s); Store.putIds("viewed", v); Store.putPlaces((s + v).distinct().mapNotNull { Geo.cache[it] }) } }
        launch { snapshotFlow { kids.toList() to prefs.mapValues { it.value.toList() } }.collect { (k, p) -> Store.putKids(k, p) } }
        launch { snapshotFlow { posts.toList() }.collect { Store.putPosts(it) } }
        launch { snapshotFlow { comments.toList() }.collect { Store.putComments(it) } }
        launch { snapshotFlow { liked.toList() }.collect { Store.putLiked(it) } }
        launch { snapshotFlow { reviews.toList() }.collect { Store.putReviews(it) } }
        launch { snapshotFlow { recent.toList() }.collect { Store.putStrs("recent", it) } }
    }

    // 위치
    var locAsked by remember { mutableStateOf(hasLocPerm(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { g -> if (g.values.any { it } && !Geo.manual) { Geo.loc = lastLocation(ctx); refreshLocation(ctx) } }
    LaunchedEffect(Unit) { if (hasLocPerm(ctx) && !Geo.manual) { Geo.loc = lastLocation(ctx); refreshLocation(ctx) } }
    fun askLoc() { locAsked = true; permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

    // 내 위치가 바뀌면 근처 장소 · 날씨 · 동네 이름 · 행사를 실제로 불러와요
    val lk = locKey(Geo.eff, 3)
    LaunchedEffect(lk, Geo.tick) {
        val l = Geo.eff   // 한국 안의 위치만 사용 (에뮬레이터 기본 위치 = 캘리포니아는 제외)
        Geo.nearLoading = true
        if (l == null) { if (!Geo.manual) { Geo.area = ""; Geo.sido = "" }; Geo.near = emptyList(); Geo.wx = null }
        else if (!Geo.manual) areaInfo(ctx, l).let { Geo.area = it.label; Geo.sido = it.sido }
        coroutineScope {
            if (l != null) {
                launch { fetchWeather(l.latitude, l.longitude).onSuccess { Geo.wx = it }.onFailure { android.util.Log.w("Gabozayo", "날씨 실패: ${it.message}") } }
                launch { Tour.nearby(l.latitude, l.longitude).onSuccess { Geo.near = it; Geo.nearMsg = "" }.onFailure { Geo.nearMsg = it.message ?: "근처 장소를 불러오지 못했어요" } }
            }
            launch { Tour.events(l?.latitude, l?.longitude, Geo.sido).onSuccess { Geo.events = it.first; Geo.eventsInRegion = it.second } }
        }
        Geo.nearLoading = false
    }

    Scaffold(containerColor = Bg) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (r[0]) {
                "login" -> Login { tab("intro") }
                "intro" -> Intro { tab("home") }
                "home" -> Home(saved, ::fav, kids, prefs, { go("search") }, { tab("my") }, ::askLoc, { refreshLocation(ctx); Geo.tick++ }, { go("map") }, { go("car") }) { go("detail/$it") }
                "search" -> Search(recent, saved, ::fav, ::submit, { go("detail/$it") }, ::back)
                "result" -> SearchResult(q, saved, ::fav, ::back) { go("detail/$it") }
                "map" -> NearMap(::back) { go("detail/$it") }
                "detail" -> {
                    val did = r[1].toInt(); val pp = Geo.cache[did]
                    if (pp == null) Plain("장소", ::back) { Text("장소 정보를 찾을 수 없어요", fontSize = 12.sp, color = Sb) }
                    else {
                        LaunchedEffect(did) { viewed.remove(did); viewed.add(0, did); while (viewed.size > 30) viewed.removeAt(viewed.lastIndex) }
                        Detail(pp, saved, ::fav, ::back, ::toast, cars.isNotEmpty(), reviews.filter { it.placeId == did }, { go("reviews/$did") }, { s, t -> addReview(pp, s, t) }, { go("navi/$did") }, { go("car") })
                    }
                }
                "reviews" -> Geo.cache[r[1].toInt()]?.let { pp -> Reviews(pp, reviews.filter { it.placeId == pp.id }, ::back) { s, t -> addReview(pp, s, t) } }
                "navi" -> Geo.cache[r[1].toInt()]?.let { NaviSend(it, cars, ::back) { go("car") } }
                "saved" -> Saved(saved, ::fav) { go("detail/$it") }
                "story" -> Story(posts, { go("post/$it") }, { go("ssearch") }, { tab("my") }) { go("write") }
                "post" -> {
                    val pid = r[1].toLongOrNull(); val pst = posts.firstOrNull { it.id == pid }
                    if (pst == null) Plain("이야기", ::back) { Text("글을 찾을 수 없어요", fontSize = 12.sp, color = Sb) }
                    else PostDetail(
                        pst, pst.id in liked, comments.filter { it.postId == pst.id }.sortedBy { it.id }, ::back,
                        onLike = { toggleLike(pst) },
                        onComment = { t -> addComment(pst, t) },
                        onDelComment = { delComment(it) },
                        onDelPost = if (pst.mine) ({ delPost(pst) { back() } }) else null,
                    )
                }
                "ssearch" -> SSearch(posts, { go("post/$it") }, ::back)
                "write" -> Write(::back) { k, t, b -> addPost(k, t, b) }
                "my" -> My(kids, prefs, cars.isNotEmpty(), viewed.size, posts.count { it.mine } + reviews.size, { k -> kids.remove(k); prefs.remove(k) }, ::go)
                "mine" -> Mine(posts.filter { it.mine }, reviews, { delPost(it) }, { reviews.remove(it) }, ::back, { go("post/$it") }) { go("detail/$it") }
                "prefs" -> prefs[r[1]]?.let { Prefs(r[1], it, ::back) { kids.remove(r[1]); prefs.remove(r[1]); back() } }
                "kids" -> Kids(kids, prefs, ::back, { k -> kids.remove(k); prefs.remove(k) }) { n -> kids.add(n); prefs[n] = mutableStateListOf(); back(); go("prefs/$n") }
                "car" -> Cars(cars, ::back)
                "recent" -> Recent(viewed, saved, ::fav, ::back) { go("detail/$it") }
                "stamp" -> Plain("나들이 스탬프", ::back) {
                    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏅", fontSize = 56.sp)
                        Text("다녀온 곳에 도착하면 이곳에 스탬프가 찍혀요.\n도착을 모으면 선물이 있어요! (준비 중)", fontSize = 12.sp, color = Sb, textAlign = TextAlign.Center, lineHeight = 18.sp)
                    }
                }
                "set" -> SettingsScreen(::back, { tab("login") }, { viewed.clear() }, { recent.clear(); SearchStats.clear() }, { saved.clear() },
                    { resetAll() }, ::toast, ::askLoc)
            }
            if (r[0] in tabs) FloatBar(r[0], ::tab, Modifier.align(Alignment.BottomCenter))
            if (r[0] == "home" && !locAsked) AlertDialog({ locAsked = true }, title = { Text("내 위치 공유") },
                text = { Text("내 근처 가볼만한 곳을 찾고 거리를 계산하려면 현재 위치가 필요해요. 위치 정보는 장소 추천과 길찾기에만 사용돼요.") },
                confirmButton = { TextButton({ askLoc() }) { Text("허용") } }, dismissButton = { TextButton({ locAsked = true }) { Text("나중에") } })
        }
    }
}

// ---------- 공통 ----------
val Shade = Brush.verticalGradient(0.40f to Color.Transparent, 1f to Color(0xD9000000))

@Composable
fun SectionTitle(t: String, sub: String = "", modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(t, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Nv, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = Sb, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RoundBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onClick), Alignment.Center) { content() }
}

@Composable
fun HeartBtn(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sc by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0.9f, androidx.compose.animation.core.spring(dampingRatio = 0.3f, stiffness = 500f), label = "heart")
    Box(modifier.size(36.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onClick), Alignment.Center) {
        Icon(if (on) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "찜", tint = if (on) Color(0xFFFF6B6B) else Color.White, modifier = Modifier.size(20.dp).graphicsLayer { scaleX = sc; scaleY = sc })
    }
}

/** 사진이 주인공인 카드. 이름·거리는 사진 위에 한 줄로 */
@Composable
fun PlaceCard(p: Place, fav: Boolean, onFav: () -> Unit, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null, click: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(Im).pressScale(click)) {
        if (p.img.isNotBlank()) AsyncImage(model = p.img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else PhotoPlaceholder(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Shade))
        HeartBtn(fav, onFav, Modifier.align(Alignment.TopEnd).padding(12.dp))
        if (onRemove != null) Box(Modifier.align(Alignment.TopStart).padding(12.dp).size(36.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onRemove), Alignment.Center) {
            Icon(Icons.Default.Close, "삭제", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.n, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(placeSub(p), color = Color(0xCCFFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 이모지 대신 아이콘 + 글자 한 줄 */
@Composable
fun PinText(t: String, color: Color = Sb, size: Int = 14, iconColor: Color = color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(Icons.Default.LocationOn, null, tint = iconColor, modifier = Modifier.size((size + 3).dp))
        Text(t, fontSize = size.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Thumb(url: String, modifier: Modifier, big: Boolean = false) {
    if (url.isBlank()) PhotoPlaceholder(modifier, if (big) 30.dp else 20.dp)
    else AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.background(Im))
}

/** 한글 조합(IME)을 깨지 않도록 TextFieldValue 로 직접 받는 입력창 */
@Composable
fun KField(
    value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier, hint: String = "", singleLine: Boolean = true,
    maxLen: Int = Int.MAX_VALUE, shape: Shape = RoundedCornerShape(12.dp), ime: ImeAction = ImeAction.Default,
    onDone: () -> Unit = {}, hintSize: TextUnit = 14.sp,
) {
    var tf by remember { mutableStateOf(TextFieldValue(value)) }
    if (tf.text != value) tf = TextFieldValue(value, TextRange(value.length))
    OutlinedTextField(
        tf, { nv ->
            tf = if (nv.composition == null && nv.text.length > maxLen) TextFieldValue(nv.text.take(maxLen), TextRange(maxLen)) else nv
            onValue(tf.text)
        }, modifier, singleLine = singleLine, placeholder = { Text(hint, fontSize = hintSize) }, shape = shape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ime),
        keyboardActions = KeyboardActions(onSearch = { onDone() }, onDone = { onDone() }, onGo = { onDone() }),
    )
}

@Composable
fun Chip(t: String, on: Boolean, click: () -> Unit) = Text(t, Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) Bl else Chp).clickable(onClick = click).padding(horizontal = 14.dp, vertical = 7.dp),
    if (on) OnBl else ChT, 12.sp, maxLines = 1)

@Composable
fun Tag(t: String) = Text(t, Modifier.clip(RoundedCornerShape(6.dp)).background(TagBg).padding(horizontal = 6.dp, vertical = 1.dp), TagTx, 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)

@Composable
fun Bar(t: String, back: () -> Unit, right: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Sf).clickable(onClick = back), Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Nv, modifier = Modifier.size(20.dp)) }
        Text(t, Modifier.weight(1f).padding(start = 12.dp), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Nv, maxLines = 1, overflow = TextOverflow.Ellipsis); right()
    }
}

@Composable
fun Plain(t: String, back: () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) { Bar(t, back); Column(Modifier.padding(16.dp), Arrangement.spacedBy(14.dp)) { body() } }
}

@Composable
fun Avatar(click: () -> Unit, size: Int = 44) = Box(Modifier.size(size.dp).clip(CircleShape).background(Bl).clickable(onClick = click), Alignment.Center) { Text("G", color = OnBl, fontWeight = FontWeight.Bold) }

/** 떠 있는 바. 선택한 탭만 알약으로 늘어나며 이름이 보여요 */
@Composable
fun FloatBar(cur: String, onTab: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier.padding(horizontal = 22.dp, vertical = 16.dp).fillMaxWidth().height(64.dp)
            .shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.30f))
            .clip(shape).background(Sf.copy(alpha = 0.94f)).border(0.8.dp, Color.White.copy(alpha = if (Th.dark) 0.08f else 0.6f), shape).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(Triple("home", "홈", IcHome), Triple("saved", "찜", IcHeart), Triple("story", "이야기", IcChat), Triple("my", "내 정보", IcUser)).forEach { (k, l, i) ->
            val on = cur == k
            val w by androidx.compose.animation.core.animateFloatAsState(if (on) 2.1f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f), label = "w")
            val bg by androidx.compose.animation.animateColorAsState(if (on) Bl else Color.Transparent, label = "bg")
            val tint by androidx.compose.animation.animateColorAsState(if (on) OnBl else Sb, label = "tint")
            Row(
                Modifier.weight(w).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(bg)
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onTab(k) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(i, l, Modifier.size(23.dp), tint = tint)
                if (on) Text(l, Modifier.padding(start = 6.dp), OnBl, 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** 모든 글자를 한 줄로 (넘치면 …) */
@Composable
fun PlaceRow(p: Place, fav: Boolean, onFav: () -> Unit, modifier: Modifier = Modifier, tag: Boolean = false, sub: String = placeSub(p), badge: Like? = null, click: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Sf).pressScale(click).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumb(p.img, Modifier.size(92.dp).clip(RoundedCornerShape(20.dp))); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.n, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Nv, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false)); if (tag) Tag(p.tag)
            }
            if (badge != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${badge.emoji} ${badge.label}", Modifier.clip(RoundedCornerShape(8.dp)).background(badge.color.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 2.dp), badge.color, 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (sub.isNotBlank()) Text(sub, Modifier.weight(1f, false), fontSize = 12.sp, color = Sb, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = Sb, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onFav) { Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "찜", tint = if (fav) Red else Sb) }
    }
}

// ---------- 로그인 ----------
@Composable
fun Login(onLogin: () -> Unit) {
    // 계정 없이 시작해요. 폰과 차량은 6자리 연결 코드로 이어져요.
    Column(Modifier.fillMaxSize().background(Sf).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text("가보자요", color = BlTx, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
        Text("아이와 함께 갈 나들이,\n차에서 한 번에", Modifier.padding(top = 14.dp), fontSize = 15.sp, color = Sb, textAlign = TextAlign.Center, lineHeight = 22.sp)
        Spacer(Modifier.weight(1.3f))
        Button(onLogin, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Bl)) {
            Text("시작하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

private data class IntroPage(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val body: String)

/** 시작하기 다음에 나오는 기능 소개 4장. 다음 버튼으로 넘기고 마지막 장에서 가보자요! 로 홈에 들어가요. */
@Composable
fun Intro(onDone: () -> Unit) {
    val pages = remember {
        listOf(
            IntroPage(Icons.Default.Face, "아이 취향으로 추천해요", "아이별로 좋아하는 것을 고르면\n동물·곤충·물놀이처럼 취향에 맞는\n장소와 행사를 골라 보여 줘요."),
            IntroPage(Icons.Default.Place, "오늘 갈 곳을 한눈에", "지금 있는 지역의 날씨와\n오늘 열리는 행사, 가까운 추천 장소를\n홈에서 바로 볼 수 있어요."),
            IntroPage(Icons.Default.Favorite, "찜하고 차로 보내요", "마음에 드는 곳을 찜하거나 \"가보자요!\"로 보내면\n차량 앱에 바로 나타나요.\n차에서는 한 번의 탭으로 경로를 만들어요."),
            IntroPage(Icons.Default.Create, "후기와 이야기를 나눠요", "다녀온 후기와 궁금한 점, 꿀팁을\n익명으로 남기고 다른 가족의\n이야기도 읽어 볼 수 있어요."),
        )
    }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    Column(Modifier.fillMaxSize().background(Sf).padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
            val pg = pages[i]
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(132.dp).clip(CircleShape).background(Bl.copy(alpha = 0.12f)), Alignment.Center) {
                    Icon(pg.icon, null, Modifier.size(60.dp), tint = BlTx)
                }
                Text(pg.title, Modifier.padding(top = 36.dp), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Nv, textAlign = TextAlign.Center)
                Text(pg.body, Modifier.padding(top = 16.dp), fontSize = 15.sp, color = Sb, textAlign = TextAlign.Center, lineHeight = 24.sp)
            }
        }
        Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(pages.size) { i ->
                Box(Modifier.height(8.dp).width(if (i == pager.currentPage) 24.dp else 8.dp).clip(CircleShape).background(if (i == pager.currentPage) Bl else Ln))
            }
        }
        Button(
            { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Bl),
        ) { Text(if (last) "가보자요!" else "다음", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

// ---------- 홈 ----------
@Composable
fun EventPager(evs: List<Place>, open: (Int) -> Unit) {
    val n = evs.size
    val pager = rememberPagerState(initialPage = n * 500) { n * 1000 }
    LaunchedEffect(n) { if (n > 1) while (true) { delay(6000); pager.animateScrollToPage(pager.currentPage + 1, animationSpec = tween(1200)) } }
    HorizontalPager(pager, contentPadding = PaddingValues(start = 20.dp, end = 52.dp), pageSpacing = 12.dp) { page ->
        val e = evs[page % n]
        val f = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(300.dp).graphicsLayer { val sc = 1f - 0.06f * f; scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(28.dp)).background(Im).pressScale { open(e.id) }) {
            if (e.img.isNotBlank()) AsyncImage(model = e.img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else PhotoPlaceholder(Modifier.fillMaxSize(), 36.dp)
            Box(Modifier.fillMaxSize().background(Shade))
            if (e.period.isNotBlank()) Text(e.period, Modifier.align(Alignment.TopStart).padding(16.dp).background(Color(0x66000000), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 5.dp), Color.White, 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(e.n, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (e.region().isNotBlank()) PinText(e.region(), Color(0xCCFFFFFF), 13)
            }
        }
    }
}

@Composable
fun Home(
    saved: List<Int>, fav: (Int) -> Unit, kids: List<String>, prefs: Map<String, List<Int>>,
    onSearch: () -> Unit, onMy: () -> Unit, onAskLoc: () -> Unit, onRefresh: () -> Unit, onMap: () -> Unit, onCar: () -> Unit, open: (Int) -> Unit,
) {
    val here = Geo.eff
    val hk = locKey(here, 2)
    val ctx = LocalContext.current
    var pick by remember { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); today = LocalDate.now() } }
    val dateText = today.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))

    // 아이가 등록돼 있고 취향이 있을 때만, 현재 지역 안에서 추천을 불러와요
    val topLikes = (if (Store.bool("s_rec", true)) kids.flatMap { prefs[it] ?: emptyList() } else emptyList())
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }.filter { it in likes.indices }
    var recs by remember { mutableStateOf<List<Pair<Place, Int>>>(emptyList()) }
    var recsLoading by remember { mutableStateOf(false) }
    LaunchedEffect(topLikes.joinToString("|"), hk, Geo.sido) {
        if (topLikes.isEmpty() || here == null) { recs = emptyList(); recsLoading = false; return@LaunchedEffect }
        recsLoading = true
        Tour.recommend(topLikes.map { it to likes[it].kw }, here.latitude, here.longitude, Geo.sido).onSuccess { recs = it }.onFailure { recs = emptyList() }
        recsLoading = false
    }

    val rolling = SearchStats.top(4)
    val off = remember { Animatable(0f) }
    LaunchedEffect(rolling.size) {
        off.snapTo(0f)
        if (rolling.size > 1) { var i = 0; while (true) { delay(6000); i++; off.animateTo(i * 54f, tween(1200)); if (i == rolling.size) { off.snapTo(0f); i = 0 } } }
    }
    val px = Modifier.padding(horizontal = 20.dp)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 20.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(30.dp)) {
        // 헤더: 날짜·날씨 + 큰 인사말 + 검색
        item {
            Column(px, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(dateText, fontSize = 14.sp, color = Sb, fontWeight = FontWeight.Medium, maxLines = 1)
                    // 날씨가 없을 때도 이유를 알려줘요 (위치가 없거나 한국 밖이면 날씨를 불러오지 않아요)
                    val wxText = Geo.wx?.let { "${it.emoji} ${it.temp}° ${it.label}" }
                        ?: when { Geo.outside -> "한국 밖 위치예요"; Geo.eff == null -> "위치를 켜면 날씨가 보여요"; else -> "날씨 불러오는 중…" }
                    Text(wxText, Modifier.clip(RoundedCornerShape(16.dp)).background(Sf).padding(horizontal = 12.dp, vertical = 6.dp), if (Geo.wx != null) Nv else Sb, 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Text("오늘은 어디로\n가볼까요?", fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, color = Nv)
                Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp)).background(Sf).clickable(onClick = onSearch).padding(start = 22.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(0.dp))) {
                        if (rolling.isEmpty()) Row(Modifier.height(54.dp), verticalAlignment = Alignment.CenterVertically) { Text("장소, 행사, 지역을 검색해 보세요", fontSize = 14.sp, color = Sb, maxLines = 1) }
                        else Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true).offset(y = (-off.value).dp)) {
                            (rolling + rolling[0]).forEachIndexed { i, k ->
                                Row(Modifier.height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${i % rolling.size + 1}", color = BlTx, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("  $k", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Bl), Alignment.Center) { Icon(Icons.Default.Search, "검색", tint = OnBl, modifier = Modifier.size(20.dp)) }
                }
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                    BentoTile("내 근처 지도", "한눈에 보기", IcMap, true, Modifier.weight(1f), onMap)
                    BentoTile("가보자요", "차량으로 보내기", IcCar, false, Modifier.weight(1f), onCar)
                }
            }
        }
        // 지역별 이벤트: 큰 포스터 카드
        if (Geo.events.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("지역별 이벤트", when { here == null -> "진행 중이거나 곧 열려요"; Geo.eventsInRegion -> "현재 지역 기준 가까운 순"; else -> "지금 지역에 행사가 없어 가까운 행사를 보여드려요" }, px)
                key(Geo.events.size) { EventPager(Geo.events, open) }
            }
        }
        // 우리 아이 관심사 추천: 아이가 있고 취향이 있을 때만
        if (kids.isNotEmpty() && topLikes.isNotEmpty()) item {
            Column(px, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("우리 아이 관심사 추천", if (here != null) "고른 취향에 맞는 곳을 현재 지역에서 찾았어요" else "고른 취향에 맞춰 찾아드려요")
                when {
                    here == null -> Text("내 위치나 지역을 정하면 취향에 맞는 곳을 추천해 드려요", fontSize = 13.sp, color = Sb)
                    recsLoading -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("  찾는 중…", fontSize = 13.sp, color = Sb) }
                    recs.isEmpty() -> Text("현재 지역에는 고른 취향에 맞는 장소가 아직 없어요", fontSize = 13.sp, color = Sb)
                    else -> recs.forEach { (p, li) -> PlaceRow(p, saved.contains(p.id), { fav(p.id) }, sub = "${p.region()} · ${p.typeLabel()}", badge = likes.getOrNull(li)) { open(p.id) } }
                }
            }
        }
        // 내 근처: 가로로 넘기는 사진 카드
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(px) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("내 근처 가볼만한 곳", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Nv, maxLines = 1)
                        if (here != null) IconButton(onRefresh, Modifier.size(34.dp)) { Icon(Icons.Default.Refresh, "새로고침", tint = Sb, modifier = Modifier.size(20.dp)) }
                        TextButton(onMap) { Text("지도로 보기 ›", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }
                    }
                    if (here != null) PinText("${Geo.area.ifBlank { "현재 위치" }} 기준 · 지역 바꾸기", Sb, 13, BlTx, Modifier.clickable { pick = true })
                }
                when {
                    here == null && Geo.outside -> Column(px.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PinText("현재 위치가 한국 밖으로 잡혀요", Nv, 14, Red)
                        Text("에뮬레이터 기본 위치(미국 캘리포니아)일 수 있어요. 지역을 직접 고르거나, 에뮬레이터의 위치를 한국으로 바꿔 주세요.", fontSize = 12.sp, color = Sb, lineHeight = 18.sp)
                        Row {
                            TextButton({ pick = true }) { Text("지역 직접 선택", fontSize = 13.sp, maxLines = 1) }
                            TextButton(onRefresh) { Text("다시 확인", fontSize = 13.sp, maxLines = 1) }
                        }
                    }
                    here == null -> Column(px.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PinText("내 위치를 공유하면 가까운 곳을 보여드려요", Sb, 12, BlTx)
                        Row {
                            TextButton(onAskLoc) { Text("위치 허용", fontSize = 13.sp, maxLines = 1) }
                            TextButton({ pick = true }) { Text("지역 직접 선택", fontSize = 13.sp, maxLines = 1) }
                        }
                    }
                    Geo.near.isNotEmpty() -> LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(Geo.near) { p -> PlaceCard(p, saved.contains(p.id), { fav(p.id) }, Modifier.width(230.dp).height(300.dp)) { open(p.id) } }
                    }
                    Geo.nearLoading -> Row(px, verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("  불러오는 중…", fontSize = 13.sp, color = Sb) }
                    else -> Column(px, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Geo.nearMsg.ifBlank { "근처에 등록된 장소가 없어요 (한국 안에서만 찾을 수 있어요)" }, fontSize = 13.sp, color = Sb)
                        OutlinedButton({ pick = true }, shape = RoundedCornerShape(20.dp)) { Text("지역 직접 선택", fontSize = 13.sp) }
                    }
                }
            }
        }
    }

    if (pick) AlertDialog({ pick = false }, title = { Text("지역 선택", fontSize = 16.sp) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("선택한 지역을 기준으로 근처 장소를 찾아요", fontSize = 12.sp, color = Sb)
            cities.chunked(3).forEach { rw ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rw.forEach { (n, ll) ->
                        Chip(n, Geo.manual && Geo.area == n) { setManualCity(n); pick = false }
                    }
                }
            }
            TextButton({ useRealLocation(ctx); pick = false }) { Text("내 실제 위치 사용", fontSize = 13.sp) }
        }
    }, confirmButton = {}, dismissButton = { TextButton({ pick = false }) { Text("닫기") } })
}

// ---------- 검색 ----------
@Composable
fun Search(recent: MutableList<String>, saved: List<Int>, fav: (Int) -> Unit, onQuery: (String) -> Unit, open: (Int) -> Unit, back: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var live by remember { mutableStateOf<List<Place>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val k = q.trim()
    // 입력하는 동안 실시간 검색 (0.35초 쉬었다가)
    LaunchedEffect(k) {
        if (k.isEmpty()) { live = emptyList(); busy = false; err = ""; return@LaunchedEffect }
        busy = true; delay(350.milliseconds)
        Tour.search(k, 15).onSuccess { live = it; err = "" }.onFailure { live = emptyList(); err = it.message ?: "검색에 실패했어요" }
        busy = false
    }
    val hot = SearchStats.top(10)
    Column(Modifier.fillMaxSize().padding(16.dp), Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KField(q, { q = it }, Modifier.weight(1f), "어디로 가볼까요?", shape = RoundedCornerShape(24.dp), ime = ImeAction.Search, onDone = { if (k.isNotEmpty()) onQuery(k) }, hintSize = 13.sp)
            TextButton(back) { Text("취소", color = Sb, maxLines = 1) }
        }
        if (k.isEmpty()) {
            if (hot.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("인기 검색어", fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("이 기기 기준 · ${LocalDate.now().format(DateTimeFormatter.ofPattern("MM.dd"))}", fontSize = 11.sp, color = Sb, maxLines = 1)
                }
                Row(Modifier.fillMaxWidth().card().padding(16.dp)) {
                    listOf(hot.take(5), hot.drop(5)).forEachIndexed { c, col ->
                        Column(Modifier.weight(1f)) {
                            col.forEachIndexed { i, h -> Text("${c * 5 + i + 1}   $h", Modifier.fillMaxWidth().clickable { onQuery(h) }.padding(vertical = 9.dp), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("최근 검색", fontWeight = FontWeight.Bold, maxLines = 1)
                if (recent.isNotEmpty()) Text("전체 삭제", Modifier.clickable { recent.clear() }, Sb, 11.sp, maxLines = 1)
            }
            if (recent.isEmpty()) Text("최근 검색한 내용이 없어요", fontSize = 12.sp, color = Sb)
            else Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) { recent.take(8).forEach { Chip(it, false) { onQuery(it) } } }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("'$k' 전체 결과 보기 ›", Modifier.fillMaxWidth().clickable { onQuery(k) }.padding(vertical = 4.dp), BlTx, 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (busy) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Text("  찾는 중…", fontSize = 12.sp, color = Sb) } }
                if (err.isNotBlank()) item { Text(err, fontSize = 12.sp, color = Red) }
                if (!busy && err.isBlank() && live.isEmpty()) item { Text("검색 결과가 없어요", fontSize = 12.sp, color = Sb) }
                items(live) { p -> PlaceRow(p, saved.contains(p.id), { fav(p.id) }, tag = true) { open(p.id) } }
            }
        }
    }
}

@Composable
fun SearchResult(q: String, saved: List<Int>, fav: (Int) -> Unit, back: () -> Unit, open: (Int) -> Unit) {
    var f by remember { mutableStateOf("전체") }
    var list by remember(q) { mutableStateOf<List<Place>>(emptyList()) }
    var loading by remember(q) { mutableStateOf(true) }
    var err by remember(q) { mutableStateOf("") }
    LaunchedEffect(q) { loading = true; Tour.search(q, 40).onSuccess { list = it }.onFailure { err = it.message ?: "검색에 실패했어요" }; loading = false }
    val shown = list.filter { f == "전체" || it.tag == f }.let { l -> if (Geo.eff != null) l.sortedBy { distKm(it) ?: 1e9 } else l }
    Column(Modifier.fillMaxSize()) {
        Bar(q, back)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("전체", "이벤트", "장소").forEach { Chip(it, f == it) { f = it } } }
        if (loading) Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Text("  찾는 중…", fontSize = 12.sp, color = Sb) }
        if (err.isNotBlank()) Text(err, Modifier.padding(16.dp), Red, 12.sp)
        if (!loading && err.isBlank() && shown.isEmpty()) Text("'$q' 검색 결과가 없어요", Modifier.fillMaxWidth().padding(top = 40.dp), Sb, 12.sp, textAlign = TextAlign.Center)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown) { p -> PlaceRow(p, saved.contains(p.id), { fav(p.id) }, tag = true) { open(p.id) } }
        }
    }
}

// ---------- 장소 상세 ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Detail(
    p: Place, saved: List<Int>, fav: (Int) -> Unit, back: () -> Unit, toast: (String) -> Unit, hasCar: Boolean,
    reviews: List<Review>, onReviews: () -> Unit, onWrite: (Int, String) -> Unit, onNavi: () -> Unit, onNeedCar: () -> Unit,
) {
    var sheet by remember { mutableStateOf("") }
    var wr by remember { mutableStateOf(false) }
    val ss = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val on = saved.contains(p.id)
    val ctx = LocalContext.current
    var d by remember(p.id) { mutableStateOf<PlaceDetail?>(null) }
    var loading by remember(p.id) { mutableStateOf(true) }
    var err by remember(p.id) { mutableStateOf("") }
    LaunchedEffect(p.id) { loading = true; Tour.detail(p).onSuccess { d = it }.onFailure { err = it.message ?: "" }; loading = false }
    val imgs = (d?.images ?: emptyList()).ifEmpty { listOf(p.img).filter { it.isNotBlank() } }
    val addr = d?.address ?: p.a
    val avg = if (reviews.isEmpty()) 0.0 else reviews.map { it.star }.average()
    val sub = listOf(p.region(), dText(p).let { if (it.isBlank()) "" else "내 위치에서 $it" }).filter { it.isNotBlank() }.joinToString(" · ")

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(340.dp).background(Im), Alignment.Center) {
                    if (imgs.isEmpty()) PhotoPlaceholder(Modifier.fillMaxSize(), 40.dp)
                    else key(imgs.size) {
                        val pg = rememberPagerState { imgs.size }
                        HorizontalPager(pg, Modifier.fillMaxSize()) { i -> PosterImage(imgs[i], Modifier.fillMaxSize()) }
                        if (imgs.size > 1) Text("${pg.currentPage + 1} / ${imgs.size}", Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 48.dp).background(Color(0x99000000), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 3.dp), Color.White, 12.sp)
                    }
                    Box(Modifier.fillMaxWidth().height(90.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent))))
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween) {
                    RoundBtn(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Color.White) }
                    RoundBtn({ sheet = "share" }) { Icon(Icons.Default.Share, "공유", tint = Color.White) }
                }
                Column(Modifier.fillMaxWidth().padding(top = 316.dp).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Bg).padding(horizontal = 20.dp, vertical = 24.dp), Arrangement.spacedBy(26.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(p.tag, p.period, dText(p)).filter { it.isNotBlank() }.forEach {
                                Text(it, Modifier.clip(RoundedCornerShape(12.dp)).background(Chp).padding(horizontal = 10.dp, vertical = 4.dp), ChT, 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                        Text(p.n, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp, color = Nv)
                        if (p.region().isNotBlank()) PinText(p.region(), Sb, 14, BlTx)
                    }
                    if (loading) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Text("  정보를 불러오는 중…", fontSize = 13.sp, color = Sb) }
                    if (err.isNotBlank()) Text(err, fontSize = 13.sp, color = Red)
                    d?.let { det ->
                        if (det.overview.isNotBlank()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("소개", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Nv)
                            var more by remember { mutableStateOf(false) }
                            Text(det.overview, fontSize = 14.sp, lineHeight = 22.sp, color = Tx2, maxLines = if (more) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
                            Text(if (more) "접기" else "더보기", Modifier.clickable { more = !more }, BlTx, 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (det.facilities.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("편의시설", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Nv)
                            det.facilities.chunked(3).forEach { rw ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { rw.forEach { Text(it, Modifier.clip(RoundedCornerShape(16.dp)).background(Sf).padding(horizontal = 12.dp, vertical = 8.dp), Nv, 13.sp, maxLines = 1) } }
                            }
                        }
                        if (det.rows.isNotEmpty() || det.homepage.isNotBlank()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("이용 정보", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Nv)
                            InfoList(det.rows, det.homepage, ctx) { toast(it) }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("후기", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Nv)
                            if (reviews.isNotEmpty()) Text("★ ${"%.1f".format(avg)} · ${reviews.size}개", fontSize = 13.sp, color = Sb)
                        }
                        if (reviews.isEmpty()) Text("아직 후기가 없어요. 첫 후기를 남겨 보세요!", fontSize = 13.sp, color = Sb)
                        reviews.take(2).forEach { r ->
                            Column(Modifier.fillMaxWidth().card().padding(16.dp), Arrangement.spacedBy(4.dp)) {
                                Text("${r.who} · ★${r.star} · ${r.date}", fontSize = 12.sp, color = Sb, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(r.text, fontSize = 14.sp, lineHeight = 21.sp)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ wr = true }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("후기 쓰기", fontSize = 13.sp, maxLines = 1) }
                            if (reviews.isNotEmpty()) OutlinedButton(onReviews, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("전체 보기 ›", fontSize = 13.sp, maxLines = 1) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Sf).clickable { fav(p.id) }, Alignment.Center) {
                Icon(if (on) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "찜", tint = if (on) Red else Sb)
            }
            Button({ sheet = "nav" }, Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(27.dp)) {
                Icon(IcMap, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("길찾기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
    if (wr) ReviewDialog(p.n, { wr = false }) { s, t -> onWrite(s, t); wr = false }
    if (sheet.isNotEmpty()) ModalBottomSheet({ sheet = "" }, sheetState = ss, containerColor = Sf) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), Arrangement.spacedBy(10.dp)) {
            if (sheet == "share") {
                Text("공유하기", fontWeight = FontWeight.Bold, fontSize = 15.sp); Text(p.n, fontSize = 11.sp, color = Sb, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceAround) {
                    listOf(IcLink to "링크 복사", IcChat to "카카오톡", Icons.Default.MailOutline to "문자", Icons.Default.MoreVert to "더보기").forEach { (e, l) ->
                        Column(Modifier.clickable { sheet = ""; shareTo(ctx, l, p, toast) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(if (l == "카카오톡") Color(0xFFFEE500) else Chp), Alignment.Center) { Icon(e, l, Modifier.size(22.dp), tint = if (l == "카카오톡") Color(0xFF3A1D1D) else ChT) }
                            Text(l, fontSize = 10.sp, color = Sb, maxLines = 1)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("https://map.naver.com/p/search/${p.n}", Modifier.weight(1f), Sb, 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton({ shareTo(ctx, "링크 복사", p, toast) }) { Text("복사", fontSize = 11.sp) }
                }
            } else {
                Text("길찾기", fontWeight = FontWeight.Bold, fontSize = 15.sp); Text("${p.n} · $addr", fontSize = 11.sp, color = Sb, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Bl).clickable { sheet = ""; if (hasCar) onNavi() else { toast("연결된 차량이 없어요. 차량을 먼저 연결해 주세요"); onNeedCar() } }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("가보자요", color = OnBl, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(if (hasCar) "내 차량으로 목적지 보내기" else "차량을 먼저 연결해 주세요", color = OnBl, fontSize = 11.sp)
                    }
                    Text("›", color = OnBl, fontSize = 20.sp)
                }
                Text("지도 앱으로 열기", fontSize = 11.sp, color = Sb)
                listOf("N" to "네이버지도", "K" to "카카오맵", "G" to "구글지도").forEach { (l, n) ->
                    Row(Modifier.fillMaxWidth().clickable { sheet = ""; openMapApp(ctx, n, p) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(when (l) { "N" -> Color(0xFF03C75A); "K" -> Color(0xFFFEE500); else -> Im }), Alignment.Center) { Text(l, fontWeight = FontWeight.Bold, color = if (l == "N") Color.White else Nv) }
                        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(n, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text("앱으로 이동", fontSize = 10.sp, color = Sb) }; Text("›", color = Sb)
                    }
                }
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                    OutlinedButton({ sheet = ""; copyText(ctx, addr); toast("주소를 복사했어요") }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("주소 복사", fontSize = 12.sp) }
                    Button({ sheet = "" }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Chp, contentColor = ChT)) { Text("닫기", fontSize = 12.sp) }
                }
            }
        }
    }
}

// ---------- 찜 · 이야기 ----------
@Composable
fun Saved(saved: List<Int>, fav: (Int) -> Unit, open: (Int) -> Unit) {
    var f by remember { mutableStateOf("전체") }
    val list = saved.mapNotNull { Geo.cache[it] }.filter { f == "전체" || it.tag == f }
    LazyColumn(contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("찜 목록", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Nv) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("전체", "장소", "이벤트").forEach { Chip(it, f == it) { f = it } } } }
        if (list.isEmpty()) item { Text("찜한 곳이 없어요. 하트를 눌러 담아 보세요.", Modifier.fillMaxWidth().padding(top = 40.dp), Sb, 13.sp, textAlign = TextAlign.Center) }
        items(list.chunked(2)) { rowItems ->
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(14.dp)) {
                rowItems.forEach { p -> PlaceCard(p, true, { fav(p.id) }, Modifier.weight(1f).height(230.dp)) { open(p.id) } }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun kindColors(k: String): Pair<Color, Color> = when (k) {
    "질문" -> TagBg to TagTx
    "꿀팁" -> (if (Th.dark) Color(0xFF16382A) else Color(0xFFDDF5E6)) to (if (Th.dark) Color(0xFF7BE0A6) else Color(0xFF14683A))
    else -> Chp to ChT
}

@Composable
fun KindChip(k: String) {
    val (bg, fg) = kindColors(k)
    Text(k, Modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp), fg, 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
fun PostCard(p: Post, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().card().clickable(onClick = onClick).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KindChip(p.kind); if (p.mine) Tag("내 글")
            Spacer(Modifier.weight(1f)); Text(p.date, fontSize = 11.sp, color = Sb, maxLines = 1)
        }
        Text(p.title, Modifier.padding(top = 10.dp), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 22.sp, color = Nv, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(p.body, Modifier.padding(top = 6.dp), fontSize = 13.sp, color = Sb, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Default.Favorite, null, Modifier.size(15.dp), tint = Sb.copy(alpha = 0.7f))
            Text("${p.like}", fontSize = 12.sp, color = Sb)
        }
    }
}

@Composable
fun PostDetail(
    p: Post, liked: Boolean, comments: List<Comment>, back: () -> Unit,
    onLike: () -> Unit, onComment: (String) -> Unit, onDelComment: (Comment) -> Unit, onDelPost: (() -> Unit)?,
) {
    var text by remember { mutableStateOf("") }
    var confirmDel by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Bar("이야기", back) { if (onDelPost != null) TextButton({ confirmDel = true }) { Text("삭제", color = Red, fontSize = 13.sp, fontWeight = FontWeight.Bold) } }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(Modifier.fillMaxWidth().card().padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { KindChip(p.kind); Spacer(Modifier.weight(1f)); if (p.mine) Tag("내 글") }
                    Text(p.title, Modifier.padding(top = 12.dp), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 26.sp, color = Nv)
                    Row(Modifier.padding(top = 14.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Bl), Alignment.Center) { Text("G", color = OnBl, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
                        Column { Text(p.who, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Nv); Text(p.date, fontSize = 11.sp, color = Sb) }
                    }
                    HorizontalDivider(color = Ln)
                    Text(p.body, Modifier.padding(top = 16.dp), fontSize = 15.sp, lineHeight = 26.sp, color = Tx2)
                    Row(Modifier.padding(top = 18.dp).clip(RoundedCornerShape(20.dp)).background(if (liked) Red.copy(alpha = 0.12f) else Bg).clickable(onClick = onLike).padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Favorite, "좋아요", Modifier.size(16.dp), tint = if (liked) Red else Sb)
                        Text("좋아요 ${p.like}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (liked) Red else Sb, maxLines = 1)
                    }
                }
            }
            item { Text("댓글", Modifier.padding(top = 8.dp, start = 2.dp), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Nv) }
            if (comments.isEmpty()) item { Text("아직 댓글이 없어요.\n첫 댓글을 남겨 보세요!", Modifier.fillMaxWidth().padding(vertical = 24.dp), Sb, 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp) }
            items(comments) { c ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sf).padding(horizontal = 14.dp, vertical = 12.dp), Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("익명의 가보자 · ${c.date}", Modifier.weight(1f), fontSize = 11.sp, color = Sb, maxLines = 1)
                        if (c.mine) Text("삭제", Modifier.clickable { onDelComment(c) }.padding(4.dp), Red, 11.sp)
                    }
                    Text(c.text, fontSize = 13.sp, lineHeight = 19.sp, color = Tx2)
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KField(text, { text = it }, Modifier.weight(1f), "댓글을 남겨 보세요", shape = RoundedCornerShape(24.dp), maxLen = 200, hintSize = 13.sp)
            val ok = text.isNotBlank()
            Box(Modifier.size(46.dp).clip(CircleShape).background(if (ok) Bl else Ln).clickable(enabled = ok) { onComment(text.trim()); text = "" }, Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Send, "등록", Modifier.size(20.dp), tint = if (ok) OnBl else Sb)
            }
        }
    }
    if (confirmDel) AlertDialog({ confirmDel = false }, title = { Text("글을 삭제할까요?", fontSize = 16.sp) }, text = { Text("서버에서도 함께 삭제되고, 되돌릴 수 없어요.", fontSize = 13.sp) },
        confirmButton = { TextButton({ confirmDel = false; onDelPost?.invoke() }) { Text("삭제", color = Red) } },
        dismissButton = { TextButton({ confirmDel = false }) { Text("취소") } })
}

@Composable
fun Story(posts: List<Post>, onOpen: (Long) -> Unit, onSearch: () -> Unit, onMy: () -> Unit, onWrite: () -> Unit) {
    var f by remember { mutableStateOf("전체") }
    val shown = posts.filter { f == "전체" || it.kind == f }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("이야기", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold); Text("익명으로 남겨요", fontSize = 11.sp, color = Sb) }
                    IconButton(onSearch) { Icon(Icons.Default.Search, "검색") }; Avatar(onMy, 36)
                }
            }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("전체", "후기", "질문", "꿀팁").forEach { Chip(it, f == it) { f = it } } } }
            if (shown.isEmpty()) item { Text("아직 올라온 글이 없어요.\n첫 글을 남겨 보세요!", Modifier.fillMaxWidth().padding(top = 40.dp), Sb, 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp) }
            items(shown) { PostCard(it) { onOpen(it.id) } }
        }
        ExtendedFloatingActionButton(onWrite, Modifier.align(Alignment.BottomEnd).padding(20.dp, 20.dp, 20.dp, 96.dp), containerColor = Bl, contentColor = OnBl) { Text("글쓰기") }
    }
}

@Composable
fun SSearch(posts: List<Post>, onOpen: (Long) -> Unit, back: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val k = q.trim()
    val res = if (k.isBlank()) emptyList() else posts.filter { it.title.contains(k, true) || it.body.contains(k, true) }
    Column(Modifier.fillMaxSize().padding(12.dp), Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
            KField(q, { q = it }, Modifier.weight(1f), "이야기 검색", shape = RoundedCornerShape(24.dp), ime = ImeAction.Search, hintSize = 13.sp)
        }
        when {
            k.isBlank() -> Text("검색어를 입력해 주세요", Modifier.fillMaxWidth().padding(top = 40.dp), Sb, 12.sp, textAlign = TextAlign.Center)
            res.isEmpty() -> Text("검색 결과가 없어요", Modifier.fillMaxWidth().padding(top = 40.dp), Sb, 12.sp, textAlign = TextAlign.Center)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { items(res) { PostCard(it) { onOpen(it.id) } } }
        }
    }
}

@Composable
private fun CardField(value: String, onValue: (String) -> Unit, hint: String, maxLen: Int, minHeight: androidx.compose.ui.unit.Dp, single: Boolean) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Sf).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(min = minHeight)) {
            if (value.isEmpty()) Text(hint, fontSize = 15.sp, color = Sb.copy(alpha = 0.7f))
            androidx.compose.foundation.text.BasicTextField(
                value, { if (it.length <= maxLen) onValue(it) }, Modifier.fillMaxWidth(), singleLine = single,
                textStyle = androidx.compose.ui.text.TextStyle(color = Nv, fontSize = 15.sp, lineHeight = 24.sp), cursorBrush = SolidColor(Bl),
            )
        }
        Text("${value.length} / $maxLen", Modifier.align(Alignment.End).padding(top = 6.dp), fontSize = 11.sp, color = Sb)
    }
}

@Composable
fun Write(back: () -> Unit, onPost: (String, String, String) -> Unit) {
    var k by remember { mutableStateOf("후기") }; var t by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding()) {
        Bar("글쓰기", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), Arrangement.spacedBy(10.dp)) {
            Text("어떤 이야기인가요?", Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Nv)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple("후기", "다녀온 이야기", Icons.Default.Star), Triple("질문", "궁금한 점", Icons.Default.Info), Triple("꿀팁", "알려 주고 싶은 것", Icons.Default.ThumbUp)).forEach { (name, hint, icon) ->
                    val on = k == name
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (on) Chp else Sf)
                            .border(2.dp, if (on) Bl else Color.Transparent, RoundedCornerShape(20.dp)).clickable { k = name }.padding(vertical = 14.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(if (on) Bl else Bg), Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = if (on) OnBl else Sb) }
                        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Nv, maxLines = 1)
                        Text(hint, fontSize = 11.sp, color = Sb, maxLines = 1)
                    }
                }
            }
            Text("제목", Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Nv)
            CardField(t, { t = it }, "제목을 적어 주세요", 40, 24.dp, true)
            Text("내용", Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Nv)
            CardField(b, { b = it }, "내용을 적어 주세요", 500, 150.dp, false)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(16.dp)).background(Sf).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = Sb)
                Text("익명으로 올라가고, 우리 가족만 볼 수 있어요", fontSize = 12.sp, color = Sb, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
        }
        Button({ onPost(k, t.trim(), b.trim()) }, Modifier.fillMaxWidth().padding(16.dp).height(54.dp), enabled = t.isNotBlank() && b.isNotBlank(), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Bl, disabledContainerColor = Ln, disabledContentColor = Sb)) { Text("올리기", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

// ---------- 내 정보 ----------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun My(kids: List<String>, prefs: Map<String, List<Int>>, hasCar: Boolean, viewedN: Int, mineN: Int, onDel: (String) -> Unit, go: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 16.dp, 16.dp, 100.dp), Arrangement.spacedBy(12.dp)) {
        Text("내 정보", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Nv)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Bl, Color(0xFFF0C95A)))).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0x59FFFFFF)), Alignment.Center) { Text("G", color = OnBl, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("익명의 가보자", color = OnBl, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1)
                Text("계정 없이 이용 중", color = Color(0xB32B1D00), fontSize = 12.sp, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            StatTile("${viewedN}", "최근 본 곳", Modifier.weight(1f)) { go("recent") }
            StatTile("${mineN}", "내가 쓴 글", Modifier.weight(1f)) { go("mine") }
            StatTile(if (hasCar) "연결됨" else "없음", "내 차량", Modifier.weight(1f)) { go("car") }
        }
        FamilyKids(kids, prefs, go, onDel)
        Column(Modifier.fillMaxWidth().card()) {
            listOf(Triple("나들이 스탬프", "준비 중", "stamp"), Triple("설정", "", "set")).forEach { (n, v, r) ->
                Row(Modifier.fillMaxWidth().clickable { go(r) }.padding(16.dp), Arrangement.SpaceBetween) { Text(n, fontSize = 13.sp, maxLines = 1); Text("$v ›", fontSize = 12.sp, color = Sb, maxLines = 1) }
            }
        }
    }
}

@Composable
fun Mine(posts: List<Post>, reviews: List<Review>, delPost: (Post) -> Unit, delReview: (Review) -> Unit, back: () -> Unit, onOpenPost: (Long) -> Unit, open: (Int) -> Unit) {
    var tab by remember { mutableStateOf("글") }
    Column(Modifier.fillMaxSize()) {
        Bar("내가 쓴 글", back)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("이야기 ${posts.size}", tab == "글") { tab = "글" }; Chip("후기 ${reviews.size}", tab == "후기") { tab = "후기" }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tab == "글") {
                if (posts.isEmpty()) item { Text("내가 쓴 이야기가 없어요", Modifier.fillMaxWidth().padding(top = 40.dp), Sb, 12.sp, textAlign = TextAlign.Center) }
                items(posts) { p ->
                    Column {
                        PostCard(p) { onOpenPost(p.id) }
                        Text("삭제", Modifier.align(Alignment.End).clickable { delPost(p) }.padding(8.dp), Red, 12.sp)
                    }
                }
            } else {
                if (reviews.isEmpty()) item { Text("내가 쓴 후기가 없어요", Modifier.fillMaxWidth().padding(top = 40.dp), Sb, 12.sp, textAlign = TextAlign.Center) }
                items(reviews) { rv ->
                    Column(Modifier.fillMaxWidth().card().clickable { open(rv.placeId) }.padding(14.dp), Arrangement.spacedBy(4.dp)) {
                        Text(rv.placeName, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(stars(rv.star), color = Amber, fontSize = 13.sp, maxLines = 1); Text(rv.date, fontSize = 11.sp, color = Sb, maxLines = 1) }
                        Text(rv.text, fontSize = 12.sp, lineHeight = 18.sp)
                        Text("삭제", Modifier.align(Alignment.End).clickable { delReview(rv) }.padding(4.dp), Red, 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Prefs(kid: String, sel: MutableList<Int>, back: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val first = sel.firstOrNull()?.let { likes.getOrNull(it) }
    val c = first?.color ?: NoPref
    Column(Modifier.fillMaxSize()) {
        Bar("취향 고르기", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(58.dp).clip(CircleShape).background(c.copy(alpha = 0.16f)), Alignment.Center) { Text(first?.emoji ?: "🙂", fontSize = 28.sp) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("$kid! 뭐 좋아해?", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (first == null) "좋아하는 걸 모두 골라 주세요" else "${sel.size}개 선택 · '${first.label}'이 대표 취향이에요", fontSize = 12.sp, color = Sb, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            likeGroups.forEach { g ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(g, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Nv)
                    // 3열 고정 격자: 선택해도 칩 크기가 변하지 않아 줄바꿈이 흔들리지 않음
                    likes.withIndex().filter { it.value.group == g }.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                            row.forEach { (i, lk) ->
                                val on = sel.contains(i); val order = sel.indexOf(i)
                                Box(Modifier.weight(1f)) {
                                    Column(Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(16.dp)).background(if (on) lk.color.copy(alpha = 0.14f) else Sf)
                                        .border(1.5.dp, if (on) lk.color else Ln, RoundedCornerShape(16.dp)).clickable { if (on) sel.remove(i) else sel.add(i) }
                                        .padding(horizontal = 4.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                                        Text(lk.emoji, fontSize = 24.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(lk.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) lk.color else Nv, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (on) Text(if (order == 0) "대표" else "${order + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).clip(RoundedCornerShape(8.dp)).background(lk.color).padding(horizontal = 6.dp, vertical = 1.dp))
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Text("이 아이 삭제", Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(10.dp)).clickable { confirm = true }.padding(horizontal = 16.dp, vertical = 10.dp), Red, 13.sp, maxLines = 1)
        }
        Button(back, Modifier.fillMaxWidth().padding(16.dp).height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = c)) {
            Text(if (sel.isEmpty()) "나중에 고를게요" else "다 골랐어요 (${sel.size})", fontWeight = FontWeight.Bold)
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("$kid 삭제") }, text = { Text("이 아이와 고른 취향이 모두 지워져요. 삭제할까요?") },
        confirmButton = { TextButton({ confirm = false; onDelete() }) { Text("삭제", color = Red) } },
        dismissButton = { TextButton({ confirm = false }) { Text("취소") } })
}

@Composable
fun Kids(kids: List<String>, prefs: Map<String, List<Int>>, back: () -> Unit, onDel: (String) -> Unit, onAdd: (String) -> Unit) {
    var n by remember { mutableStateOf("") }
    var del by remember { mutableStateOf<String?>(null) }
    val name = n.replace("/", "").trim().take(8)
    Plain("아이 관리", back) {
        Text("아이별로 취향을 골라 두면 추천에 반영돼요", fontSize = 12.sp, color = Sb)
        Column(Modifier.fillMaxWidth().card().padding(18.dp), Arrangement.spacedBy(12.dp)) {
            Text("우리 아이 ${kids.size}명", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Nv)
            if (kids.isEmpty()) Text("아직 등록한 아이가 없어요.\n아래에서 첫 아이를 추가해 보세요", fontSize = 12.sp, color = Sb, lineHeight = 18.sp)
            kids.toList().forEach { k ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Bg).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(kidColor(prefs, k).copy(alpha = 0.16f)), Alignment.Center) { Text(kidEmoji(prefs, k), fontSize = 22.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(k, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Nv, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("고른 취향 ${prefs[k]?.size ?: 0}개", fontSize = 11.sp, color = Sb, maxLines = 1)
                    }
                    Text("지우기", Modifier.clip(RoundedCornerShape(10.dp)).clickable { del = k }.padding(8.dp), Sb, 12.sp, maxLines = 1)
                }
            }
        }
        Column(Modifier.fillMaxWidth().card().padding(18.dp), Arrangement.spacedBy(12.dp)) {
            Text("아이 추가", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Nv)
            KField(n, { n = it }, Modifier.fillMaxWidth(), "이름을 입력해 주세요 (최대 8자)", maxLen = 8, shape = RoundedCornerShape(14.dp))
            Button({ onAdd(name) }, Modifier.fillMaxWidth().height(52.dp), enabled = name.isNotBlank() && !kids.contains(name), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Bl, contentColor = OnBl, disabledContainerColor = Ln, disabledContentColor = Sb)) {
                Text("추가하고 취향 고르기", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    del?.let { k -> AlertDialog({ del = null }, title = { Text("$k 정보를 지울까요?", fontSize = 16.sp) },
        text = { Text("고른 취향도 함께 지워지고, 차량 앱에서도 사라져요.", fontSize = 13.sp) },
        confirmButton = { TextButton({ del = null; onDel(k) }) { Text("지우기", color = Red) } },
        dismissButton = { TextButton({ del = null }) { Text("취소") } }) }
}

@Composable
fun Recent(viewed: MutableList<Int>, saved: List<Int>, fav: (Int) -> Unit, back: () -> Unit, open: (Int) -> Unit) {
    val list = viewed.toList().mapNotNull { Geo.cache[it] }
    Column(Modifier.fillMaxSize()) {
        Bar("최근 본 장소", back) { if (list.isNotEmpty()) TextButton({ viewed.clear() }) { Text("전체 삭제", fontSize = 13.sp, color = Red, maxLines = 1) } }
        if (list.isEmpty()) Column(Modifier.fillMaxWidth().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🕘", fontSize = 40.sp); Text("최근 본 장소가 없어요", fontSize = 13.sp, color = Sb)
        } else LazyColumn(contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("최근 본 ${list.size}곳 · 최근 순", fontSize = 13.sp, color = Sb) }
            items(list, key = { it.id }) { p -> RecentRow(p, saved.contains(p.id), { fav(p.id) }, { viewed.remove(p.id) }) { open(p.id) } }
        }
    }
}

@Composable
fun RecentRow(p: Place, fav: Boolean, onFav: () -> Unit, onRemove: () -> Unit, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().card().pressScale(click).padding(10.dp), Arrangement.spacedBy(14.dp), Alignment.CenterVertically) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(16.dp)).background(Im)) {
            if (p.img.isNotBlank()) AsyncImage(model = p.img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else PhotoPlaceholder(Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), Arrangement.spacedBy(5.dp)) {
            if (p.tag.isNotBlank()) Text(p.tag, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BlTx, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Bl.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp))
            Text(p.n, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Nv, maxLines = 1, overflow = TextOverflow.Ellipsis)
            PinText(listOf(p.region(), dText(p)).filter { it.isNotBlank() }.joinToString(" · "), Sb, 12)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Bg).clickable(onClick = onFav), Alignment.Center) {
                Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "찜", tint = if (fav) Color(0xFFFF6B6B) else Sb, modifier = Modifier.size(18.dp))
            }
            Box(Modifier.size(34.dp).clip(CircleShape).background(Bg).clickable(onClick = onRemove), Alignment.Center) {
                Icon(Icons.Default.Close, "삭제", tint = Sb, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ---------- 상세 보조 ----------
/** 포스터가 잘리지 않게: 뒤는 같은 사진을 흐리게, 앞은 전체가 보이게 */
@Composable
fun PosterImage(url: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Im)) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(28.dp).graphicsLayer { alpha = 0.55f })
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(bottom = 28.dp))
    }
}

val IcClock = ico("M12 3a9 9 0 100 18a9 9 0 000-18zM12 7v5l3 2")

@Composable
private fun InfoIcon(k: String) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(Chp), Alignment.Center) {
        val m = Modifier.size(18.dp)
        when (k) {
            "운영시간", "소요시간" -> Icon(IcClock, null, tint = ChT, modifier = m)
            "기간", "휴무일" -> Icon(Icons.Default.DateRange, null, tint = ChT, modifier = m)
            "문의" -> Icon(Icons.Default.Call, null, tint = ChT, modifier = m)
            "추천 연령" -> Icon(Icons.Default.Face, null, tint = ChT, modifier = m)
            "장소", "주소" -> Icon(Icons.Default.LocationOn, null, tint = ChT, modifier = m)
            "이용요금" -> Text("₩", color = ChT, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            "주차" -> Text("P", color = ChT, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            else -> Icon(Icons.Default.Info, null, tint = ChT, modifier = m)
        }
    }
}

/** 항목마다 아이콘 + 제목 위, 값 아래. 여러 값은 칩으로 나눠서 한눈에 보이게. 주소만 따로 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoList(rows: List<Pair<String, String>>, homepage: String, ctx: Context, toast: (String) -> Unit) {
    val main = rows.filter { it.first != "주소" }
    val addr = rows.firstOrNull { it.first == "주소" }?.second.orEmpty()
    Column(Modifier.fillMaxWidth().card().padding(18.dp), Arrangement.spacedBy(18.dp)) {
        main.forEach { (k, v) ->
            val parts = v.split(Regex("\\s*/\\s*|\\n|;")).map { it.trim() }.filter { it.isNotEmpty() }
            val tel = if (k == "문의") Regex("[0-9][0-9\\-]{6,}").find(v)?.value else null
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                InfoIcon(k)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(k, fontSize = 12.sp, color = Sb)
                    when {
                        tel != null -> Text(v, Modifier.clickable { openUrl(ctx, "tel:$tel") }, BlTx, 14.sp, fontWeight = FontWeight.SemiBold)
                        parts.size > 1 -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            parts.forEach { Text(it, Modifier.clip(RoundedCornerShape(10.dp)).background(Bg).padding(horizontal = 10.dp, vertical = 5.dp), Nv, 13.sp) }
                        }
                        else -> Text(v, fontSize = 14.sp, color = Nv, lineHeight = 20.sp)
                    }
                }
            }
        }
        if (homepage.isNotBlank()) Row(Modifier.clickable { openUrl(ctx, homepage) }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            InfoIcon("홈페이지")
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("홈페이지", fontSize = 12.sp, color = Sb); Text("열기 ›", color = BlTx, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
        if (addr.isNotBlank()) {
            HorizontalDivider(color = Ln)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                InfoIcon("주소")
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("주소", fontSize = 12.sp, color = Sb); Text(addr, fontSize = 14.sp, color = Nv, lineHeight = 20.sp) }
                Text("복사", Modifier.clickable { copyText(ctx, addr); toast("주소를 복사했어요") }.padding(4.dp), BlTx, 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------- 관심사 통계 (시상대) ----------
fun pctText(v: Double): String = if (kotlin.math.abs(v - v.roundToInt()) < 0.05) "${v.roundToInt()}%" else "%.1f%%".format(v)

/** 아이 + 관심사 통합 카드. 전체=우리 집 시상대, 아이 선택=그 아이의 취향 칩 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilyKids(kids: List<String>, prefs: Map<String, List<Int>>, go: (String) -> Unit, onDel: (String) -> Unit) {
    var who by remember { mutableStateOf("전체") }
    var confirm by remember { mutableStateOf<String?>(null) }
    if (who != "전체" && who !in kids) who = "전체"
    Column(Modifier.fillMaxWidth().card().padding(18.dp), Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("우리 아이", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Nv)
            Text("+ 아이 추가", Modifier.clip(RoundedCornerShape(10.dp)).clickable { go("kids") }.padding(6.dp), BlTx, 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        if (kids.isEmpty()) Text("아이를 등록하면 취향에 맞는 곳을 추천해 드려요", fontSize = 12.sp, color = Sb)
        else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                KidAvatar("전체", "👨‍👩‍👧", Nv, who == "전체") { who = "전체" }
                kids.forEach { k -> KidAvatar(k, kidEmoji(prefs, k), kidColor(prefs, k), who == k) { who = k } }
            }
            if (who == "전체") {
                val tally = kids.flatMap { prefs[it] ?: emptyList() }.groupingBy { it }.eachCount()
                val items = tally.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
                    .mapNotNull { e -> likes.getOrNull(e.key)?.let { it to e.value } }
                Text("우리 집 취향 순위 · 아이 이름을 누르면 아이별로 봐요", fontSize = 12.sp, color = Sb)
                if (items.isEmpty()) Text("아직 고른 취향이 없어요. 아이를 눌러 골라 보세요.", fontSize = 12.sp, color = Sb)
                else {
                    Podium(items.take(3), kids.size > 1)
                    if (items.size > 3) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.drop(3).forEachIndexed { i, (lk, _) ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Bg).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("${i + 4}", Modifier.width(20.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BlTx)
                                Text("${lk.emoji} ${lk.label}", Modifier.weight(1f), fontSize = 13.sp, color = Nv, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    val top = items.take(2).map { it.first.label }
                    val gold = Color(0xFFF5B83D)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(gold.copy(alpha = 0.12f)).border(1.dp, gold.copy(alpha = 0.45f), RoundedCornerShape(16.dp)).padding(14.dp)) {
                        Text("이번 주말 추천 테마", fontSize = 11.sp, color = Sb)
                        Text("✨ " + top.joinToString(" + ") + " 체험", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                val mine = (prefs[who] ?: emptyList()).filter { it in likes.indices }
                Text(if (mine.isEmpty()) "$who 취향을 아직 고르지 않았어요" else "$who(이)가 좋아하는 것 · ${mine.size}개", fontSize = 12.sp, color = Sb)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mine.forEachIndexed { o, i ->
                        val lk = likes[i]
                        Row(Modifier.clip(RoundedCornerShape(16.dp)).background(lk.color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${lk.emoji} ${lk.label}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = lk.color, maxLines = 1)
                            if (o == 0) Text("대표", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(lk.color).padding(horizontal = 6.dp, vertical = 1.dp))
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Bl.copy(alpha = 0.10f)).clickable { go("prefs/$who") }.padding(vertical = 12.dp), Alignment.Center) {
                    Text("취향 고르기·수정", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BlTx, maxLines = 1)
                }
                Text("아이 정보 관리 ›", Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(10.dp)).clickable { go("kids") }.padding(horizontal = 14.dp, vertical = 6.dp), Sb, 12.sp, maxLines = 1)
            }
        }
    }
    confirm?.let { k -> AlertDialog(onDismissRequest = { confirm = null },
        title = { Text("$k 삭제") }, text = { Text("이 아이와 고른 취향이 모두 지워져요. 삭제할까요?") },
        confirmButton = { TextButton({ confirm = null; who = "전체"; onDel(k) }) { Text("삭제", color = Red) } },
        dismissButton = { TextButton({ confirm = null }) { Text("취소") } }) }
}

@Composable
private fun KidAvatar(name: String, emoji: String, color: Color, on: Boolean, click: () -> Unit) {
    Column(Modifier.width(56.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = click), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)).border(2.5.dp, if (on) color else Color.Transparent, CircleShape), Alignment.Center) { Text(emoji, fontSize = 24.sp) }
        Text(name, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) Nv else Sb, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 시상대: 2위(왼쪽) · 1위(가운데, 제일 높게) · 3위(오른쪽). 이모지는 막대 위에 얹어요 */
@Composable
private fun Podium(top: List<Pair<Like, Int>>, multi: Boolean) {
    val barColor = listOf(Color(0xFF3B82F6), Color(0xFFE0642B), Color(0xFF16A37F))   // 1위 파랑 · 2위 주황 · 3위 초록
    val heights = listOf(110.dp, 84.dp, 64.dp)
    val order = listOf(1, 0, 2).filter { it < top.size }                              // 화면에 놓는 순서
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        order.forEach { r ->
            val (lk, n) = top[r]
            val h by animateDpAsState(heights[r], tween(700), label = "podium$r")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(lk.emoji, fontSize = if (r == 0) 38.sp else 30.sp)
                Text(lk.label, Modifier.padding(top = 2.dp, bottom = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Nv, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Brush.verticalGradient(listOf(barColor[r], barColor[r].copy(alpha = 0.78f)))), Alignment.TopCenter) {
                    Column(Modifier.padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${r + 1}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        if (multi) Text("${n}명", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** 큰 순서대로 12시 방향에서 시계 방향. 조각 사이는 카드 색으로 살짝 띄워요 */
@Composable
fun PieChart(items: List<Pair<Like, Int>>, total: Int, modifier: Modifier = Modifier) {
    val gap = Sf
    val colors = items.map { it.first.color }
    Canvas(modifier) {
        val d = size.minDimension
        val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        var start = -90f
        items.forEachIndexed { i, (_, n) ->
            val sweep = 360f * n / total
            drawArc(colors[i], start, sweep, true, tl, Size(d, d))
            if (items.size > 1) drawArc(gap, start, sweep, true, tl, Size(d, d), style = Stroke(width = 3.dp.toPx()))
            start += sweep
        }
    }
}
