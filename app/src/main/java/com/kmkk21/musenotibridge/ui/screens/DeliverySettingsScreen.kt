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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kmkk21.musenotibridge.data.prefs.BridgeSettings
import com.kmkk21.musenotibridge.data.prefs.SmtpPreset
import com.kmkk21.musenotibridge.ui.BridgeViewModel

private val INTERVAL_OPTIONS = listOf(30, 60, 120, 180, 360, 720)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeliverySettingsScreen(
    settings: BridgeSettings,
    isBusy: Boolean,
    onChangeInterval: (Int) -> Unit,
    onToggleKeepAlive: (Boolean) -> Unit,
    onSaveDelivery: (
        emailEnabled: Boolean,
        smtpPreset: SmtpPreset,
        smtpHost: String,
        smtpPort: Int,
        smtpUseSsl: Boolean,
        smtpUsername: String,
        smtpPassword: String,
        recipientEmail: String,
        webhookEnabled: Boolean,
        webhookUrl: String,
        webhookAuthHeader: String,
        museInstruction: String
    ) -> Unit,
    onTestDelivery: (BridgeSettings) -> Unit
) {
    var emailEnabled by remember { mutableStateOf(settings.emailEnabled) }
    var smtpPreset by remember { mutableStateOf(settings.smtpPreset) }
    var smtpHost by remember { mutableStateOf(settings.smtpHost) }
    var smtpPort by remember { mutableStateOf(settings.smtpPort.toString()) }
    var smtpUseSsl by remember { mutableStateOf(settings.smtpUseSsl) }
    var smtpUsername by remember { mutableStateOf(settings.smtpUsername) }
    var smtpPassword by remember { mutableStateOf(settings.smtpPassword) }
    var showPassword by remember { mutableStateOf(false) }
    var recipientEmail by remember { mutableStateOf(settings.recipientEmail) }

    var webhookEnabled by remember { mutableStateOf(settings.webhookEnabled) }
    var webhookUrl by remember { mutableStateOf(settings.webhookUrl) }
    var webhookAuthHeader by remember { mutableStateOf(settings.webhookAuthHeader) }
    var museInstruction by remember { mutableStateOf(settings.museInstruction) }

    var showGuideDialog by remember { mutableStateOf(false) }

    // Sync initial persisted settings once loaded
    LaunchedEffect(
        settings.smtpUsername,
        settings.recipientEmail,
        settings.webhookUrl,
        settings.museInstruction
    ) {
        emailEnabled = settings.emailEnabled
        smtpPreset = settings.smtpPreset
        smtpHost = settings.smtpHost
        smtpPort = settings.smtpPort.toString()
        smtpUseSsl = settings.smtpUseSsl
        smtpUsername = settings.smtpUsername
        smtpPassword = settings.smtpPassword
        recipientEmail = settings.recipientEmail
        webhookEnabled = settings.webhookEnabled
        webhookUrl = settings.webhookUrl
        webhookAuthHeader = settings.webhookAuthHeader
        museInstruction = settings.museInstruction
    }

    fun buildCandidate(): BridgeSettings = settings.copy(
        emailEnabled = emailEnabled,
        smtpPreset = smtpPreset,
        smtpHost = smtpHost.trim(),
        smtpPort = smtpPort.toIntOrNull() ?: 465,
        smtpUseSsl = smtpUseSsl,
        smtpUsername = smtpUsername.trim(),
        smtpPassword = smtpPassword.trim(),
        recipientEmail = recipientEmail.trim(),
        webhookEnabled = webhookEnabled,
        webhookUrl = webhookUrl.trim(),
        webhookAuthHeader = webhookAuthHeader.trim(),
        museInstruction = museInstruction.trim()
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Batch Interval & Keep-Alive
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "배치 전송 주기 (토큰 절약 핵심)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "알림을 하나씩 보내지 않고 주기마다 한 번에 묶어 전송합니다. 오프라인이면 인터넷 연결 시 자동 재전송됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        INTERVAL_OPTIONS.forEach { mins ->
                            FilterChip(
                                selected = settings.batchIntervalMinutes == mins,
                                onClick = { onChangeInterval(mins) },
                                label = { Text(BridgeViewModel.formatInterval(mins)) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "상단 상주 알림으로 연결 보호 (추천)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "갤럭시 One UI 초절전에서도 알림 수집이 끊기지 않게 유지합니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.keepAliveEnabled,
                            onCheckedChange = onToggleKeepAlive
                        )
                    }
                }
            }
        }

        // 2. Muse Mail / Email (SMTP) Channel
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "채널 1: Muse Mail / 이메일 전송 (추천)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Muse 전용 이메일(@muse.ai) 또는 Muse가 읽는 메일함으로 바로 전송",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = emailEnabled,
                            onCheckedChange = { emailEnabled = it }
                        )
                    }

                    if (emailEnabled) {
                        TextButton(
                            onClick = { showGuideDialog = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("30초 설정 가이드 보기 (Muse Mail · Gmail 앱 비밀번호)")
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmtpPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = smtpPreset == preset,
                                    onClick = {
                                        smtpPreset = preset
                                        if (preset != SmtpPreset.CUSTOM) {
                                            smtpHost = preset.host
                                            smtpPort = preset.port.toString()
                                            smtpUseSsl = preset.useSsl
                                        }
                                    },
                                    label = { Text(preset.displayName, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = recipientEmail,
                            onValueChange = { recipientEmail = it },
                            label = { Text("받는 주소 (Muse Mail: 예) myagent@muse.ai)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = smtpUsername,
                            onValueChange = { smtpUsername = it },
                            label = { Text("보내는 내 이메일 (Muse에 인증된 주소 권장)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = smtpPassword,
                            onValueChange = { smtpPassword = it },
                            label = { Text("이메일 앱 비밀번호 (16자리)") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "비밀번호 표시 전환"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (smtpPreset == SmtpPreset.CUSTOM) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = smtpHost,
                                    onValueChange = { smtpHost = it },
                                    label = { Text("SMTP 호스트") },
                                    singleLine = true,
                                    modifier = Modifier.weight(2f)
                                )
                                OutlinedTextField(
                                    value = smtpPort,
                                    onValueChange = { smtpPort = it },
                                    label = { Text("포트") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Webhook Channel
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "채널 2: Webhook 전송 (선택)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Muse Custom Connector, n8n, Make, Google Apps Script URL로 JSON 전송",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = webhookEnabled,
                            onCheckedChange = { webhookEnabled = it }
                        )
                    }

                    if (webhookEnabled) {
                        OutlinedTextField(
                            value = webhookUrl,
                            onValueChange = { webhookUrl = it },
                            label = { Text("Webhook POST URL") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = webhookAuthHeader,
                            onValueChange = { webhookAuthHeader = it },
                            label = { Text("인증 헤더 (선택 · 예: Bearer xxx 또는 X-Key: xxx)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 4. Muse Instruction Prompt
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Muse 분석 지시사항 (메시지 상단 포함)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { museInstruction = BridgeSettings.DEFAULT_MUSE_INSTRUCTION }) {
                            Text("기본값 복원")
                        }
                    }
                    OutlinedTextField(
                        value = museInstruction,
                        onValueChange = { museInstruction = it },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 5. Save & Test Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onSaveDelivery(
                            emailEnabled,
                            smtpPreset,
                            smtpHost,
                            smtpPort.toIntOrNull() ?: 465,
                            smtpUseSsl,
                            smtpUsername,
                            smtpPassword,
                            recipientEmail,
                            webhookEnabled,
                            webhookUrl,
                            webhookAuthHeader,
                            museInstruction
                        )
                    },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("설정 저장")
                }

                OutlinedButton(
                    onClick = { onTestDelivery(buildCandidate()) },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("테스트 전송")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showGuideDialog) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            title = { Text("Muse Mail 연동 30초 가이드") },
            text = {
                Text(
                    text = """
                        1. Muse 앱에서 내 에이전트 전용 주소(@muse.ai)를 확인하고, 내 Gmail/네이버 주소를 '인증된 외부 이메일'로 등록합니다.
                        (만약 @muse.ai가 없다면, Muse에 연결된 본인 Gmail 주소를 받는 주소에 넣어도 됩니다!)

                        2. Gmail 앱 비밀번호 발급:
                        Google 계정 관리 → 보안 → 2단계 인증 켜기 → '앱 비밀번호' 검색 → 16자리 비밀번호 생성 후 위 칸에 붙여넣기.

                        3. 기존 Muse 앱에서 '핸드폰 알림 직접 읽기' 예약 작업을 꺼두시면 오프라인 오류가 완전히 사라집니다!
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { showGuideDialog = false }) {
                    Text("확인")
                }
            }
        )
    }
}
