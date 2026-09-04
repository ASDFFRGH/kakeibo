package jp.local.kakeibo

class KakeiboApplication : android.app.Application() {
    private val dataContainer by lazy { jp.local.kakeibo.data.KakeiboDataContainer(this) }

    val repository by lazy { dataContainer.repository }
    val gamblingRepository by lazy { dataContainer.gamblingRepository }
}
