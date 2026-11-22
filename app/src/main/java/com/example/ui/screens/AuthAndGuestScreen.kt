package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.PublicShareSnapshot
import com.example.data.model.RoundtableStatus
import com.example.ui.components.MonospaceCodeBlock
import com.example.ui.components.StagePipelineCard
import com.example.ui.components.TelemetryPill
import com.example.ui.components.copyToClipboard
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald

@Composable
fun AuthWelcomeScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onGoogleSignIn: () -> Unit,
    onOpenPublicShareCode: (String) -> Unit
) {
    var showGuestDialog by remember { mutableStateOf(false) }
    var shareCodeInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(104.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground_img),
                    contentDescription = stringResource(R.string.cd_vortex_logo),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureRow(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "7-Stage Grounded Engine & Strategic Router",
                        desc = "Google Search grounded synthesis, emulation layers, and surgical model escalation."
                    )
                    FeatureRow(
                        icon = Icons.Outlined.FolderOpen,
                        title = "Architect Repo Scaffolding & AI Roundtable",
                        desc = "4 professional blueprints, nested PRD/ index, universal code/PDF upload & 2-way relay."
                    )
                    FeatureRow(
                        icon = Icons.Outlined.CloudSync,
                        title = "Team RBAC, GitHub 1-Tap Mirror & SAF Sync",
                        desc = "Collaborate with Admin/Editor/Read-Only roles or share sign-in-free read-only repos."
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = onGoogleSignIn,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = stringResource(R.string.sign_in_with_google),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showGuestDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("guest_open_share_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.open_shared_repo_guest),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    if (showGuestDialog) {
        AlertDialog(
            onDismissRequest = { showGuestDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Open Shared Repo (Read-Only)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste a public share URL (vortexresearcher://share/...) or share code to view a repository with zero sign-in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = shareCodeInput,
                        onValueChange = { shareCodeInput = it },
                        label = { Text("Share link or code") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guest_share_code_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGuestDialog = false
                        onOpenPublicShareCode(shareCodeInput)
                    },
                    modifier = Modifier.testTag("confirm_open_guest_share")
                ) {
                    Text("Open Repo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuestDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GuestPublicShareViewerScreen(
    snapshot: PublicShareSnapshot,
    onClose: () -> Unit,
    onSignInPrompt: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val stages = remember(snapshot.stagesJson) { snapshot.parsedStages() }
    val files = remember(snapshot.filesJson) { snapshot.parsedFiles() }
    val prds = remember(snapshot.prdsJson) { snapshot.parsedPrds() }
    val roundtable = remember(snapshot.roundtableJson) { snapshot.parsedRoundtable() }
    var selectedPrdVersion by remember(snapshot.shareCode, prds) {
        mutableStateOf(prds.firstOrNull()?.version ?: snapshot.latestPrdVersion)
    }
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = snapshot.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = "Public Read-Only Snapshot · ${snapshot.rootFolderName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("guest_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSignInPrompt) {
                        Text("Sign In")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val prdCount = prds.size.coerceAtLeast(if (snapshot.latestPrdMarkdown.isNotBlank()) 1 else 0)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Research") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Files (${files.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("PRD ($prdCount)") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Roundtable (${roundtable.size})") })
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        item {
                            TelemetryPill(
                                text = "Read-Only Guest Access · ${snapshot.detectedDomain.ifBlank { "Research" }}"
                            )
                        }
                        if (snapshot.chatAnswer.isNotBlank()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "Synthesis Summary",
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = {
                                                copyToClipboard(context, snapshot.title, snapshot.chatAnswer)
                                            }) {
                                                Icon(
                                                    Icons.Outlined.ContentCopy,
                                                    contentDescription = stringResource(R.string.cd_copy_text),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        SelectionContainer {
                                            Text(snapshot.chatAnswer, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                        items(stages) { stage ->
                            StagePipelineCard(stage = stage)
                        }
                    }

                    1 -> {
                        if (files.isEmpty()) {
                            item {
                                Text(
                                    "This shared session was created in Chat Mode without repository files.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(files) { file ->
                                MonospaceCodeBlock(
                                    title = "${snapshot.rootFolderName}/${file.relativePath}",
                                    body = file.content,
                                    onCopy = { copyToClipboard(context, file.relativePath, file.content) }
                                )
                            }
                        }
                    }

                    2 -> {
                        if (snapshot.prdIndexMarkdown.isNotBlank()) {
                            item {
                                MonospaceCodeBlock(
                                    title = "PRD/PRD_INDEX",
                                    body = snapshot.prdIndexMarkdown,
                                    onCopy = { copyToClipboard(context, "PRD_INDEX", snapshot.prdIndexMarkdown) }
                                )
                            }
                        }
                        if (prds.isNotEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "PRD Version History (${prds.size})",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        prds.forEach { doc ->
                                            FilterChip(
                                                selected = doc.version == selectedPrdVersion,
                                                onClick = { selectedPrdVersion = doc.version },
                                                label = { Text(doc.version) },
                                                modifier = Modifier.testTag("guest_prd_version_${doc.version}")
                                            )
                                        }
                                    }
                                }
                            }
                            val chosen = prds.firstOrNull { it.version == selectedPrdVersion } ?: prds.first()
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        TelemetryPill(text = chosen.version, accentColor = VerifiedEmerald)
                                        if (chosen.modelUsed.isNotBlank()) {
                                            TelemetryPill(text = chosen.modelUsed)
                                        }
                                        if (chosen.authorName.isNotBlank()) {
                                            TelemetryPill(text = "By ${chosen.authorName}", accentColor = BridgeAmber)
                                        }
                                    }
                                    if (chosen.deltaSummary.isNotBlank()) {
                                        Text(
                                            text = "Delta: ${chosen.deltaSummary}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    MonospaceCodeBlock(
                                        title = "PRD ${chosen.version} · ${chosen.title.ifBlank { snapshot.title }}",
                                        body = chosen.markdownContent,
                                        onCopy = { copyToClipboard(context, "PRD ${chosen.version}", chosen.markdownContent) }
                                    )
                                }
                            }
                        } else if (snapshot.latestPrdMarkdown.isNotBlank()) {
                            item {
                                MonospaceCodeBlock(
                                    title = "Latest PRD (${snapshot.latestPrdVersion})",
                                    body = snapshot.latestPrdMarkdown,
                                    onCopy = { copyToClipboard(context, "PRD", snapshot.latestPrdMarkdown) }
                                )
                            }
                        } else {
                            item {
                                Text(
                                    "No PRD has been generated for this repository yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    3 -> {
                        if (roundtable.isEmpty()) {
                            item {
                                Text(
                                    "No A.i. Roundtable artifacts have been published in this snapshot yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val byFolder = roundtable.groupBy { it.modelFolder.ifBlank { "External" } }
                            byFolder.forEach { (folder, entries) ->
                                item {
                                    Text(
                                        text = "AI_Roundtable/$folder/ (${entries.size})",
                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                items(entries) { entry ->
                                    val status = RoundtableStatus.fromWire(entry.queueStatus)
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = entry.fileName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily)
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                TelemetryPill(text = entry.detectedLanguage)
                                                TelemetryPill(
                                                    text = status.label,
                                                    accentColor = when (status) {
                                                        RoundtableStatus.ADOPTED, RoundtableStatus.CONSENSUS_RESOLVED -> VerifiedEmerald
                                                        RoundtableStatus.REJECTED -> MaterialTheme.colorScheme.error
                                                        else -> BridgeAmber
                                                    }
                                                )
                                                if (entry.scrutinizedByModel.isNotBlank()) {
                                                    TelemetryPill(text = "Audited by ${entry.scrutinizedByModel}")
                                                }
                                            }
                                            MonospaceCodeBlock(
                                                title = "$folder/${entry.fileName}",
                                                body = entry.content,
                                                onCopy = { copyToClipboard(context, entry.fileName, entry.content) }
                                            )
                                            if (entry.scrutinyVerdict.isNotBlank()) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                                Text(
                                                    text = "Gemini Empirical Audit Verdict",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = VerifiedEmerald
                                                )
                                                SelectionContainer {
                                                    Text(
                                                        text = entry.scrutinyVerdict,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
