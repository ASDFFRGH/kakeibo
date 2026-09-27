package jp.local.kakeibo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.http.POST
import kotlin.reflect.KClass

class SyncEndpointContractTest {
    @Test
    fun `sync endpoints remain relative to the versioned API base URL`() {
        val householdPath = postPath(KakeiboApi::class)
        val gamblingPath = postPath(GamblingApi::class)

        assertEquals("sync", householdPath)
        assertEquals("gambling/sync", gamblingPath)
        assertFalse(householdPath.startsWith('/'))
        assertFalse(gamblingPath.startsWith('/'))
    }

    private fun postPath(api: KClass<*>): String =
        requireNotNull(
            api.java.declaredMethods
                .single { it.name == "sync" }
                .getAnnotation(POST::class.java),
        ).value
}
