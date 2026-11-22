package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ResearchMode
import com.example.data.model.ResearchProject
import com.example.data.model.TeamRole
import com.example.data.sync.OfflineSyncTelemetry
import com.example.ui.components.TelemetryPill
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    projects: List<ResearchProject>,
    activeProjectId: String?,
    currentUid: String,
    userDisplayName: String,
    syncTelemetry: OfflineSyncTelemetry,
    onSelectProject: (ResearchProject) -> Unit,
    onNewProject: () -> Unit,
    onJoinProject: (String) -> Unit,
    onDeleteProject: (ResearchProject) -> Unit,
    onSignOut: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedModeFilter by remember { mutableStateOf<ResearchMode?>(null) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var inviteInput by remember { mutableStateOf("") }

    val filtered = remember(projects, searchQuery, selectedModeFilter) {
        projects.filter { project ->
            val modeMatches = selectedModeFilter == null ||
                ResearchMode.fromWire(project.mode) == selectedModeFilter
            val queryMatches = searchQuery.isBlank() ||
                project.title.contains(searchQuery, ignoreCase = true) ||
                project.rootFolderName.contains(searchQuery, ignoreCase = true) ||
                project.detectedDomain.contains(searchQuery, ignoreCase = true)
            modeMatches && queryMatches
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Research Library", style = MaterialTheme.typography.headlineSmall)
                    val cacheLabel = if (syncTelemetry.isOnline) {
                        "${projects.size} repos cached locally"
                    } else {
                        "Offline · ${projects.size} cached repos ready"
                    }
                    Text(
                        text = "$userDisplayName · $cacheLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (syncTelemetry.isOnline) MaterialTheme.colorScheme.onSurfaceVariant else BridgeAmber
                    )
                }
                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = stringResource(R.string.sign_out),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onNewProject,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("library_new_project_button")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Research")
                }
                OutlinedButton(
                    onClick = { showJoinDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("join_project_button")
                ) {
                    Icon(Icons.Outlined.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Join via Link")
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search projects, folders, or domains...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            val chatCount = projects.count { ResearchMode.fromWire(it.mode) == ResearchMode.CHAT }
            val projectCount = projects.count { ResearchMode.fromWire(it.mode) == ResearchMode.PROJECT }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedModeFilter == null,
                    onClick = { selectedModeFilter = null },
                    label = { Text("All (${projects.size})") },
                    modifier = Modifier.testTag("library_filter_all")
                )
                FilterChip(
                    selected = selectedModeFilter == ResearchMode.PROJECT,
                    onClick = { selectedModeFilter = ResearchMode.PROJECT },
                    label = { Text("Project Mode ($projectCount)") },
                    modifier = Modifier.testTag("library_filter_project")
                )
                FilterChip(
                    selected = selectedModeFilter == ResearchMode.CHAT,
                    onClick = { selectedModeFilter = ResearchMode.CHAT },
                    label = { Text("Chat Mode ($chatCount)") },
                    modifier = Modifier.testTag("library_filter_chat")
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No repositories found", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Start a new 7-stage research session or redeem a teammate's invite URL.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { project ->
                val isSelected = project.id == activeProjectId
                val role = project.roleFor(currentUid)
                val isOwner = project.ownerId == currentUid

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectProject(project) }
                        .testTag("library_project_card_${project.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${project.rootFolderName}/ · ${ResearchMode.fromWire(project.mode).label}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (isOwner) {
                                IconButton(onClick = { onDeleteProject(project) }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete project",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TelemetryPill(
                                text = role.label,
                                accentColor = if (role == TeamRole.ADMIN) BridgeAmber else MaterialTheme.colorScheme.primary
                            )
                            if (project.detectedDomain.isNotBlank()) {
                                TelemetryPill(project.detectedDomain, MaterialTheme.colorScheme.secondary)
                            }
                            if (project.latestPrdVersion.isNotBlank()) {
                                TelemetryPill("PRD ${project.latestPrdVersion}", VerifiedEmerald)
                            }
                            if (project.memberUids.size > 1) {
                                TelemetryPill("${project.memberUids.size} members")
                            }
                            if (project.id in syncTelemetry.dirtyProjectIds) {
                                TelemetryPill("Pending Sync", BridgeAmber)
                            } else {
                                TelemetryPill("Cached Offline", VerifiedEmerald)
                            }
                            if (project.githubRepoUrl.isNotBlank()) {
                                TelemetryPill("GitHub Linked", VerifiedEmerald)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.join_project_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste an invite link (vortexresearcher://join?code=...) or invite code shared by a project Admin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inviteInput,
                        onValueChange = { inviteInput = it },
                        label = { Text("Invite URL or code") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_invite_code_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inviteInput.isNotBlank()) {
                            onJoinProject(inviteInput)
                            inviteInput = ""
                            showJoinDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_join_project_button")
                ) { Text("Join Project") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) { Text("Cancel") }
            }
        )
    }
}
