package com.example.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Polyline
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.ProjectFile
import com.example.data.model.ResearchProject
import com.example.data.model.StageRecord
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SyntaxKeyword
import com.example.ui.theme.VerifiedEmerald
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

private fun formatIconFor(kind: SpecializedFormatKind): ImageVector = when (kind) {
    SpecializedFormatKind.LATEX -> Icons.Outlined.Functions
    SpecializedFormatKind.CYPHER_AGE -> Icons.Outlined.Hub
    SpecializedFormatKind.GRAPH_DOT -> Icons.Outlined.AccountTree
    SpecializedFormatKind.GRAPH_EXCHANGE -> Icons.Outlined.Polyline
    SpecializedFormatKind.GEOSPATIAL -> Icons.Outlined.Map
    SpecializedFormatKind.POSTGRES_SQL -> Icons.Outlined.Storage
}

private fun formatAccentFor(kind: SpecializedFormatKind): Color = when (kind) {
    SpecializedFormatKind.LATEX -> BridgeAmber
    SpecializedFormatKind.CYPHER_AGE -> ElectricCyan
    SpecializedFormatKind.GRAPH_DOT -> VerifiedEmerald
    SpecializedFormatKind.GRAPH_EXCHANGE -> SyntaxKeyword
    SpecializedFormatKind.GEOSPATIAL -> VerifiedEmerald
    SpecializedFormatKind.POSTGRES_SQL -> ElectricCyan
}

@Stable
class SpecializedExportHandler(
    val isExporting: Boolean,
    val exportOption: (SpecializedExportOption) -> Unit
)

@Composable
fun rememberSpecializedExportHandler(
    controller: MermaidCanvasController,
    kind: SpecializedFormatKind,
    sourceCode: String,
    title: String,
    onToast: ((String) -> Unit)? = null
): SpecializedExportHandler {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingOption by remember { mutableStateOf<SpecializedExportOption?>(null) }

    fun notify(msg: String) {
        if (onToast != null) onToast(msg)
        else Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun writePending(uri: Uri?) {
        val bytes = pendingBytes
        val opt = pendingOption
        pendingBytes = null
        pendingOption = null
        if (uri == null || bytes == null || opt == null) {
            isExporting = false
            return
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error("Storage provider rejected output stream.")
                    stream.use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                }
            }
            isExporting = false
            if (saved.isSuccess) {
                val sz = MermaidCanvasController.formatByteSize(bytes.size)
                notify("Exported ${opt.extension.uppercase()} ($sz) to device.")
            } else {
                notify("Export failed: ${saved.exceptionOrNull()?.message ?: "Unable to write file."}")
            }
        }
    }

    val pngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri -> writePending(uri) }

    val svgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/svg+xml")
    ) { uri -> writePending(uri) }

    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> writePending(uri) }

    return remember(controller, kind, sourceCode, title, isExporting) {
        SpecializedExportHandler(
            isExporting = isExporting,
            exportOption = { option ->
                if (!isExporting) {
                    isExporting = true
                    scope.launch {
                        val slug = title.lowercase()
                            .replace(Regex("[^a-z0-9]+"), "_")
                            .trim('_')
                            .take(42)
                            .ifBlank { kind.name.lowercase() }
                        val suggestedName = "$slug.${option.extension}"

                        if (option.isImage && option.imageFormat != null) {
                            val result = controller.exportDiagram(option.imageFormat, null)
                            result.onSuccess { bytes ->
                                pendingBytes = bytes
                                pendingOption = option
                                when (option.imageFormat) {
                                    MermaidImageExportFormat.PNG -> pngLauncher.launch(suggestedName)
                                    MermaidImageExportFormat.SVG -> svgLauncher.launch(suggestedName)
                                }
                            }.onFailure { err ->
                                isExporting = false
                                notify(err.message ?: "Image export failed. Switch to Visual view first.")
                            }
                        } else {
                            val payloadText = runCatching {
                                option.transform?.invoke(sourceCode, title) ?: sourceCode
                            }.getOrElse { sourceCode }
                            pendingBytes = payloadText.toByteArray(Charsets.UTF_8)
                            pendingOption = option
                            docLauncher.launch(suggestedName)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun SpecializedExportMenuButton(
    kind: SpecializedFormatKind,
    visualActive: Boolean,
    isExporting: Boolean,
    onSelectOption: (SpecializedExportOption) -> Unit,
    buttonTestTag: String,
    useOutlinedStyle: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(kind) { SpecializedFormatConverters.exportOptionsFor(kind) }
    val imageOpts = remember(options) { options.filter { it.isImage } }
    val textOpts = remember(options) { options.filterNot { it.isImage } }
    val cdExport = stringResource(R.string.cd_specialized_export)

    Box(modifier = modifier) {
        val content: @Composable () -> Unit = {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (isExporting) stringResource(R.string.mermaid_exporting)
                else stringResource(R.string.specialized_export_label),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1
            )
            if (!isExporting) {
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

        if (useOutlinedStyle) {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = !isExporting,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = cdExport }
                    .testTag(buttonTestTag)
            ) { content() }
        } else {
            FilledTonalButton(
                onClick = { expanded = true },
                enabled = !isExporting,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = cdExport }
                    .testTag(buttonTestTag)
            ) { content() }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            imageOpts.forEach { opt ->
                DropdownMenuItem(
                    enabled = visualActive,
                    text = {
                        Column {
                            Text(opt.label, style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = if (visualActive) opt.description else "Switch to Visual view to export image",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (opt.extension == "png") Icons.Outlined.Image else Icons.Outlined.Polyline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectOption(opt)
                    },
                    modifier = Modifier.testTag("${buttonTestTag}_${opt.id}")
                )
            }
            if (textOpts.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                textOpts.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(opt.label, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = opt.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelectOption(opt)
                        },
                        modifier = Modifier.testTag("${buttonTestTag}_${opt.id}")
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpecializedFormatWebView(
    kind: SpecializedFormatKind,
    code: String,
    title: String,
    theme: MermaidThemePreset,
    graphLayout: String = "layered",
    zoomInTick: Int = 0,
    zoomOutTick: Int = 0,
    zoomResetTick: Int = 0,
    controller: MermaidCanvasController? = null,
    modifier: Modifier = Modifier
) {
    val htmlDocument = remember(kind, code, title, theme) {
        SpecializedFormatEngine.buildSpecializedHtml(kind, code, title, theme, graphLayout)
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastLoadedHtml by remember { mutableStateOf("") }
    var pageLoaded by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        onDispose {
            webViewRef?.let { view ->
                controller?.detach(view)
                view.stopLoading()
                view.destroy()
            }
            webViewRef = null
        }
    }

    LaunchedEffect(zoomInTick, pageLoaded) {
        if (zoomInTick > 0 && pageLoaded) {
            webViewRef?.evaluateJavascript("window.vortexZoomIn && window.vortexZoomIn();", null)
        }
    }
    LaunchedEffect(zoomOutTick, pageLoaded) {
        if (zoomOutTick > 0 && pageLoaded) {
            webViewRef?.evaluateJavascript("window.vortexZoomOut && window.vortexZoomOut();", null)
        }
    }
    LaunchedEffect(zoomResetTick, pageLoaded) {
        if (zoomResetTick > 0 && pageLoaded) {
            webViewRef?.evaluateJavascript("window.vortexZoomReset && window.vortexZoomReset();", null)
        }
    }
    LaunchedEffect(graphLayout, pageLoaded) {
        if (kind.isGraphLike && pageLoaded) {
            val safe = MermaidDiagramEngine.toSafeJsStringLiteral(graphLayout)
            webViewRef?.evaluateJavascript("window.vortexSetGraphLayout && window.vortexSetGraphLayout($safe);", null)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    builtInZoomControls = false
                    displayZoomControls = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        val msg = consoleMessage?.message().orEmpty()
                        controller?.handleConsoleMessage(msg)
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString().orEmpty()
                        val allowed = url.startsWith(MermaidDiagramEngine.SANDBOX_BASE_URL) ||
                            url == SpecializedFormatEngine.PINNED_KATEX_JS_URL ||
                            url == SpecializedFormatEngine.PINNED_KATEX_CSS_URL ||
                            url.startsWith(SpecializedFormatEngine.PINNED_KATEX_FONT_PREFIX) ||
                            url.startsWith("data:image/")
                        return if (allowed) {
                            super.shouldInterceptRequest(view, request)
                        } else {
                            WebResourceResponse(
                                "text/plain",
                                "utf-8",
                                403,
                                "Blocked",
                                emptyMap(),
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageLoaded = true
                        controller?.setPageLoaded(true)
                    }
                }
                webViewRef = this
                controller?.attach(this, loaded = false)
                lastLoadedHtml = htmlDocument
                loadDataWithBaseURL(
                    MermaidDiagramEngine.SANDBOX_BASE_URL,
                    htmlDocument,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { view ->
            webViewRef = view
            controller?.attach(view, loaded = pageLoaded)
            if (lastLoadedHtml != htmlDocument) {
                lastLoadedHtml = htmlDocument
                pageLoaded = false
                controller?.setPageLoaded(false)
                view.loadDataWithBaseURL(
                    MermaidDiagramEngine.SANDBOX_BASE_URL,
                    htmlDocument,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}

@Composable
fun SpecializedFormatCard(
    kind: SpecializedFormatKind,
    code: String,
    title: String = kind.displayName,
    subtitle: String? = null,
    sourceLanguage: String = kind.defaultExtension,
    canvasHeight: Dp = 320.dp,
    initialTheme: MermaidThemePreset = MermaidThemePreset.OBSIDIAN,
    onSaveToRepo: ((String) -> Unit)? = null,
    onToast: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSource by remember { mutableStateOf(false) }
    var theme by remember(initialTheme) { mutableStateOf(initialTheme) }
    var graphLayout by remember { mutableStateOf("layered") }
    var showStudio by remember { mutableStateOf(false) }
    var zoomInTick by remember { mutableIntStateOf(0) }
    var zoomOutTick by remember { mutableIntStateOf(0) }
    var zoomResetTick by remember { mutableIntStateOf(0) }

    val canvasController = rememberMermaidCanvasController()
    val exportHandler = rememberSpecializedExportHandler(
        controller = canvasController,
        kind = kind,
        sourceCode = code,
        title = title,
        onToast = onToast
    )

    val computedSubtitle = remember(kind, code, title, subtitle) {
        subtitle?.takeIf { it.isNotBlank() } ?: SpecializedFormatEngine.summarizeArtifact(kind, code, title)
    }
    val accentColor = remember(kind) { formatAccentFor(kind) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("specialized_format_card_${kind.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = formatIconFor(kind),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = kind.badgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = computedSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        val presets = MermaidThemePreset.entries
                        theme = presets[(presets.indexOf(theme) + 1) % presets.size]
                    },
                    modifier = Modifier.size(48.dp).testTag("specialized_theme_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = stringResource(R.string.cd_specialized_cycle_theme),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { showSource = !showSource },
                    modifier = Modifier.size(48.dp).testTag("specialized_toggle_source_button")
                ) {
                    Icon(
                        imageVector = if (showSource) Icons.Outlined.Visibility else Icons.Outlined.Code,
                        contentDescription = stringResource(R.string.cd_specialized_toggle_source),
                        tint = if (showSource) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText(title, code))
                        if (onToast != null) onToast("Copied ${kind.badgeLabel} source")
                        else Toast.makeText(context, "Copied ${kind.badgeLabel} source", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(48.dp).testTag("specialized_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.cd_specialized_copy),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { showStudio = true },
                    modifier = Modifier.size(48.dp).testTag("specialized_expand_studio_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInFull,
                        contentDescription = stringResource(R.string.cd_specialized_expand),
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Body: visual canvas or syntax-highlighted source
            if (showSource) {
                val highlighted = remember(code, sourceLanguage) {
                    CodeSyntaxHighlighter.highlight(code, sourceLanguage)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = canvasHeight)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
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
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeight)
                        .background(
                            when (theme) {
                                MermaidThemePreset.OBSIDIAN -> Color(0xFF0B1018)
                                MermaidThemePreset.BLUEPRINT -> Color(0xFFF8FAFC)
                                MermaidThemePreset.EMERALD -> Color(0xFF050B0A)
                            }
                        )
                ) {
                    SpecializedFormatWebView(
                        kind = kind,
                        code = code,
                        title = title,
                        theme = theme,
                        graphLayout = graphLayout,
                        zoomInTick = zoomInTick,
                        zoomOutTick = zoomOutTick,
                        zoomResetTick = zoomResetTick,
                        controller = canvasController,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom action bar: Export (PNG/SVG/Ecosystem) + Graph Layout + Zoom/Fit
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SpecializedExportMenuButton(
                        kind = kind,
                        visualActive = !showSource,
                        isExporting = exportHandler.isExporting,
                        onSelectOption = exportHandler.exportOption,
                        buttonTestTag = "specialized_export_button"
                    )

                    if (kind.isGraphLike && !showSource) {
                        FilterChip(
                            selected = graphLayout == "force",
                            onClick = {
                                graphLayout = if (graphLayout == "layered") "force" else "layered"
                            },
                            label = {
                                Text(
                                    text = if (graphLayout == "force") "Force" else "Layered",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag("specialized_layout_toggle")
                        )
                    }
                }

                if (!showSource) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { zoomOutTick++ },
                            modifier = Modifier.size(48.dp).testTag("specialized_zoom_out")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ZoomOut,
                                contentDescription = stringResource(R.string.cd_mermaid_zoom_out),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { zoomResetTick++ },
                            modifier = Modifier.size(48.dp).testTag("specialized_zoom_reset")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FitScreen,
                                contentDescription = stringResource(R.string.cd_mermaid_zoom_reset),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { zoomInTick++ },
                            modifier = Modifier.size(48.dp).testTag("specialized_zoom_in")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ZoomIn,
                                contentDescription = stringResource(R.string.cd_mermaid_zoom_in),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStudio) {
        SpecializedFormatStudioDialog(
            kind = kind,
            initialCode = code,
            title = title,
            initialTheme = theme,
            onDismiss = { showStudio = false },
            onSaveToRepo = onSaveToRepo,
            onToast = onToast
        )
    }
}

@Composable
fun SpecializedFormatStudioDialog(
    kind: SpecializedFormatKind,
    initialCode: String,
    title: String,
    initialTheme: MermaidThemePreset = MermaidThemePreset.OBSIDIAN,
    onDismiss: () -> Unit,
    onSaveToRepo: ((String) -> Unit)? = null,
    onToast: ((String) -> Unit)? = null
) {
    var draftCode by remember(initialCode) { mutableStateOf(initialCode) }
    var theme by remember(initialTheme) { mutableStateOf(initialTheme) }
    var graphLayout by remember { mutableStateOf("layered") }
    var showEditor by remember { mutableStateOf(false) }
    var zoomInTick by remember { mutableIntStateOf(0) }
    var zoomOutTick by remember { mutableIntStateOf(0) }
    var zoomResetTick by remember { mutableIntStateOf(0) }

    val studioController = rememberMermaidCanvasController()
    val exportHandler = rememberSpecializedExportHandler(
        controller = studioController,
        kind = kind,
        sourceCode = draftCode,
        title = title,
        onToast = onToast
    )
    val accent = remember(kind) { formatAccentFor(kind) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("specialized_studio_dialog"),
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
                    Icon(formatIconFor(kind), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = SpecializedFormatEngine.summarizeArtifact(kind, draftCode, title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = { showEditor = !showEditor },
                        modifier = Modifier.size(48.dp).testTag("specialized_studio_edit_toggle")
                    ) {
                        Icon(
                            imageVector = if (showEditor) Icons.Outlined.Visibility else Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.cd_specialized_toggle_source),
                            tint = if (showEditor) accent else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("specialized_studio_close")
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_close_dialog))
                    }
                }

                // Controls strip: theme + layout + zoom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MermaidThemePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = theme == preset,
                            onClick = { theme = preset },
                            label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (kind.isGraphLike) {
                        FilterChip(
                            selected = graphLayout == "force",
                            onClick = { graphLayout = if (graphLayout == "layered") "force" else "layered" },
                            label = {
                                Text(
                                    text = if (graphLayout == "force") "Force Layout" else "Layered Layout",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { zoomOutTick++ }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.ZoomOut, contentDescription = stringResource(R.string.cd_mermaid_zoom_out))
                    }
                    IconButton(onClick = { zoomResetTick++ }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.FitScreen, contentDescription = stringResource(R.string.cd_mermaid_zoom_reset))
                    }
                    IconButton(onClick = { zoomInTick++ }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.ZoomIn, contentDescription = stringResource(R.string.cd_mermaid_zoom_in))
                    }
                }

                // Main canvas + optional live editor
                if (showEditor) {
                    OutlinedTextField(
                        value = draftCode,
                        onValueChange = { draftCode = it },
                        label = { Text("${kind.displayName} Source (Live Preview)") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("specialized_studio_editor_input")
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (showEditor) 0.55f else 1f)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when (theme) {
                                MermaidThemePreset.OBSIDIAN -> Color(0xFF0B1018)
                                MermaidThemePreset.BLUEPRINT -> Color(0xFFF8FAFC)
                                MermaidThemePreset.EMERALD -> Color(0xFF050B0A)
                            }
                        )
                ) {
                    SpecializedFormatWebView(
                        kind = kind,
                        code = draftCode,
                        title = title,
                        theme = theme,
                        graphLayout = graphLayout,
                        zoomInTick = zoomInTick,
                        zoomOutTick = zoomOutTick,
                        zoomResetTick = zoomResetTick,
                        controller = studioController,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom bar: Export + Save to Repo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpecializedExportMenuButton(
                        kind = kind,
                        visualActive = true,
                        isExporting = exportHandler.isExporting,
                        onSelectOption = exportHandler.exportOption,
                        buttonTestTag = "specialized_studio_export_button",
                        useOutlinedStyle = true
                    )

                    if (onSaveToRepo != null) {
                        FilledTonalButton(
                            onClick = {
                                onSaveToRepo(draftCode)
                                onToast?.invoke("Saved ${kind.badgeLabel} artifact to repository")
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("specialized_studio_save_repo")
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.specialized_save_to_repo))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dynamic pipeline & workspace visualizer for LaTeX, Cypher/Apache AGE, PostGIS GeoJSON/WKT,
 * NetworkX/D3/GraphML/DOT, and PostgreSQL schema artifacts—delivering full Mermaid parity.
 */
@Composable
fun ResearchSpecializedArtifactsCard(
    stages: List<StageRecord>,
    synthesisMarkdown: String,
    project: ResearchProject?,
    projectFiles: List<ProjectFile>,
    canSaveToRepo: Boolean = false,
    onSaveArtifactToRepo: ((relativePath: String, content: String) -> Unit)? = null,
    onToast: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val artifacts = remember(stages, synthesisMarkdown, project?.id, projectFiles) {
        SpecializedFormatEngine.collectProjectSpecializedArtifacts(
            stages = stages,
            synthesisMarkdown = synthesisMarkdown,
            project = project,
            projectFiles = projectFiles
        )
    }
    if (artifacts.isEmpty()) return

    var selectedId by remember(artifacts) { mutableStateOf(artifacts.first().id) }
    val activeSpec = artifacts.firstOrNull { it.id == selectedId } ?: artifacts.first()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("research_specialized_artifacts_card"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.35f))
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Hub,
                        contentDescription = null,
                        tint = VerifiedEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.specialized_visualizer_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${artifacts.size} processed ${if (artifacts.size == 1) "artifact" else "artifacts"} · LaTeX, Cypher/AGE, PostGIS, D3/NetworkX, SQL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (artifacts.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    artifacts.forEach { spec ->
                        FilterChip(
                            selected = spec.id == activeSpec.id,
                            onClick = { selectedId = spec.id },
                            leadingIcon = {
                                Icon(
                                    imageVector = formatIconFor(spec.kind),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "${spec.kind.badgeLabel}: ${spec.title.take(22)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.testTag("specialized_tab_${spec.kind.name.lowercase()}")
                        )
                    }
                }
            }

            SpecializedFormatCard(
                kind = activeSpec.kind,
                code = activeSpec.code,
                title = activeSpec.title,
                subtitle = activeSpec.subtitle,
                sourceLanguage = activeSpec.sourceLanguage,
                canvasHeight = 320.dp,
                onSaveToRepo = if (canSaveToRepo && onSaveArtifactToRepo != null) {
                    { updatedCode -> onSaveArtifactToRepo(activeSpec.suggestedFileName, updatedCode) }
                } else null,
                onToast = onToast
            )
        }
    }
}
