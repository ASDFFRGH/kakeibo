package jp.local.kakeibo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.summary.SummaryCalculator
import jp.local.kakeibo.summary.SummaryPeriod
import jp.local.kakeibo.summary.SummaryResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SummaryScreen(
    expenses: List<ExpenseEntity>,
    modifier: Modifier = Modifier,
) {
    var period by remember { mutableStateOf(SummaryPeriod.MONTH) }
    var anchorDate by remember { mutableStateOf(LocalDate.now()) }
    val summary = remember(period, anchorDate, expenses) { SummaryCalculator.calculate(period, anchorDate, expenses) }

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
        PeriodNavigator(
            summary = summary,
            onPrevious = { anchorDate = period.shift(anchorDate, -1) },
            onNext = { anchorDate = period.shift(anchorDate, 1) },
        )
        SummaryTotal(summary)
        HorizontalDivider()
        if (summary.expenseCount == 0) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("この期間の支出はありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val maximumAmount = summary.buckets.maxOfOrNull { it.amount }?.coerceAtLeast(1) ?: 1
            LazyColumn {
                items(summary.buckets, key = { it.key }) { bucket ->
                    val amountRatio = bucket.amount.toFloat() / maximumAmount.toFloat()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(bucketLabel(summary.period, bucket.from), fontWeight = FontWeight.Medium)
                            Text("%,d円  ${bucket.expenseCount}件".format(bucket.amount))
                        }
                        LinearProgressIndicator(
                            progress = { amountRatio },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
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
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("期間合計", style = MaterialTheme.typography.labelMedium)
            Text(
                "%,d円".format(summary.totalAmount),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("${summary.expenseCount}件  ${dateRange(summary)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun periodLabel(summary: SummaryResult): String =
    when (summary.period) {
        SummaryPeriod.DAY -> summary.anchorDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
        SummaryPeriod.WEEK -> "${shortDate(summary.from)}〜${shortDate(summary.to)}"
        SummaryPeriod.MONTH -> summary.anchorDate.format(DateTimeFormatter.ofPattern("yyyy年M月"))
        SummaryPeriod.YEAR -> summary.anchorDate.format(DateTimeFormatter.ofPattern("yyyy年"))
    }

private fun dateRange(summary: SummaryResult): String =
    if (summary.from == summary.to) {
        summary.from.toString()
    } else {
        "${summary.from}〜${summary.to}"
    }

private fun bucketLabel(
    period: SummaryPeriod,
    date: LocalDate,
): String =
    if (period == SummaryPeriod.YEAR) {
        "${date.monthValue}月"
    } else {
        date.format(DateTimeFormatter.ofPattern("M/d（E）", Locale.JAPAN))
    }

private fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("M/d"))
