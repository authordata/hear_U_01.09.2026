package com.hearu.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import com.hearu.app.ui.theme.HearUTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeContrastTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun contrastRatio(fg: Color, bg: Color): Float {
        val l1 = fg.luminance()
        val l2 = bg.luminance()
        return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
    }

    @Test
    fun lightAndDarkColorSchemes_satisfyWcagAaMinimumContrast() {
        val ratios = mutableListOf<Pair<String, Float>>()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                val c = MaterialTheme.colorScheme
                ratios += "light onPrimary/primary" to contrastRatio(c.onPrimary, c.primary)
                ratios += "light onSecondary/secondary" to contrastRatio(c.onSecondary, c.secondary)
                ratios += "light onPrimaryContainer/primaryContainer" to contrastRatio(c.onPrimaryContainer, c.primaryContainer)
                ratios += "light onSecondaryContainer/secondaryContainer" to contrastRatio(c.onSecondaryContainer, c.secondaryContainer)
                ratios += "light onSurfaceVariant/background" to contrastRatio(c.onSurfaceVariant, c.background)
                ratios += "light onBackground/background" to contrastRatio(c.onBackground, c.background)
                ratios += "light onSurface/surface" to contrastRatio(c.onSurface, c.surface)
                ratios += "light onSurfaceVariant/surfaceVariant" to contrastRatio(c.onSurfaceVariant, c.surfaceVariant)
                ratios += "light onError/error" to contrastRatio(c.onError, c.error)
                ratios += "light onErrorContainer/errorContainer" to contrastRatio(c.onErrorContainer, c.errorContainer)
            }
            HearUTheme(darkTheme = true) {
                val c = MaterialTheme.colorScheme
                ratios += "dark onPrimary/primary" to contrastRatio(c.onPrimary, c.primary)
                ratios += "dark onSecondary/secondary" to contrastRatio(c.onSecondary, c.secondary)
                ratios += "dark onPrimaryContainer/primaryContainer" to contrastRatio(c.onPrimaryContainer, c.primaryContainer)
                ratios += "dark onSecondaryContainer/secondaryContainer" to contrastRatio(c.onSecondaryContainer, c.secondaryContainer)
                ratios += "dark onSurfaceVariant/background" to contrastRatio(c.onSurfaceVariant, c.background)
                ratios += "dark onBackground/background" to contrastRatio(c.onBackground, c.background)
                ratios += "dark onSurface/surface" to contrastRatio(c.onSurface, c.surface)
                ratios += "dark onSurfaceVariant/surfaceVariant" to contrastRatio(c.onSurfaceVariant, c.surfaceVariant)
                ratios += "dark onError/error" to contrastRatio(c.onError, c.error)
                ratios += "dark onErrorContainer/errorContainer" to contrastRatio(c.onErrorContainer, c.errorContainer)
            }
        }

        ratios.forEach { (label, ratio) ->
            assertTrue("$label contrast $ratio must be >= 4.5", ratio >= 4.5f)
        }
    }
}
