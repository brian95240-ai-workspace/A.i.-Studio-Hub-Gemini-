package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.ProjectFile
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.StageRecord
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry card placed on the Research screen allowing one-tap launch into the
 * PostGIS / PostgreSQL Entity Schema Mapper (with optional Firebase AI deep extraction).
 */
@Composable
fun PostGisSchemaMapperCard(
    project: ResearchProject?,
    stages: List<StageRecord>,
    roundtableItems: List<RoundtableItem>,
    files: List<ProjectFile>,
    canSaveToRepo: Boolean,
    isAiExtracting: Boolean = false,
    onAiExtractEntities: ((corpus: String, projectTitle: String, onComplete: (Result<PgSchemaMappingPlan>) -> Unit) -> Unit)? = null,
    onSaveMigrationToRepo: ((relativePath: String, sqlContent: String) -> Unit)? = null,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val baselinePlan = remember(project?.id, project?.chatAnswer, stages, roundtableItems.size, files.size) {
        PostGisSchemaMapperEngine.extractEntitiesLocally(
            project = project,
            stages = stages,
            roundtableItems = roundtableItems,
            files = files
        )
    }

    var showMapperDialog by remember { mutableStateOf(false) }
    var triggerAiOnOpen by remember { mutableStateOf(false) }

    val spatialTables = baselinePlan.entities.count { it.geometryKind != PgGeometryKind.NONE }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("postgis_schema_mapper_card"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.38f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ElectricCyan.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.padding(9.dp).size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.postgis_mapper_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.postgis_mapper_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryPill(
                    text = "${baselinePlan.entities.size} Candidate Tables",
                    accentColor = ElectricCyan
                )
                TelemetryPill(
                    text = "$spatialTables PostGIS Spatial",
                    accentColor = VerifiedEmerald
                )
                TelemetryPill(
                    text = "${baselinePlan.relationships.size} FK Relations",
                    accentColor = BridgeAmber
                )
                TelemetryPill(
                    text = "EPSG:4326",
                    accentColor = MaterialTheme.colorScheme.secondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        triggerAiOnOpen = false
                        showMapperDialog = true
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .heightIn(min = 48.dp)
                        .testTag("open_postgis_mapper_button")
                ) {
                    Icon(Icons.Outlined.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.postgis_mapper_open_cta))
                }

                if (onAiExtractEntities != null) {
                    FilledTonalButton(
                        onClick = {
                            triggerAiOnOpen = true
                            showMapperDialog = true
                        },
                        enabled = !isAiExtracting,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("postgis_mapper_ai_quick_button")
                    ) {
                        if (isAiExtracting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.postgis_mapper_ai_cta))
                    }
                }
            }
        }
    }

    if (showMapperDialog) {
        PostGisSchemaMapperDialog(
            project = project,
            stages = stages,
            roundtableItems = roundtableItems,
            files = files,
            initialPlan = baselinePlan,
            autoTriggerAi = triggerAiOnOpen,
            canSaveToRepo = canSaveToRepo,
            isAiExtractingExternal = isAiExtracting,
            onAiExtractEntities = onAiExtractEntities,
            onSaveMigrationToRepo = onSaveMigrationToRepo,
            onToast = onToast,
            onDismiss = {
                showMapperDialog = false
                triggerAiOnOpen = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PostGisSchemaMapperDialog(
    project: ResearchProject?,
    stages: List<StageRecord>,
    roundtableItems: List<RoundtableItem>,
    files: List<ProjectFile>,
    initialPlan: PgSchemaMappingPlan,
    autoTriggerAi: Boolean = false,
    canSaveToRepo: Boolean = false,
    isAiExtractingExternal: Boolean = false,
    onAiExtractEntities: ((corpus: String, projectTitle: String, onComplete: (Result<PgSchemaMappingPlan>) -> Unit) -> Unit)? = null,
    onSaveMigrationToRepo: ((relativePath: String, sqlContent: String) -> Unit)? = null,
    onToast: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val projectTitle = project?.title?.ifBlank { project.rootFolderName } ?: "Research Project"
    val defaultSchemaSlug = remember(project?.rootFolderName) {
        PostGisSchemaMapperEngine.sanitizeIdentifier(
            project?.rootFolderName?.substringBefore('-') ?: "research",
            "research"
        )
    }

    var plan by remember(initialPlan) { mutableStateOf(initialPlan) }
    var config by remember(defaultSchemaSlug) {
        mutableStateOf(
            PgSchemaMappingConfig(
                schemaName = defaultSchemaSlug,
                ageGraphName = "${defaultSchemaSlug}_kg"
            )
        )
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var localAiLoading by remember { mutableStateOf(false) }
    val aiBusy = isAiExtractingExternal || localAiLoading

    var newEntityName by remember { mutableStateOf("") }
    var showAddEntityRow by remember { mutableStateOf(false) }
    var pendingSqlExport by remember { mutableStateOf<String?>(null) }

    val generatedSql = remember(plan, config, projectTitle) {
        PostGisSchemaMapperEngine.generatePostgresMigrationSql(plan, config, projectTitle)
    }

    val sqlExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/sql")
    ) { uri: Uri? ->
        val sqlText = pendingSqlExport
        pendingSqlExport = null
        if (uri != null && sqlText != null) {
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    runCatching {
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: error("Storage provider rejected output stream.")
                        stream.use { out ->
                            out.write(sqlText.toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                    }
                }
                if (res.isSuccess) {
                    onToast("Exported PostGIS/PostgreSQL migration (.sql) to device.")
                } else {
                    onToast("SQL export failed: ${res.exceptionOrNull()?.message ?: "Write error"}")
                }
            }
        }
    }

    fun triggerAiExtraction() {
        val extractor = onAiExtractEntities ?: return
        if (aiBusy) return
        localAiLoading = true
        val corpus = PostGisSchemaMapperEngine.buildExtractionCorpus(project, stages, roundtableItems, files)
        extractor(corpus, projectTitle) { result ->
            localAiLoading = false
            result.onSuccess { aiPlan ->
                plan = aiPlan
                onToast(aiPlan.sourceSummary)
            }.onFailure { err ->
                onToast("AI entity extraction failed: ${err.message?.take(80) ?: "Try again."}")
            }
        }
    }

    LaunchedEffect(autoTriggerAi) {
        if (autoTriggerAi && onAiExtractEntities != null) {
            triggerAiExtraction()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("postgis_schema_mapper_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Storage, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.postgis_mapper_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = plan.sourceSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (plan.usedAiExtraction) VerifiedEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (onAiExtractEntities != null) {
                        FilledTonalButton(
                            onClick = { triggerAiExtraction() },
                            enabled = !aiBusy,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("postgis_dialog_ai_extract_button")
                        ) {
                            if (aiBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (aiBusy) stringResource(R.string.postgis_mapper_extracting)
                                else stringResource(R.string.postgis_mapper_ai_cta),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("postgis_dialog_close_button")
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_close_dialog))
                    }
                }

                // Target Database Config Strip (Schema + SRID + Extensions)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = config.schemaName,
                            onValueChange = { config = config.copy(schemaName = it) },
                            label = { Text("PG Schema") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("postgis_schema_name_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(4326 to "WGS84", 3857 to "WebMerc", 2163 to "US Atlas").forEach { (srid, lbl) ->
                                FilterChip(
                                    selected = config.defaultSrid == srid,
                                    onClick = { config = config.copy(defaultSrid = srid) },
                                    label = { Text("$srid", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("postgis_srid_$srid")
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = config.enablePostGis,
                            onClick = { config = config.copy(enablePostGis = !config.enablePostGis) },
                            label = { Text("PostGIS", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("postgis_toggle_postgis")
                        )
                        FilterChip(
                            selected = config.enableApacheAge,
                            onClick = { config = config.copy(enableApacheAge = !config.enableApacheAge) },
                            label = { Text("Apache AGE", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("postgis_toggle_age")
                        )
                        FilterChip(
                            selected = config.includeSpatialIndexes,
                            onClick = { config = config.copy(includeSpatialIndexes = !config.includeSpatialIndexes) },
                            label = { Text("GiST Indexes", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("postgis_toggle_gist")
                        )
                        FilterChip(
                            selected = config.includeSeedInserts,
                            onClick = { config = config.copy(includeSeedInserts = !config.includeSeedInserts) },
                            label = { Text("Seed Inserts", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("postgis_toggle_seeds")
                        )
                        FilterChip(
                            selected = config.includeAnalyticsViews,
                            onClick = { config = config.copy(includeAnalyticsViews = !config.includeAnalyticsViews) },
                            label = { Text("Spatial Views", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("postgis_toggle_views")
                        )
                        FilterChip(
                            selected = config.useUuidPrimaryKeys,
                            onClick = { config = config.copy(useUuidPrimaryKeys = !config.useUuidPrimaryKeys) },
                            label = { Text(if (config.useUuidPrimaryKeys) "UUID PK" else "BIGSERIAL PK", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("postgis_toggle_uuid")
                        )
                    }
                }

                // 3 Clean Tabs: Entities | Visual ER | SQL Migration
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Entities (${plan.entities.count { it.enabled }})") },
                        icon = { Icon(Icons.Outlined.TableChart, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("postgis_tab_entities")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Visual ER") },
                        icon = { Icon(Icons.Outlined.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("postgis_tab_er")
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("SQL DDL") },
                        icon = { Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("postgis_tab_sql")
                    )
                }

                // Tab Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        0 -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item(key = "add_entity_bar") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Mapped PostgreSQL / PostGIS Tables",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    plan = PostGisSchemaMapperEngine.extractEntitiesLocally(
                                                        project, stages, roundtableItems, files
                                                    )
                                                    onToast("Reset to local entity scan.")
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.heightIn(min = 48.dp).testTag("postgis_rescan_button")
                                            ) {
                                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Rescan", style = MaterialTheme.typography.labelSmall)
                                            }
                                            FilledTonalButton(
                                                onClick = { showAddEntityRow = !showAddEntityRow },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.heightIn(min = 48.dp).testTag("postgis_add_entity_toggle")
                                            ) {
                                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Add Table", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    if (showAddEntityRow) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = newEntityName,
                                                onValueChange = { newEntityName = it },
                                                label = { Text("New Entity / Table Name") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f).testTag("postgis_new_entity_input")
                                            )
                                            Button(
                                                onClick = {
                                                    val clean = newEntityName.trim()
                                                    if (clean.isNotBlank()) {
                                                        val tbl = PostGisSchemaMapperEngine.sanitizeIdentifier(clean, "custom_entity")
                                                        val added = PgMappedEntity(
                                                            id = "custom_${System.currentTimeMillis()}",
                                                            entityName = clean,
                                                            tableName = tbl,
                                                            category = PgEntityCategory.SPATIAL_SITE,
                                                            description = "Custom mapped PostGIS table",
                                                            geometryKind = PgGeometryKind.POINT,
                                                            columns = listOf(
                                                                PgMappedColumn("c_${System.currentTimeMillis()}", "name", PgColumnDataType.TEXT, nullable = false, sampleValue = clean),
                                                                PgMappedColumn("c_m_${System.currentTimeMillis()}", "attributes", PgColumnDataType.JSONB, sampleValue = "{}")
                                                            ),
                                                            sampleWkt = "POINT(0 0)",
                                                            sourceLabel = "Custom"
                                                        )
                                                        plan = plan.copy(
                                                            entities = plan.entities + added,
                                                            sourceSummary = "${plan.entities.size + 1} mapped tables"
                                                        )
                                                        newEntityName = ""
                                                        showAddEntityRow = false
                                                    }
                                                },
                                                modifier = Modifier.heightIn(min = 48.dp).testTag("postgis_confirm_add_entity")
                                            ) {
                                                Text("Add")
                                            }
                                        }
                                    }
                                }

                                items(plan.entities, key = { it.id }) { entity ->
                                    EntityMappingEditorCard(
                                        entity = entity,
                                        srid = config.defaultSrid,
                                        onUpdate = { updated ->
                                            plan = plan.copy(
                                                entities = plan.entities.map { if (it.id == updated.id) updated else it }
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        1 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SpecializedFormatCard(
                                    kind = SpecializedFormatKind.POSTGRES_SQL,
                                    code = generatedSql,
                                    title = "${config.schemaName} · PostGIS ER Schema",
                                    subtitle = "Live ER Diagram + PNG / SVG / Cypher / NetworkX / D3 Export",
                                    sourceLanguage = "sql",
                                    canvasHeight = 420.dp,
                                    onSaveToRepo = if (canSaveToRepo && onSaveMigrationToRepo != null) {
                                        { sql ->
                                            val path = "db/migrations/${PostGisSchemaMapperEngine.sanitizeIdentifier(config.schemaName, "research")}_postgis_schema.sql"
                                            onSaveMigrationToRepo(path, sql)
                                        }
                                    } else null,
                                    onToast = onToast
                                )
                            }
                        }

                        2 -> {
                            val highlighted = remember(generatedSql) {
                                CodeSyntaxHighlighter.highlight(generatedSql, "sql")
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "db/migrations/${PostGisSchemaMapperEngine.sanitizeIdentifier(config.schemaName, "research")}_postgis_schema.sql",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                        color = ElectricCyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("PostGIS Schema SQL", generatedSql))
                                            onToast("Copied PostgreSQL/PostGIS migration SQL.")
                                        },
                                        modifier = Modifier.size(48.dp).testTag("postgis_copy_sql_button")
                                    ) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.cd_copy_text), tint = ElectricCyan)
                                    }
                                }
                                Surface(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .horizontalScroll(rememberScrollState())
                                            .padding(14.dp)
                                    ) {
                                        Text(
                                            text = highlighted,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Persistent Footer Actions: Export .sql (SAF) + Save to Repo
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            pendingSqlExport = generatedSql
                            val slug = PostGisSchemaMapperEngine.sanitizeIdentifier(config.schemaName, "research")
                            sqlExportLauncher.launch("${slug}_postgis_schema.sql")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("postgis_export_sql_button")
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.postgis_mapper_export_sql))
                    }

                    if (canSaveToRepo && onSaveMigrationToRepo != null) {
                        Button(
                            onClick = {
                                val slug = PostGisSchemaMapperEngine.sanitizeIdentifier(config.schemaName, "research")
                                val targetPath = "db/migrations/${slug}_postgis_schema.sql"
                                onSaveMigrationToRepo(targetPath, generatedSql)
                                onToast("Saved $targetPath to project repository.")
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .heightIn(min = 48.dp)
                                .testTag("postgis_save_repo_button")
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.postgis_mapper_save_repo))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntityMappingEditorCard(
    entity: PgMappedEntity,
    srid: Int,
    onUpdate: (PgMappedEntity) -> Unit
) {
    var addingCol by remember { mutableStateOf(false) }
    var newColName by remember { mutableStateOf("") }
    var newColType by remember { mutableStateOf(PgColumnDataType.TEXT) }

    val borderColor = if (!entity.enabled) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    } else if (entity.geometryKind != PgGeometryKind.NONE) {
        VerifiedEmerald.copy(alpha = 0.55f)
    } else {
        ElectricCyan.copy(alpha = 0.45f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("postgis_entity_card_${entity.tableName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = entity.entityName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TelemetryPill(
                            text = entity.sourceLabel,
                            accentColor = if (entity.geometryKind != PgGeometryKind.NONE) VerifiedEmerald else ElectricCyan
                        )
                    }
                    if (entity.description.isNotBlank()) {
                        Text(
                            text = entity.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Switch(
                    checked = entity.enabled,
                    onCheckedChange = { onUpdate(entity.copy(enabled = it)) },
                    modifier = Modifier.testTag("postgis_entity_toggle_${entity.tableName}")
                )
            }

            if (entity.enabled) {
                OutlinedTextField(
                    value = entity.tableName,
                    onValueChange = { onUpdate(entity.copy(tableName = it)) },
                    label = { Text("PostgreSQL Table Name") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth()
                )

                // PostGIS Geometry Column Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.Map, contentDescription = null, tint = VerifiedEmerald, modifier = Modifier.size(16.dp))
                    PgGeometryKind.entries.forEach { gKind ->
                        FilterChip(
                            selected = entity.geometryKind == gKind,
                            onClick = { onUpdate(entity.copy(geometryKind = gKind)) },
                            label = { Text(gKind.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (entity.geometryKind != PgGeometryKind.NONE) {
                    Text(
                        text = "Spatial column: ${entity.geometryColumnName} ${entity.geometryKind.formatSqlType(srid).orEmpty()}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = VerifiedEmerald
                    )
                }

                // Mapped Columns
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    entity.columns.forEach { col ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = buildString {
                                        append(col.name)
                                        append(": ")
                                        append(col.dataType.label)
                                        if (col.referencesTable != null) append(" -> ${col.referencesTable}")
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = {
                                        onUpdate(entity.copy(columns = entity.columns.filterNot { it.id == col.id }))
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(R.string.cd_postgis_remove_column),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (addingCol) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newColName,
                            onValueChange = { newColName = it },
                            label = { Text("Column") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val all = PgColumnDataType.entries
                                newColType = all[(all.indexOf(newColType) + 1) % all.size]
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(newColType.label, style = MaterialTheme.typography.labelSmall)
                        }
                        FilledTonalButton(
                            onClick = {
                                val clean = PostGisSchemaMapperEngine.sanitizeIdentifier(newColName, "")
                                if (clean.isNotBlank()) {
                                    onUpdate(
                                        entity.copy(
                                            columns = entity.columns + PgMappedColumn(
                                                id = "col_${System.currentTimeMillis()}",
                                                name = clean,
                                                dataType = newColType
                                            )
                                        )
                                    )
                                    newColName = ""
                                    addingCol = false
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Add")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { addingCol = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Column", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
