package com.gabozayo.app

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 이모지 대신 쓰는 선형 아이콘 (모두 같은 굵기)
val IcPhoto = ico("M4 7h3.5l1.5-2h6l1.5 2H20v12H4zM12 10.2a3 3 0 1 0 0.01 0z")
val IcCar = ico("M5 15V11l1.8-4.5h10.4L19 11v4M5 15h14M5 15v3h3v-3M16 15v3h3v-3M7.5 11h9")
val IcMap = ico("M9 4L3 6.5v13.5l6-2.5 6 2.5 6-2.5V4l-6 2.5zM9 4v13.5M15 6.5V20")
val IcLink = ico("M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")

/** 눌렀을 때 살짝 작아졌다가 돌아오는 터치 반응 (물결 효과 대신) */
fun Modifier.pressScale(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press")
    this.graphicsLayer { scaleX = sc; scaleY = sc }.clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** 사진이 없을 때: 이모지 대신 은은한 그라데이션 + 아이콘 */
@Composable
fun PhotoPlaceholder(modifier: Modifier = Modifier, icon: Dp = 28.dp) {
    Box(modifier.background(Brush.linearGradient(listOf(Im, Chp))), Alignment.Center) {
        Icon(IcPhoto, null, Modifier.size(icon), tint = Sb.copy(alpha = 0.55f))
    }
}

/** 홈 위쪽의 바로가기 타일 */
@Composable
fun BentoTile(title: String, sub: String, icon: ImageVector, strong: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fg = if (strong) OnBl else Nv
    val fg2 = if (strong) Color(0xCC2B1D00) else Sb
    Row(
        modifier.height(76.dp).clip(RoundedCornerShape(24.dp))
            .then(if (strong) Modifier.background(Brush.linearGradient(listOf(Bl, Color(0xFFF0C95A)))) else Modifier.background(Sf))
            .pressScale(onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(if (strong) Color(0x1F000000) else Chp), Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (strong) OnBl else BlTx)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = fg2, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 작은 통계 타일 (큰 숫자 + 이름) */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Sf).pressScale(onClick).padding(vertical = 16.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Nv, maxLines = 1)
        Text(label, fontSize = 12.sp, color = Sb, maxLines = 1)
    }
}
