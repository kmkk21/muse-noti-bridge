package com.kmkk21.musenotibridge.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kmkk21.musenotibridge.data.db.NotificationCategory
import com.kmkk21.musenotibridge.data.db.NotificationEntity
import com.kmkk21.musenotibridge.data.db.NotificationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogFilterTab(val label: String) {
    ALL("전체"),
    PENDING("전송대기"),
    SENT("전송완료"),
    AD_BLOCKED("광고삭제"),
    IGNORED("무시됨")
}

@Composable
fun LogsScreen(
    notifications: List<NotificationEntity>,
    onRequeue: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(LogFilterTab.ALL) }

    val filtered = remember(notifications, selectedTab) {
        when (selectedTab) {
            LogFilterTab.ALL -> notifications
            LogFilterTab.PENDING -> notifications.filter {
                it.status == NotificationStatus.PENDING || it.status == NotificationStatus.FAILED
            }
            LogFilterTab.SENT -> notifications.filter { it.status == NotificationStatus.SENT }
            LogFilterTab.AD_BLOCKED -> notifications.filter { it.status == NotificationStatus.AD_BLOCKED }
            LogFilterTab.IGNORED -> notifications.filter { it.status == NotificationStatus.IGNORED }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LogFilterTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label) }
                    )
                }
            }
            if (notifications.isNotEmpty()) {
                IconButton(onClick = onClearAll) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "기록 비우기")
                }
            }
        }

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "표시할 알림 기록이 없습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "알림 접근 권한을 켜면 수신된 알림과 차단된 광고가 여기에 실시간 표시됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    NotificationLogCard(item = item, onRequeue = { onRequeue(item.id) })
                }
            }
        }
    }
}

@Composable
private fun NotificationLogCard(
    item: NotificationEntity,
    onRequeue: () -> Unit
) {
    val timeFmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.KOREA) }
    val (statusLabel, badgeBg, badgeFg) = when (item.status) {
        NotificationStatus.PENDING -> Triple("전송대기", Color(0xFFDBEAFE), Color(0xFF1E40AF))
        NotificationStatus.SENT -> Triple("전송완료", Color(0xFFDCFCE7), Color(0xFF166534))
        NotificationStatus.AD_BLOCKED -> Triple("광고삭제", Color(0xFFFEE2E2), Color(0xFF991B1B))
        NotificationStatus.FAILED -> Triple("재시도중", Color(0xFFFEF3C7), Color(0xFF92400E))
        else -> Triple("무시됨", Color(0xFFE2E8F0), Color(0xFF334155))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.appName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "· ${NotificationCategory.displayLabel(item.category)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = badgeFg,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.content.isNotBlank()) {
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val ruleText = if (item.matchedRule.isNotBlank()) " · 규칙: ${item.matchedRule}" else ""
                Text(
                    text = "${timeFmt.format(Date(item.receivedAt))}$ruleText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (item.status == NotificationStatus.AD_BLOCKED || item.status == NotificationStatus.IGNORED) {
                    TextButton(
                        onClick = onRequeue,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Muse로 복구 전송", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
