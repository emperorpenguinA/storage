package com.mementostorage.app.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.mementostorage.app.generated.resources.Res
import com.mementostorage.app.generated.resources.noto_sans_jp
import org.jetbrains.compose.resources.Font

/**
 * Compose Multiplatform draws text itself (via Skia) instead of delegating to the platform's
 * native text views, so it never picks up the OS's own Japanese font automatically — without
 * this, Japanese text renders as empty "tofu" boxes on every target, not just the web one.
 * Noto Sans JP is bundled as a compose resource and applied to every Material3 type scale slot.
 */
@Composable
fun rememberJapaneseTypography(): Typography {
    val japaneseFontFamily = FontFamily(Font(Res.font.noto_sans_jp))
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = japaneseFontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = japaneseFontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = japaneseFontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = japaneseFontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = japaneseFontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = japaneseFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = japaneseFontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = japaneseFontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = japaneseFontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = japaneseFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = japaneseFontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = japaneseFontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = japaneseFontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = japaneseFontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = japaneseFontFamily),
    )
}
