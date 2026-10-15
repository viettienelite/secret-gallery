package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val GoogleSansRounded = FontFamily(
  Font(R.font.googlesansrounded_thin, FontWeight.Thin),
  Font(R.font.googlesansrounded_extralight, FontWeight.ExtraLight),
  Font(R.font.googlesansrounded_light, FontWeight.Light),
  Font(R.font.googlesansrounded_regular, FontWeight.Normal),
  Font(R.font.googlesansrounded_medium, FontWeight.Medium),
  Font(R.font.googlesansrounded_medium, FontWeight.SemiBold),
  Font(R.font.googlesansrounded_bold, FontWeight.Bold),
  Font(R.font.googlesansrounded_bold, FontWeight.ExtraBold),
  Font(R.font.googlesansrounded_bold, FontWeight.Black)
)

private val defaultTypography = Typography()

val Typography = Typography(
  displayLarge = defaultTypography.displayLarge.copy(fontFamily = GoogleSansRounded),
  displayMedium = defaultTypography.displayMedium.copy(fontFamily = GoogleSansRounded),
  displaySmall = defaultTypography.displaySmall.copy(fontFamily = GoogleSansRounded),
  headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = GoogleSansRounded),
  headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = GoogleSansRounded),
  headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = GoogleSansRounded),
  titleLarge = defaultTypography.titleLarge.copy(fontFamily = GoogleSansRounded),
  titleMedium = defaultTypography.titleMedium.copy(fontFamily = GoogleSansRounded),
  titleSmall = defaultTypography.titleSmall.copy(fontFamily = GoogleSansRounded),
  bodyLarge = TextStyle(
    fontFamily = GoogleSansRounded,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.2.sp
  ),
  bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = GoogleSansRounded),
  bodySmall = defaultTypography.bodySmall.copy(fontFamily = GoogleSansRounded),
  labelLarge = defaultTypography.labelLarge.copy(fontFamily = GoogleSansRounded),
  labelMedium = defaultTypography.labelMedium.copy(fontFamily = GoogleSansRounded),
  labelSmall = defaultTypography.labelSmall.copy(fontFamily = GoogleSansRounded)
)