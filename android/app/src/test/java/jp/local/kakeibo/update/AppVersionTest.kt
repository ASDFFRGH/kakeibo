package jp.local.kakeibo.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun newerVersionIsDetected() {
        assertTrue(isVersionNewer("v1.19", "1.18"))
        assertTrue(isVersionNewer("2.0", "1.99"))
        assertTrue(isVersionNewer("1.18.1", "1.18"))
    }

    @Test
    fun equalOrOlderVersionIsIgnored() {
        assertFalse(isVersionNewer("v1.18", "1.18"))
        assertFalse(isVersionNewer("1.18.0", "1.18"))
        assertFalse(isVersionNewer("1.17.9", "1.18"))
    }

    @Test
    fun malformedVersionIsIgnored() {
        assertFalse(isVersionNewer("latest", "1.18"))
        assertFalse(isVersionNewer("v1.19-beta", "1.18"))
        assertFalse(isVersionNewer("v1.19", "development"))
    }
}
