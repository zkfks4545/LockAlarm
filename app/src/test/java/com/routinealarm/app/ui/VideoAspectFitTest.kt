package com.routinealarm.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoAspectFitTest {
    @Test
    fun highResolutionPreviewWithMatchingRatioDoesNotShrink() {
        assertEquals(
            PreviewTextureScale(1f, 1f),
            calculatePreviewTextureScale(1_920, 1_080, 480, 270, cropToFill = true),
        )
        assertEquals(
            PreviewTextureScale(1f, 1f),
            calculatePreviewTextureScale(3_840, 2_160, 480, 270, cropToFill = true),
        )
    }

    @Test
    fun portraitPreviewCropsVerticallyInsideWideCard() {
        val scale = calculatePreviewTextureScale(1_080, 1_920, 510, 290, cropToFill = true)

        assertEquals(1f, scale.x, 0.001f)
        assertEquals(3.1264f, scale.y, 0.001f)
    }

    @Test
    fun portraitPreviewFitsInsideWideEditorWithoutCropping() {
        val scale = calculatePreviewTextureScale(1_080, 1_920, 510, 290, cropToFill = false)

        assertEquals(0.32f, scale.x, 0.001f)
        assertEquals(1f, scale.y, 0.001f)
    }

    @Test
    fun landscapeVideoUsesMaximumWidthInsideWideScreen() {
        assertEquals(
            VideoDisplaySize(width = 2_133, height = 1_200),
            calculateAspectFitSize(
                sourceWidth = 1_920,
                sourceHeight = 1_080,
                maxWidth = 2_400,
                maxHeight = 1_200,
            ),
        )
    }

    @Test
    fun landscapeVideoUsesMaximumWidthInsidePortraitScreen() {
        assertEquals(
            VideoDisplaySize(width = 1_080, height = 608),
            calculateAspectFitSize(
                sourceWidth = 1_920,
                sourceHeight = 1_080,
                maxWidth = 1_080,
                maxHeight = 2_400,
            ),
        )
    }

    @Test
    fun portraitVideoUsesMaximumHeightInsidePortraitScreen() {
        assertEquals(
            VideoDisplaySize(width = 1_350, height = 2_400),
            calculateAspectFitSize(
                sourceWidth = 1_080,
                sourceHeight = 1_920,
                maxWidth = 1_440,
                maxHeight = 2_400,
            ),
        )
    }

    @Test
    fun matchingRatioUsesEveryAvailablePixel() {
        assertEquals(
            VideoDisplaySize(width = 2_400, height = 1_350),
            calculateAspectFitSize(
                sourceWidth = 1_920,
                sourceHeight = 1_080,
                maxWidth = 2_400,
                maxHeight = 1_350,
            ),
        )
    }
}
