package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.RoutingPreferences
import com.example.data.model.StageRecord
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.VerifiedEmerald
import java.io.ByteArrayInputStream

enum class MermaidRenderStatus {
    LOADING,
    MERMAID_JS,
    OFFLINE_SVG,
    ERROR
}

private enum class MermaidCardMode {
    DIAGRAM,
    SOURCE
}

/**
 * Sandboxed WebView canvas that renders Mermaid.js diagrams with strict CSP,
 * `securityLevel: 'strict'`, `htmlLabels: false`, request allowlisting, and
 * automatic offline SVG fallback.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun MermaidCanvasWebView(
    mermaidCode: String,
    theme: MermaidThemePreset,
    modifier: Modifier = Modifier,
    controller: MermaidCanvasController? = null,
    zoomCommandToken: Int = 0,
    zoomCommandDelta: Float = 0f,
    resetZoomToken: Int = 0,
    offlineFallbackToken: Int = 0,
    onZoomPercentChanged: (Int) -> Unit = {},
    onRenderStatusChanged: (MermaidRenderStatus, String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val sanitizedCode = remember(mermaidCode) { MermaidDiagramEngine.sanitizeMermaidSyntax(mermaidCode) }
    var pageLoaded by remember { mutableStateOf(false) }

    val webView = remember(context) {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.parseColor(theme.canvasBgHex))
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = true
            // Prevent parent LazyColumn from hijacking multi-touch pinch/pan gestures inside the diagram canvas
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_MOVE -> {
                        if (event.pointerCount > 1 || canScrollHorizontally(-1) || canScrollHorizontally(1)) {
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                databaseEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
                saveFormData = false
                setGeolocationEnabled(false)
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = true // Block all navigation away from the sandboxed diagram canvas

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString().orEmpty()
                    if (url == MermaidDiagramEngine.PINNED_MERMAID_CDN_URL ||
                        url.startsWith(MermaidDiagramEngine.SANDBOX_BASE_URL) ||
                        url.startsWith("data:image/")
                    ) {
                        return null // Allow pinned Mermaid.js CDN script, sandbox base URL, or local data URI images
                    }
                    // Block all other outbound network requests from the sandboxed WebView
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        403,
                        "Forbidden",
                        emptyMap(),
                        ByteArrayInputStream(ByteArray(0))
                    )
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    pageLoaded = true
                    controller?.setPageLoaded(true)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    val msg = consoleMessage?.message().orEmpty()
                    if (controller?.handleConsoleMessage(msg) == true) {
                        return true
                    }
                    when {
                        msg == "VORTEX_MERMAID:RENDERED:MERMAID_JS" ->
                            onRenderStatusChanged(MermaidRenderStatus.MERMAID_JS, null)
                        msg == "VORTEX_MERMAID:RENDERED:OFFLINE_SVG" ->
                            onRenderStatusChanged(MermaidRenderStatus.OFFLINE_SVG, null)
                        msg.startsWith("VORTEX_MERMAID:ZOOM:") -> {
                            val pct = msg.removePrefix("VORTEX_MERMAID:ZOOM:").toIntOrNull()
                            if (pct != null) onZoomPercentChanged(pct)
                        }
                        msg.startsWith("VORTEX_MERMAID:ERROR:") ->
                            onRenderStatusChanged(
                                MermaidRenderStatus.ERROR,
                                msg.removePrefix("VORTEX_MERMAID:ERROR:")
                            )
                    }
                    return true
                }
            }

            val initialHtml = MermaidDiagramEngine.buildSandboxedViewerHtml(sanitizedCode, theme)
            loadDataWithBaseURL(
                MermaidDiagramEngine.SANDBOX_BASE_URL,
                initialHtml,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    LaunchedEffect(sanitizedCode, theme, pageLoaded) {
        if (pageLoaded) {
            onRenderStatusChanged(MermaidRenderStatus.LOADING, null)
            webView.setBackgroundColor(AndroidColor.parseColor(theme.canvasBgHex))
            val safeCode = MermaidDiagramEngine.toSafeJsStringLiteral(sanitizedCode)
            val safeTheme = MermaidDiagramEngine.toSafeJsStringLiteral(theme.id)
            webView.evaluateJavascript("window.vortexRender($safeCode, $safeTheme);", null)
        }
    }

    LaunchedEffect(zoomCommandToken) {
        if (zoomCommandToken > 0 && pageLoaded) {
            webView.evaluateJavascript("window.vortexZoom($zoomCommandDelta);", null)
        }
    }

    LaunchedEffect(resetZoomToken) {
        if (resetZoomToken > 0 && pageLoaded) {
            webView.evaluateJavascript("window.vortexResetZoom();", null)
        }
    }

    LaunchedEffect(offlineFallbackToken) {
        if (offlineFallbackToken > 0 && pageLoaded) {
            webView.evaluateJavascript("window.vortexRenderOfflineFallback();", null)
        }
    }

    DisposableEffect(webView, controller) {
        controller?.attach(webView, pageLoaded)
        onDispose {
            controller?.detach(webView)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}

/**
 * Compact inline Mermaid.js viewer card used inside MarkdownPreviewView, UnifiedArtifactViewer,
 * and the Research Architecture Visualizer.
 */
@Composable
fun MermaidDiagramCard(
    code: String,
    title: String = "Mermaid Diagram",
    subtitle: String = "",
    canvasHeight: Dp = 280.dp,
    modifier: Modifier = Modifier,
    initialBoard: DiagramAnnotationBoard? = null,
    canAnnotate: Boolean = true,
    currentUserUid: String = "",
    currentUserName: String = "Researcher",
    onBoardChanged: ((DiagramAnnotationBoard) -> Unit)? = null,
    onCopyCode: ((String) -> Unit)? = null,
    onExpandStudio: (() -> Unit)? = null,
    onToast: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val canvasController = rememberMermaidCanvasController()
    var viewMode by remember { mutableStateOf(MermaidCardMode.DIAGRAM) }
    var theme by remember { mutableStateOf(MermaidThemePreset.OBSIDIAN) }
    var renderStatus by remember { mutableStateOf(MermaidRenderStatus.LOADING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var zoomToken by remember { mutableStateOf(0) }
    var zoomDelta by remember { mutableStateOf(0f) }
    var resetToken by remember { mutableStateOf(0) }
    var fallbackToken by remember { mutableStateOf(0) }
    var zoomPercent by remember { mutableStateOf(100) }
    var showLocalStudio by remember { mutableStateOf(false) }

    // Collaborative annotation layer state
    var board by remember(title) {
        mutableStateOf(
            initialBoard ?: DiagramAnnotationBoard(
                diagramId = title,
                diagramTitle = title
            )
        )
    }
    LaunchedEffect(initialBoard) {
        if (initialBoard != null && initialBoard != board) {
            board = initialBoard
        }
    }
    var activeTool by remember { mutableStateOf(MermaidAnnotationTool.INTERACT) }
    var selectedDrawColor by remember { mutableStateOf(MermaidAnnotationPalettes.drawColors.first()) }
    var selectedStrokeStyle by remember { mutableStateOf(AnnotationStrokeStyle.PEN) }
    var showAnnotations by remember { mutableStateOf(true) }
    var showNotesListDialog by remember { mutableStateOf(false) }
    var pendingStickyPlacement by remember { mutableStateOf<Triple<Float, Float, DiagramRenderedNode?>?>(null) }
    var editingStickyNote by remember { mutableStateOf<DiagramStickyNote?>(null) }

    fun commitBoard(updated: DiagramAnnotationBoard) {
        val stamped = updated.copy(
            diagramId = updated.diagramId.ifBlank { title },
            diagramTitle = title,
            updatedAtMillis = System.currentTimeMillis()
        )
        board = stamped
        onBoardChanged?.invoke(stamped)
    }

    val diagramType = remember(code) { MermaidDiagramEngine.detectDiagramType(code) }
    val highlightedSource = remember(code) { CodeSyntaxHighlighter.highlight(code, "mmd") }
    val exportHandler = rememberMermaidImageExportHandler(
        controller = canvasController,
        diagramTitle = title.ifBlank { diagramType },
        overlayJsonProvider = {
            if (showAnnotations && !board.isEmpty) MermaidBoardCodec.encodeBoard(board) else null
        },
        onToast = onToast
    )
    val canExportImage = viewMode == MermaidCardMode.DIAGRAM &&
        (renderStatus == MermaidRenderStatus.MERMAID_JS || renderStatus == MermaidRenderStatus.OFFLINE_SVG)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("mermaid_diagram_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountTree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { diagramType },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val engineBadge = when (renderStatus) {
                        MermaidRenderStatus.MERMAID_JS -> "Mermaid.js v11"
                        MermaidRenderStatus.OFFLINE_SVG -> "Offline SVG Engine"
                        MermaidRenderStatus.ERROR -> "Syntax Issue"
                        MermaidRenderStatus.LOADING -> "Rendering…"
                    }
                    Text(
                        text = if (subtitle.isNotBlank()) "$subtitle · $engineBadge" else "$diagramType · $engineBadge",
                        style = MaterialTheme.typography.labelSmall,
                        color = when (renderStatus) {
                            MermaidRenderStatus.MERMAID_JS -> VerifiedEmerald
                            MermaidRenderStatus.OFFLINE_SVG -> BridgeAmber
                            MermaidRenderStatus.ERROR -> MaterialTheme.colorScheme.error
                            MermaidRenderStatus.LOADING -> MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Cycle Mermaid theme (Obsidian -> Blueprint -> Emerald)
                IconButton(
                    onClick = {
                        val presets = MermaidThemePreset.entries
                        theme = presets[(presets.indexOf(theme) + 1) % presets.size]
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mermaid_cycle_theme_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = stringResource(R.string.cd_mermaid_cycle_theme),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Toggle Diagram vs Source
                IconButton(
                    onClick = {
                        viewMode = if (viewMode == MermaidCardMode.DIAGRAM) {
                            MermaidCardMode.SOURCE
                        } else {
                            MermaidCardMode.DIAGRAM
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mermaid_toggle_source_button")
                ) {
                    Icon(
                        imageVector = if (viewMode == MermaidCardMode.DIAGRAM) Icons.Outlined.Code else Icons.Outlined.Preview,
                        contentDescription = stringResource(R.string.cd_mermaid_toggle_source),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Copy Mermaid source
                IconButton(
                    onClick = {
                        if (onCopyCode != null) {
                            onCopyCode(code)
                        } else {
                            copyToClipboard(context, title, code)
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mermaid_copy_source_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.cd_mermaid_copy),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Full-screen Studio
                IconButton(
                    onClick = {
                        if (onExpandStudio != null) onExpandStudio()
                        else showLocalStudio = true
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mermaid_expand_studio_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInFull,
                        contentDescription = stringResource(R.string.cd_mermaid_expand),
                        tint = VerifiedEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (viewMode == MermaidCardMode.DIAGRAM) {
                MermaidAnnotationToolbar(
                    activeTool = activeTool,
                    onSelectTool = { activeTool = it },
                    selectedColor = selectedDrawColor,
                    onSelectColor = { selectedDrawColor = it },
                    selectedStrokeStyle = selectedStrokeStyle,
                    onSelectStrokeStyle = { selectedStrokeStyle = it },
                    showAnnotations = showAnnotations,
                    onToggleShowAnnotations = { showAnnotations = !showAnnotations },
                    canEdit = canAnnotate,
                    board = board,
                    nodeCount = canvasController.renderedNodes.size,
                    onUndoStroke = {
                        if (board.strokes.isNotEmpty()) {
                            commitBoard(board.copy(strokes = board.strokes.dropLast(1)))
                        }
                    },
                    onClearStrokes = {
                        if (board.strokes.isNotEmpty()) {
                            commitBoard(board.copy(strokes = emptyList()))
                        }
                    },
                    onOpenNotesList = { showNotesListDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeight)
                ) {
                    MermaidCanvasWebView(
                        mermaidCode = code,
                        theme = theme,
                        controller = canvasController,
                        zoomCommandToken = zoomToken,
                        zoomCommandDelta = zoomDelta,
                        resetZoomToken = resetToken,
                        offlineFallbackToken = fallbackToken,
                        onZoomPercentChanged = { zoomPercent = it },
                        onRenderStatusChanged = { status, err ->
                            renderStatus = status
                            errorMessage = err
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    MermaidAnnotationCanvasOverlay(
                        board = board,
                        viewportMetrics = canvasController.viewportMetrics,
                        renderedNodes = canvasController.renderedNodes,
                        activeTool = activeTool,
                        selectedColor = selectedDrawColor,
                        selectedStrokeStyle = selectedStrokeStyle,
                        showAnnotations = showAnnotations,
                        canEdit = canAnnotate,
                        currentUserUid = currentUserUid,
                        currentUserName = currentUserName,
                        onAddStroke = { stroke ->
                            showAnnotations = true
                            commitBoard(board.copy(strokes = board.strokes + stroke))
                        },
                        onRemoveStroke = { strokeId ->
                            commitBoard(board.copy(strokes = board.strokes.filterNot { it.id == strokeId }))
                        },
                        onRequestPlaceSticky = { u, v, hitNode ->
                            pendingStickyPlacement = Triple(u, v, hitNode)
                        },
                        onMoveStickyNote = { noteId, newU, newV, newNode ->
                            val updatedNotes = board.notes.map { nt ->
                                if (nt.id == noteId) {
                                    nt.copy(
                                        u = newU,
                                        v = newV,
                                        anchoredNodeId = newNode?.id ?: nt.anchoredNodeId,
                                        anchoredNodeLabel = newNode?.label ?: nt.anchoredNodeLabel,
                                        updatedAtMillis = System.currentTimeMillis()
                                    )
                                } else nt
                            }
                            commitBoard(board.copy(notes = updatedNotes))
                        },
                        onTapStickyNote = { note ->
                            editingStickyNote = note
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (renderStatus == MermaidRenderStatus.LOADING) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                // Dedicated bottom action strip: Export PNG/SVG on left, Zoom/Fit controls on right (zero canvas occlusion)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianSurfaceElevated)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MermaidExportMenuButton(
                        enabled = canExportImage,
                        isExporting = exportHandler.isExporting,
                        onSelectFormat = exportHandler.exportAs,
                        buttonTestTag = "mermaid_export_image_button",
                        pngOptionTestTag = "mermaid_export_png_option",
                        svgOptionTestTag = "mermaid_export_svg_option"
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            zoomDelta = -0.2f
                            zoomToken += 1
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Remove,
                            contentDescription = stringResource(R.string.cd_mermaid_zoom_out),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "$zoomPercent%",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    IconButton(
                        onClick = { resetToken += 1 },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Outlined.FitScreen,
                            contentDescription = stringResource(R.string.cd_mermaid_zoom_reset),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            zoomDelta = 0.2f
                            zoomToken += 1
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.cd_mermaid_zoom_in),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (renderStatus == MermaidRenderStatus.ERROR && !errorMessage.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Mermaid syntax issue — inspect source or use structural preview.",
                            style = MaterialTheme.typography.labelSmall,
                            color = BridgeAmber,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { fallbackToken += 1 },
                            modifier = Modifier.testTag("mermaid_offline_fallback_button")
                        ) {
                            Text("Preview", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = highlightedSource,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (pendingStickyPlacement != null || editingStickyNote != null) {
        val placement = pendingStickyPlacement
        val editing = editingStickyNote
        StickyNoteEditorDialog(
            initialNote = editing,
            initialU = placement?.first ?: editing?.u ?: 0.5f,
            initialV = placement?.second ?: editing?.v ?: 0.5f,
            initialAnchoredNode = placement?.third,
            renderedNodes = canvasController.renderedNodes,
            canEdit = canAnnotate,
            currentUserUid = currentUserUid,
            currentUserName = currentUserName,
            onDismiss = {
                pendingStickyPlacement = null
                editingStickyNote = null
            },
            onSave = { savedNote ->
                showAnnotations = true
                val updatedNotes = if (board.notes.any { it.id == savedNote.id }) {
                    board.notes.map { if (it.id == savedNote.id) savedNote else it }
                } else {
                    board.notes + savedNote
                }
                commitBoard(board.copy(notes = updatedNotes))
                pendingStickyPlacement = null
                editingStickyNote = null
                if (activeTool == MermaidAnnotationTool.STICKY) {
                    activeTool = MermaidAnnotationTool.INTERACT
                }
            },
            onDelete = { idToDelete ->
                commitBoard(board.copy(notes = board.notes.filterNot { it.id == idToDelete }))
                editingStickyNote = null
            }
        )
    }

    if (showNotesListDialog) {
        BrainstormNotesListDialog(
            diagramTitle = title.ifBlank { diagramType },
            board = board,
            renderedNodes = canvasController.renderedNodes,
            canEdit = canAnnotate,
            onDismiss = { showNotesListDialog = false },
            onEditNote = { note ->
                showNotesListDialog = false
                editingStickyNote = note
            },
            onAddNoteOnNode = { node ->
                showNotesListDialog = false
                val u = node?.let { (it.u + it.w * 0.75f).coerceIn(0.1f, 0.9f) } ?: 0.5f
                val v = node?.let { (it.v + it.h * 0.25f).coerceIn(0.1f, 0.9f) } ?: 0.4f
                pendingStickyPlacement = Triple(u, v, node)
            },
            onClearAllBoard = {
                commitBoard(board.copy(strokes = emptyList(), notes = emptyList()))
                showNotesListDialog = false
            }
        )
    }

    if (showLocalStudio) {
        val localSpec = MermaidDiagramSpec(
            id = "inline_diagram",
            title = title,
            subtitle = subtitle.ifBlank { diagramType },
            category = MermaidDiagramCategory.EXTRACTED,
            code = code,
            suggestedFileName = "architecture_diagram.mmd"
        )
        MermaidStudioDialog(
            diagrams = listOf(localSpec),
            initialDiagramId = "inline_diagram",
            boardsByDiagramId = mapOf("inline_diagram" to board),
            canAnnotate = canAnnotate,
            currentUserUid = currentUserUid,
            currentUserName = currentUserName,
            onBoardChanged = { _, updatedBoard -> commitBoard(updatedBoard) },
            onDismiss = { showLocalStudio = false },
            onSaveToRepo = null,
            onToast = { msg -> onToast?.invoke(msg) }
        )
    }
}

/**
 * Dynamic Composable view that consumes the live or completed output of the research synthesis
 * pipeline (`stages` and `synthesisMarkdown`), automatically extracts AI-emitted Mermaid.js syntax
 * diagrams alongside live synthesis & model architecture blueprints, and renders them dynamically
 * inside a sandboxed Mermaid.js WebView without reloading the page.
 */
@Composable
fun ResearchSynthesisDiagramView(
    stages: List<StageRecord>,
    synthesisMarkdown: String = "",
    project: ResearchProject? = null,
    draftTitle: String = "",
    routingPrefs: RoutingPreferences = RoutingPreferences(),
    roundtableItems: List<RoundtableItem> = emptyList(),
    isPipelineRunning: Boolean = false,
    initiallyExpanded: Boolean = true,
    canSaveToRepo: Boolean = false,
    currentUserUid: String = "",
    currentUserName: String = "Researcher",
    onSaveDiagramToRepo: ((relativePath: String, content: String) -> Unit)? = null,
    onSaveDiagramBoard: ((relativePath: String, boardJson: String) -> Unit)? = null,
    onToast: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionKey = project?.id ?: "live_session"
    val diagrams = remember(
        project?.id,
        project?.detectedDomain,
        project?.complexityScore,
        project?.gapVerdict,
        project?.latestPrdVersion,
        project?.filesJson,
        stages,
        synthesisMarkdown,
        draftTitle,
        routingPrefs,
        roundtableItems
    ) {
        MermaidDiagramEngine.collectSynthesisPipelineDiagrams(
            stages = stages,
            synthesisMarkdown = synthesisMarkdown,
            project = project,
            fallbackTitle = draftTitle,
            routingPrefs = routingPrefs,
            roundtableItems = roundtableItems
        )
    }

    val extractedCount = remember(diagrams) {
        diagrams.count { !it.id.startsWith("auto_") }
    }

    var expanded by remember(sessionKey) { mutableStateOf(initiallyExpanded) }
    var userPinnedTab by remember(sessionKey) { mutableStateOf(false) }
    var selectedDiagramId by remember(sessionKey) {
        mutableStateOf(diagrams.firstOrNull()?.id.orEmpty())
    }
    var showStudio by remember { mutableStateOf(false) }

    // Dynamically auto-focus newly emitted AI Mermaid diagrams from the synthesis pipeline
    LaunchedEffect(diagrams.firstOrNull()?.id, extractedCount) {
        val firstId = diagrams.firstOrNull()?.id.orEmpty()
        if (firstId.isNotBlank() && (!userPinnedTab || diagrams.none { it.id == selectedDiagramId })) {
            selectedDiagramId = firstId
        }
    }

    val selectedDiagram = diagrams.firstOrNull { it.id == selectedDiagramId } ?: diagrams.firstOrNull() ?: return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            1.dp,
            if (isPipelineRunning) VerifiedEmerald.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("research_synthesis_diagram_view")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("architecture_visualizer_toggle"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountTree,
                    contentDescription = stringResource(R.string.cd_mermaid_visualizer),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.mermaid_visualizer_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isPipelineRunning) {
                            TelemetryPill(
                                text = stringResource(R.string.mermaid_live_rendering),
                                accentColor = VerifiedEmerald
                            )
                        }
                    }
                    Text(
                        text = if (extractedCount > 0) {
                            "$extractedCount pipeline Mermaid diagram(s) + ${diagrams.size - extractedCount} live blueprint(s)"
                        } else {
                            "${diagrams.size} dynamic Mermaid diagram(s) · Synthesis & Architecture"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = VerifiedEmerald,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { showStudio = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("architecture_visualizer_open_studio_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInFull,
                        contentDescription = stringResource(R.string.cd_mermaid_expand),
                        tint = VerifiedEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(R.string.cd_mermaid_visualizer),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(diagrams, key = { it.id }) { spec ->
                            FilterChip(
                                selected = spec.id == selectedDiagram.id,
                                onClick = {
                                    userPinnedTab = true
                                    selectedDiagramId = spec.id
                                },
                                label = {
                                    Text(
                                        text = spec.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("mermaid_tab_${spec.category.name.lowercase()}")
                            )
                        }
                    }

                    val projectFiles = remember(project?.filesJson) { project?.parsedFiles().orEmpty() }
                    val selectedBoardPath = remember(selectedDiagram.id, selectedDiagram.title, selectedDiagram.suggestedFileName) {
                        MermaidBoardCodec.boardRepoPath(selectedDiagram.title, selectedDiagram.suggestedFileName)
                    }
                    val persistedSelectedBoard = remember(projectFiles, selectedBoardPath) {
                        val fileContent = projectFiles.firstOrNull {
                            it.relativePath.equals(selectedBoardPath, ignoreCase = true)
                        }?.content
                        MermaidBoardCodec.decodeBoard(fileContent)
                            ?: DiagramAnnotationBoard(
                                diagramId = selectedDiagram.id,
                                diagramTitle = selectedDiagram.title
                            )
                    }

                    MermaidDiagramCard(
                        code = selectedDiagram.code,
                        title = selectedDiagram.title,
                        subtitle = selectedDiagram.subtitle,
                        canvasHeight = 320.dp,
                        initialBoard = persistedSelectedBoard,
                        canAnnotate = canSaveToRepo || project == null,
                        currentUserUid = currentUserUid,
                        currentUserName = currentUserName,
                        onBoardChanged = { updatedBoard ->
                            if (canSaveToRepo && onSaveDiagramBoard != null) {
                                onSaveDiagramBoard(selectedBoardPath, MermaidBoardCodec.encodeBoard(updatedBoard))
                            }
                        },
                        onCopyCode = { src ->
                            copyToClipboard(context, selectedDiagram.title, src)
                            onToast("Copied Mermaid source for '${selectedDiagram.title}'.")
                        },
                        onExpandStudio = { showStudio = true },
                        onToast = onToast
                    )
                }
            }
        }
    }

    if (showStudio) {
        val projectFiles = remember(project?.filesJson) { project?.parsedFiles().orEmpty() }
        val boardsMap = remember(diagrams, projectFiles) {
            diagrams.associate { spec ->
                val path = MermaidBoardCodec.boardRepoPath(spec.title, spec.suggestedFileName)
                val decoded = MermaidBoardCodec.decodeBoard(
                    projectFiles.firstOrNull { it.relativePath.equals(path, ignoreCase = true) }?.content
                ) ?: DiagramAnnotationBoard(diagramId = spec.id, diagramTitle = spec.title)
                spec.id to decoded
            }
        }
        MermaidStudioDialog(
            diagrams = diagrams,
            initialDiagramId = selectedDiagram.id,
            boardsByDiagramId = boardsMap,
            canAnnotate = canSaveToRepo || project == null,
            currentUserUid = currentUserUid,
            currentUserName = currentUserName,
            onBoardChanged = { spec, updatedBoard ->
                if (canSaveToRepo && onSaveDiagramBoard != null) {
                    val path = MermaidBoardCodec.boardRepoPath(spec.title, spec.suggestedFileName)
                    onSaveDiagramBoard(path, MermaidBoardCodec.encodeBoard(updatedBoard))
                }
            },
            onDismiss = { showStudio = false },
            onSaveToRepo = if (canSaveToRepo && onSaveDiagramToRepo != null) {
                { fileName, code ->
                    val path = "docs/diagrams/$fileName"
                    onSaveDiagramToRepo(path, code)
                    onToast("Saved diagram to $path")
                }
            } else null,
            onToast = onToast
        )
    }
}

/**
 * Backwards-compatible alias delegating to [ResearchSynthesisDiagramView].
 */
@Composable
fun ResearchArchitectureVisualizerCard(
    project: ResearchProject,
    stages: List<StageRecord>,
    routingPrefs: RoutingPreferences,
    roundtableItems: List<RoundtableItem>,
    canSaveToRepo: Boolean,
    onSaveDiagramToRepo: ((relativePath: String, content: String) -> Unit)?,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ResearchSynthesisDiagramView(
        stages = stages,
        synthesisMarkdown = project.chatAnswer,
        project = project,
        draftTitle = project.title,
        routingPrefs = routingPrefs,
        roundtableItems = roundtableItems,
        isPipelineRunning = false,
        initiallyExpanded = true,
        canSaveToRepo = canSaveToRepo,
        onSaveDiagramToRepo = onSaveDiagramToRepo,
        onToast = onToast,
        modifier = modifier
    )
}

/**
 * Full-screen Interactive Mermaid.js Studio Dialog with zoom/pan, theme switcher,
 * diagram selector, and live Mermaid syntax editor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MermaidStudioDialog(
    diagrams: List<MermaidDiagramSpec>,
    initialDiagramId: String,
    boardsByDiagramId: Map<String, DiagramAnnotationBoard> = emptyMap(),
    canAnnotate: Boolean = true,
    currentUserUid: String = "",
    currentUserName: String = "Researcher",
    onBoardChanged: ((MermaidDiagramSpec, DiagramAnnotationBoard) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSaveToRepo: ((fileName: String, code: String) -> Unit)?,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedId by remember(diagrams, initialDiagramId) {
        mutableStateOf(
            diagrams.firstOrNull { it.id == initialDiagramId }?.id
                ?: diagrams.firstOrNull()?.id.orEmpty()
        )
    }
    val activeSpec = diagrams.firstOrNull { it.id == selectedId } ?: diagrams.firstOrNull() ?: return

    val canvasController = rememberMermaidCanvasController()
    var draftCode by remember(activeSpec.id, activeSpec.code) { mutableStateOf(activeSpec.code) }
    var theme by remember { mutableStateOf(MermaidThemePreset.OBSIDIAN) }
    var editingSource by remember { mutableStateOf(false) }
    var renderStatus by remember { mutableStateOf(MermaidRenderStatus.LOADING) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var zoomToken by remember { mutableStateOf(0) }
    var zoomDelta by remember { mutableStateOf(0f) }
    var resetToken by remember { mutableStateOf(0) }
    val visualTransformation = remember { SyntaxHighlightVisualTransformation("mmd") }

    // Studio collaborative brainstorming layer state
    val incomingBoard = boardsByDiagramId[activeSpec.id]
    var studioBoard by remember(activeSpec.id) {
        mutableStateOf(
            incomingBoard ?: DiagramAnnotationBoard(
                diagramId = activeSpec.id,
                diagramTitle = activeSpec.title
            )
        )
    }
    LaunchedEffect(incomingBoard) {
        if (incomingBoard != null && incomingBoard != studioBoard) {
            studioBoard = incomingBoard
        }
    }
    var studioTool by remember { mutableStateOf(MermaidAnnotationTool.INTERACT) }
    var studioDrawColor by remember { mutableStateOf(MermaidAnnotationPalettes.drawColors.first()) }
    var studioStrokeStyle by remember { mutableStateOf(AnnotationStrokeStyle.PEN) }
    var showStudioAnnotations by remember { mutableStateOf(true) }
    var showStudioNotesList by remember { mutableStateOf(false) }
    var pendingStudioSticky by remember { mutableStateOf<Triple<Float, Float, DiagramRenderedNode?>?>(null) }
    var editingStudioSticky by remember { mutableStateOf<DiagramStickyNote?>(null) }

    fun commitStudioBoard(updated: DiagramAnnotationBoard) {
        val stamped = updated.copy(
            diagramId = activeSpec.id,
            diagramTitle = activeSpec.title,
            updatedAtMillis = System.currentTimeMillis()
        )
        studioBoard = stamped
        onBoardChanged?.invoke(activeSpec, stamped)
    }

    val exportHandler = rememberMermaidImageExportHandler(
        controller = canvasController,
        diagramTitle = activeSpec.title,
        overlayJsonProvider = {
            if (showStudioAnnotations && !studioBoard.isEmpty) MermaidBoardCodec.encodeBoard(studioBoard) else null
        },
        onToast = onToast
    )
    val canExportImage = !editingSource &&
        (renderStatus == MermaidRenderStatus.MERMAID_JS || renderStatus == MermaidRenderStatus.OFFLINE_SVG)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("close_mermaid_studio_button")
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_close))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeSpec.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${activeSpec.subtitle} · ${
                                        when (renderStatus) {
                                            MermaidRenderStatus.MERMAID_JS -> "Mermaid.js v11"
                                            MermaidRenderStatus.OFFLINE_SVG -> "Offline SVG Engine"
                                            MermaidRenderStatus.ERROR -> "Syntax Error"
                                            MermaidRenderStatus.LOADING -> "Rendering…"
                                        }
                                    }",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (renderStatus) {
                                        MermaidRenderStatus.MERMAID_JS -> VerifiedEmerald
                                        MermaidRenderStatus.OFFLINE_SVG -> BridgeAmber
                                        MermaidRenderStatus.ERROR -> MaterialTheme.colorScheme.error
                                        MermaidRenderStatus.LOADING -> MaterialTheme.colorScheme.primary
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    copyToClipboard(context, activeSpec.title, draftCode)
                                    onToast("Copied Mermaid diagram source.")
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("mermaid_studio_copy_button")
                            ) {
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(R.string.cd_mermaid_copy),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (diagrams.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(diagrams, key = { it.id }) { spec ->
                                    FilterChip(
                                        selected = spec.id == activeSpec.id,
                                        onClick = { selectedId = spec.id },
                                        label = { Text(spec.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = !editingSource,
                                onClick = { editingSource = false },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Preview, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("Diagram") },
                                modifier = Modifier.testTag("mermaid_studio_tab_diagram")
                            )
                            FilterChip(
                                selected = editingSource,
                                onClick = { editingSource = true },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("Live Editor") },
                                modifier = Modifier.testTag("mermaid_studio_tab_editor")
                            )
                            MermaidThemePreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = theme == preset,
                                    onClick = { theme = preset },
                                    label = { Text(preset.label) },
                                    modifier = Modifier.testTag("mermaid_theme_${preset.id}")
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!editingSource) {
                            IconButton(
                                onClick = {
                                    zoomDelta = -0.25f
                                    zoomToken += 1
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("mermaid_studio_zoom_out")
                            ) {
                                Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.cd_mermaid_zoom_out), modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { resetToken += 1 },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("mermaid_studio_zoom_reset")
                            ) {
                                Icon(Icons.Outlined.FitScreen, contentDescription = stringResource(R.string.cd_mermaid_zoom_reset), modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    zoomDelta = 0.25f
                                    zoomToken += 1
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("mermaid_studio_zoom_in")
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.cd_mermaid_zoom_in), modifier = Modifier.size(18.dp))
                            }

                            MermaidExportMenuButton(
                                enabled = canExportImage,
                                isExporting = exportHandler.isExporting,
                                onSelectFormat = exportHandler.exportAs,
                                buttonTestTag = "mermaid_studio_export_image_button",
                                pngOptionTestTag = "mermaid_studio_export_png_option",
                                svgOptionTestTag = "mermaid_studio_export_svg_option",
                                useOutlinedStyle = true
                            )
                        } else if (draftCode != activeSpec.code) {
                            OutlinedButton(
                                onClick = { draftCode = activeSpec.code },
                                modifier = Modifier.testTag("mermaid_studio_reset_source")
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }

                        if (onSaveToRepo != null) {
                            Button(
                                onClick = { onSaveToRepo(activeSpec.suggestedFileName, draftCode) },
                                modifier = Modifier.testTag("mermaid_studio_save_repo_button")
                            ) {
                                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save .mmd")
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!editingSource) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        MermaidAnnotationToolbar(
                            activeTool = studioTool,
                            onSelectTool = { studioTool = it },
                            selectedColor = studioDrawColor,
                            onSelectColor = { studioDrawColor = it },
                            selectedStrokeStyle = studioStrokeStyle,
                            onSelectStrokeStyle = { studioStrokeStyle = it },
                            showAnnotations = showStudioAnnotations,
                            onToggleShowAnnotations = { showStudioAnnotations = !showStudioAnnotations },
                            canEdit = canAnnotate,
                            board = studioBoard,
                            nodeCount = canvasController.renderedNodes.size,
                            onUndoStroke = {
                                if (studioBoard.strokes.isNotEmpty()) {
                                    commitStudioBoard(studioBoard.copy(strokes = studioBoard.strokes.dropLast(1)))
                                }
                            },
                            onClearStrokes = {
                                if (studioBoard.strokes.isNotEmpty()) {
                                    commitStudioBoard(studioBoard.copy(strokes = emptyList()))
                                }
                            },
                            onOpenNotesList = { showStudioNotesList = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            MermaidCanvasWebView(
                                mermaidCode = draftCode,
                                theme = theme,
                                controller = canvasController,
                                zoomCommandToken = zoomToken,
                                zoomCommandDelta = zoomDelta,
                                resetZoomToken = resetToken,
                                onRenderStatusChanged = { status, err ->
                                    renderStatus = status
                                    errorMsg = err
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            MermaidAnnotationCanvasOverlay(
                                board = studioBoard,
                                viewportMetrics = canvasController.viewportMetrics,
                                renderedNodes = canvasController.renderedNodes,
                                activeTool = studioTool,
                                selectedColor = studioDrawColor,
                                selectedStrokeStyle = studioStrokeStyle,
                                showAnnotations = showStudioAnnotations,
                                canEdit = canAnnotate,
                                currentUserUid = currentUserUid,
                                currentUserName = currentUserName,
                                onAddStroke = { stroke ->
                                    showStudioAnnotations = true
                                    commitStudioBoard(studioBoard.copy(strokes = studioBoard.strokes + stroke))
                                },
                                onRemoveStroke = { strokeId ->
                                    commitStudioBoard(studioBoard.copy(strokes = studioBoard.strokes.filterNot { it.id == strokeId }))
                                },
                                onRequestPlaceSticky = { u, v, hitNode ->
                                    pendingStudioSticky = Triple(u, v, hitNode)
                                },
                                onMoveStickyNote = { noteId, newU, newV, newNode ->
                                    val updatedNotes = studioBoard.notes.map { nt ->
                                        if (nt.id == noteId) {
                                            nt.copy(
                                                u = newU,
                                                v = newV,
                                                anchoredNodeId = newNode?.id ?: nt.anchoredNodeId,
                                                anchoredNodeLabel = newNode?.label ?: nt.anchoredNodeLabel,
                                                updatedAtMillis = System.currentTimeMillis()
                                            )
                                        } else nt
                                    }
                                    commitStudioBoard(studioBoard.copy(notes = updatedNotes))
                                },
                                onTapStickyNote = { note -> editingStudioSticky = note },
                                modifier = Modifier.fillMaxSize()
                            )
                            if (renderStatus == MermaidRenderStatus.LOADING) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (renderStatus == MermaidRenderStatus.ERROR && !errorMsg.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = "Syntax error: ${errorMsg.orEmpty()} — switch to Live Editor to fix.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Edit Mermaid.js syntax below, then switch back to Diagram to view live updates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BasicTextField(
                                value = draftCode,
                                onValueChange = { draftCode = it },
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                visualTransformation = visualTransformation,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                                    .testTag("mermaid_live_editor_field")
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingStudioSticky != null || editingStudioSticky != null) {
        val placement = pendingStudioSticky
        val editing = editingStudioSticky
        StickyNoteEditorDialog(
            initialNote = editing,
            initialU = placement?.first ?: editing?.u ?: 0.5f,
            initialV = placement?.second ?: editing?.v ?: 0.5f,
            initialAnchoredNode = placement?.third,
            renderedNodes = canvasController.renderedNodes,
            canEdit = canAnnotate,
            currentUserUid = currentUserUid,
            currentUserName = currentUserName,
            onDismiss = {
                pendingStudioSticky = null
                editingStudioSticky = null
            },
            onSave = { savedNote ->
                showStudioAnnotations = true
                val updatedNotes = if (studioBoard.notes.any { it.id == savedNote.id }) {
                    studioBoard.notes.map { if (it.id == savedNote.id) savedNote else it }
                } else {
                    studioBoard.notes + savedNote
                }
                commitStudioBoard(studioBoard.copy(notes = updatedNotes))
                pendingStudioSticky = null
                editingStudioSticky = null
                if (studioTool == MermaidAnnotationTool.STICKY) {
                    studioTool = MermaidAnnotationTool.INTERACT
                }
            },
            onDelete = { idToDelete ->
                commitStudioBoard(studioBoard.copy(notes = studioBoard.notes.filterNot { it.id == idToDelete }))
                editingStudioSticky = null
            }
        )
    }

    if (showStudioNotesList) {
        BrainstormNotesListDialog(
            diagramTitle = activeSpec.title,
            board = studioBoard,
            renderedNodes = canvasController.renderedNodes,
            canEdit = canAnnotate,
            onDismiss = { showStudioNotesList = false },
            onEditNote = { note ->
                showStudioNotesList = false
                editingStudioSticky = note
            },
            onAddNoteOnNode = { node ->
                showStudioNotesList = false
                val u = node?.let { (it.u + it.w * 0.75f).coerceIn(0.1f, 0.9f) } ?: 0.5f
                val v = node?.let { (it.v + it.h * 0.25f).coerceIn(0.1f, 0.9f) } ?: 0.4f
                pendingStudioSticky = Triple(u, v, node)
            },
            onClearAllBoard = {
                commitStudioBoard(studioBoard.copy(strokes = emptyList(), notes = emptyList()))
                showStudioNotesList = false
            }
        )
    }
}
