package com.kmkk21.musenotibridge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kmkk21.musenotibridge.data.prefs.AppFilterMode
import com.kmkk21.musenotibridge.data.prefs.BridgeSettings
import com.kmkk21.musenotibridge.util.InstalledAppItem
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersScreen(
    settings: BridgeSettings,
    installedApps: List<InstalledAppItem>,
    onLoadApps: () -> Unit,
    onToggleAdBlock: (Boolean) -> Unit,
    onAddAdKeyword: (String) -> Unit,
    onRemoveAdKeyword: (String) -> Unit,
    onAddIgnoreKeyword: (String) -> Unit,
    onRemoveIgnoreKeyword: (String) -> Unit,
    onToggleUrgentInstant: (Boolean) -> Unit,
    onAddUrgentKeyword: (String) -> Unit,
    onRemoveUrgentKeyword: (String) -> Unit,
    onChangeMaxChars: (Int) -> Unit,
    onChangeAppFilterMode: (AppFilterMode) -> Unit,
    onTogglePackage: (String) -> Unit
) {
    LaunchedEffect(Unit) { onLoadApps() }

    var appSearchQuery by remember { mutableStateOf("") }
    var showAppList by remember { mutableStateOf(false) }

    val filteredApps = remember(installedApps, appSearchQuery, settings.selectedPackages) {
        val base = if (appSearchQuery.isBlank()) {
            installedApps
        } else {
            val q = appSearchQuery.lowercase()
            installedApps.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
        // Keep checked apps near the top for easy management
        base.sortedByDescending { it.packageName in settings.selectedPackages }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Local Ad Blocker
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "광고 푸시 알림 자동 삭제",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "아래 키워드가 포함된 알림은 폰에서 즉시 삭제하고 Muse로 보내지 않습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.adBlockEnabled,
                            onCheckedChange = onToggleAdBlock
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    KeywordChipEditor(
                        placeholder = "광고 키워드 추가 (예: 특가)",
                        keywords = settings.adKeywords,
                        onAdd = onAddAdKeyword,
                        onRemove = onRemoveAdKeyword
                    )
                }
            }
        }

        // 2. Ignore / Privacy Filter
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "전송 제외 (보안·노이즈 키워드)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "알림창에서 지우지는 않지만, 개인정보 보호와 토큰 절약을 위해 Muse에 전송하지 않습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    KeywordChipEditor(
                        placeholder = "제외 키워드 추가 (예: 인증번호)",
                        keywords = settings.ignoreKeywords,
                        onAdd = onAddIgnoreKeyword,
                        onRemove = onRemoveIgnoreKeyword
                    )
                }
            }
        }

        // 3. Urgent Instant Send
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "긴급 알림 즉시 전송",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "배치 주기를 기다리지 않고 바로 Muse로 전달할 긴급 키워드",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.urgentInstantSend,
                            onCheckedChange = onToggleUrgentInstant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    KeywordChipEditor(
                        placeholder = "긴급 키워드 추가 (예: 해외결제)",
                        keywords = settings.urgentKeywords,
                        onAdd = onAddUrgentKeyword,
                        onRemove = onRemoveUrgentKeyword
                    )
                }
            }
        }

        // 4. Token Saver: Max Content Length
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "토큰 절약: 알림 본문 최대 길이",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${settings.maxContentChars}자",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "긴 알림을 앞부분만 잘라서 보내 Muse 토큰 소모를 크게 줄입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = settings.maxContentChars.toFloat(),
                        onValueChange = { onChangeMaxChars((it / 20f).roundToInt() * 20) },
                        valueRange = 80f..300f,
                        steps = 10
                    )
                }
            }
        }

        // 5. App Selection Filter
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "수집 대상 앱 필터 (${settings.selectedPackages.size}개 선택됨)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.appFilterMode == AppFilterMode.EXCLUDE_SELECTED,
                            onClick = { onChangeAppFilterMode(AppFilterMode.EXCLUDE_SELECTED) },
                            label = { Text("선택한 앱만 제외") }
                        )
                        FilterChip(
                            selected = settings.appFilterMode == AppFilterMode.INCLUDE_ONLY,
                            onClick = { onChangeAppFilterMode(AppFilterMode.INCLUDE_ONLY) },
                            label = { Text("선택한 앱만 수집") }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (settings.appFilterMode == AppFilterMode.INCLUDE_ONLY) {
                            "카드사·은행·문자·예약 앱 등 꼭 필요한 앱만 체크하면 토큰이 가장 적게 듭니다."
                        } else {
                            "모든 앱 알림을 수집하되, 아래에서 체크한 불필요한 앱만 제외합니다."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FilterChip(
                        selected = showAppList,
                        onClick = { showAppList = !showAppList },
                        label = { Text(if (showAppList) "앱 목록 접기" else "설치된 앱 목록 열기 (${installedApps.size}개)") }
                    )

                    if (showAppList) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = appSearchQuery,
                            onValueChange = { appSearchQuery = it },
                            placeholder = { Text("앱 이름 검색 (예: 토스, 현대카드, 카카오톡)") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (showAppList) {
            items(filteredApps.take(80), key = { it.packageName }) { app ->
                val checked = app.packageName in settings.selectedPackages
                Card(
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onTogglePackage(app.packageName) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordChipEditor(
    placeholder: String,
    keywords: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilledTonalIconButton(
            onClick = {
                if (input.isNotBlank()) {
                    onAdd(input.trim())
                    input = ""
                }
            }
        ) {
            Icon(Icons.Default.Add, contentDescription = "추가")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keywords.sorted().forEach { kw ->
            InputChip(
                selected = false,
                onClick = { onRemove(kw) },
                label = { Text(kw, style = MaterialTheme.typography.labelSmall) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "삭제",
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }
    }
}
