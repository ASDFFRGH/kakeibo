package jp.local.kakeibo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SyncFailureTest {
    @Test
    fun `unknown host explains that URL and network should be checked`() {
        val message = syncFailureMessage(UnknownHostException("example.invalid"))

        assertTrue(message.contains("接続先のアドレスを確認できません"))
        assertTrue(message.contains("同期先URL"))
    }

    @Test
    fun `connection refusal explains server side checks`() {
        val message = syncFailureMessage(ConnectException("Connection refused"))

        assertTrue(message.contains("サーバーに接続できません"))
        assertTrue(message.contains("ファイアウォール"))
    }

    @Test
    fun `timeout is shown as the cause`() {
        val message = syncFailureMessage(SocketTimeoutException("timeout"))

        assertTrue(message.contains("時間内に応答がありませんでした"))
    }

    @Test
    fun `http response includes status and server message`() {
        val message = httpFailureMessage(400, """{"success":false,"message":"invalid sync request"}""")

        assertEquals(
            "サーバーがエラーを返しました（HTTP 400）: invalid sync request",
            message,
        )
    }

    @Test
    fun `invalid error body still includes status`() {
        val message = httpFailureMessage(500, "<html>error</html>")

        assertEquals("サーバーがエラーを返しました（HTTP 500）", message)
    }
}
