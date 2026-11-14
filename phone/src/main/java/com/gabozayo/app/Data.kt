package com.gabozayo.app

import android.content.Context
import android.content.SharedPreferences
import android.location.Geocoder
import android.location.Location
import android.text.Html
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// ───────── 모델 ─────────
data class Place(
    val id: Int, val n: String, val a: String, val tag: String = "장소",
    val lat: Double = 0.0, val lng: Double = 0.0, val img: String = "", val type: Int = 0, val period: String = "",
)

data class Post(val id: Long, val who: String, val kind: String, val title: String, val body: String, val like: Int, val cmt: Int, val mine: Boolean, val date: String)
data class Comment(val id: Long, val postId: Long, val text: String, val date: String, val mine: Boolean = true)
data class Review(val placeId: Int, val placeName: String, val star: Int, val date: String, val text: String, val who: String = "익명의 가보자")

data class PlaceDetail(
    val overview: String, val address: String, val images: List<String>,
    val facilities: List<String>, val rows: List<Pair<String, String>>, val homepage: String,
)

data class Wx(val temp: Int, val label: String, val emoji: String)

private val SIDO = listOf(
    "서울" to "서울", "부산" to "부산", "대구" to "대구", "인천" to "인천", "광주" to "광주", "대전" to "대전", "울산" to "울산", "세종" to "세종",
    "경기" to "경기", "강원" to "강원", "제주" to "제주", "충청북" to "충북", "충북" to "충북", "충청남" to "충남", "충남" to "충남",
    "전라북" to "전북", "전북" to "전북", "전라남" to "전남", "전남" to "전남", "경상북" to "경북", "경북" to "경북", "경상남" to "경남", "경남" to "경남",
)

/** "부산광역시" → "부산", "경기도" → "경기" (주소의 맨 앞 단어로 판별) */
fun sidoShort(s: String): String? { val t = s.trim(); return SIDO.firstOrNull { t.startsWith(it.first) }?.second }

/** TourAPI 시·도 코드 (areaCode / 법정동 코드) */
val AREA_CODE = mapOf("서울" to "1", "인천" to "2", "대전" to "3", "대구" to "4", "광주" to "5", "부산" to "6", "울산" to "7", "세종" to "8",
    "경기" to "31", "강원" to "32", "충북" to "33", "충남" to "34", "경북" to "35", "경남" to "36", "전북" to "37", "전남" to "38", "제주" to "39")
val LDONG_CODE = mapOf("서울" to "11", "부산" to "26", "대구" to "27", "인천" to "28", "광주" to "29", "대전" to "30", "울산" to "31", "세종" to "36",
    "경기" to "41", "강원" to "51", "충북" to "43", "충남" to "44", "전북" to "52", "전남" to "46", "경북" to "47", "경남" to "48", "제주" to "50")

private val SIDO_CENTER = listOf(
    "서울" to (37.5665 to 126.9780), "부산" to (35.1796 to 129.0756), "대구" to (35.8714 to 128.6014), "인천" to (37.4563 to 126.7052),
    "광주" to (35.1595 to 126.8526), "대전" to (36.3504 to 127.3845), "울산" to (35.5384 to 129.3114), "세종" to (36.4800 to 127.2890),
    "경기" to (37.4138 to 127.5183), "강원" to (37.8228 to 128.1555), "충북" to (36.6357 to 127.4917), "충남" to (36.5184 to 126.8000),
    "전북" to (35.7175 to 127.1530), "전남" to (34.8161 to 126.4629), "경북" to (36.4919 to 128.8889), "경남" to (35.4606 to 128.2132), "제주" to (33.4996 to 126.5312),
)

/** 지오코더가 안 될 때를 위한 대략적인 시·도 판별 (가장 가까운 시·도 중심) */
fun nearestSido(lat: Double, lng: Double): String = SIDO_CENTER.minByOrNull { (_, c) -> distM(lat, lng, c.first, c.second) }?.first ?: ""

/** 대한민국 안의 좌표인지 (에뮬레이터 기본 위치 = 캘리포니아를 걸러내요) */
fun inKorea(l: Location): Boolean = l.latitude in 33.0..38.9 && l.longitude in 124.5..132.0

/** "부산광역시 연제구 …" → "부산 연제구" 처럼 짧게 */
fun Place.region(): String {
    val t = a.split(" ").filter { it.isNotBlank() }
    if (t.isEmpty()) return ""
    val s = sidoShort(t[0]) ?: return t.take(2).joinToString(" ")
    if (s == "세종") return "세종"
    val g = t.getOrNull(1) ?: return s
    return if (g.startsWith(s)) g else "$s $g"
}

fun Place.typeLabel(): String = when (type) { 12 -> "관광지"; 14 -> "문화시설"; 15 -> "행사"; 28 -> "레포츠"; else -> "장소" }
fun todayStr(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))

fun distM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = FloatArray(1); Location.distanceBetween(lat1, lng1, lat2, lng2, r); return r[0].toDouble()
}

// ───────── 앱 전역 상태 (내 위치 · 근처 장소 · 날씨 · 행사) ─────────
object Geo {
    var loc by mutableStateOf<Location?>(null)
    /** 한국 안의 위치만 써요. 에뮬레이터 기본 위치(캘리포니아)처럼 한국 밖이면 null */
    val eff: Location? get() = loc?.takeIf { inKorea(it) }
    /** 위치는 잡혔는데 한국 밖일 때 */
    val outside: Boolean get() = loc?.let { !inKorea(it) } ?: false
    /** 화면에 보여줄 짧은 지역 이름 (예: "부산 연제구") */
    var area by mutableStateOf("")
    /** 현재 지역의 시·도 (예: "부산"). 이벤트·추천을 이 지역으로 좁혀요 */
    var sido by mutableStateOf("")
    var wx by mutableStateOf<Wx?>(null)
    var near by mutableStateOf<List<Place>>(emptyList())
    var nearMsg by mutableStateOf("")
    var nearLoading by mutableStateOf(false)
    var events by mutableStateOf<List<Place>>(emptyList())
    /** 행사가 현재 지역 안에서 찾은 것인지 (false 면 지역에 행사가 없어 가까운 행사로 대신 보여줘요) */
    var eventsInRegion by mutableStateOf(true)
    var tick by mutableStateOf(0)
    /** true 면 사용자가 직접 고른 지역을 쓰고, GPS 값으로 덮어쓰지 않아요 */
    var manual by mutableStateOf(false)
    val cache = mutableStateMapOf<Int, Place>()
    fun register(p: Place): Place { cache[p.id] = p; return p }
}

// ───────── 기기 저장소 (찜·최근 본·아이·글·후기·검색 기록) ─────────
object Store {
    private var sp: SharedPreferences? = null
    fun init(ctx: Context) { if (sp == null) sp = ctx.applicationContext.getSharedPreferences("gabozayo", Context.MODE_PRIVATE) }
    private fun rd(k: String): String = sp?.getString(k, "") ?: ""
    private fun wr(k: String, v: String) { sp?.edit()?.putString(k, v)?.apply() }
    fun str(k: String, d: String): String = rd(k).ifBlank { d }
    fun putStr(k: String, v: String) = wr(k, v)
    fun bool(k: String, d: Boolean): Boolean = when (rd(k)) { "1" -> true; "0" -> false; else -> d }
    fun putBool(k: String, v: Boolean) = wr(k, if (v) "1" else "0")
    fun wipe() { sp?.edit()?.clear()?.apply() }
    private fun arr(k: String): JSONArray = try { JSONArray(rd(k).ifBlank { "[]" }) } catch (e: Exception) { JSONArray() }
    private fun <T : Any> objs(k: String, f: (JSONObject) -> T): List<T> {
        val a = arr(k)
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { f(it) } }
    }
    private fun intList(a: JSONArray?): List<Int> = if (a == null) emptyList() else (0 until a.length()).map { a.optInt(it) }

    fun ids(k: String): List<Int> = intList(arr(k))
    fun putIds(k: String, l: List<Int>) = wr(k, JSONArray(l).toString())
    fun strs(k: String): List<String> { val a = arr(k); return (0 until a.length()).map { a.optString(it) } }
    fun putStrs(k: String, l: List<String>) = wr(k, JSONArray(l).toString())

    fun kids(): List<Pair<String, List<Int>>> = objs("kids") { o -> o.optString("n") to intList(o.optJSONArray("p")) }
    fun putKids(names: List<String>, prefs: Map<String, List<Int>>) {
        val a = JSONArray()
        names.forEach { n -> a.put(JSONObject().put("n", n).put("p", JSONArray(prefs[n] ?: emptyList<Int>()))) }
        wr("kids", a.toString())
    }

    fun comments(): List<Comment> = objs("comments") { o -> Comment(o.optLong("id"), o.optLong("pid"), o.optString("text"), o.optString("date"), o.optBoolean("mine", true)) }
    fun putComments(l: List<Comment>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("pid", it.postId).put("text", it.text).put("date", it.date).put("mine", it.mine)) }
        wr("comments", a.toString())
    }
    /** 좋아요 누른 글 id 들 */
    fun liked(): List<Long> = rd("liked").split(",").mapNotNull { it.toLongOrNull() }
    fun putLiked(l: List<Long>) = wr("liked", l.joinToString(","))

    fun posts(): List<Post> = objs("posts") { o -> Post(o.optLong("id"), o.optString("who"), o.optString("kind"), o.optString("title"), o.optString("body"), o.optInt("like"), o.optInt("cmt"), o.optBoolean("mine"), o.optString("date")) }
    fun putPosts(l: List<Post>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("who", it.who).put("kind", it.kind).put("title", it.title).put("body", it.body).put("like", it.like).put("cmt", it.cmt).put("mine", it.mine).put("date", it.date)) }
        wr("posts", a.toString())
    }

    fun reviews(): List<Review> = objs("reviews") { o -> Review(o.optInt("pid"), o.optString("pn"), o.optInt("star"), o.optString("date"), o.optString("text"), o.optString("who", "익명의 가보자")) }
    fun putReviews(l: List<Review>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("pid", it.placeId).put("pn", it.placeName).put("star", it.star).put("date", it.date).put("text", it.text).put("who", it.who)) }
        wr("reviews", a.toString())
    }

    fun places(): List<Place> = objs("places") { o -> Place(o.optInt("id"), o.optString("n"), o.optString("a"), o.optString("tag", "장소"), o.optDouble("lat", 0.0), o.optDouble("lng", 0.0), o.optString("img"), o.optInt("type"), o.optString("period")) }
    fun putPlaces(l: List<Place>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("n", it.n).put("a", it.a).put("tag", it.tag).put("lat", it.lat).put("lng", it.lng).put("img", it.img).put("type", it.type).put("period", it.period)) }
        wr("places", a.toString())
    }

    fun counts(): Map<String, Int> {
        val o = try { JSONObject(rd("counts").ifBlank { "{}" }) } catch (e: Exception) { JSONObject() }
        return o.keys().asSequence().associateWith { o.optInt(it) }
    }
    fun putCounts(m: Map<String, Int>) { val o = JSONObject(); m.forEach { (k, v) -> o.put(k, v) }; wr("counts", o.toString()) }
}

/**
 * 검색어 통계. 지금은 "이 기기"에서 검색한 횟수를 셉니다.
 * 모든 사용자의 인기 검색어가 필요하면 서버에 검색어를 올리고 top() 을 서버 응답으로 바꾸면 돼요.
 */
object SearchStats {
    val counts = mutableStateMapOf<String, Int>()
    fun load() { counts.clear(); counts.putAll(Store.counts()) }
    fun record(q: String) {
        val k = q.trim(); if (k.isBlank()) return
        counts[k] = (counts[k] ?: 0) + 1
        Store.putCounts(counts.toMap())
    }
    fun clear() { counts.clear(); Store.putCounts(emptyMap()) }
    fun top(n: Int = 10): List<String> =
        counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(n).map { it.key }
}

// ───────── HTTP ─────────
suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
    val con = URL(url).openConnection() as HttpURLConnection
    con.connectTimeout = 8000; con.readTimeout = 8000
    try {
        val code = con.responseCode
        if (code !in 200..299) error("서버 응답 오류 ($code)")
        con.inputStream.bufferedReader().use { it.readText() }
    } finally { con.disconnect() }
}

// ───────── 한국관광공사 TourAPI (KorService2) ─────────
object Tour {
    private val KEY: String get() = BuildConfig.TOUR_API_KEY
    val ready: Boolean get() = KEY.isNotBlank()
    /** 관광지 · 문화시설 · 축제/공연/행사 · 레포츠 */
    val KID_TYPES = setOf(12, 14, 15, 28)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun key() = if (KEY.contains("%")) KEY else enc(KEY)
    private fun https(u: String) = u.trim().replace("http://", "https://")

    private fun html(s: String): String =
        if (s.isBlank()) "" else Html.fromHtml(s, Html.FROM_HTML_MODE_COMPACT).toString().trim()

    private fun JSONObject.pick(vararg ks: String): String = ks.map { html(optString(it)) }.firstOrNull { it.isNotBlank() } ?: ""

    private suspend fun call(path: String, params: String): List<JSONObject> {
        require(ready) { "TourAPI 인증키가 없어요. local.properties 에 TOUR_API_KEY 를 넣어 주세요" }
        val text = httpGet("https://apis.data.go.kr/B551011/KorService2/$path?serviceKey=${key()}&MobileOS=AND&MobileApp=Gabozayo&_type=json&$params")
        val root = try { JSONObject(text) } catch (e: Exception) { error("API 응답을 읽지 못했어요. 인증키를 확인해 주세요") }
        val resp = root.optJSONObject("response") ?: error(root.optString("resultMsg").ifBlank { "API 응답 오류" })
        val head = resp.optJSONObject("header")
        val rc = head?.optString("resultCode").orEmpty()
        if (rc.isNotEmpty() && rc != "0000" && rc != "00") error(head?.optString("resultMsg").orEmpty().ifBlank { "API 오류 ($rc)" })
        val items = resp.optJSONObject("body")?.opt("items") as? JSONObject ?: return emptyList()
        val arr = items.optJSONArray("item") ?: items.optJSONObject("item")?.let { JSONArray().put(it) } ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    /** 시·도 코드로 좁혀서 불러와요. 코드를 못 알아듣거나 결과가 없으면 전국으로 한 번 더 */
    private suspend fun callArea(path: String, params: String, sido: String): List<JSONObject> {
        val tries = listOfNotNull(LDONG_CODE[sido]?.let { "&lDongRegnCd=$it" }, AREA_CODE[sido]?.let { "&areaCode=$it" }, "")
        var last: List<JSONObject> = emptyList(); var err: Throwable? = null
        for (t in tries) {
            val r = runCatching { call(path, params + t) }
            if (r.isSuccess) { last = r.getOrThrow(); if (last.isNotEmpty()) return last } else err = r.exceptionOrNull()
        }
        if (last.isEmpty() && err != null) throw err
        return last
    }

    private fun fd(s: String) = if (s.length == 8) "${s.substring(0, 4)}.${s.substring(4, 6)}.${s.substring(6, 8)}" else s
    private fun period(st: String, en: String) = when {
        st.isBlank() && en.isBlank() -> ""
        en.isBlank() || st == en -> fd(st)
        else -> "${fd(st)} ~ ${fd(en)}"
    }

    private fun JSONObject.toPlace(): Place? {
        val id = optString("contentid").toIntOrNull() ?: return null
        val t = optString("contenttypeid").toIntOrNull() ?: 0
        val title = optString("title").trim()
        if (title.isBlank()) return null
        return Place(
            id, title, listOf(optString("addr1"), optString("addr2")).filter { it.isNotBlank() }.joinToString(" "),
            if (t == 15) "이벤트" else "장소", optString("mapy").toDoubleOrNull() ?: 0.0, optString("mapx").toDoubleOrNull() ?: 0.0,
            https(optString("firstimage")), t, period(optString("eventstartdate"), optString("eventenddate")),
        )
    }

    /** 내 위치 주변 (관광지·문화시설·레포츠·행사). 비어 있으면 반경을 넓혀 한 번 더 찾아요. */
    suspend fun nearby(lat: Double, lng: Double, radius: Int = 10000): Result<List<Place>> = runCatching {
        val list = coroutineScope {
            listOf(12, 14, 28, 15).map { t ->
                async { call("locationBasedList2", "numOfRows=10&pageNo=1&arrange=E&mapX=$lng&mapY=$lat&radius=$radius&contentTypeId=$t") }
            }.awaitAll().flatten()
        }.mapNotNull { it.toPlace() }.distinctBy { it.id }.sortedBy { distM(lat, lng, it.lat, it.lng) }.take(12)
        if (list.isEmpty() && radius < 20000) nearby(lat, lng, 20000).getOrThrow() else list.map { Geo.register(it) }
    }

    /** 진행 중이거나 곧 열리는 행사. 현재 시·도 안에서 먼저 찾고, 가까운 순으로 정렬해요. */
    suspend fun events(lat: Double?, lng: Double?, sido: String): Result<Pair<List<Place>, Boolean>> = runCatching {
        val fmt = DateTimeFormatter.BASIC_ISO_DATE
        val today = LocalDate.now().format(fmt)
        val from = LocalDate.now().minusDays(45).format(fmt)   // 이미 시작했지만 아직 진행 중인 행사도 포함
        val raw = callArea("searchFestival2", "numOfRows=300&pageNo=1&arrange=C&eventStartDate=$from", sido)
            .filter { o -> o.optString("eventenddate").let { it.isBlank() || it >= today } }
            .mapNotNull { it.toPlace() }.distinctBy { it.id }
        val inRegion = if (sido.isNotBlank()) raw.filter { sidoShort(it.a) == sido } else raw
        val local = sido.isBlank() || inRegion.isNotEmpty()
        val base = if (local) inRegion else raw
        val withImg = base.filter { it.img.isNotBlank() }.let { if (it.size >= 3) it else base }
        val sorted = if (lat != null && lng != null) withImg.sortedBy { distM(lat, lng, it.lat, it.lng) } else withImg
        sorted.take(8).map { Geo.register(it) } to local
    }

    suspend fun search(q: String, rows: Int = 30): Result<List<Place>> = runCatching {
        call("searchKeyword2", "numOfRows=$rows&pageNo=1&arrange=A&keyword=${enc(q)}")
            .mapNotNull { it.toPlace() }.distinctBy { it.id }.map { Geo.register(it) }
    }

    /**
     * 아이 취향(번호 to 키워드)으로 찾은 장소. 현재 시·도 안에서만 찾고, 없으면 60km 안의 가까운 곳까지만 넓혀요.
     * 반환: 장소 to 어떤 취향 때문에 추천됐는지(취향 번호)
     */
    suspend fun recommend(kws: List<Pair<Int, String>>, lat: Double?, lng: Double?, sido: String): Result<List<Pair<Place, Int>>> = runCatching {
        val all = coroutineScope {
            kws.map { (li, k) ->
                async { callArea("searchKeyword2", "numOfRows=200&pageNo=1&arrange=A&keyword=${enc(k)}", sido).mapNotNull { it.toPlace() }.filter { it.type in KID_TYPES }.map { it to li } }
            }.awaitAll()
        }.flatten().distinctBy { it.first.id }
        fun d(p: Place) = if (lat != null && lng != null) distM(lat, lng, p.lat, p.lng) else 0.0
        val pool = when {
            sido.isNotBlank() -> all.filter { sidoShort(it.first.a) == sido }.ifEmpty { if (lat != null && lng != null) all.filter { d(it.first) <= 60_000 } else emptyList() }
            else -> all
        }
        // 취향이 여러 개면 번갈아 섞어서 6개
        val groups = pool.groupBy { it.second }.values.map { g -> g.sortedBy { d(it.first) }.toMutableList() }
        val out = mutableListOf<Pair<Place, Int>>()
        while (out.size < 6 && groups.any { it.isNotEmpty() }) groups.forEach { g -> if (g.isNotEmpty() && out.size < 6) out.add(g.removeAt(0)) }
        out.map { (p, li) -> Geo.register(p) to li }
    }

    private fun yes(s: String) = s.isNotBlank() && !no(s) && (s.contains("가능") || s.contains("있") || s.contains("대여"))
    private fun no(s: String) = s.contains("불가") || s.contains("없") || s.contains("불허")

    suspend fun detail(p: Place): Result<PlaceDetail> = runCatching {
        require(ready) { "TourAPI 인증키가 없어요. local.properties 에 TOUR_API_KEY 를 넣어 주세요" }
        val (c, i, im) = coroutineScope {
            val a = async { runCatching { call("detailCommon2", "contentId=${p.id}") }.getOrNull() }
            val b = async { if (p.type > 0) runCatching { call("detailIntro2", "contentId=${p.id}&contentTypeId=${p.type}") }.getOrNull() else null }
            val d = async { runCatching { call("detailImage2", "contentId=${p.id}&imageYN=Y&numOfRows=15&pageNo=1") }.getOrNull() }
            Triple(a.await(), b.await(), d.await())
        }
        if (c == null && i == null && im == null) error("상세 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요")
        val co = c?.firstOrNull(); val ro = i?.firstOrNull()
        fun ip(vararg k: String): String = ro?.pick(*k) ?: ""

        val hours = ip("usetime", "usetimeculture", "usetimeleports", "playtime", "opentimefood")
        val rest = ip("restdate", "restdateculture", "restdateleports", "restdatefood")
        val fee = ip("usefee", "usefeeleports", "usetimefestival")
        val age = ip("expagerange", "expagerangeleports", "agelimit")
        val parking = ip("parking", "parkingculture", "parkingleports", "parkingfood")
        val stroller = ip("chkbabycarriage", "chkbabycarriageculture", "chkbabycarriageleports")
        val pet = ip("chkpet", "chkpetculture", "chkpetleports")
        val spend = ip("spendtime")
        val where = ip("eventplace")
        val tel = ip("infocenter", "infocenterculture", "infocenterleports", "infocenterfood", "sponsor1tel").ifBlank { co?.pick("tel") ?: "" }
        val address = co?.let { listOf(it.optString("addr1"), it.optString("addr2")).filter { s -> s.isNotBlank() }.joinToString(" ") }?.ifBlank { null } ?: p.a

        val fac = buildList {
            if (parking.isNotBlank() && !no(parking)) add("🅿️ 주차장")
            if (yes(stroller)) add("🛒 유모차 대여")
            if (yes(pet)) add("🐶 반려동물")
        }
        // 값이 있는 항목만 보여줘요 (정보 없음 표시 X)
        val rows = listOf(
            "기간" to p.period, "운영시간" to hours, "휴무일" to rest, "이용요금" to fee, "추천 연령" to age,
            "소요시간" to spend, "장소" to where, "주차" to parking, "문의" to tel, "주소" to address,
        ).filter { it.second.isNotBlank() }

        val home = co?.optString("homepage").orEmpty().let { h ->
            Regex("href=\"([^\"]+)\"").find(h)?.groupValues?.get(1) ?: html(h)
        }.trim().let { if (it.startsWith("http")) it else "" }

        val imgs = (listOf(p.img, co?.optString("firstimage").orEmpty()) + (im ?: emptyList()).map { it.optString("originimgurl") })
            .map { https(it) }.filter { it.isNotBlank() }.distinct()

        PlaceDetail(co?.pick("overview") ?: "", address, imgs, fac, rows, home)
    }
}

// ───────── 날씨 (Open-Meteo, 키 필요 없음) ─────────
suspend fun fetchWeather(lat: Double, lng: Double): Result<Wx> = runCatching {
    val t = httpGet("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current=temperature_2m,weather_code&timezone=auto")
    val c = JSONObject(t).getJSONObject("current")
    val (label, emoji) = when (val code = c.getInt("weather_code")) {
        0 -> "맑음" to "☀️"
        1 -> "대체로 맑음" to "🌤️"
        2 -> "구름 조금" to "⛅"
        3 -> "흐림" to "☁️"
        45, 48 -> "안개" to "🌫️"
        in 51..57 -> "이슬비" to "🌦️"
        in 61..67 -> "비" to "🌧️"
        in 71..77 -> "눈" to "❄️"
        in 80..82 -> "소나기" to "🌦️"
        85, 86 -> "눈 소나기" to "🌨️"
        in 95..99 -> "뇌우" to "⛈️"
        else -> "날씨" to "🌡️"
    }
    Wx(c.getDouble("temperature_2m").roundToInt(), label, emoji)
}

// ───────── 현재 위치 → 짧은 지역 이름 + 시·도 ─────────
data class AreaInfo(val label: String, val sido: String)

@Suppress("DEPRECATION")
suspend fun areaInfo(ctx: Context, loc: Location): AreaInfo = withContext(Dispatchers.IO) {
    var label = ""; var sido = ""
    try {
        if (Geocoder.isPresent()) {
            val a = Geocoder(ctx, Locale.KOREA).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            if (a != null) {
                sido = sidoShort(a.adminArea.orEmpty()).orEmpty()
                val gu = listOf(a.locality, a.subAdminArea, a.subLocality).firstOrNull { !it.isNullOrBlank() }.orEmpty()
                label = listOf(sido.ifBlank { a.adminArea.orEmpty() }, if (gu.startsWith(sido) && sido.isNotBlank()) "" else gu).filter { it.isNotBlank() }.joinToString(" ")
            }
        }
    } catch (e: Exception) { }
    if (sido.isBlank()) sido = nearestSido(loc.latitude, loc.longitude)
    AreaInfo(label.ifBlank { sido }, sido)
}
