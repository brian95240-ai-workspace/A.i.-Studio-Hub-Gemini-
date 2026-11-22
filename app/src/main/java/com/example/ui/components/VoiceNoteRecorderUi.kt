package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.ai.AudioTranscriptionResult
import com.example.data.model.NoteCategory
import com.example.data.voice.RecordedAudioClip
import com.example.data.voice.VortexAudioRecorder
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TelemetryBlue
import com.example.ui.theme.VerifiedEmerald
import kotlinx.coroutines.delay
import java.util.Locale

data class VoiceNoteRecordingState(
    val isRecording: Boolean = false,
    val elapsedSec: Int = 0,
    val micLevel: Float = 0f
)

class VoiceNoteRecorderHandle(
    val state: VoiceNoteRecordingState,
    val startOrStop: () -> Unit,
    val stopAndTranscribe: () -> Unit,
    val cancel: () -> Unit
)

@Composable
fun rememberVoiceNoteRecorderHandle(
    onAudioCaptured: (RecordedAudioClip) -> Unit,
    onNotice: (String) -> Unit
): VoiceNoteRecorderHandle {
    val context = LocalContext.current
    val latestCapturedCb by rememberUpdatedState(onAudioCaptured)
    val latestNoticeCb by rememberUpdatedState(onNotice)

    val recorder = remember(context) { VortexAudioRecorder(context.applicationContext) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    var micLevel by remember { mutableFloatStateOf(0.12f) }

    fun beginRecording() {
        recorder.startRecording()
            .onSuccess {
                elapsedSec = 0
                micLevel = 0.14f
                isRecording = true
            }
            .onFailure { err ->
                isRecording = false
                latestNoticeCb(err.message ?: "Unable to start microphone recording.")
            }
    }

    fun finishAndEmit() {
        if (!isRecording) return
        isRecording = false
        recorder.stopAndCollect()
            .onSuccess { clip -> latestCapturedCb(clip) }
            .onFailure { err -> latestNoticeCb(err.message ?: "Recording could not be captured.") }
    }

    fun discard() {
        isRecording = false
        elapsedSec = 0
        micLevel = 0f
        recorder.cancelAndDiscard()
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginRecording()
        } else {
            latestNoticeCb("Microphone permission is required to record verbal research notes.")
        }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        while (isRecording) {
            micLevel = recorder.readNormalizedAmplitude()
            elapsedSec = recorder.elapsedSeconds()
            if (elapsedSec >= VortexAudioRecorder.MAX_RECORDING_SECONDS) {
                finishAndEmit()
                break
            }
            delay(120L)
        }
    }

    DisposableEffect(recorder) {
        onDispose { recorder.release() }
    }

    val startOrStop: () -> Unit = {
        if (isRecording) {
            finishAndEmit()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                beginRecording()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    return VoiceNoteRecorderHandle(
        state = VoiceNoteRecordingState(
            isRecording = isRecording,
            elapsedSec = elapsedSec,
            micLevel = micLevel
        ),
        startOrStop = startOrStop,
        stopAndTranscribe = { finishAndEmit() },
        cancel = { discard() }
    )
}

fun formatRecordingClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

@Composable
fun VoiceNoteRecordButton(
    isRecording: Boolean,
    isTranscribing: Boolean,
    elapsedSec: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent by animateColorAsState(
        targetValue = when {
            isRecording -> MaterialTheme.colorScheme.error
            isTranscribing -> VerifiedEmerald
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(200),
        label = "voice_note_btn_accent"
    )
    val pulseTransition = rememberInfiniteTransition(label = "voice_note_rec_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice_note_rec_alpha"
    )

    val cd = when {
        isRecording -> stringResource(R.string.cd_voice_note_stop_transcribe)
        isTranscribing -> stringResource(R.string.cd_voice_note_transcribing)
        else -> stringResource(R.string.cd_voice_note_record)
    }

    Surface(
        onClick = onClick,
        enabled = enabled && !isTranscribing,
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = if (isRecording) 0.18f else 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (isRecording) 0.78f else 0.48f)),
        modifier = modifier
            .heightIn(min = 52.dp)
            .semantics { contentDescription = cd }
            .testTag("voice_note_record_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when {
                isTranscribing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accent
                    )
                    Text(
                        text = stringResource(R.string.voice_note_transcribing_short),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = accent,
                        maxLines = 1
                    )
                }
                isRecording -> {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(pulseAlpha)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = formatRecordingClock(elapsedSec),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = accent,
                        maxLines = 1
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.voice_note_button_label),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = accent,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceNoteRecordingPanel(
    state: VoiceNoteRecordingState,
    autoRunPipeline: Boolean,
    saveToProjectRepo: Boolean,
    hasActiveProject: Boolean,
    onToggleAutoRun: (Boolean) -> Unit,
    onToggleSaveToRepo: (Boolean) -> Unit,
    onStopAndTranscribe: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.isRecording,
        enter = expandVertically(tween(200)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
        modifier = modifier
    ) {
        val accent = MaterialTheme.colorScheme.error
        Surface(
            color = accent.copy(alpha = 0.09f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_note_recording_panel")
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VoiceNoteWaveformBars(level = state.micLevel, color = accent)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.voice_note_recording_title),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatRecordingClock(state.elapsedSec)} / ${formatRecordingClock(VortexAudioRecorder.MAX_RECORDING_SECONDS)} · Firebase AI Gemini 3.8 Flash",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("voice_note_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_voice_note_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = autoRunPipeline,
                        onClick = { onToggleAutoRun(!autoRunPipeline) },
                        leadingIcon = {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Auto-Run 7-Stage Pipeline") },
                        modifier = Modifier.testTag("voice_note_autorun_chip")
                    )
                    if (hasActiveProject) {
                        FilterChip(
                            selected = saveToProjectRepo,
                            onClick = { onToggleSaveToRepo(!saveToProjectRepo) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Save to research/voice_notes/") },
                            modifier = Modifier.testTag("voice_note_save_repo_chip")
                        )
                    }
                }

                Button(
                    onClick = onStopAndTranscribe,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VerifiedEmerald,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("voice_note_transcribe_now_button")
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (autoRunPipeline) {
                            stringResource(R.string.voice_note_stop_transcribe_run)
                        } else {
                            stringResource(R.string.voice_note_stop_transcribe)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceNoteTranscribingBanner(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(180)) + fadeIn(tween(160)),
        exit = shrinkVertically(tween(180)) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_note_transcribing_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.voice_note_transcribing_title),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.voice_note_transcribing_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun noteCategoryAccent(category: NoteCategory): androidx.compose.ui.graphics.Color = when (category) {
    NoteCategory.RESEARCH_QUERY -> ElectricCyan
    NoteCategory.ACTION_ITEM -> BridgeAmber
    NoteCategory.DRAFT -> TelemetryBlue
    NoteCategory.ARCHITECTURE_NOTE -> VerifiedEmerald
    NoteCategory.DECISION -> VerifiedEmerald
    NoteCategory.FOLLOW_UP -> MaterialTheme.colorScheme.secondary
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceNoteTranscriptReviewCard(
    result: AudioTranscriptionResult?,
    usingRefinedBrief: Boolean,
    hasActiveProject: Boolean,
    canEdit: Boolean,
    onSelectVariant: (useRefinedBrief: Boolean) -> Unit,
    onChangeCategory: (NoteCategory) -> Unit = {},
    onSaveToRepo: () -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = result != null,
        enter = expandVertically(tween(200)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(180)) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        if (result == null) return@AnimatedVisibility
        val categoryAccent = noteCategoryAccent(result.category)
        Surface(
            color = VerifiedEmerald.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_note_review_card")
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = VerifiedEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = result.suggestedTitle.ifBlank { "Transcribed Verbal Note" },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Firebase AI · ${result.modelUsed} · Auto-Categorized",
                            style = MaterialTheme.typography.labelSmall,
                            color = VerifiedEmerald,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TelemetryPill(
                        text = result.category.label,
                        accentColor = categoryAccent
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("voice_note_dismiss_review_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 1-Tap Category Override Strip (populated by Firebase AI ingestion, editable anytime)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.voice_note_category_label),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NoteCategory.entries.forEach { cat ->
                            val selected = cat == result.category
                            FilterChip(
                                selected = selected,
                                onClick = { onChangeCategory(cat) },
                                label = {
                                    Text(
                                        text = cat.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("voice_note_category_${cat.wire.lowercase()}")
                            )
                        }
                    }
                }

                // Auto-Generated Metadata Tags & Key Ideas
                if (result.tags.isNotEmpty() || result.keyIdeas.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        result.tags.take(5).forEach { tag ->
                            TelemetryPill(
                                text = "#$tag",
                                accentColor = categoryAccent
                            )
                        }
                        result.keyIdeas.take(3).forEach { idea ->
                            TelemetryPill(
                                text = idea.take(36),
                                accentColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = usingRefinedBrief,
                        onClick = { onSelectVariant(true) },
                        label = { Text("Synthesis Brief") },
                        modifier = Modifier.testTag("voice_note_variant_brief")
                    )
                    FilterChip(
                        selected = !usingRefinedBrief,
                        onClick = { onSelectVariant(false) },
                        label = { Text("Verbatim") },
                        modifier = Modifier.testTag("voice_note_variant_verbatim")
                    )
                    if (hasActiveProject && canEdit) {
                        OutlinedButton(
                            onClick = onSaveToRepo,
                            modifier = Modifier.testTag("voice_note_save_to_repo_button")
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Note")
                        }
                    }
                    TextButton(
                        onClick = onUndo,
                        modifier = Modifier.testTag("voice_note_undo_button")
                    ) {
                        Text("Undo", color = BridgeAmber)
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceNoteErrorBanner(
    errorMessage: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !errorMessage.isNullOrBlank(),
        enter = expandVertically(tween(180)) + fadeIn(tween(150)),
        exit = shrinkVertically(tween(180)) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        if (errorMessage.isNullOrBlank()) return@AnimatedVisibility
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_note_error_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceNoteWaveformBars(
    level: Float,
    color: androidx.compose.ui.graphics.Color
) {
    val weights = listOf(0.38f, 0.72f, 1.0f, 0.85f, 0.95f, 0.65f, 0.42f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        modifier = Modifier.height(24.dp)
    ) {
        weights.forEachIndexed { idx, factor ->
            val targetH = (5f + 19f * (level.coerceIn(0.08f, 1f) * factor)).coerceIn(4f, 24f).dp
            val barHeight by animateDpAsState(
                targetValue = targetH,
                animationSpec = tween(durationMillis = 95),
                label = "voice_note_bar_$idx"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}
