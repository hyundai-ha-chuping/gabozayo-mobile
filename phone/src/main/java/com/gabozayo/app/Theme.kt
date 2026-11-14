package com.gabozayo.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** 화면 모드: light(라이트) / dark(다크) / system(휴대폰 설정 따라가기) */
object Th {
    var mode by mutableStateOf("system")
    var sys by mutableStateOf(false)
    val dark: Boolean get() = when (mode) { "dark" -> true; "light" -> false; else -> sys }

    fun load(ctx: Context) {
        mode = Store.str("theme", "system").let { if (it == "light" || it == "dark") it else "system" }
        sys = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun set(m: String) { mode = m; Store.putStr("theme", m) }
}

// ───────── 색상 토큰 (라이트/다크 자동 전환) ─────────
val Bg: Color get() = if (Th.dark) Color(0xFF14130F) else Color(0xFFF7F5EE)       // 화면 배경
val Sf: Color get() = if (Th.dark) Color(0xFF1F1D17) else Color.White             // 카드·시트 배경
val Bl: Color get() = Color(0xFFE3B32B)                                            // 포인트 (아이콘 노랑 #E3B32B 그대로)
val BlTx: Color get() = if (Th.dark) Color(0xFFE3B32B) else Color(0xFFA86B00)     // 노랑 글자·아이콘 (밝은 배경에서도 읽히게 진하게)
val OnBl = Color(0xFF2B1D00)                                                        // 노랑 위의 글자·아이콘
val Nv: Color get() = if (Th.dark) Color(0xFFF5F2E8) else Color(0xFF211B0D)       // 기본 글자
val Sb: Color get() = if (Th.dark) Color(0xFFA49F90) else Color(0xFF746E5E)       // 보조 글자
val Tx2: Color get() = if (Th.dark) Color(0xFFDAD5C6) else Color(0xFF433C2B)      // 본문 글자
val Ln: Color get() = if (Th.dark) Color(0xFF3A362B) else Color(0xFFE9E5D9)       // 구분선
val Im: Color get() = if (Th.dark) Color(0xFF2A271F) else Color(0xFFEBE6D8)       // 이미지 자리
val Red: Color get() = if (Th.dark) Color(0xFFFF6B6B) else Color(0xFFE5484D)
val Chp: Color get() = if (Th.dark) Color(0xFF3A2E10) else Color(0xFFFFF1CC)      // 칩 배경
val ChT: Color get() = if (Th.dark) Color(0xFFFFD66B) else Color(0xFF7A4A00)      // 칩 글자
val Hy: Color get() = if (Th.dark) Color(0xFF6B4A12) else Color(0xFF3D2A00)       // 진한 갈색 (로그인·머리글)
val TagBg: Color get() = if (Th.dark) Color(0xFF22343B) else Color(0xFFE1EBEE)
val TagTx: Color get() = if (Th.dark) Color(0xFFA9C8D4) else Color(0xFF2F4B57)
val HyFix = Color(0xFF3D2A00)                                                       // 시작 화면(항상 동일)

/**
 * 앱 전체 서체. 지금은 기본 서체예요.
 * Pretendard 를 쓰려면 app/src/main/res/font/ 에 pretendard_regular.ttf / pretendard_bold.ttf 를 넣고
 * 아래를 FontFamily(Font(R.font.pretendard_regular, FontWeight.Normal), Font(R.font.pretendard_bold, FontWeight.Bold)) 로 바꾸세요.
 */
val AppFont: FontFamily = FontFamily.Default
// 한국어는 낱말(어절) 단위로 줄을 바꿔요 — "바뀌어/요" 처럼 끊기지 않게
private val baseText = TextStyle(fontFamily = AppFont, letterSpacing = (-0.2).sp,
    lineBreak = LineBreak(LineBreak.Strategy.HighQuality, LineBreak.Strictness.Normal, LineBreak.WordBreak.Phrase))
private fun appTypography(): Typography {
    val b = Typography()
    fun TextStyle.t() = merge(baseText)
    return Typography(
        displayLarge = b.displayLarge.t(), displayMedium = b.displayMedium.t(), displaySmall = b.displaySmall.t(),
        headlineLarge = b.headlineLarge.t(), headlineMedium = b.headlineMedium.t(), headlineSmall = b.headlineSmall.t(),
        titleLarge = b.titleLarge.t(), titleMedium = b.titleMedium.t(), titleSmall = b.titleSmall.t(),
        bodyLarge = b.bodyLarge.t(), bodyMedium = b.bodyMedium.t(), bodySmall = b.bodySmall.t(),
        labelLarge = b.labelLarge.t(), labelMedium = b.labelMedium.t(), labelSmall = b.labelSmall.t(),
    )
}

@Composable
fun appScheme(): ColorScheme = if (Th.dark) darkColorScheme(
    primary = Bl, onPrimary = OnBl, background = Bg, onBackground = Nv, surface = Sf, onSurface = Nv,
    surfaceVariant = Chp, onSurfaceVariant = Sb, outline = Ln, outlineVariant = Ln,
    primaryContainer = Chp, onPrimaryContainer = ChT, secondaryContainer = Chp, onSecondaryContainer = ChT,
    surfaceContainerLowest = Bg, surfaceContainerLow = Sf, surfaceContainer = Sf, surfaceContainerHigh = Sf, surfaceContainerHighest = Im,
) else lightColorScheme(
    primary = Bl, onPrimary = OnBl, background = Bg, onBackground = Nv, surface = Sf, onSurface = Nv,
    surfaceVariant = Chp, onSurfaceVariant = Sb, outline = Color(0xFFC9C3B2), outlineVariant = Ln,
    primaryContainer = Chp, onPrimaryContainer = ChT, secondaryContainer = Chp, onSecondaryContainer = ChT,
    surfaceContainerLowest = Sf, surfaceContainerLow = Sf, surfaceContainer = Sf, surfaceContainerHigh = Sf, surfaceContainerHighest = Im,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val sys = isSystemInDarkTheme()
    SideEffect { Th.sys = sys }
    val view = LocalView.current
    val dark = Th.dark
    val bar = Bg.toArgb()
    if (!view.isInEditMode) SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        @Suppress("DEPRECATION")
        run { w.statusBarColor = bar; w.navigationBarColor = bar }
        WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
    }
    MaterialTheme(colorScheme = appScheme(), typography = appTypography()) { ProvideTextStyle(baseText, content) }
}
