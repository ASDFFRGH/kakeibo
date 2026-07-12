package jp.local.kakeibo

class KakeiboApplication : android.app.Application() {
    val repository by lazy {
        jp.local.kakeibo.data.KakeiboRepository
            .create(this)
    }
}
