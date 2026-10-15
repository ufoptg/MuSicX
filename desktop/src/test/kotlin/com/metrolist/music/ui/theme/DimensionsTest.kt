package com.metrolist.music.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DimensionsTest {
    @Test
    fun homeGridHeightsMatchAndroid() {
        assertEquals(128.dp, Dimensions.GridThumbnailHeight)
        assertEquals(104.dp, Dimensions.SmallGridThumbnailHeight)
    }

    @Test
    fun listAndThumbnailSizesMatchAndroid() {
        assertEquals(64.dp, Dimensions.ListItemHeight)
        assertEquals(48.dp, Dimensions.ListThumbnailSize)
        assertEquals(3.dp, Dimensions.ThumbnailCornerRadius)
    }
}
