package jp.local.kakeibo.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.DirectionsTransit
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.SmartToy
import org.junit.Assert.assertSame
import org.junit.Test

class CategoryIconTest {
    @Test
    fun `default expense categories use matching icons`() {
        assertSame(Icons.Outlined.Restaurant, categoryIcon("食費"))
        assertSame(Icons.Outlined.ShoppingBasket, categoryIcon("日用品"))
        assertSame(Icons.Outlined.DirectionsTransit, categoryIcon("交通費"))
    }

    @Test
    fun `custom categories are matched by keyword without case sensitivity`() {
        assertSame(Icons.Outlined.Casino, categoryIcon("ギャンブル"))
        assertSame(Icons.Outlined.AccountBalance, categoryIcon("国から"))
        assertSame(Icons.Outlined.SmartToy, categoryIcon("AI"))
        assertSame(Icons.Outlined.SmartToy, categoryIcon("ai"))
    }
}
