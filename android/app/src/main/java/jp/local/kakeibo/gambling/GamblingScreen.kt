package jp.local.kakeibo.gambling

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.local.kakeibo.data.GamblingRecordEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val gameTypeSuggestions = listOf("パチンコ", "スロット", "競馬", "競艇", "競輪", "FX", "株", "その他")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamblingRoute(
    viewModel: GamblingViewModel = viewModel(),
    onFullscreenChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var editorOpen by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<GamblingRecordEntity?>(null) }
    var trashOpen by rememberSaveable { mutableStateOf(false) }
    var summaryOpen by rememberSaveable { mutableStateOf(false) }
    var selectedMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val selectedMonth = YearMonth.parse(selectedMonthText)
    val snackbar = remember { SnackbarHostState() }
    val fullscreen = editorOpen || editingRecord != null

    LaunchedEffect(fullscreen) { onFullscreenChange(fullscreen) }
    DisposableEffect(Unit) {
        onDispose { onFullscreenChange(false) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it, duration = SnackbarDuration.Long)
        }
    }

    if (fullscreen) {
        val closeEditor = {
            editorOpen = false
            editingRecord = null
        }
        BackHandler(onBack = closeEditor)
        GamblingEditorPage(
            current = editingRecord,
            initialDate = defaultDateFor(selectedMonth),
            onClose = closeEditor,
            onSave = { current, date, stake, payout, gameType, memo ->
                viewModel.save(current, date, stake, payout, gameType, memo)
                closeEditor()
            },
            onDelete = { record ->
                viewModel.delete(record)
                closeEditor()
            },
        )
        return
    }

    if (summaryOpen) {
        BackHandler(onBack = { summaryOpen = false })
        GamblingSummaryPage(
            records = state.records,
            selectedMonth = selectedMonth,
            onMonthChange = { selectedMonthText = it.toString() },
            onBack = { summaryOpen = false },
        )
        return
    }

    BackHandler(enabled = trashOpen) { trashOpen = false }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            if (trashOpen) "ギャンブルのゴミ箱" else "ギャンブル収支",
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        if (trashOpen) {
                            IconButton(onClick = { trashOpen = false }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "収支一覧に戻る")
                            }
                        }
                    },
                    actions = {
                        if (!trashOpen) {
                            IconButton(onClick = { summaryOpen = true }) {
                                Icon(Icons.Outlined.Assessment, "ギャンブル収支の集計を開く")
                            }
                            IconButton(onClick = { trashOpen = true }) {
                                Icon(Icons.Outlined.Delete, "ギャンブルのゴミ箱を開く")
                            }
                            IconButton(
                                onClick = viewModel::sync,
                                enabled = !state.syncing,
                            ) {
                                Icon(Icons.Outlined.Sync, "ギャンブル収支を同期")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                if (state.syncing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        floatingActionButton = {
            if (!trashOpen) {
                ExtendedFloatingActionButton(
                    onClick = { editorOpen = true },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("収支を追加") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (trashOpen) {
            GamblingTrash(
                records = state.deletedRecords,
                onRestore = viewModel::restore,
                modifier = Modifier.padding(padding),
            )
        } else {
            GamblingLedger(
                records = state.records,
                selectedMonth = selectedMonth,
                onMonthChange = { selectedMonthText = it.toString() },
                onEdit = { editingRecord = it },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun GamblingLedger(
    records: List<GamblingRecordEntity>,
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onEdit: (GamblingRecordEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthlyRecords =
        remember(records, selectedMonth) {
            records.filter { record ->
                runCatching { YearMonth.from(LocalDate.parse(record.date)) }.getOrNull() == selectedMonth
            }
        }
    val totals = remember(monthlyRecords) { calculateGamblingTotals(monthlyRecords) }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MonthSelector(selectedMonth = selectedMonth, onMonthChange = onMonthChange)
            }
            item {
                GamblingTotalsCard(
                    stake = totals.totalStake,
                    payout = totals.totalPayout,
                    balance = totals.balance,
                    count = monthlyRecords.size,
                )
            }
            if (monthlyRecords.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Casino,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                            Text("この月の記録はありません", fontWeight = FontWeight.Bold)
                            Text(
                                "投資額と回収額を記録すると、月の収支を確認できます。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(monthlyRecords, key = { it.uuid }) { record ->
                    GamblingRecordCard(record = record, onEdit = { onEdit(record) })
                }
            }
        }
    }
}

private enum class GamblingSummaryMode(val label: String) {
    MONTH("月別"),
    ALL("全体"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamblingSummaryPage(
    records: List<GamblingRecordEntity>,
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onBack: () -> Unit,
) {
    var modeName by rememberSaveable { mutableStateOf(GamblingSummaryMode.MONTH.name) }
    val mode = GamblingSummaryMode.valueOf(modeName)
    val monthGameTypes = remember(records, selectedMonth) {
        calculateGamblingGameTypeSummaries(records, selectedMonth)
    }
    val monthTotals = remember(monthGameTypes) {
        GamblingTotals(
            totalStake = monthGameTypes.sumOf { it.totals.totalStake },
            totalPayout = monthGameTypes.sumOf { it.totals.totalPayout },
            balance = monthGameTypes.sumOf { it.totals.balance },
        )
    }
    val monthCount = remember(monthGameTypes) { monthGameTypes.sumOf { it.count } }
    val allTotals = remember(records) { calculateGamblingTotals(records) }
    val allCount = remember(records) { records.count { it.deletedAt == null } }
    val months = remember(records) { calculateGamblingMonthSummaries(records) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("ギャンブル収支の集計", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "収支一覧に戻る")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    TabRow(selectedTabIndex = mode.ordinal) {
                        GamblingSummaryMode.entries.forEach { item ->
                            Tab(
                                selected = mode == item,
                                onClick = { modeName = item.name },
                                text = { Text(item.label) },
                            )
                        }
                    }
                }
                if (mode == GamblingSummaryMode.MONTH) {
                    item { MonthSelector(selectedMonth = selectedMonth, onMonthChange = onMonthChange) }
                    item {
                        GamblingSummaryTotalsCard(
                            title = "${selectedMonth.format(DateTimeFormatter.ofPattern("yyyy年M月"))}の合計",
                            totals = monthTotals,
                            count = monthCount,
                        )
                    }
                    item { GamblingBreakdownHeader("種目別") }
                    if (monthGameTypes.isEmpty()) {
                        item { GamblingSummaryEmpty("この月の集計対象となる記録はありません") }
                    } else {
                        items(monthGameTypes, key = { it.gameType }) { summary ->
                            GamblingBreakdownCard(
                                title = summary.gameType,
                                subtitle = "${summary.count}件",
                                totals = summary.totals,
                            )
                        }
                    }
                } else {
                    item {
                        GamblingSummaryTotalsCard(
                            title = "これまでの合計",
                            totals = allTotals,
                            count = allCount,
                        )
                    }
                    item { GamblingBreakdownHeader("月別") }
                    if (months.isEmpty()) {
                        item { GamblingSummaryEmpty("集計対象となる記録はありません") }
                    } else {
                        items(months, key = { it.month.toString() }) { summary ->
                            GamblingBreakdownCard(
                                title = summary.month.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                                subtitle = "${summary.count}件",
                                totals = summary.totals,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GamblingSummaryTotalsCard(title: String, totals: GamblingTotals, count: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold)
                Text("${count}件", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalMetric("投資", formatYen(totals.totalStake), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                TotalMetric("回収", formatYen(totals.totalPayout), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                TotalMetric("収支", signedYen(totals.balance), balanceColor(totals.balance), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GamblingBreakdownHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun GamblingSummaryEmpty(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GamblingBreakdownCard(title: String, subtitle: String, totals: GamblingTotals) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "投資 ${formatYen(totals.totalStake)}・回収 ${formatYen(totals.totalPayout)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "収支 ${signedYen(totals.balance)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = balanceColor(totals.balance),
            )
        }
    }
}

@Composable
private fun MonthSelector(
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onMonthChange(selectedMonth.minusMonths(1)) }) {
            Icon(Icons.Outlined.ChevronLeft, "前月")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                selectedMonth.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = { onMonthChange(selectedMonth.plusMonths(1)) }) {
            Icon(Icons.Outlined.ChevronRight, "翌月")
        }
    }
}

@Composable
private fun GamblingTotalsCard(
    stake: Long,
    payout: Long,
    balance: Long,
    count: Int,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("月間サマリー", fontWeight = FontWeight.Bold)
                Text("${count}件", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TotalMetric("投資", formatYen(stake), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                TotalMetric("回収", formatYen(payout), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                TotalMetric("収支", signedYen(balance), balanceColor(balance), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TotalMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun GamblingRecordCard(
    record: GamblingRecordEntity,
    onEdit: () -> Unit,
) {
    val balance = record.payoutAmount - record.stakeAmount
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "${record.gameType}の記録を編集", onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(record.gameType, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        formatDate(record.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "収支 ${signedYen(balance)}",
                        color = balanceColor(balance),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "投資 ${formatYen(record.stakeAmount)}・回収 ${formatYen(record.payoutAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (record.memo.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(record.memo, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun GamblingTrash(
    records: List<GamblingRecordEntity>,
    onRestore: (GamblingRecordEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "削除した記録は復元できます。復元内容は次回の同期でサーバーにも反映されます。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (records.isEmpty()) {
                item {
                    Text(
                        "削除した記録はありません",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
            }
            items(records, key = { it.uuid }) { record ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(record.gameType, fontWeight = FontWeight.Bold)
                            Text(
                                "${formatDate(record.date)}・収支 ${signedYen(record.payoutAmount - record.stakeAmount)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { onRestore(record) }) {
                            Icon(Icons.Outlined.Restore, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("復元")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamblingEditorPage(
    current: GamblingRecordEntity?,
    initialDate: LocalDate,
    onClose: () -> Unit,
    onSave: (GamblingRecordEntity?, LocalDate, Long, Long, String, String) -> Unit,
    onDelete: (GamblingRecordEntity) -> Unit,
) {
    var date by remember(current?.uuid) {
        mutableStateOf(current?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: initialDate)
    }
    var gameType by remember(current?.uuid) { mutableStateOf(current?.gameType.orEmpty()) }
    var stake by remember(current?.uuid) { mutableStateOf(current?.stakeAmount?.toString() ?: "0") }
    var payout by remember(current?.uuid) { mutableStateOf(current?.payoutAmount?.toString() ?: "0") }
    var memo by remember(current?.uuid) { mutableStateOf(current?.memo.orEmpty()) }
    var deleteConfirmationOpen by remember { mutableStateOf(false) }
    val stakeAmount = stake.toLongOrNull()
    val payoutAmount = payout.toLongOrNull()
    val amountsValid = stakeAmount != null && payoutAmount != null && (stakeAmount > 0 || payoutAmount > 0)
    val valid = gameType.isNotBlank() && amountsValid
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (current == null) "ギャンブル収支を追加" else "ギャンブル収支を編集",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, "入力画面を閉じる")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> date = LocalDate.of(year, month + 1, day) },
                            date.year,
                            date.monthValue - 1,
                            date.dayOfMonth,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")))
                }

                OutlinedTextField(
                    value = gameType,
                    onValueChange = { gameType = it.take(80) },
                    label = { Text("種目") },
                    supportingText = { Text("例：パチンコ、競馬、オンラインカジノ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(gameTypeSuggestions) { suggestion ->
                        FilterChip(
                            selected = gameType == suggestion,
                            onClick = { gameType = suggestion },
                            label = { Text(suggestion) },
                        )
                    }
                }

                OutlinedTextField(
                    value = stake,
                    onValueChange = { stake = it.filter(Char::isDigit) },
                    label = { Text("投資額") },
                    suffix = { Text("円") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = payout,
                    onValueChange = { payout = it.filter(Char::isDigit) },
                    label = { Text("回収額") },
                    suffix = { Text("円") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        if (!amountsValid) {
                            Text("投資額と回収額を入力し、どちらか一方は1円以上にしてください")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (stakeAmount != null && payoutAmount != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("今回の収支", fontWeight = FontWeight.Bold)
                            val balance = payoutAmount - stakeAmount
                            Text(
                                signedYen(balance),
                                color = balanceColor(balance),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it.take(500) },
                    label = { Text("メモ（任意）") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        onSave(
                            current,
                            date,
                            requireNotNull(stakeAmount),
                            requireNotNull(payoutAmount),
                            gameType.trim(),
                            memo.trim(),
                        )
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("保存")
                }

                if (current != null) {
                    TextButton(
                        onClick = { deleteConfirmationOpen = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("この記録を削除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (deleteConfirmationOpen && current != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmationOpen = false },
            title = { Text("記録を削除しますか？") },
            text = { Text("削除後もゴミ箱から復元できます。") },
            confirmButton = {
                TextButton(onClick = { onDelete(current) }) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmationOpen = false }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun balanceColor(balance: Long): Color =
    if (balance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

private fun defaultDateFor(month: YearMonth): LocalDate {
    val today = LocalDate.now()
    return if (YearMonth.from(today) == month) {
        today
    } else {
        month.atDay(today.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
    }
}

private fun formatDate(value: String): String =
    runCatching {
        LocalDate.parse(value).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    }.getOrDefault(value)

private fun formatYen(value: Long): String = "%,d円".format(value)

private fun signedYen(value: Long): String =
    when {
        value > 0 -> "+${formatYen(value)}"
        value < 0 -> "-${formatYen(-value)}"
        else -> formatYen(0)
    }
