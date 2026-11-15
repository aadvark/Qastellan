package com.kithandkeen.qastellan.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNameTest {
    @Test
    fun stripsPendingAndTrashedPrefixes() {
        assertEquals("miaoyulianzhu (2).apk", Scanner.displayName(".pending-1795341526-miaoyulianzhu (2).apk"))
        assertEquals("photo.jpg", Scanner.displayName(".trashed-1795341526-photo.jpg"))
        assertEquals(".hidden.apk", Scanner.displayName(".hidden.apk"))
    }
}
