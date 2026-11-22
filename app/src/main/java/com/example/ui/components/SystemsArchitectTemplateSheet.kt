package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ExportFileFormat
import com.example.data.model.ScaffoldingBlueprint
import com.example.data.sync.WorkspaceTemplateEngine
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TelemetryBlue
import com.example.ui.theme.VerifiedEmerald

private val DefaultDomainPresets = listOf(
    "Systems Architecture",
    "Software Engineering & Code",
    "Product Strategy & Market",
    "Biomedical & Hard Science",
    "Mathematics & Formal Logic"
)

private val OptionalExtraFolderPresets = listOf(
    "tests" to "Verification & benchmarks",
    "infra" to "IaC & CI/CD manifests",
    "diagrams" to "C4 & Mermaid diagrams",
    "scripts" to "Automation & tooling"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SystemsArchitectTemplateSheet(
    initializing: Boolean,
    onDismiss: () -> Unit,
    onInitialize: (
        title: String,
        rootFolderName: String,
        blueprint: ScaffoldingBlueprint,
        fileFormat: ExportFileFormat,
        domain: String,
        extraFolders: List<String>,
        openAfterCreate: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var projectTitle by remember { mutableStateOf("") }
    var rootFolderInput by remember { mutableStateOf("") }
    var userEditedRootFolder by remember { mutableStateOf(false) }
    var selectedDomain by remember { mutableStateOf("Systems Architecture") }
    var selectedBlueprint by remember { mutableStateOf(ScaffoldingBlueprint.SYSTEM_ARCHITECTURE_CANONICAL) }
    var selectedFormat by remember { mutableStateOf(ExportFileFormat.MD) }
    var selectedExtras by remember { mutableStateOf<Set<String>>(emptySet()) }
    var openAfterCreate by remember { mutableStateOf(true) }
    var showTitleError by remember { mutableStateOf(false) }

    val effectiveSlug = remember(projectTitle, rootFolderInput, userEditedRootFolder) {
        if (userEditedRootFolder && rootFolderInput.isNotBlank()) {
            WorkspaceTemplateEngine.sanitizeRootFolderSlug(rootFolderInput)
        } else if (projectTitle.isNotBlank()) {
            WorkspaceTemplateEngine.sanitizeRootFolderSlug(projectTitle)
        } else {
            "systems-architecture-project"
        }
    }

    val previewFiles = remember(selectedBlueprint, selectedFormat, selectedExtras) {
        WorkspaceTemplateEngine.previewInitialTree(
            blueprint = selectedBlueprint,
            fileFormat = selectedFormat,
            extraFolders = selectedExtras.sorted()
        )
    }

    ModalBottomSheet(
        onDismissRequest = { if (!initializing) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.testTag("systems_architect_template_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    color = ElectricCyan.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountTree,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.template_init_sheet_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.template_init_sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    enabled = !initializing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("template_init_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_close)
                    )
                }
            }

            // 1. Project Name & Root Folder Slug
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = projectTitle,
                    onValueChange = { next ->
                        projectTitle = next
                        showTitleError = false
                        if (!userEditedRootFolder) {
                            rootFolderInput = if (next.isBlank()) "" else WorkspaceTemplateEngine.sanitizeRootFolderSlug(next)
                        }
                    },
                    label = { Text("Project Name") },
                    placeholder = { Text("e.g., Event-Driven Payments Core") },
                    singleLine = true,
                    isError = showTitleError,
                    supportingText = if (showTitleError) {
                        { Text("Enter a project name to initialize the workspace.") }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("template_init_project_title")
                )

                OutlinedTextField(
                    value = rootFolderInput,
                    onValueChange = { next ->
                        userEditedRootFolder = next.isNotBlank()
                        rootFolderInput = next
                    },
                    label = { Text("Workspace Root Folder") },
                    placeholder = { Text(effectiveSlug) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.FolderSpecial,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                    },
                    suffix = {
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = ElectricCyan
                        )
                    },
                    supportingText = {
                        Text(
                            text = "Mirrored path: $effectiveSlug/",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("template_init_root_folder")
                )
            }

            // 2. Domain Focus & Default File Format
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Architecture Domain",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DefaultDomainPresets.forEach { domain ->
                        val selected = selectedDomain == domain
                        FilterChip(
                            selected = selected,
                            onClick = { selectedDomain = domain },
                            label = { Text(domain, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Document Format:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ExportFileFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = selectedFormat == fmt,
                            onClick = { selectedFormat = fmt },
                            label = { Text(fmt.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.16f),
                                selectedLabelColor = ElectricCyan
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("template_init_format_${fmt.wire.lowercase()}")
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

            // 3. Standardized 'Systems Architect' Folder Templates
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Systems Architect Folder Template",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TelemetryPill("Standardized", VerifiedEmerald)
                }

                ScaffoldingBlueprint.entries.forEach { blueprint ->
                    val isSelected = selectedBlueprint == blueprint
                    val isRecommended = blueprint == ScaffoldingBlueprint.SYSTEM_ARCHITECTURE_CANONICAL
                    TemplateBlueprintOptionCard(
                        blueprint = blueprint,
                        selected = isSelected,
                        recommended = isRecommended,
                        onSelect = { selectedBlueprint = blueprint }
                    )
                }
            }

            // 4. Optional Extra Architect Folders
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Optional Additional Directories",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OptionalExtraFolderPresets.forEach { (folder, _) ->
                        val active = folder in selectedExtras
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedExtras = if (active) selectedExtras - folder else selectedExtras + folder
                            },
                            label = {
                                Text(
                                    text = "/$folder",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TelemetryBlue.copy(alpha = 0.18f),
                                selectedLabelColor = TelemetryBlue
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("template_init_extra_$folder")
                        )
                    }
                }
            }

            // 5. Live Directory & File Tree Preview
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("template_init_tree_preview")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "$effectiveSlug/",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = ElectricCyan
                            )
                        }
                        TelemetryPill("${previewFiles.size} starter files", TelemetryBlue)
                    }
                    Spacer(Modifier.height(2.dp))
                    previewFiles.forEach { relPath ->
                        val isNested = relPath.contains('/')
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(start = if (isNested) 10.dp else 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = if (relPath.endsWith(".json")) VerifiedEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = relPath,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 6. Open immediately toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Checkbox) { openAfterCreate = !openAfterCreate }
                    .padding(vertical = 4.dp)
                    .testTag("template_init_open_after_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = openAfterCreate,
                    onCheckedChange = { openAfterCreate = it },
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
                Text(
                    text = stringResource(R.string.template_init_open_after),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 7. Primary Initialize Action Button
            Button(
                onClick = {
                    val trimmed = projectTitle.trim()
                    if (trimmed.isBlank()) {
                        showTitleError = true
                    } else {
                        onInitialize(
                            trimmed,
                            effectiveSlug,
                            selectedBlueprint,
                            selectedFormat,
                            selectedDomain,
                            selectedExtras.sorted(),
                            openAfterCreate
                        )
                    }
                },
                enabled = !initializing,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("template_init_confirm_button")
            ) {
                if (initializing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.template_init_creating_cta))
                } else {
                    Icon(
                        imageVector = Icons.Outlined.CreateNewFolder,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.template_init_create_cta),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateBlueprintOptionCard(
    blueprint: ScaffoldingBlueprint,
    selected: Boolean,
    recommended: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (selected) ElectricCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .testTag("template_blueprint_${blueprint.wire.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (selected) "Selected template" else "Select template",
                    tint = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = blueprint.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (recommended) {
                    TelemetryPill("Standard", ElectricCyan)
                }
            }

            Text(
                text = blueprint.plainEnglishSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                blueprint.canonicalFolders.forEach { folder ->
                    Surface(
                        color = if (selected) ElectricCyan.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (selected) ElectricCyan.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "/$folder",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp
                            ),
                            color = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
