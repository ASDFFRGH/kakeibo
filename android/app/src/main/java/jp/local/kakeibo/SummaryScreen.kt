package jp.local.kakeibo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.local.kakeibo.data.CategoryEntity
import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.data.TransactionType
import jp.local.kakeibo.summary.SummaryCalculator
import jp.local.kakeibo.summary.SummaryPeriod
import jp.local.kakeibo.summary.SummaryResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    expenses: List<ExpenseEntity>,
    categories: List<CategoryEntity>,
    selectedCategoryUuid: String,
    onCategoryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var period by remember { mutableStateOf(SummaryPeriod.MONTH) }
    var anchorDate by remember { mutableStateOf(LocalDate.now()) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    val visibleExpenses = remember(expenses, selectedCategoryUuid) {
        expenses.filter { selectedCategoryUuid.isEmpty() || it.categoryUuid == selectedCategoryUuid }
    }
    val summary = remember(period, anchorDate, visibleExpenses) {
        SummaryCalculator.calculate(period, anchorDate, visibleExpenses)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryPeriod.entries.forEach { option ->
                FilterChip(
                    selected = period == option,
                    onClick = { period = option },
                    label = { Text(option.displayName) },
                )
            }
        }
        ExposedDropdownMenuBox(
            expanded = categoryMenuOpen,
            onExpandedChange = { categoryMenuOpen = !categoryMenuOpen },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            val selectedCategory = categories.find { it.uuid == selectedCategoryUuid }
            OutlinedTextField(
                value = selectedCategory?.let(::summaryCategoryLabel) ?: "すべてのカテゴリ",
                onValueChange = {},
                readOnly = true,
                label = { Text("カテゴリで絞り込み") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryMenuOpen) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = categoryMenuOpen,
                onDismissRequest = { categoryMenuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("すべてのカテゴリ") },
                    onClick = {
                        onCategoryChange("")
                        categoryMenuOpen = false
                    },
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(summaryCategoryLabel(category)) },
                        onClick = {
                            onCategoryChange(category.uuid)
                            categoryMenuOpen = false
                        },
                    )
                }
            }
        }
        PeriodNavigator(
            summary = summary,
            onPrevious = { anchorDate = period.shift(anchorDate, -1) },
            onNext = { anchorDate = period.shift(anchorDate, 1) },
        )
        SummaryTotal(summary)
        HorizontalDivider()
        if (summary.expenseCount + summary.incomeCount == 0) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("この期間の収支はありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn {
                item {
                    Text(
                        "カテゴリ別",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                items(summary.categories, key = { it.categoryUuid }) { categorySummary ->
                    val category = categories.find { it.uuid == categorySummary.categoryUuid }
                    val isIncome =
                        category?.type == TransactionType.INCOME ||
                            (
                                category == null &&
                                    categorySummary.incomeAmount > 0 &&
                                    categorySummary.expenseAmount == 0L
                            )
                    val amount = if (isIncome) categorySummary.incomeAmount else categorySummary.expenseAmount
                    val count = if (isIncome) categorySummary.incomeCount else categorySummary.expenseCount
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                category?.let(::summaryCategoryLabel) ?: "不明なカテゴリ",
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "${if (isIncome) "+" else "-"}%,d円  ${count}件".format(amount),
                                color = if (isIncome) incomeColor else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun PeriodNavigator(
    summary: SummaryResult,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) { Icon(Icons.Outlined.ChevronLeft, "前の期間") }
        Text(periodLabel(summary), fontWeight = FontWeight.Bold)
        IconButton(onClick = onNext) { Icon(Icons.Outlined.ChevronRight, "次の期間") }
    }
}

@Composable
private fun SummaryTotal(summary: SummaryResult) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("期間の差引", style = MaterialTheme.typography.labelLarge)
            Text(
                "%,d円".format(summary.balance),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("収入 +%,d円（${summary.incomeCount}件）".format(summary.totalIncome), color = incomeColor)
                Text(
                    "支出 -%,d円（${summary.expenseCount}件）".format(summary.totalExpense),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(dateRange(summary), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun periodLabel(summary: SummaryResult): String =
    when (summary.period) {
        SummaryPeriod.MONTH -> summary.anchorDate.format(DateTimeFormatter.ofPattern("yyyy年M月"))
        SummaryPeriod.YEAR -> summary.anchorDate.format(DateTimeFormatter.ofPattern("yyyy年"))
    }

private fun dateRange(summary: SummaryResult): String =
    if (summary.from == summary.to) {
        summary.from.toString()
    } else {
        "${summary.from}〜${summary.to}"
}

private fun summaryCategoryLabel(category: CategoryEntity): String =
    "${if (category.type == TransactionType.INCOME) "収入" else "支出"}・${category.name}"

private val incomeColor = Color(0xFF176B4D)
