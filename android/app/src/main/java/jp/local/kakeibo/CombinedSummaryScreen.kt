package jp.local.kakeibo

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.data.GamblingRecordEntity
import jp.local.kakeibo.summary.CombinedSummaryCalculator
import jp.local.kakeibo.summary.CombinedSummaryResult
import jp.local.kakeibo.summary.CombinedTotals
import jp.local.kakeibo.summary.SummaryPeriod
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class CombinedSummaryUiState(
    val expenses: List<ExpenseEntity> = emptyList(),
    val gamblingRecords: List<GamblingRecordEntity> = emptyList(),
)

class CombinedSummaryViewModel(app: Application) : AndroidViewModel(app) {
    private val kakeiboApp = app as KakeiboApplication

    val state = combine(
        kakeiboApp.repository.expenses,
        kakeiboApp.gamblingRepository.records,
    ) { expenses, gamblingRecords ->
        CombinedSummaryUiState(expenses, gamblingRecords)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CombinedSummaryUiState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombinedSummaryRoute(viewModel: CombinedSummaryViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var periodName by rememberSaveable { mutableStateOf(SummaryPeriod.MONTH.name) }
    var anchorDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val period = SummaryPeriod.valueOf(periodName)
    val anchorDate = LocalDate.parse(anchorDateText)
    val summary = remember(period, anchorDate, state) {
        CombinedSummaryCalculator.calculate(
            period = period,
            anchorDate = anchorDate,
            expenses = state.expenses,
            gamblingRecords = state.gamblingRecords,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("全体サマリー", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { padding ->
        CombinedSummaryContent(
            summary = summary,
            period = period,
            anchorDate = anchorDate,
            onPeriodChange = { periodName = it.name },
            onAnchorDateChange = { anchorDateText = it.toString() },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun CombinedSummaryContent(
    summary: CombinedSummaryResult,
    period: SummaryPeriod,
    anchorDate: LocalDate,
    onPeriodChange: (SummaryPeriod) -> Unit,
    onAnchorDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryPeriod.entries.forEach { option ->
                        FilterChip(
                            selected = option == period,
                            onClick = { onPeriodChange(option) },
                            label = { Text(option.displayName) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onAnchorDateChange(period.shift(anchorDate, -1)) }) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "前の期間")
                    }
                    Text(
                        if (period == SummaryPeriod.MONTH) {
                            anchorDate.format(DateTimeFormatter.ofPattern("yyyy年M月"))
                        } else {
                            anchorDate.format(DateTimeFormatter.ofPattern("yyyy年"))
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { onAnchorDateChange(period.shift(anchorDate, 1)) }) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "次の期間")
                    }
                }
            }
        }
        item {
            SummaryTotalsCard(
                title = "合計",
                totals = summary.combined,
                emphasized = true,
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            )
        }
        item {
            Text(
                "内訳",
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            SummaryTotalsCard(
                title = "家計簿",
                totals = summary.household,
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            )
        }
        item {
            SummaryTotalsCard(
                title = "ギャンブル",
                totals = summary.gambling,
                incomeLabel = "回収",
                expenseLabel = "投資",
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            )
        }
        item {
            Text(
                "合計の収入には回収額、支出には投資額を含みます。",
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SummaryTotalsCard(
    title: String,
    totals: CombinedTotals,
    modifier: Modifier = Modifier,
    incomeLabel: String = "収入",
    expenseLabel: String = "支出",
    emphasized: Boolean = false,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            MetricRow(incomeLabel, totals.totalIncome, MaterialTheme.colorScheme.primary)
            MetricRow(expenseLabel, totals.totalExpense, MaterialTheme.colorScheme.error)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MetricRow(
                "収支",
                totals.balance,
                if (totals.balance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                signed = true,
                emphasized = true,
            )
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    amount: Long,
    color: Color,
    signed: Boolean = false,
    emphasized: Boolean = false,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp).weight(1f))
        Text(
            text = if (signed && amount > 0) "+%,d円".format(amount) else "%,d円".format(amount),
            color = color,
            style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
