package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.Base64
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Polyline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

enum class MermaidImageExportFormat(
    val shortLabel: String,
    val extension: String,
    val mimeType: String
) {
    PNG(
        shortLabel = "PNG",
        extension = "png",
        mimeType = "image/png"
    ),
    SVG(
        shortLabel = "SVG",
        extension = "svg",
        mimeType = "image/svg+xml"
    )
}

/**
 * Controller bridging a live [MermaidCanvasWebView] to Compose so diagrams can be exported
 * on demand as crisp standalone SVG or 2x rasterized PNG bytes (with native WebView Bitmap
 * fallback if canvas rasterization ever fails).
 */
@Stable
class MermaidCanvasController {

    private var webView: WebView? = null
    private var pageLoaded: Boolean = false
    private val seq = AtomicInteger(0)
    private val pendingPngContinuations = ConcurrentHashMap<String, CancellableContinuation<ByteArray?>>()

    var viewportMetrics by mutableStateOf(DiagramViewportMetrics())
        private set

    var renderedNodes by mutableStateOf<List<DiagramRenderedNode>>(emptyList())
        private set

    internal fun attach(view: WebView, loaded: Boolean) {
        webView = view
        pageLoaded = loaded
    }

    internal fun setPageLoaded(loaded: Boolean) {
        pageLoaded = loaded
    }

    internal fun detach(view: WebView) {
        if (webView === view) {
            webView = null
            pageLoaded = false
        }
        val keys = pendingPngContinuations.keys.toList()
        keys.forEach { key ->
            pendingPngContinuations.remove(key)?.let { cont ->
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    internal fun handleConsoleMessage(msg: String): Boolean {
        return when {
            msg.startsWith("VORTEX_MERMAID:VIEWPORT:") -> {
                val parts = msg.removePrefix("VORTEX_MERMAID:VIEWPORT:").split(',')
                if (parts.size >= 4) {
                    val l = parts[0].toFloatOrNull()
                    val t = parts[1].toFloatOrNull()
                    val w = parts[2].toFloatOrNull()
                    val h = parts[3].toFloatOrNull()
                    val nodeFlag = parts.getOrNull(4)?.toIntOrNull() ?: 0
                    if (l != null && t != null && w != null && h != null && w > 0.001f && h > 0.001f) {
                        viewportMetrics = DiagramViewportMetrics(
                            relLeft = l,
                            relTop = t,
                            relWidth = w,
                            relHeight = h,
                            ready = true
                        )
                    }
                    if (nodeFlag > 0) {
                        fetchRenderedNodes()
                    }
                }
                true
            }
            msg.startsWith("VORTEX_MERMAID:EXPORT_PNG_READY:") -> {
                val id = msg.removePrefix("VORTEX_MERMAID:EXPORT_PNG_READY:").trim()
                resolvePngFromWebView(id)
                true
            }
            msg.startsWith("VORTEX_MERMAID:EXPORT_PNG_FALLBACK:") -> {
                val id = msg.removePrefix("VORTEX_MERMAID:EXPORT_PNG_FALLBACK:").trim()
                resolvePngWithNativeFallback(id)
                true
            }
            msg.startsWith("VORTEX_MERMAID:EXPORT_ERR:") -> {
                val rest = msg.removePrefix("VORTEX_MERMAID:EXPORT_ERR:")
                val id = rest.substringBefore(':').trim()
                pendingPngContinuations.remove(id)?.let { cont ->
                    if (cont.isActive) cont.resume(null)
                }
                true
            }
            else -> false
        }
    }

    private fun fetchRenderedNodes() {
        val view = webView ?: return
        view.post {
            view.evaluateJavascript("window.vortexGetNodesJson();") { raw ->
                val json = decodeJsString(raw)
                if (!json.isNullOrBlank()) {
                    renderedNodes = MermaidBoardCodec.decodeNodes(json)
                }
            }
        }
    }

    suspend fun exportDiagram(
        format: MermaidImageExportFormat,
        overlayJson: String? = null
    ): Result<ByteArray> {
        val view = webView
            ?: return Result.failure(IllegalStateException("Diagram canvas is not active. Switch to Diagram view first."))
        if (!pageLoaded) {
            return Result.failure(IllegalStateException("Diagram is still rendering. Please try again in a moment."))
        }
        return when (format) {
            MermaidImageExportFormat.SVG -> exportSvg(view, overlayJson)
            MermaidImageExportFormat.PNG -> exportPng(view, overlayJson)
        }
    }

    private suspend fun exportSvg(view: WebView, overlayJson: String?): Result<ByteArray> {
        val safeOverlay = MermaidDiagramEngine.toSafeJsStringLiteral(overlayJson.orEmpty())
        val svgText = withTimeoutOrNull(4_000L) {
            suspendCancellableCoroutine<String?> { cont ->
                view.post {
                    view.evaluateJavascript("window.vortexExportSvg($safeOverlay);") { raw ->
                        if (cont.isActive) {
                            cont.resume(decodeJsString(raw))
                        }
                    }
                }
            }
        }
        if (svgText.isNullOrBlank() || !svgText.contains("<svg", ignoreCase = true)) {
            return Result.failure(IllegalStateException("No rendered SVG diagram found to export."))
        }
        return Result.success(svgText.toByteArray(Charsets.UTF_8))
    }

    private suspend fun exportPng(view: WebView, overlayJson: String?): Result<ByteArray> {
        val exportId = "exp_${seq.incrementAndGet()}"
        val safeOverlay = MermaidDiagramEngine.toSafeJsStringLiteral(overlayJson.orEmpty())
        val jsBytes = withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine<ByteArray?> { cont ->
                pendingPngContinuations[exportId] = cont
                cont.invokeOnCancellation { pendingPngContinuations.remove(exportId) }
                val safeId = MermaidDiagramEngine.toSafeJsStringLiteral(exportId)
                view.post {
                    view.evaluateJavascript("window.vortexExportPngAsync($safeId, $safeOverlay);", null)
                }
            }
        }
        if (jsBytes != null && jsBytes.isNotEmpty()) {
            return Result.success(jsBytes)
        }
        pendingPngContinuations.remove(exportId)
        val fallback = captureWebViewBitmapPng(view)
        return if (fallback != null && fallback.isNotEmpty()) {
            Result.success(fallback)
        } else {
            Result.failure(IllegalStateException("Unable to rasterize diagram to PNG."))
        }
    }

    private fun resolvePngFromWebView(exportId: String) {
        val view = webView
        val cont = pendingPngContinuations.remove(exportId) ?: return
        if (view == null) {
            if (cont.isActive) cont.resume(null)
            return
        }
        val safeId = MermaidDiagramEngine.toSafeJsStringLiteral(exportId)
        view.post {
            view.evaluateJavascript("window.vortexTakeExportPayload($safeId);") { raw ->
                val dataUrl = decodeJsString(raw)
                val base64 = dataUrl?.substringAfter("base64,", "")
                val bytes = if (!base64.isNullOrBlank()) {
                    runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
                } else null
                if (bytes != null && bytes.isNotEmpty()) {
                    if (cont.isActive) cont.resume(bytes)
                } else {
                    val fallback = captureWebViewBitmapPng(view)
                    if (cont.isActive) cont.resume(fallback)
                }
            }
        }
    }

    private fun resolvePngWithNativeFallback(exportId: String) {
        val view = webView
        val cont = pendingPngContinuations.remove(exportId) ?: return
        if (view == null) {
            if (cont.isActive) cont.resume(null)
            return
        }
        view.post {
            val fallback = captureWebViewBitmapPng(view)
            if (cont.isActive) cont.resume(fallback)
        }
    }

    private fun captureWebViewBitmapPng(view: WebView): ByteArray? {
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return null
        return runCatching {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                view.draw(canvas)
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }

    companion object {
        internal fun decodeJsString(raw: String?): String? {
            if (raw.isNullOrBlank() || raw == "null" || raw == "undefined") return null
            return runCatching {
                val arr = JSONArray("[$raw]")
                if (arr.isNull(0)) null else arr.optString(0)
            }.getOrNull()
        }

        fun buildExportFileName(title: String, format: MermaidImageExportFormat): String {
            val slug = title.lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .take(48)
                .ifBlank { "mermaid_diagram" }
            return "$slug.${format.extension}"
        }

        fun formatByteSize(bytes: Int): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${(bytes + 512) / 1024} KB"
            else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f))
        }
    }
}

@Composable
fun rememberMermaidCanvasController(): MermaidCanvasController = remember { MermaidCanvasController() }

@Stable
class MermaidImageExportHandler(
    val isExporting: Boolean,
    val exportAs: (MermaidImageExportFormat) -> Unit
)

/**
 * Coordinates zero-permission Storage Access Framework (CreateDocument) image exports
 * for PNG and SVG diagrams without requesting broad device storage permissions.
 */
@Composable
fun rememberMermaidImageExportHandler(
    controller: MermaidCanvasController,
    diagramTitle: String,
    overlayJsonProvider: (() -> String?)? = null,
    onToast: ((String) -> Unit)? = null
): MermaidImageExportHandler {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingFormat by remember { mutableStateOf<MermaidImageExportFormat?>(null) }

    fun notify(message: String) {
        if (onToast != null) {
            onToast(message)
        } else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun writeExportToUri(uri: Uri?, format: MermaidImageExportFormat) {
        val bytes = pendingBytes
        pendingBytes = null
        pendingFormat = null
        if (uri == null || bytes == null) {
            isExporting = false
            return
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                writeBytesToUri(context, uri, bytes)
            }
            isExporting = false
            if (saved.isSuccess) {
                val sizeLabel = MermaidCanvasController.formatByteSize(bytes.size)
                notify("Saved ${format.shortLabel} diagram ($sizeLabel) to device.")
            } else {
                notify("Export failed: ${saved.exceptionOrNull()?.message ?: "Unable to write file."}")
            }
        }
    }

    val pngCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MermaidImageExportFormat.PNG.mimeType)
    ) { uri ->
        writeExportToUri(uri, MermaidImageExportFormat.PNG)
    }

    val svgCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MermaidImageExportFormat.SVG.mimeType)
    ) { uri ->
        writeExportToUri(uri, MermaidImageExportFormat.SVG)
    }

    return remember(controller, diagramTitle, isExporting, overlayJsonProvider) {
        MermaidImageExportHandler(
            isExporting = isExporting,
            exportAs = { format ->
                if (!isExporting) {
                    isExporting = true
                    scope.launch {
                        val overlayJson = overlayJsonProvider?.invoke()
                        val result = controller.exportDiagram(format, overlayJson)
                        result.onSuccess { bytes ->
                            pendingBytes = bytes
                            pendingFormat = format
                            val suggestedName = MermaidCanvasController.buildExportFileName(diagramTitle, format)
                            when (format) {
                                MermaidImageExportFormat.PNG -> pngCreateLauncher.launch(suggestedName)
                                MermaidImageExportFormat.SVG -> svgCreateLauncher.launch(suggestedName)
                            }
                        }.onFailure { err ->
                            isExporting = false
                            notify(err.message ?: "Diagram export failed.")
                        }
                    }
                }
            }
        )
    }
}

private fun writeBytesToUri(context: Context, uri: Uri, bytes: ByteArray): Result<Unit> = runCatching {
    val stream = context.contentResolver.openOutputStream(uri)
        ?: error("Device storage provider rejected the output file.")
    stream.use { out ->
        out.write(bytes)
        out.flush()
    }
}

/**
 * Reusable Export Image button + PNG/SVG format picker dropdown for both inline
 * [MermaidDiagramCard] and full-screen [MermaidStudioDialog].
 */
@Composable
fun MermaidExportMenuButton(
    enabled: Boolean,
    isExporting: Boolean,
    onSelectFormat: (MermaidImageExportFormat) -> Unit,
    buttonTestTag: String,
    pngOptionTestTag: String,
    svgOptionTestTag: String,
    useOutlinedStyle: Boolean = false,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val exportContentDesc = stringResource(R.string.cd_mermaid_export_image)

    Box(modifier = modifier) {
        val buttonContent: @Composable () -> Unit = {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (isExporting) {
                    stringResource(R.string.mermaid_exporting)
                } else {
                    stringResource(R.string.mermaid_export_image)
                },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1
            )
            if (!isExporting) {
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (useOutlinedStyle) {
            OutlinedButton(
                onClick = { menuExpanded = true },
                enabled = enabled && !isExporting,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = exportContentDesc }
                    .testTag(buttonTestTag)
            ) {
                buttonContent()
            }
        } else {
            FilledTonalButton(
                onClick = { menuExpanded = true },
                enabled = enabled && !isExporting,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = exportContentDesc }
                    .testTag(buttonTestTag)
            ) {
                buttonContent()
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.mermaid_export_png),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = stringResource(R.string.mermaid_export_png_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    menuExpanded = false
                    onSelectFormat(MermaidImageExportFormat.PNG)
                },
                modifier = Modifier.testTag(pngOptionTestTag)
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.mermaid_export_svg),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = stringResource(R.string.mermaid_export_svg_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Polyline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                onClick = {
                    menuExpanded = false
                    onSelectFormat(MermaidImageExportFormat.SVG)
                },
                modifier = Modifier.testTag(svgOptionTestTag)
            )
        }
    }
}
