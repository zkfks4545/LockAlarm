package com.routinealarm.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoAspectFitTest {
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
