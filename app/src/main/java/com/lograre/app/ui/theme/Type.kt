package com.lograre.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lograre.app.R

val LoraFontFamily = FontFamily(
    Font(resId = R.font.lora_regular, weight = FontWeight.Normal),
    Font(resId = R.font.lora_medium, weight = FontWeight.Medium)
)

val NunitoFontFamily = FontFamily(
    Font(resId = R.font.nunito_light, weight = FontWeight.Light),
    Font(resId = R.font.nunito_regular, weight = FontWeight.Normal),
    Font(resId = R.font.nunito_semibold, weight = FontWeight.SemiBold)
)

@Immutable
data class ZenTypography(
    val headline: TextStyle = TextStyle(
        fontFamily = LoraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 42.sp
    ),
    val title: TextStyle = TextStyle(
        fontFamily = LoraFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    val subtitle: TextStyle = TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    val body: TextStyle = TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    val label: TextStyle = TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp
    ),
    val time: TextStyle = TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        fontFeatureSettings = "tnum"
    ),
    val keypad: TextStyle = TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontFeatureSettings = "tnum"
    )
)

val DefaultZenTypography = ZenTypography()

val LocalZenTypography = staticCompositionLocalOf { DefaultZenTypography }

val ZenMaterialTypography = Typography(
    displayLarge = DefaultZenTypography.time,
    headlineLarge = DefaultZenTypography.headline,
    headlineMedium = DefaultZenTypography.title,
    titleLarge = DefaultZenTypography.title,
    titleMedium = DefaultZenTypography.subtitle,
    bodyLarge = DefaultZenTypography.body,
    bodyMedium = DefaultZenTypography.body,
    bodySmall = DefaultZenTypography.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = DefaultZenTypography.subtitle.copy(fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = DefaultZenTypography.label,
    labelSmall = DefaultZenTypography.label
)
