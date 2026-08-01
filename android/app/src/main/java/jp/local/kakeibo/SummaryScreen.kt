package jp.local.kakeibo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.local.kakeibo.data.CategoryEntity
import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.data.TransactionType
import jp.local.kakeibo.category.categoryIcon
import jp.local.kakeibo.summary.CategorySummary
import jp.local.kakeibo.summary.SummaryCalculator
import jp.local.kakeibo.summary.SummaryPeriod
import jp.local.kakeibo.summary.SummaryResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private const val REPORT_PAGER_PAGE_COUNT = 2_401
private const val REPORT_PAGER_INITIAL_PAGE = REPORT_PAGER_PAGE_COUNT / 2

@Composable
fun SummaryScreen(
    expenses: List<ExpenseEntity>,
    categories: List<CategoryEntity>,
    modifier: Modifier = Modifier,
) {
    var period by remember { mutableStateOf(SummaryPeriod.MONTH) }
    var transactionType by remember { mutableStateOf(TransactionType.EXPENSE) }
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        ReportControls(
            period = period,
            onPeriodChange = { period = it },
            transactionType = transactionType,
            onTransactionTypeChange = { transactionType = it },
        )
        key(period) {
            SummaryPeriodPager(
                period = period,
                transactionType = transactionType,
                expenses = expenses,
                categories = categories,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryPeriodPager(
    period: SummaryPeriod,
    transactionType: String,
    expenses: List<ExpenseEntity>,
    categories: List<CategoryEntity>,
    modifier: Modifier = Modifier,
) {
    val baseDate = remember { LocalDate.now() }
    val pagerState =
        rememberPagerState(
            initialPage = REPORT_PAGER_INITIAL_PAGE,
            pageCount = { REPORT_PAGER_PAGE_COUNT },
        )
    val scope = rememberCoroutineScope()

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        beyondViewportPageCount = 1,
    ) { page ->
        val anchorDate = period.shift(baseDate, (page - REPORT_PAGER_INITIAL_PAGE).toLong())
        val summary =
            remember(period, anchorDate, expenses) {
                SummaryCalculator.calculate(period, anchorDate, expenses)
            }
        val breakdown =
            remember(summary, categories, transactionType) {
                summary.categories
                    .mapNotNull { categorySummary ->
                        categorySummary.toBreakdownItem(categories, transactionType)
                    }.sortedByDescending { it.amount }
            }
        val total = remember(breakdown) { breakdown.sumOf { it.amount } }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item {
                PeriodStrip(
                    period = period,
                    anchorDate = anchorDate,
                    onPrevious = {
                        if (page > 0) {
                            scope.launch { pagerState.animateScrollToPage(page - 1) }
                        }
                    },
                    onNext = {
                        if (page < REPORT_PAGER_PAGE_COUNT - 1) {
                            scope.launch { pagerState.animateScrollToPage(page + 1) }
                        }
                    },
                )
            }
            item {
                ReportOverview(
                    summary = summary,
                    transactionType = transactionType,
                    breakdown = breakdown,
                )
            }
            item {
                Text(
                    text = if (transactionType == TransactionType.EXPENSE) "支出の内訳" else "収入の内訳",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
            if (breakdown.isEmpty()) {
                item {
                    Text(
                        text = "この期間の${reportTransactionLabel(transactionType)}はありません",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                    )
                }
            } else {
                items(breakdown, key = { it.categoryUuid }) { item ->
                    BreakdownRow(item = item, total = total)
                }
            }
        }
    }
}

@Composable
private fun PeriodStrip(
    period: SummaryPeriod,
    anchorDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val dates = listOf(period.shift(anchorDate, -1), anchorDate, period.shift(anchorDate, 1))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 8.dp),
    ) {
        dates.forEachIndexed { index, date ->
            val selected = index == 1
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !selected) {
                        if (index == 0) onPrevious() else onNext()
                    }
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = periodLabel(period, date),
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        ),
                )
            }
        }
    }
}

@Composable
private fun ReportControls(
    period: SummaryPeriod,
    onPeriodChange: (SummaryPeriod) -> Unit,
    transactionType: String,
    onTransactionTypeChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ReportSegmentedControl(
            selectedType = transactionType,
            onSelectedTypeChange = onTransactionTypeChange,
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = period == SummaryPeriod.YEAR,
            onClick = {
                onPeriodChange(
                    if (period == SummaryPeriod.MONTH) SummaryPeriod.YEAR else SummaryPeriod.MONTH,
                )
            },
            label = { Text(if (period == SummaryPeriod.MONTH) "月" else "年") },
        )
    }
}

@Composable
private fun ReportSegmentedControl(
    selectedType: String,
    onSelectedTypeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row {
            listOf(TransactionType.EXPENSE to "支出", TransactionType.INCOME to "収入").forEach { (type, label) ->
                val selected = selectedType == type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                        ).clickable { onSelectedTypeChange(type) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportOverview(
    summary: SummaryResult,
    transactionType: String,
    breakdown: List<BreakdownItem>,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val chartSize = if (maxWidth < 360.dp) 148.dp else 176.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DonutChart(
                items = breakdown,
                transactionType = transactionType,
                size = chartSize,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SummaryMetric("収入", summary.totalIncome, ReportIncomeColor)
                SummaryMetric("支出", summary.totalExpense, ReportExpenseColor)
                SummaryMetric(
                    "収支",
                    summary.balance,
                    if (summary.balance >= 0) ReportIncomeColor else ReportExpenseColor,
                )
            }
        }
    }
}

@Composable
private fun DonutChart(
    items: List<BreakdownItem>,
    transactionType: String,
    size: Dp,
) {
    val total = items.sumOf { it.amount }
    val segments = remember(items) { donutSegments(items) }
    val chartDescription =
        if (items.isEmpty()) {
            "${reportTransactionLabel(transactionType)}の内訳なし"
        } else {
            items.joinToString(
                prefix = "${reportTransactionLabel(transactionType)}の内訳。 ",
                separator = "、",
            ) { "${it.name} ${formatCurrency(it.amount)}" }
        }
    Box(
        modifier = Modifier.size(size).semantics { contentDescription = chartDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val strokeWidth = 30.dp.toPx()
            if (total == 0L) {
                drawArc(
                    color = ReportChartTrack,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Butt),
                )
            } else {
                var startAngle = -90f
                segments.forEach { segment ->
                    val sweep = segment.amount.toFloat() / total.toFloat() * 360f
                    drawArc(
                        color = segment.color,
                        startAngle = startAngle,
                        sweepAngle = (sweep - 1f).coerceAtLeast(0.5f),
                        useCenter = false,
                        style = Stroke(strokeWidth, cap = StrokeCap.Butt),
                    )
                    startAngle += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                reportTransactionLabel(transactionType),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                compactCurrency(total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    amount: Long,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            formatCurrency(amount),
            color = color,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun BreakdownRow(
    item: BreakdownItem,
    total: Long,
) {
    val percentage = if (total == 0L) 0 else (item.amount * 100 / total).toInt()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(item.color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = categoryIcon(item.name),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                "$percentage% ・ ${item.count}件",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "${if (item.transactionType == TransactionType.EXPENSE) "-" else "+"}${formatCurrency(item.amount)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 82.dp, end = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private fun CategorySummary.toBreakdownItem(
    categories: List<CategoryEntity>,
    transactionType: String,
): BreakdownItem? {
    val amount = if (transactionType == TransactionType.INCOME) incomeAmount else expenseAmount
    if (amount <= 0) return null
    val count = if (transactionType == TransactionType.INCOME) incomeCount else expenseCount
    val category = categories.find { it.uuid == categoryUuid }
    return BreakdownItem(
        categoryUuid = categoryUuid,
        name = category?.name ?: "不明なカテゴリ",
        amount = amount,
        count = count,
        transactionType = transactionType,
        color = categoryColor(categoryUuid),
    )
}

private fun donutSegments(items: List<BreakdownItem>): List<DonutSegment> {
    if (items.size <= 6) {
        return items.map { DonutSegment(it.name, it.amount, it.color) }
    }
    val primary = items.take(5).map { DonutSegment(it.name, it.amount, it.color) }
    val other = DonutSegment("その他", items.drop(5).sumOf { it.amount }, ReportOtherColor)
    return primary + other
}

private fun categoryColor(categoryUuid: String): Color {
    val index = ((categoryUuid.hashCode().toLong() and 0x7fffffff) % ReportPalette.size).toInt()
    return ReportPalette[index]
}

private fun periodLabel(
    period: SummaryPeriod,
    date: LocalDate,
): String =
    when (period) {
        SummaryPeriod.MONTH -> date.format(DateTimeFormatter.ofPattern("yyyy年M月"))
        SummaryPeriod.YEAR -> date.format(DateTimeFormatter.ofPattern("yyyy年"))
    }

private fun reportTransactionLabel(type: String): String =
    if (type == TransactionType.INCOME) "収入" else "支出"

private fun formatCurrency(amount: Long): String = "%,d円".format(amount)

private fun compactCurrency(amount: Long): String =
    when {
        amount >= 100_000_000 -> "%.1f億円".format(amount / 100_000_000.0)
        amount >= 10_000 -> "%.1f万円".format(amount / 10_000.0)
        else -> formatCurrency(amount)
    }

private data class BreakdownItem(
    val categoryUuid: String,
    val name: String,
    val amount: Long,
    val count: Int,
    val transactionType: String,
    val color: Color,
)

private data class DonutSegment(
    val name: String,
    val amount: Long,
    val color: Color,
)

private val ReportIncomeColor = Color(0xFF1976D2)
private val ReportExpenseColor = Color(0xFFE13D35)
private val ReportChartTrack = Color(0xFFE5E7EB)
private val ReportOtherColor = Color(0xFF8A94A6)
private val ReportPalette =
    listOf(
        Color(0xFFF04438),
        Color(0xFFE91E63),
        Color(0xFFFFC928),
        Color(0xFF009F92),
        Color(0xFF159DE4),
        Color(0xFF673AB7),
        Color(0xFFB8D622),
        Color(0xFFEF6C00),
    )
