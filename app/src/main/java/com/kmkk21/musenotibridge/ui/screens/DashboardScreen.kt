package com.kmkk21.musenotibridge.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kmkk21.musenotibridge.data.db.DeliveryLogEntity
import com.kmkk21.musenotibridge.data.prefs.BridgeSettings
import com.kmkk21.musenotibridge.ui.BridgeViewModel
import com.kmkk21.musenotibridge.ui.SystemStatusState
import com.kmkk21.musenotibridge.util.PermissionHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    settings: BridgeSettings,
    systemStatus: SystemStatusState,
    pendingCount: Int,
    sentTodayCount: Int,
    adBlockedTodayCount: Int,
    estimatedPendingTokens: Int,
    deliveryLogs: List<DeliveryLogEntity>,
    isBusy: Boolean,
    onToggleMaster: (Boolean) -> Unit,
    onSendNow: () -> Unit,
    onSendTest: () -> Unit,
    onNavigateToDeliverySettings: () -> Unit
) {
    val context = LocalContext.current
    val allReady = systemStatus.notificationPermissionGranted &&
        systemStatus.batteryOptimizationIgnored &&
        settings.hasAnyDeliveryChannel

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Master Control Hero Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (settings.masterEnabled && allReady) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                !settings.masterEnabled -> "브릿지 일시 중지됨"
                                !allReady -> "초기 설정이 필요합니다"
                                else -> "Muse 브릿지 정상 작동 중"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (settings.masterEnabled) {
                                "광고 즉시 삭제 · ${BridgeViewModel.formatInterval(settings.batchIntervalMinutes)}마다 배치 전송"
                            } else {
                                "스위치를 켜면 알림 수집과 광고 차단이 시작됩니다."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.masterEnabled,
                        onCheckedChange = onToggleMaster
                    )
                }
            }
        }

        // 2. Setup Checklist Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "연결 끊김 방지 필수 체크",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    ChecklistRow(
                        title = "1. 알림 접근 권한",
                        subtitle = if (systemStatus.notificationPermissionGranted) {
                            "권한 허용됨 (실시간 광고 삭제 및 수집 가능)"
                        } else {
                            "알림을 읽고 광고를 지우려면 허용이 필요합니다."
                        },
                        isOk = systemStatus.notificationPermissionGranted,
                        actionLabel = "권한 켜기",
                        onAction = { PermissionHelper.openNotificationListenerSettings(context) }
                    )

                    ChecklistRow(
                        title = "2. 배터리 절전 예외 등록",
                        subtitle = if (systemStatus.batteryOptimizationIgnored) {
                            "제한 없음 설정 완료 (화면 꺼져도 생존)"
                        } else {
                            "오프라인 오류 방지를 위해 절전 예외를 켜주세요."
                        },
                        isOk = systemStatus.batteryOptimizationIgnored,
                        actionLabel = "예외 등록",
                        onAction = { PermissionHelper.requestIgnoreBatteryOptimizations(context) }
                    )

                    ChecklistRow(
                        title = "3. Muse 전송 채널 연결",
                        subtitle = when {
                            settings.isEmailConfigured && settings.isWebhookConfigured ->
                                "Muse Mail + Webhook 연결됨"
                            settings.isEmailConfigured ->
                                "Muse Mail (${settings.recipientEmail}) 연결됨"
                            settings.isWebhookConfigured ->
                                "Webhook 연결됨"
                            else ->
                                "Muse Mail 주소 또는 Webhook URL을 입력해 주세요."
                        },
                        isOk = settings.hasAnyDeliveryChannel,
                        actionLabel = "연동 설정",
                        onAction = onNavigateToDeliverySettings
                    )
                }
            }
        }

        // 3. Today's Stats 2x2 Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        modifier = Modifier.weight(1f),
                        label = "전송 대기 중",
                        value = "${pendingCount}건",
                        sub = if (estimatedPendingTokens > 0) "예상 ~${estimatedPendingTokens} 토큰" else "토큰 소모 없음",
                        icon = Icons.Default.NotificationsActive,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    StatTile(
                        modifier = Modifier.weight(1f),
                        label = "오늘 광고 자동 삭제",
                        value = "${adBlockedTodayCount}건",
                        sub = "토큰 0원으로 차단",
                        icon = Icons.Default.Block,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        modifier = Modifier.weight(1f),
                        label = "오늘 Muse 전송 완료",
                        value = "${sentTodayCount}건",
                        sub = "누락 없이 전달됨",
                        icon = Icons.Default.CloudDone,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    StatTile(
                        modifier = Modifier.weight(1f),
                        label = "배치 전송 주기",
                        value = BridgeViewModel.formatInterval(settings.batchIntervalMinutes),
                        sub = if (settings.urgentInstantSend) "긴급 키워드는 즉시 전송" else "정기 배치만 전송",
                        icon = Icons.Default.BatteryChargingFull,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // 4. Quick Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSendNow,
                    enabled = !isBusy && pendingCount > 0,
                    modifier = Modifier.weight(1.3f)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("지금 바로 전송 ($pendingCount)")
                }

                OutlinedButton(
                    onClick = onSendTest,
                    enabled = !isBusy && settings.hasAnyDeliveryChannel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("연결 테스트")
                }
            }
        }

        // 5. Recent Delivery History
        item {
            Text(
                text = "최근 Muse 전송 이력",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (deliveryLogs.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "아직 전송 이력이 없습니다. 설정 완료 후 알림이 쌓이면 주기적으로 자동 전송됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(deliveryLogs.take(10), key = { it.id }) { log ->
                DeliveryLogRow(log)
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    title: String,
    subtitle: String,
    isOk: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isOk) Color(0xFF16A34A) else Color(0xFFD97706),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!isOk) {
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun StatTile(
    modifier: Modifier,
    label: String,
    value: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeliveryLogRow(log: DeliveryLogEntity) {
    val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (log.success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = if (log.success) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${log.channel} · ${log.itemCount}건 (약 ${log.estimatedTokens} 토큰)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${fmt.format(Date(log.deliveredAt))} · ${log.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
