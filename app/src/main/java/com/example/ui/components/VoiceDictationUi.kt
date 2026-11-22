package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.ResearchMode
import com.example.data.voice.DictationTarget
import com.example.data.voice.DictationUiState
import com.example.data.voice.VortexSpeechController
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald

class VoiceDictationHandle(
    val state: DictationUiState,
    val startOrToggle: (DictationTarget) -> Unit,
    val stopAndKeepPartial: () -> Unit,
    val cancel: () -> Unit
)

@Composable
fun rememberVoiceDictationHandle(
    mode: ResearchMode,
    onTranscriptReady: (DictationTarget, String) -> Unit,
    onNotice: (String) -> Unit
): VoiceDictationHandle {
    val context = LocalContext.current
    val latestTranscriptCb by rememberUpdatedState(onTranscriptReady)
    val latestNoticeCb by rememberUpdatedState(onNotice)
    val latestMode by rememberUpdatedState(mode)

    var uiState by remember { mutableStateOf(DictationUiState()) }
    var pendingPermissionTarget by remember { mutableStateOf<DictationTarget?>(null) }
    var pendingIntentTarget by remember { mutableStateOf<DictationTarget?>(null) }

    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val target = pendingIntentTarget
        pendingIntentTarget = null
        if (result.resultCode == Activity.RESULT_OK && target != null) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) {
                latestTranscriptCb(target, spoken)
            } else {
                latestNoticeCb("No speech was transcribed — tap the mic to try again.")
            }
        }
    }

    val controller = remember(context) {
        VortexSpeechController(
            appContext = context.applicationContext,
            onState = { uiState = it },
            onFinalTranscript = { target, spoken -> latestTranscriptCb(target, spoken) },
            onFallbackIntentNeeded = { target ->
                val prompt = when {
                    target == DictationTarget.SEARCH -> "Speak to search projects, chats, or tags"
                    target == DictationTarget.PROJECT_FOLDER -> "Speak your project folder name"
                    target == DictationTarget.FOLLOW_UP -> "Speak your follow-up research question"
                    latestMode == ResearchMode.CHAT -> "Speak your research question"
                    else -> "Speak your project research brief"
                }
                pendingIntentTarget = target
                val speechIntent = IntentLauncherHelper.buildPromptIntent(prompt)
                runCatching { systemSpeechLauncher.launch(speechIntent) }.onFailure {
                    pendingIntentTarget = null
                    latestNoticeCb("Speech recognition service is unavailable on this device.")
                }
            },
            onWarning = { msg -> latestNoticeCb(msg) }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = pendingPermissionTarget
        pendingPermissionTarget = null
        if (target == null) return@rememberLauncherForActivityResult
        if (granted && controller.isDirectRecognizerReady()) {
            controller.startInline(target)
        } else {
            // Fall back cleanly to zero-permission Android system RecognizerIntent
            val prompt = when {
                target == DictationTarget.SEARCH -> "Speak to search projects, chats, or tags"
                target == DictationTarget.PROJECT_FOLDER -> "Speak your project folder name"
                target == DictationTarget.FOLLOW_UP -> "Speak your follow-up research question"
                latestMode == ResearchMode.CHAT -> "Speak your research question"
                else -> "Speak your project research brief"
            }
            pendingIntentTarget = target
            runCatching { systemSpeechLauncher.launch(controller.buildSystemSpeechIntent(prompt)) }
                .onFailure {
                    pendingIntentTarget = null
                    latestNoticeCb("Microphone permission is required for inline voice-to-text dictation.")
                }
        }
    }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    val toggleTarget: (DictationTarget) -> Unit = { target ->
        if (uiState.activeTarget == target) {
            controller.stopInline(commitPartial = true)
        } else {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasMicPermission && controller.isDirectRecognizerReady()) {
                controller.startInline(target)
            } else if (!hasMicPermission) {
                pendingPermissionTarget = target
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                val prompt = when {
                    target == DictationTarget.SEARCH -> "Speak to search projects, chats, or tags"
                    target == DictationTarget.PROJECT_FOLDER -> "Speak your project folder name"
                    target == DictationTarget.FOLLOW_UP -> "Speak your follow-up research question"
                    latestMode == ResearchMode.CHAT -> "Speak your research question"
                    else -> "Speak your project research brief"
                }
                pendingIntentTarget = target
                runCatching { systemSpeechLauncher.launch(controller.buildSystemSpeechIntent(prompt)) }
                    .onFailure {
                        pendingIntentTarget = null
                        latestNoticeCb("Speech recognition is not installed on this device.")
                    }
            }
        }
    }

    return VoiceDictationHandle(
        state = uiState,
        startOrToggle = toggleTarget,
        stopAndKeepPartial = { controller.stopInline(commitPartial = true) },
        cancel = { controller.stopInline(commitPartial = false) }
    )
}

private object IntentLauncherHelper {
    fun buildPromptIntent(prompt: String): android.content.Intent =
        android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
}

@Composable
fun VoiceMicToggleButton(
    isListening: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent by animateColorAsState(
        targetValue = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        animationSpec = tween(220),
        label = "voice_mic_accent"
    )
    val pulseTransition = rememberInfiniteTransition(label = "voice_mic_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice_mic_pulse_alpha"
    )

    val cd = if (isListening) stringResource(R.string.cd_voice_stop) else stringResource(R.string.cd_voice_dictate)

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = if (isListening) 0.18f else 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (isListening) 0.75f else 0.45f)),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = cd }
            .testTag("voice_dictate_query_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isListening) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(pulseAlpha)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
            Icon(
                imageVector = if (isListening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (isListening) stringResource(R.string.voice_stop_listening) else stringResource(R.string.voice_dictate_query),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
                maxLines = 1
            )
        }
    }
}

@Composable
fun VoiceListeningPanel(
    state: DictationUiState,
    mode: ResearchMode,
    onDone: () -> Unit,
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
        val level = state.micLevel.coerceIn(0.08f, 1f)
        val prompt = when {
            state.activeTarget == DictationTarget.SEARCH -> stringResource(R.string.voice_listening_prompt_search)
            state.activeTarget == DictationTarget.PROJECT_FOLDER -> "Listening for project folder name…"
            mode == ResearchMode.CHAT -> stringResource(R.string.voice_listening_prompt_chat)
            else -> stringResource(R.string.voice_listening_prompt_project)
        }

        Surface(
            color = accent.copy(alpha = 0.09f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.48f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_listening_panel")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoiceLevelMeter(level = level, color = accent)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.statusNote ?: prompt,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.liveDraft.isNotBlank()) {
                            Text(
                                text = "\"${state.liveDraft}\"",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDone,
                        modifier = Modifier.testTag("voice_done_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Insert transcribed text",
                            tint = VerifiedEmerald
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("voice_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cancel voice dictation",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceLevelMeter(
    level: Float,
    color: androidx.compose.ui.graphics.Color
) {
    val multipliers = listOf(0.45f, 0.85f, 1.0f, 0.72f, 0.40f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        modifier = Modifier.height(20.dp)
    ) {
        multipliers.forEachIndexed { idx, factor ->
            val targetHeight = (5f + 15f * (level * factor)).coerceIn(4f, 20f).dp
            val barHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = tween(durationMillis = 90),
                label = "voice_bar_$idx"
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

@Composable
fun VoiceTranscriptReviewBanner(
    visible: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(180)) + fadeIn(tween(150)),
        exit = shrinkVertically(tween(180)) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        Surface(
            color = VerifiedEmerald.copy(alpha = 0.11f),
            border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.42f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_transcript_review_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = VerifiedEmerald,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.voice_transcribed_editable_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onUndo,
                    modifier = Modifier.testTag("voice_undo_transcript_button")
                ) {
                    Text(
                        text = stringResource(R.string.voice_undo_dictation),
                        color = VerifiedEmerald
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("voice_dismiss_review_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
