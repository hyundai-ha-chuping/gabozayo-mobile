package com.gabozayo.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 폰이 찜한 곳 (폰 → 차량) */
data class SavedItem(
    val id: Int, val name: String, val address: String, val region: String, val tag: String, val lat: Double, val lng: Double,
    /** 대표 사진 주소 (없으면 빈 문자열) */ val img: String = "",
    /** 추천 이유 한 줄, 예: "동하 취향 · 공룡" (없으면 빈 문자열) */ val why: String = "",
    /** 아래는 TourAPI 상세에서 받은 값이에요. 없으면 비워 두고 차량 화면에도 그 칸을 보여주지 않아요 */
    val hours: String = "", val age: String = "", val overview: String = "",
    val facilities: List<String> = emptyList(), val imgCount: Int = 0,
    /** 상세 사진 주소들 (차량 상세에서 넘겨 봐요). 없으면 대표 사진만 */ val images: List<String> = emptyList(),
)

/** 가족의 아이 한 명과 그 아이가 고른 취향 이름들 (폰 → 차량) */
data class KidItem(val name: String, val likes: List<String>)

/** 우리 아이 관심사 순위 한 칸 (폰 → 차량). 많이 고른 순서대로 보내요 */
data class InterestItem(val emoji: String, val label: String, val count: Int)

/** 폰이 "가보자요"로 보낸 목적지 (폰 → 차량). route = recommend / fastest / shortest / free */
data class SentDest(val id: String, val name: String, val address: String, val lat: Double, val lng: Double, val route: String, val sentAt: Long)

/** 차량이 돌려주는 진행 상태 (차량 → 폰). ack = received / searching / ready / guiding / failed */
/** etaSec: 내비가 계산한 도착까지 남은 시간(초), distM: 남은 거리(m). 없으면 0 */
data class AckMsg(val id: String, val ack: String, val message: String, val ts: Long, val etaSec: Int = 0, val distM: Int = 0)

/** 이야기 댓글 (by = 쓴 기기. 내 댓글인지 구분해요) */
data class StoryComment(val id: Long, val text: String, val date: String, val by: String)

/** 이야기 글 하나 (후기·질문·꿀팁). 같은 연결 코드를 쓰는 가족 기기끼리 서버(stories/{코드})로 공유해요 */
data class StoryItem(val id: Long, val kind: String, val title: String, val body: String, val date: String, val like: Int, val by: String, val comments: List<StoryComment> = emptyList())

/** 폰과 차량이 함께 보는 문서 하나 */
data class FamilyDoc(val code: String, val saved: List<SavedItem>, val savedAt: Long, val carConnectedAt: Long, val sent: SentDest?, val ack: AckMsg?, val interests: List<InterestItem> = emptyList(),
    val recs: List<SavedItem> = emptyList(), val events: List<SavedItem> = emptyList(), val kids: List<KidItem> = emptyList(),
    /** 차량이 켜져 있는 동안 몇 초마다 바뀌는 값(서버 시각). 바뀌지 않으면 차량이 꺼졌거나 연결이 끊긴 거예요 */ val carBeat: Long = 0L,
    /** 이 코드를 처음 만든 휴대폰 (같은 코드가 겹치는 걸 막아요) */ val owner: String = "")

/**
 * 폰 ↔ 차량 연결 (Firebase Realtime Database, REST).
 * 폰이 만든 6자리 코드를 차량에서 입력하면 같은 문서 families/{코드} 를 함께 읽고 써요.
 *   폰   : savedPlaces / savedAt / sendToCar 를 씀
 *   차량 : ack / carConnectedAt 을 씀
 * 서로 덮어쓰지 않도록 항상 PATCH(부분 수정)로 써요. (설계: 팀이 준 gabojayo-sync 코드와 같아요)
 */
class FamilyApi(dbUrl: String) {
    private val base = dbUrl.trim().trimEnd('/')
    val isConfigured: Boolean get() = base.startsWith("https://")

    /** 문서가 없으면 null, 통신에 실패하면 예외 */
    suspend fun load(code: String): FamilyDoc? = withContext(Dispatchers.IO) {
        val c = open("$base/families/$code.json", "GET")
        try {
            if (c.responseCode !in 200..299) error("불러오기 실패 (${c.responseCode})")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            if (body.isBlank() || body == "null") null else parse(code, JSONObject(body))
        } finally { c.disconnect() }
    }

    /** 일부(sendToCar 등)가 깨져 있어도 나머지는 계속 읽을 수 있게 부분마다 따로 읽어요 */
    private fun parse(code: String, o: JSONObject): FamilyDoc {
        val saved = places(o, "savedPlaces")
        val sent = runCatching {
            o.optJSONObject("sendToCar")?.let { SentDest(it.getString("id"), it.getString("name"), it.optString("address"), it.getDouble("lat"), it.getDouble("lng"), it.optString("route", "recommend"), it.getLong("sentAt")) }
        }.getOrNull()
        val ack = runCatching {
            o.optJSONObject("ack")?.let { AckMsg(it.getString("id"), it.getString("ack"), it.optString("message"), it.optLong("ts"), it.optInt("etaSec"), it.optInt("distM")) }
        }.getOrNull()
        val interests = runCatching {
            val a = o.optJSONArray("interests") ?: JSONArray()
            (0 until a.length()).map { i -> a.getJSONObject(i).let { InterestItem(it.optString("emoji"), it.getString("label"), it.optInt("count")) } }
        }.getOrDefault(emptyList())
        val kids = runCatching {
            val a = o.optJSONArray("kids") ?: JSONArray()
            (0 until a.length()).map { i ->
                val k = a.getJSONObject(i)
                val l = k.optJSONArray("likes") ?: JSONArray()
                KidItem(k.getString("name"), (0 until l.length()).map { l.getString(it) })
            }
        }.getOrDefault(emptyList())
        return FamilyDoc(code, saved, o.optLong("savedAt"), o.optLong("carConnectedAt"), sent, ack, interests, places(o, "recs"), places(o, "events"), kids, o.optLong("carBeat"), o.optString("owner"))
    }

    private fun places(o: JSONObject, key: String): List<SavedItem> = runCatching {
        val a = o.optJSONArray(key) ?: JSONArray()
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let { SavedItem(it.getInt("id"), it.getString("name"), it.optString("address"), it.optString("region"), it.optString("tag"), it.optDouble("lat", 0.0), it.optDouble("lng", 0.0), it.optString("img"), it.optString("why"),
                it.optString("hours"), it.optString("age"), it.optString("overview"),
                it.optJSONArray("facilities")?.let { f -> (0 until f.length()).map { k -> f.optString(k) } } ?: emptyList(), it.optInt("imgCount", 0),
                it.optJSONArray("images")?.let { f -> (0 until f.length()).map { k -> f.optString(k) }.filter { u -> u.isNotBlank() } } ?: emptyList()) }
        }
    }.getOrDefault(emptyList())

    private fun toJson(items: List<SavedItem>): JSONArray {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("address", it.address).put("region", it.region).put("tag", it.tag).put("lat", it.lat).put("lng", it.lng).put("img", it.img).put("why", it.why)
            .put("hours", it.hours).put("age", it.age).put("overview", it.overview).put("facilities", JSONArray(it.facilities)).put("imgCount", it.imgCount).put("images", JSONArray(it.images))) }
        return arr
    }

    // ── 폰이 쓰는 것 ──
    suspend fun pushSaved(code: String, items: List<SavedItem>) {
        patch(code, JSONObject().put("savedPlaces", toJson(items)).put("savedAt", System.currentTimeMillis()))
    }

    /** 차량 홈 화면용: 아이 취향 추천 장소, 오늘의 축제, 아이 목록 */
    suspend fun pushHome(code: String, recs: List<SavedItem>, events: List<SavedItem>, kids: List<KidItem>) {
        val k = JSONArray()
        kids.forEach { kid -> k.put(JSONObject().put("name", kid.name).put("likes", JSONArray(kid.likes))) }
        patch(code, JSONObject().put("recs", toJson(recs)).put("events", toJson(events)).put("kids", k).put("homeAt", System.currentTimeMillis()))
    }

    /** 아이들이 고른 취향 순위 (차량 화면의 "우리 아이 관심사") */
    suspend fun pushInterests(code: String, items: List<InterestItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("emoji", it.emoji).put("label", it.label).put("count", it.count)) }
        patch(code, JSONObject().put("interests", arr).put("interestsAt", System.currentTimeMillis()))
    }

    suspend fun sendDest(code: String, d: SentDest) =
        patch(code, JSONObject().put("sendToCar", JSONObject().put("id", d.id).put("name", d.name).put("address", d.address).put("lat", d.lat).put("lng", d.lng).put("route", d.route).put("sentAt", d.sentAt)))

    // ── 차량이 쓰는 것 ──
    suspend fun writeAck(code: String, a: AckMsg) =
        patch(code, JSONObject().put("ack", JSONObject().put("id", a.id).put("ack", a.ack).put("message", a.message).put("ts", a.ts).put("etaSec", a.etaSec).put("distM", a.distM)))

    suspend fun markCarConnected(code: String) =
        patch(code, JSONObject().put("carConnectedAt", System.currentTimeMillis()).put("carBeat", JSONObject().put(".sv", "timestamp")))

    /** 차량이 살아 있다는 신호: 서버 시각을 써서 두 기기의 시계가 달라도 비교할 수 있어요 */
    suspend fun beat(code: String) = patch(code, JSONObject().put("carBeat", JSONObject().put(".sv", "timestamp")))

    /** 차량에서 연결을 끊었을 때 */
    suspend fun markCarDisconnected(code: String) = patch(code, JSONObject().put("carConnectedAt", 0).put("carBeat", 0))

    // ── 이야기 (families 와 따로 stories/{코드} 에 둬요. 차량이 1초마다 읽는 문서가 커지지 않게) ──
    suspend fun loadStories(code: String): List<StoryItem> = withContext(Dispatchers.IO) {
        val c = open("$base/stories/$code.json", "GET")
        try {
            if (c.responseCode !in 200..299) error("불러오기 실패 (${c.responseCode})")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            if (body.isBlank() || body == "null") emptyList() else parseStories(JSONObject(body))
        } finally { c.disconnect() }
    }

    private fun parseStories(o: JSONObject): List<StoryItem> {
        val out = ArrayList<StoryItem>()
        for (k in o.keys()) {
            val id = k.toLongOrNull() ?: continue
            val s = o.optJSONObject(k) ?: continue
            val cm = s.optJSONObject("comments")
            val comments = ArrayList<StoryComment>()
            if (cm != null) for (ck in cm.keys()) {
                val cid = ck.toLongOrNull() ?: continue
                val c = cm.optJSONObject(ck) ?: continue
                comments.add(StoryComment(cid, c.optString("text"), c.optString("date"), c.optString("by")))
            }
            out.add(StoryItem(id, s.optString("kind"), s.optString("title"), s.optString("body"), s.optString("date"), s.optInt("like", 0), s.optString("by"), comments.sortedBy { it.id }))
        }
        return out.sortedByDescending { it.id }
    }

    /** 글 올리기. 댓글은 건드리지 않도록 칸마다 경로로 써요 */
    suspend fun putStory(code: String, s: StoryItem) = send("stories/$code", "PATCH", JSONObject()
        .put("${s.id}/kind", s.kind).put("${s.id}/title", s.title).put("${s.id}/body", s.body)
        .put("${s.id}/date", s.date).put("${s.id}/by", s.by).put("${s.id}/like", s.like))

    suspend fun deleteStory(code: String, id: Long) = send("stories/$code", "PATCH", JSONObject().put("$id", JSONObject.NULL))

    suspend fun setStoryLike(code: String, id: Long, n: Int) = send("stories/$code", "PATCH", JSONObject().put("$id/like", n))

    suspend fun putStoryComment(code: String, sid: Long, c: StoryComment) = send("stories/$code", "PATCH", JSONObject()
        .put("$sid/comments/${c.id}/text", c.text).put("$sid/comments/${c.id}/date", c.date).put("$sid/comments/${c.id}/by", c.by))

    suspend fun deleteStoryComment(code: String, sid: Long, cid: Long) =
        send("stories/$code", "PATCH", JSONObject().put("$sid/comments/$cid", JSONObject.NULL))

    /** 이 코드의 서버 데이터를 모두 지워요 (찜·아이·추천·연결 기록·이야기) */
    suspend fun deleteAll(code: String) {
        send("families/$code", "DELETE", null)
        send("stories/$code", "DELETE", null)
    }

    /** 휴대폰이 코드를 자기 것으로 등록해요 (문서가 없으면 이때 만들어져요) */
    suspend fun register(code: String, owner: String) =
        patch(code, JSONObject().put("owner", owner).put("createdAt", JSONObject().put(".sv", "timestamp")))

    private suspend fun patch(code: String, body: JSONObject): Unit = withContext(Dispatchers.IO) {
        // HttpURLConnection 은 PATCH 를 지원하지 않아서 Firebase 의 메서드 덮어쓰기 헤더를 써요
        val c = open("$base/families/$code.json", "POST")
        try {
            c.doOutput = true
            c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (c.responseCode !in 200..299) error("저장 실패 (${c.responseCode})")
            Unit
        } finally { c.disconnect() }
    }

    private suspend fun send(path: String, override: String, body: JSONObject?): Unit = withContext(Dispatchers.IO) {
        val c = open("$base/$path.json", "POST")
        try {
            c.doOutput = true
            c.setRequestProperty("X-HTTP-Method-Override", override)
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write((body?.toString() ?: "null").toByteArray(Charsets.UTF_8)) }
            if (c.responseCode !in 200..299) error("저장 실패 (${c.responseCode})")
            Unit
        } finally { c.disconnect() }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply { requestMethod = method; connectTimeout = 8000; readTimeout = 8000 }

    companion object {
        /** 추측하기 어렵게 보안용 난수로 6자리를 뽑아요 */
        fun newCode(): String = (100000 + java.security.SecureRandom().nextInt(900000)).toString()
    }
}
