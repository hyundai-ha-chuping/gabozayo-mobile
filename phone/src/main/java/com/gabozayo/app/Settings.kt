package com.gabozayo.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun openAppSettings(ctx: Context) {
    try { ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
}

@Composable
private fun SetGroup(title: String, body: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 4.dp), Sb, 13.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().card()) { body() }
    }
}

@Composable
private fun SetRow(title: String, sub: String = "", value: String = "", last: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 15.sp, color = if (danger) Red else Nv, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = Sb, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (value.isNotBlank()) Text(value, Modifier.widthIn(max = 120.dp), Sb, 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("  ›", color = Sb)
        }
        if (!last) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Ln)
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, on: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 15.sp, color = Nv, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = 12.sp, color = Sb, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(on, onChange)
        }
        if (!last) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Ln)
    }
}

@Composable
private fun InfoRow(title: String, value: String, last: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), Nv, 15.sp, maxLines = 1)
            Text(value, color = Sb, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!last) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Ln)
    }
}

@Composable
fun SettingsScreen(
    back: () -> Unit, onLogout: () -> Unit, onClearRecent: () -> Unit, onClearSearch: () -> Unit, onClearSaved: () -> Unit,
    onResetAll: () -> Unit, toast: (String) -> Unit, askLoc: () -> Unit,
) {
    val ctx = LocalContext.current
    var rec by remember { mutableStateOf(Store.bool("s_rec", true)) }
    var carOn by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { while (true) { carOn = FirebaseCarBridge.carConnected(); kotlinx.coroutines.delay(5000) } }
    var confirm by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf(false) }
    val locOn = hasLocPerm(ctx)
    Column(Modifier.fillMaxSize()) {
        Bar("설정", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), Arrangement.spacedBy(22.dp)) {
            SetGroup("계정") {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar({}, 44); Spacer(Modifier.width(14.dp))
                    Column { Text("익명의 가보자", fontWeight = FontWeight.Bold); Text("계정 없이 이용 중", fontSize = 12.sp, color = Sb) }
                }
            }
            SetGroup("화면 모드") {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("light" to "라이트", "dark" to "다크", "system" to "휴대폰 설정").forEach { (k, n) ->
                        val on = Th.mode == k
                        Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Bl else Chp).clickable { Th.set(k) }, Alignment.Center) {
                            Text(n, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) OnBl else ChT, maxLines = 1)
                        }
                    }
                }
                Text(if (Th.mode == "system") "지금은 휴대폰 설정에 따라 ${if (Th.dark) "다크" else "라이트"} 모드예요" else "언제든 바꿀 수 있어요", Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp), Sb, 12.sp, maxLines = 1)
            }
            SetGroup("위치") {
                SetRow("내 위치 사용", if (locOn) "근처 장소와 거리 계산에 써요" else "허용하면 근처 장소를 찾아요", if (locOn) "허용됨" else "꺼짐") { if (locOn) openAppSettings(ctx) else askLoc() }
                SetRow("기준 지역", "근처 장소를 찾는 기준", if (Geo.manual) Geo.area else if (Geo.outside) "한국 밖 위치" else "내 위치", last = true) { pick = true }
            }
            SetGroup("추천") {
                SwitchRow("아이 취향 반영", "고른 취향으로 장소를 추천해요", rec, last = true) { rec = it; Store.putBool("s_rec", it) }
            }
            SetGroup("차량 연결") {
                InfoRow("연결 코드", familyCode().chunked(3).joinToString(" "))
                InfoRow("차량 상태", when (carOn) { null -> "확인 중"; true -> "연결됨"; false -> "코드 입력 대기" })
                SetRow("찜 목록 지금 보내기", "차량 앱의 \"찜한 곳\"에 바로 반영해요", last = true) { CarSync.tick++; toast("찜 목록을 차량으로 보내요") }
            }
            SetGroup("데이터") {
                SetRow("검색 기록 삭제", "최근 검색과 인기 검색어") { confirm = "search" }
                SetRow("최근 본 장소 삭제") { confirm = "recent" }
                SetRow("찜 목록 비우기") { confirm = "saved" }
                SetRow("내 데이터 모두 삭제", "폰과 서버의 아이·찜·이야기 등 전부", last = true, danger = true) { confirm = "all" }
            }
            SetGroup("앱 정보") {
                InfoRow("버전", BuildConfig.VERSION_NAME)
                InfoRow("장소 정보", "한국관광공사 TourAPI")
                InfoRow("날씨", "Open-Meteo", last = true)
            }
            OutlinedButton(onLogout, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("시작 화면으로", color = Red) }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirm.isNotEmpty()) {
        val (msg, act) = when (confirm) {
            "search" -> "검색 기록과 인기 검색어를 지울까요?" to { onClearSearch(); toast("검색 기록을 지웠어요") }
            "recent" -> "최근 본 장소를 모두 지울까요?" to { onClearRecent(); toast("최근 본 장소를 지웠어요") }
            "saved" -> "찜 목록을 모두 비울까요?" to { onClearSaved(); toast("찜 목록을 비웠어요") }
            else -> "폰과 서버에 저장된 아이·찜·이야기·후기 등 내 데이터를 모두 삭제하고 차량 연결을 해제해요.\n되돌릴 수 없어요." to { onResetAll() }
        }
        AlertDialog({ confirm = "" }, title = { Text("확인", fontSize = 16.sp) }, text = { Text(msg, fontSize = 14.sp) },
            confirmButton = { TextButton({ act(); confirm = "" }) { Text("지우기", color = Red) } }, dismissButton = { TextButton({ confirm = "" }) { Text("취소") } })
    }
    if (pick) AlertDialog({ pick = false }, title = { Text("기준 지역", fontSize = 16.sp) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cities.chunked(3).forEach { rw ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rw.forEach { (n, _) ->
                        Chip(n, Geo.manual && Geo.area == n) { setManualCity(n); pick = false }
                    }
                }
            }
            TextButton({ useRealLocation(ctx); pick = false }) { Text("내 실제 위치 사용", fontSize = 13.sp) }
        }
    }, confirmButton = {}, dismissButton = { TextButton({ pick = false }) { Text("닫기") } })
}
