package jp.local.kakeibo.data

import android.database.sqlite.SQLiteException
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal fun syncFailureMessage(error: Throwable): String {
    val cause = error.relevantCause()
    val reason =
        when (cause) {
            is UnknownHostException ->
                "接続先のアドレスを確認できません。同期先URLとネットワーク接続を確認してください"
            is ConnectException ->
                "サーバーに接続できません。PC側のAPI、IPアドレス、ポート、ファイアウォールを確認してください"
            is SocketTimeoutException ->
                "サーバーから時間内に応答がありませんでした"
            is SSLException ->
                "サーバーとの安全な通信に失敗しました: ${cause.safeDetail()}"
            is HttpException ->
                httpFailureMessage(cause.code(), cause.response()?.errorBody()?.string())
            is JsonParseException ->
                "サーバーの応答を読み取れませんでした。アプリとAPIのバージョンを確認してください"
            is SQLiteException ->
                "端末内データベースの更新に失敗しました: ${cause.safeDetail()}"
            is IOException ->
                "ネットワーク通信に失敗しました: ${cause.safeDetail()}"
            else ->
                "予期しないエラーが発生しました: ${cause.safeDetail()}"
        }
    return "同期に失敗しました\n原因: $reason"
}

internal fun httpFailureMessage(
    statusCode: Int,
    responseBody: String?,
): String {
    if (statusCode == 404) {
        return "同期先に必要なAPIが見つかりません（HTTP 404）。PC側のBackendを最新版に更新してください"
    }

    val serverMessage =
        responseBody
            ?.takeIf { it.isNotBlank() }
            ?.let { body ->
                runCatching {
                    JsonParser
                        .parseString(body)
                        .asJsonObject
                        .get("message")
                        ?.asString
                }.getOrNull()
            }
            ?.takeIf { it.isNotBlank() }

    return buildString {
        append("サーバーがエラーを返しました（HTTP ")
        append(statusCode)
        append("）")
        if (serverMessage != null) {
            append(": ")
            append(serverMessage)
        }
    }
}

private fun Throwable.relevantCause(): Throwable {
    if (this is HttpException) return this
    var current = this
    while (current.cause != null && current.cause !== current) {
        current = current.cause!!
        if (current is HttpException) return current
    }
    return current
}

private fun Throwable.safeDetail(): String =
    message
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(200)
        ?: this::class.java.simpleName
