package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.export.ExportFormatCategory
import com.example.data.export.PdfReportOptions
import com.example.data.export.UniversalExportFormat
import com.example.data.export.UniversalFormatCatalog
import com.example.data.model.ProjectFile
import com.example.data.model.ResearchProject
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectExportSheet(
    project: ResearchProject,
    files: List<ProjectFile>,
    prdCount: Int,
    roundtableCount: Int,
    exporting: Boolean,
    onDismiss: () -> Unit,
    onExportProjectToUri: (Uri, UniversalExportFormat, PdfReportOptions) -> Unit,
    onShareProjectExport: (UniversalExportFormat, PdfReportOptions) -> Unit,
    onExportSingleFileToUri: (Uri, ProjectFile) -> Unit,
    onShareSingleFile: (ProjectFile) -> Unit
) {
    var pdfOptions by remember { mutableStateOf(PdfReportOptions()) }
    var selectedCategory by remember { mutableStateOf(ExportFormatCategory.ALL) }
    var formatSearch by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf(UniversalFormatCatalog.allFormats.first()) }
    var pendingSaveFormat by remember { mutableStateOf<UniversalExportFormat?>(null) }
    var pendingSingleFile by remember { mutableStateOf<ProjectFile?>(null) }

    val pdfFormat = remember { UniversalFormatCatalog.findByExtension("pdf")!! }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            onExportProjectToUri(uri, pdfFormat, pdfOptions)
        }
    }

    val createGenericLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val fmt = pendingSaveFormat
        val singleFile = pendingSingleFile
        pendingSaveFormat = null
        pendingSingleFile = null
        if (uri != null) {
            when {
                singleFile != null -> onExportSingleFileToUri(uri, singleFile)
                fmt != null -> onExportProjectToUri(uri, fmt, pdfOptions)
            }
        }
    }

    val filteredFormats = remember(selectedCategory, formatSearch) {
        val q = formatSearch.trim().lowercase()
        UniversalFormatCatalog.allFormats.filter { fmt ->
            val catOk = selectedCategory == ExportFormatCategory.ALL || fmt.category == selectedCategory
            val qOk = q.isEmpty() ||
                fmt.extension.contains(q, ignoreCase = true) ||
                fmt.label.contains(q, ignoreCase = true)
            catOk && qOk
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.export_studio_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "${project.rootFolderName}/ · ${files.size} files · ${UniversalFormatCatalog.allFormats.size} export formats ready",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 1. Stakeholder Formatted PDF Report Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Stakeholder PDF Dossier",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryPill(text = "US Letter · Multi-Page", accentColor = VerifiedEmerald)
                    }
                    Text(
                        text = "Generates a professionally typeset PDF report with executive cover banner, KPI summary, full directory tree, consolidated research notes, PRDs ($prdCount), and Roundtable findings ($roundtableCount).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = pdfOptions.includeProjectTree,
                            onClick = { pdfOptions = pdfOptions.copy(includeProjectTree = !pdfOptions.includeProjectTree) },
                            label = { Text("Folder Tree") }
                        )
                        FilterChip(
                            selected = pdfOptions.includeResearchStages,
                            onClick = { pdfOptions = pdfOptions.copy(includeResearchStages = !pdfOptions.includeResearchStages) },
                            label = { Text("Research Notes") }
                        )
                        FilterChip(
                            selected = pdfOptions.includePrds,
                            onClick = { pdfOptions = pdfOptions.copy(includePrds = !pdfOptions.includePrds) },
                            label = { Text("PRDs ($prdCount)") }
                        )
                        FilterChip(
                            selected = pdfOptions.includeRoundtable,
                            onClick = { pdfOptions = pdfOptions.copy(includeRoundtable = !pdfOptions.includeRoundtable) },
                            label = { Text("Roundtable ($roundtableCount)") }
                        )
                        FilterChip(
                            selected = pdfOptions.includeFileAppendix,
                            onClick = { pdfOptions = pdfOptions.copy(includeFileAppendix = !pdfOptions.includeFileAppendix) },
                            label = { Text("Files Appendix") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val fileName = UniversalFormatCatalog.suggestedFileName(project, pdfFormat)
                                createPdfLauncher.launch(fileName)
                            },
                            enabled = !exporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_save_pdf_button")
                        ) {
                            if (exporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.export_save_pdf))
                            }
                        }
                        OutlinedButton(
                            onClick = { onShareProjectExport(pdfFormat, pdfOptions) },
                            enabled = !exporting,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("export_share_pdf_button")
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.export_share_pdf))
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // 2. All 54 Accepted Formats Default Exporter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Export Project in Any Accepted Format",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryPill(
                        text = "${UniversalFormatCatalog.allFormats.size} Formats",
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = formatSearch,
                    onValueChange = { formatSearch = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    placeholder = { Text("Search .py, .rs, .zip, .html, .csv, .tex, .yaml...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_format_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExportFormatCategory.entries.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category.label) },
                            modifier = Modifier.testTag("export_cat_${category.name.lowercase()}")
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FlowRow(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        filteredFormats.forEach { fmt ->
                            FilterChip(
                                selected = selectedFormat.extension == fmt.extension,
                                onClick = { selectedFormat = fmt },
                                label = {
                                    Text(
                                        text = ".${fmt.extension}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily)
                                    )
                                },
                                modifier = Modifier.testTag("export_fmt_${fmt.extension}")
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${selectedFormat.label} (.${selectedFormat.extension})",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "${UniversalFormatCatalog.suggestedFileName(project, selectedFormat)} · ${selectedFormat.mimeType}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (selectedFormat.extension == "pdf") {
                                createPdfLauncher.launch(UniversalFormatCatalog.suggestedFileName(project, pdfFormat))
                            } else {
                                pendingSingleFile = null
                                pendingSaveFormat = selectedFormat
                                createGenericLauncher.launch(
                                    UniversalFormatCatalog.suggestedFileName(project, selectedFormat)
                                )
                            }
                        },
                        enabled = !exporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_selected_format_save_button")
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save .${selectedFormat.extension}")
                    }
                    OutlinedButton(
                        onClick = { onShareProjectExport(selectedFormat, pdfOptions) },
                        enabled = !exporting,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("export_selected_format_share_button")
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share .${selectedFormat.extension}")
                    }
                }
            }

            // 3. Export Individual Workspace Files in Native Format
            if (files.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text(
                    text = "Export Individual Workspace Files (${files.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    files.sortedBy { it.relativePath }.take(25).forEach { file ->
                        val fileName = file.relativePath.substringAfterLast('/')
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = file.relativePath,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        pendingSaveFormat = null
                                        pendingSingleFile = file
                                        createGenericLauncher.launch(fileName)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Download,
                                        contentDescription = "Save $fileName",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { onShareSingleFile(file) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = "Share $fileName",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
        }
    }
}
