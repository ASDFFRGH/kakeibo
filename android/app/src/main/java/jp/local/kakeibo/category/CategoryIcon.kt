package jp.local.kakeibo.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.DirectionsTransit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Returns one consistent Material icon for a category wherever it appears in the app.
 * User-created names also receive a suitable icon when they contain a known keyword.
 */
fun categoryIcon(name: String): ImageVector {
    val normalizedName = name.trim().lowercase()
    return when {
        normalizedName.contains("食") || normalizedName.contains("飲食") ->
            Icons.Outlined.Restaurant
        normalizedName.contains("日用品") || normalizedName.contains("買い物") ->
            Icons.Outlined.ShoppingBasket
        normalizedName.contains("交通") || normalizedName.contains("電車") ->
            Icons.Outlined.DirectionsTransit
        normalizedName.contains("交際") ->
            Icons.Outlined.Groups
        normalizedName.contains("医療") || normalizedName.contains("病院") ->
            Icons.Outlined.MedicalServices
        normalizedName.contains("娯楽") || normalizedName.contains("ゲーム") ->
            Icons.Outlined.SportsEsports
        normalizedName.contains("家賃") || normalizedName.contains("住宅") ->
            Icons.Outlined.Home
        normalizedName.contains("水道") ->
            Icons.Outlined.WaterDrop
        normalizedName.contains("ガス") ->
            Icons.Outlined.LocalFireDepartment
        normalizedName.contains("電気") || normalizedName.contains("光熱") ->
            Icons.Outlined.Bolt
        normalizedName.contains("通信") || normalizedName.contains("スマホ") ->
            Icons.Outlined.PhoneAndroid
        normalizedName.contains("衣服") || normalizedName.contains("服飾") ->
            Icons.Outlined.Checkroom
        normalizedName.contains("サブスク") ->
            Icons.Outlined.Subscriptions
        normalizedName.contains("美容") ->
            Icons.Outlined.Spa
        normalizedName == "ai" || normalizedName.contains("人工知能") ->
            Icons.Outlined.SmartToy
        normalizedName.contains("ギャンブル") ->
            Icons.Outlined.Casino
        normalizedName.contains("ふるさと納税") || normalizedName.contains("寄付") ->
            Icons.Outlined.VolunteerActivism
        normalizedName.contains("税") || normalizedName.contains("国から") ->
            Icons.Outlined.AccountBalance
        normalizedName.contains("nisa") ||
            normalizedName.contains("投資") ||
            normalizedName.contains("貯蓄") ->
            Icons.Outlined.Savings
        normalizedName.contains("給与") || normalizedName.contains("給料") ->
            Icons.Outlined.Payments
        normalizedName.contains("おこづかい") || normalizedName.contains("小遣い") ->
            Icons.Outlined.AccountBalanceWallet
        normalizedName.contains("優待") || normalizedName.contains("特典") ->
            Icons.Outlined.CardGiftcard
        else ->
            Icons.Outlined.Category
    }
}
