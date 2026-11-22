package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.VerifiedEmerald
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Compact collaborative brainstorming toolbar shown directly above the Mermaid diagram canvas.
 * Lets users switch cleanly between Pan/Zoom, Freehand Draw, Node-Anchored Sticky Notes,
 * Eraser, Undo, Layer Visibility, and the Notes Overview sheet.
 */
@Composable
fun MermaidAnnotationToolbar(
    activeTool: MermaidAnnotationTool,
    onSelectTool: (MermaidAnnotationTool) -> Unit,
    selectedColor: AnnotationColorOption,
    onSelectColor: (AnnotationColorOption) -> Unit,
    selectedStrokeStyle: AnnotationStrokeStyle,
    onSelectStrokeStyle: (AnnotationStrokeStyle) -> Unit,
    showAnnotations: Boolean,
    onToggleShowAnnotations: () -> Unit,
    canEdit: Boolean,
    board: DiagramAnnotationBoard,
    nodeCount: Int,
    onUndoStroke: () -> Unit,
    onClearStrokes: () -> Unit,
    onOpenNotesList: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedTools by remember { mutableStateOf(activeTool != MermaidAnnotationTool.INTERACT) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianSurfaceElevated.copy(alpha = 0.92f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = expandedTools || activeTool != MermaidAnnotationTool.INTERACT,
                onClick = {
                    val next = !expandedTools
                    expandedTools = next
                    if (!next) onSelectTool(MermaidAnnotationTool.INTERACT)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Draw,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    val countSuffix = if (board.totalCount > 0) " (${board.totalCount})" else ""
                    Text(
                        text = stringResource(R.string.mermaid_brainstorm_label) + countSuffix,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier.testTag("mermaid_brainstorm_toggle_chip")
            )

            if (canEdit) {
                FilterChip(
                    selected = activeTool == MermaidAnnotationTool.DRAW,
                    onClick = {
                        expandedTools = true
                        onSelectTool(
                            if (activeTool == MermaidAnnotationTool.DRAW) MermaidAnnotationTool.INTERACT
                            else MermaidAnnotationTool.DRAW
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Draw,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = { Text(stringResource(R.string.mermaid_tool_draw), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("mermaid_tool_draw_chip")
                )

                FilterChip(
                    selected = activeTool == MermaidAnnotationTool.STICKY,
                    onClick = {
                        expandedTools = true
                        onSelectTool(
                            if (activeTool == MermaidAnnotationTool.STICKY) MermaidAnnotationTool.INTERACT
                            else MermaidAnnotationTool.STICKY
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.StickyNote2,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.mermaid_tool_sticky),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.testTag("mermaid_tool_sticky_chip")
                )

                if (expandedTools) {
                    FilterChip(
                        selected = activeTool == MermaidAnnotationTool.ERASE,
                        onClick = {
                            onSelectTool(
                                if (activeTool == MermaidAnnotationTool.ERASE) MermaidAnnotationTool.INTERACT
                                else MermaidAnnotationTool.ERASE
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.AutoFixHigh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.mermaid_tool_erase), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("mermaid_tool_erase_chip")
                    )

                    if (activeTool != MermaidAnnotationTool.INTERACT) {
                        FilterChip(
                            selected = false,
                            onClick = { onSelectTool(MermaidAnnotationTool.INTERACT) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.PanTool,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = { Text(stringResource(R.string.mermaid_tool_pan), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("mermaid_tool_pan_chip")
                        )
                    }
                }
            }

            if (board.notes.isNotEmpty() || expandedTools) {
                IconButton(
                    onClick = onOpenNotesList,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mermaid_brainstorm_notes_list_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FormatListBulleted,
                        contentDescription = stringResource(R.string.cd_mermaid_notes_list),
                        tint = BridgeAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (board.totalCount > 0) {
                IconButton(
                    onClick = onToggleShowAnnotations,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mermaid_toggle_layer_visibility_btn")
                ) {
                    Icon(
                        imageVector = if (showAnnotations) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = stringResource(R.string.cd_mermaid_toggle_annotations),
                        tint = if (showAnnotations) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Contextual Drawing Palette & Undo/Clear row
        AnimatedVisibility(visible = canEdit && activeTool == MermaidAnnotationTool.DRAW) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MermaidAnnotationPalettes.drawColors.forEach { swatch ->
                    val isSelected = swatch.hex.equals(selectedColor.hex, ignoreCase = true)
                    val swatchDesc = "Draw color ${swatch.name}"
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(swatch.color)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Black.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                            .clickable { onSelectColor(swatch) }
                            .semantics { contentDescription = swatchDesc }
                            .testTag("mermaid_draw_color_${swatch.name.lowercase()}")
                    )
                }

                Spacer(Modifier.width(4.dp))

                AnnotationStrokeStyle.entries.forEach { style ->
                    FilterChip(
                        selected = selectedStrokeStyle == style,
                        onClick = { onSelectStrokeStyle(style) },
                        label = { Text(style.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("mermaid_stroke_style_${style.name.lowercase()}")
                    )
                }

                if (board.strokes.isNotEmpty()) {
                    IconButton(
                        onClick = onUndoStroke,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("mermaid_undo_stroke_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Undo,
                            contentDescription = stringResource(R.string.cd_mermaid_undo_stroke),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    TextButton(
                        onClick = onClearStrokes,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("mermaid_clear_strokes_btn")
                    ) {
                        Text(
                            text = stringResource(R.string.mermaid_clear_drawings),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Contextual Sticky Note Node-Anchoring hint strip
        AnimatedVisibility(visible = canEdit && activeTool == MermaidAnnotationTool.STICKY) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint = BridgeAmber,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (nodeCount > 0) {
                        "Tap any highlighted diagram node ($nodeCount detected) to pin a collaborative sticky note."
                    } else {
                        "Tap anywhere on the diagram to place a collaborative sticky note."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = BridgeAmber,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        AnimatedVisibility(visible = canEdit && activeTool == MermaidAnnotationTool.ERASE) {
            Text(
                text = "Drag across drawn strokes to erase, or tap a sticky note to edit or remove it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
            )
        }
    }
}

/**
 * Interactive transparent overlay positioned directly on top of [MermaidCanvasWebView].
 * Projects SVG-normalized freehand strokes and draggable node-anchored digital sticky notes
 * onto the live rendered Mermaid diagram viewport.
 */
@Composable
fun MermaidAnnotationCanvasOverlay(
    board: DiagramAnnotationBoard,
    viewportMetrics: DiagramViewportMetrics,
    renderedNodes: List<DiagramRenderedNode>,
    activeTool: MermaidAnnotationTool,
    selectedColor: AnnotationColorOption,
    selectedStrokeStyle: AnnotationStrokeStyle,
    showAnnotations: Boolean,
    canEdit: Boolean,
    currentUserUid: String,
    currentUserName: String,
    onAddStroke: (DiagramStroke) -> Unit,
    onRemoveStroke: (String) -> Unit,
    onRequestPlaceSticky: (u: Float, v: Float, anchoredNode: DiagramRenderedNode?) -> Unit,
    onMoveStickyNote: (noteId: String, newU: Float, newV: Float, newAnchoredNode: DiagramRenderedNode?) -> Unit,
    onTapStickyNote: (DiagramStickyNote) -> Unit,
    modifier: Modifier = Modifier
) {
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val activeStrokePoints = remember { mutableStateListOf<DiagramStrokePoint>() }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { overlaySize = it }
            .testTag("mermaid_annotation_overlay")
    ) {
        val w = overlaySize.width.toFloat()
        val h = overlaySize.height.toFloat()

        // 1. Vector Canvas: Node target highlights (Sticky mode), anchor tethers, and freehand strokes
        val canvasInteractionModifier = when {
            !canEdit || activeTool == MermaidAnnotationTool.INTERACT -> Modifier
            activeTool == MermaidAnnotationTool.DRAW -> Modifier.pointerInput(
                viewportMetrics,
                overlaySize,
                selectedColor,
                selectedStrokeStyle,
                currentUserUid,
                currentUserName
            ) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        activeStrokePoints.clear()
                        val u = viewportMetrics.toDiagramU(startOffset.x, size.width.toFloat())
                        val v = viewportMetrics.toDiagramV(startOffset.y, size.height.toFloat())
                        activeStrokePoints.add(DiagramStrokePoint(u, v))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val u = viewportMetrics.toDiagramU(change.position.x, size.width.toFloat())
                        val v = viewportMetrics.toDiagramV(change.position.y, size.height.toFloat())
                        val last = activeStrokePoints.lastOrNull()
                        if (last == null || kotlin.math.abs(u - last.u) > 0.002f || kotlin.math.abs(v - last.v) > 0.002f) {
                            activeStrokePoints.add(DiagramStrokePoint(u, v))
                        }
                    },
                    onDragEnd = {
                        if (activeStrokePoints.isNotEmpty()) {
                            val pts = if (activeStrokePoints.size == 1) {
                                val p = activeStrokePoints.first()
                                listOf(p, DiagramStrokePoint(p.u + 0.003f, p.v + 0.003f))
                            } else {
                                activeStrokePoints.toList()
                            }
                            onAddStroke(
                                DiagramStroke(
                                    id = "stk_${UUID.randomUUID().toString().take(8)}",
                                    points = pts,
                                    colorHex = selectedColor.hex,
                                    widthDp = selectedStrokeStyle.widthDp,
                                    alpha = selectedStrokeStyle.alpha,
                                    authorUid = currentUserUid,
                                    authorName = currentUserName.ifBlank { "Researcher" },
                                    createdAtMillis = System.currentTimeMillis()
                                )
                            )
                            activeStrokePoints.clear()
                        }
                    },
                    onDragCancel = { activeStrokePoints.clear() }
                )
            }
            activeTool == MermaidAnnotationTool.STICKY -> Modifier.pointerInput(
                viewportMetrics,
                renderedNodes,
                overlaySize
            ) {
                detectTapGestures { tapOffset ->
                    val u = viewportMetrics.toDiagramU(tapOffset.x, size.width.toFloat())
                    val v = viewportMetrics.toDiagramV(tapOffset.y, size.height.toFloat())
                    val hitNode = MermaidBoardCodec.findNodeAt(renderedNodes, u, v)
                    onRequestPlaceSticky(u, v, hitNode)
                }
            }
            activeTool == MermaidAnnotationTool.ERASE -> Modifier.pointerInput(
                board.strokes,
                viewportMetrics,
                overlaySize
            ) {
                val eraseRadiusPx = with(density) { 22.dp.toPx() }
                fun eraseNear(pt: Offset) {
                    val ow = size.width.toFloat()
                    val oh = size.height.toFloat()
                    val hitStroke = board.strokes.lastOrNull { stroke ->
                        val pts = stroke.points
                        if (pts.isEmpty()) return@lastOrNull false
                        for (i in 0 until pts.size) {
                            val ax = viewportMetrics.toOverlayX(pts[i].u, ow)
                            val ay = viewportMetrics.toOverlayY(pts[i].v, oh)
                            val nextIdx = (i + 1).coerceAtMost(pts.lastIndex)
                            val bx = viewportMetrics.toOverlayX(pts[nextIdx].u, ow)
                            val by = viewportMetrics.toOverlayY(pts[nextIdx].v, oh)
                            if (MermaidBoardCodec.distanceToSegmentPx(pt.x, pt.y, ax, ay, bx, by) <= eraseRadiusPx) {
                                return@lastOrNull true
                            }
                        }
                        false
                    }
                    if (hitStroke != null) {
                        onRemoveStroke(hitStroke.id)
                    }
                }
                detectDragGestures(
                    onDragStart = { eraseNear(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        eraseNear(change.position)
                    }
                )
            }
            else -> Modifier
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(canvasInteractionModifier)
        ) {
            if (size.width <= 1f || size.height <= 1f) return@Canvas

            // A. Highlight discovered Mermaid diagram nodes in Sticky placement mode
            if (canEdit && activeTool == MermaidAnnotationTool.STICKY && viewportMetrics.ready) {
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                renderedNodes.forEach { node ->
                    val left = viewportMetrics.toOverlayX(node.u, size.width)
                    val top = viewportMetrics.toOverlayY(node.v, size.height)
                    val rectW = node.w * viewportMetrics.relWidth * size.width
                    val rectH = node.h * viewportMetrics.relHeight * size.height
                    if (rectW > 8f && rectH > 8f) {
                        drawRoundRect(
                            color = Color(0xFF00E5FF).copy(alpha = 0.10f),
                            topLeft = Offset(left - 3f, top - 3f),
                            size = Size(rectW + 6f, rectH + 6f),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                        drawRoundRect(
                            color = Color(0xFF00E5FF).copy(alpha = 0.72f),
                            topLeft = Offset(left - 3f, top - 3f),
                            size = Size(rectW + 6f, rectH + 6f),
                            cornerRadius = CornerRadius(10f, 10f),
                            style = Stroke(width = 2.dp.toPx(), pathEffect = dashEffect)
                        )
                        // Small pin target dot at top-right of each node
                        drawCircle(
                            color = Color(0xFFFFB74D),
                            radius = 4.dp.toPx(),
                            center = Offset(left + rectW, top)
                        )
                    }
                }
            }

            if (showAnnotations) {
                // B. Anchor tether lines connecting sticky notes to their anchored diagram node centers
                val tetherDash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                board.notes.forEach { note ->
                    if (note.anchoredNodeId.isNotBlank()) {
                        val node = renderedNodes.firstOrNull { it.id == note.anchoredNodeId }
                        if (node != null) {
                            val nodeCx = viewportMetrics.toOverlayX(node.centerU, size.width)
                            val nodeCy = viewportMetrics.toOverlayY(node.centerV, size.height)
                            val noteX = viewportMetrics.toOverlayX(note.u, size.width)
                            val noteY = viewportMetrics.toOverlayY(note.v, size.height)
                            val stickyColor = MermaidAnnotationPalettes.parseColor(note.colorHex, Color(0xFFFDE68A))
                            drawLine(
                                color = stickyColor.copy(alpha = 0.75f),
                                start = Offset(nodeCx, nodeCy),
                                end = Offset(noteX, noteY),
                                strokeWidth = 1.6.dp.toPx(),
                                pathEffect = tetherDash
                            )
                            drawCircle(
                                color = stickyColor,
                                radius = 4.dp.toPx(),
                                center = Offset(nodeCx, nodeCy)
                            )
                        }
                    }
                }

                // C. Committed freehand strokes
                board.strokes.forEach { stroke ->
                    drawNormalizedStroke(
                        points = stroke.points,
                        color = MermaidAnnotationPalettes.parseColor(stroke.colorHex, Color(0xFF00E5FF))
                            .copy(alpha = stroke.alpha.coerceIn(0.15f, 1f)),
                        strokeWidthPx = stroke.widthDp.dp.toPx(),
                        viewportMetrics = viewportMetrics
                    )
                }

                // D. Live in-progress stroke
                if (activeStrokePoints.isNotEmpty()) {
                    drawNormalizedStroke(
                        points = activeStrokePoints,
                        color = selectedColor.color.copy(alpha = selectedStrokeStyle.alpha),
                        strokeWidthPx = selectedStrokeStyle.widthDp.dp.toPx(),
                        viewportMetrics = viewportMetrics
                    )
                }
            }
        }

        // 2. Draggable Digital Sticky Notes anchored over the Mermaid nodes
        if (showAnnotations && w > 1f && h > 1f) {
            board.notes.forEach { note ->
                val baseX = viewportMetrics.toOverlayX(note.u, w)
                val baseY = viewportMetrics.toOverlayY(note.v, h)
                // Only compose notes near or inside the visible canvas bounds
                if (baseX in -180f..(w + 180f) && baseY in -140f..(h + 140f)) {
                    DraggableStickyNoteCard(
                        note = note,
                        baseX = baseX,
                        baseY = baseY,
                        canDrag = canEdit && (activeTool == MermaidAnnotationTool.INTERACT || activeTool == MermaidAnnotationTool.STICKY),
                        onDragCommit = { deltaX, deltaY ->
                            val newU = viewportMetrics.toDiagramU(baseX + deltaX, w)
                            val newV = viewportMetrics.toDiagramV(baseY + deltaY, h)
                            val reAnchored = MermaidBoardCodec.findNodeAt(renderedNodes, newU, newV)
                            onMoveStickyNote(note.id, newU, newV, reAnchored)
                        },
                        onClick = { onTapStickyNote(note) }
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNormalizedStroke(
    points: List<DiagramStrokePoint>,
    color: Color,
    strokeWidthPx: Float,
    viewportMetrics: DiagramViewportMetrics
) {
    if (points.isEmpty()) return
    val path = Path()
    val firstX = viewportMetrics.toOverlayX(points.first().u, size.width)
    val firstY = viewportMetrics.toOverlayY(points.first().v, size.height)
    path.moveTo(firstX, firstY)
    if (points.size == 1) {
        path.lineTo(firstX + 1f, firstY + 1f)
    } else {
        for (i in 1 until points.size) {
            val prevX = viewportMetrics.toOverlayX(points[i - 1].u, size.width)
            val prevY = viewportMetrics.toOverlayY(points[i - 1].v, size.height)
            val curX = viewportMetrics.toOverlayX(points[i].u, size.width)
            val curY = viewportMetrics.toOverlayY(points[i].v, size.height)
            val midX = (prevX + curX) * 0.5f
            val midY = (prevY + curY) * 0.5f
            path.quadraticTo(prevX, prevY, midX, midY)
        }
        val lastX = viewportMetrics.toOverlayX(points.last().u, size.width)
        val lastY = viewportMetrics.toOverlayY(points.last().v, size.height)
        path.lineTo(lastX, lastY)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

@Composable
private fun DraggableStickyNoteCard(
    note: DiagramStickyNote,
    baseX: Float,
    baseY: Float,
    canDrag: Boolean,
    onDragCommit: (deltaX: Float, deltaY: Float) -> Unit,
    onClick: () -> Unit
) {
    var dragOffsetX by remember(note.id, note.u, note.v) { mutableStateOf(0f) }
    var dragOffsetY by remember(note.id, note.u, note.v) { mutableStateOf(0f) }
    val bgColor = MermaidAnnotationPalettes.parseColor(note.colorHex, Color(0xFFFDE68A))
    val textColor = MermaidAnnotationPalettes.stickyTextColorFor(note.colorHex)
    val noteAccessibility = buildString {
        append("Sticky note")
        if (note.anchoredNodeLabel.isNotBlank()) append(" on ${note.anchoredNodeLabel}")
        append(": ${note.text}")
    }

    val dragMod = if (canDrag) {
        Modifier.pointerInput(note.id, baseX, baseY) {
            detectDragGestures(
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetX += dragAmount.x
                    dragOffsetY += dragAmount.y
                },
                onDragEnd = {
                    val dx = dragOffsetX
                    val dy = dragOffsetY
                    dragOffsetX = 0f
                    dragOffsetY = 0f
                    if (kotlin.math.abs(dx) > 2f || kotlin.math.abs(dy) > 2f) {
                        onDragCommit(dx, dy)
                    }
                },
                onDragCancel = {
                    dragOffsetX = 0f
                    dragOffsetY = 0f
                }
            )
        }
    } else Modifier

    Surface(
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
        color = bgColor.copy(alpha = if (note.resolved) 0.72f else 0.96f),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.25f)),
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (baseX + dragOffsetX - 24f).roundToInt(),
                    y = (baseY + dragOffsetY - 18f).roundToInt()
                )
            }
            .widthIn(min = 104.dp, max = 162.dp)
            .shadow(4.dp, RoundedCornerShape(10.dp))
            .then(dragMod)
            .clickable(onClick = onClick)
            .semantics { contentDescription = noteAccessibility }
            .testTag("mermaid_sticky_note_${note.id}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (note.resolved) Icons.Outlined.CheckCircle else Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = note.tag.ifBlank { "Idea" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = textColor.copy(alpha = 0.78f),
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = note.authorName.substringBefore(' ').take(10),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = textColor.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (note.anchoredNodeLabel.isNotBlank()) {
                Text(
                    text = "@ ${note.anchoredNodeLabel}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = textColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = note.text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 14.5.sp,
                    textDecoration = if (note.resolved) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = textColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Dialog to create, inspect, edit, re-anchor, resolve, or delete a collaborative sticky note.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickyNoteEditorDialog(
    initialNote: DiagramStickyNote?,
    initialU: Float,
    initialV: Float,
    initialAnchoredNode: DiagramRenderedNode?,
    renderedNodes: List<DiagramRenderedNode>,
    canEdit: Boolean,
    currentUserUid: String,
    currentUserName: String,
    onDismiss: () -> Unit,
    onSave: (DiagramStickyNote) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val isEditing = initialNote != null
    var noteText by remember(initialNote?.id) { mutableStateOf(initialNote?.text.orEmpty()) }
    var colorHex by remember(initialNote?.id) {
        mutableStateOf(initialNote?.colorHex ?: MermaidAnnotationPalettes.stickyColors.first().hex)
    }
    var tag by remember(initialNote?.id) { mutableStateOf(initialNote?.tag ?: StickyNoteTag.IDEA.label) }
    var resolved by remember(initialNote?.id) { mutableStateOf(initialNote?.resolved ?: false) }
    var anchoredNodeId by remember(initialNote?.id, initialAnchoredNode?.id) {
        mutableStateOf(initialNote?.anchoredNodeId ?: initialAnchoredNode?.id.orEmpty())
    }
    var anchoredNodeLabel by remember(initialNote?.id, initialAnchoredNode?.label) {
        mutableStateOf(initialNote?.anchoredNodeLabel ?: initialAnchoredNode?.label.orEmpty())
    }
    var nodeMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.StickyNote2,
                    contentDescription = null,
                    tint = MermaidAnnotationPalettes.parseColor(colorHex, BridgeAmber)
                )
                Text(
                    text = if (isEditing) {
                        stringResource(R.string.mermaid_sticky_edit_title)
                    } else {
                        stringResource(R.string.mermaid_sticky_new_title)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Anchored Diagram Node picker
                Box {
                    OutlinedButton(
                        onClick = { if (canEdit && renderedNodes.isNotEmpty()) nodeMenuExpanded = true },
                        enabled = canEdit && renderedNodes.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("mermaid_sticky_node_picker_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountTree,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (anchoredNodeLabel.isNotBlank()) {
                                "Pinned to: $anchoredNodeLabel"
                            } else {
                                "Free-floating on diagram (tap to pin to node)"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    DropdownMenu(
                        expanded = nodeMenuExpanded,
                        onDismissRequest = { nodeMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Free-floating (no node anchor)") },
                            onClick = {
                                anchoredNodeId = ""
                                anchoredNodeLabel = ""
                                nodeMenuExpanded = false
                            }
                        )
                        renderedNodes.forEach { node ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = node.label.ifBlank { node.id },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.PushPin,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    anchoredNodeId = node.id
                                    anchoredNodeLabel = node.label.ifBlank { node.id }
                                    nodeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Tag Selector
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StickyNoteTag.entries.forEach { option ->
                        FilterChip(
                            selected = tag.equals(option.label, ignoreCase = true),
                            onClick = { if (canEdit) tag = option.label },
                            enabled = canEdit,
                            label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("mermaid_sticky_tag_${option.name.lowercase()}")
                        )
                    }
                }

                // Sticky Color Swatches
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MermaidAnnotationPalettes.stickyColors.forEach { swatch ->
                        val selected = colorHex.equals(swatch.hex, ignoreCase = true)
                        val desc = "Sticky color ${swatch.name}"
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(swatch.color)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.25f),
                                    shape = CircleShape
                                )
                                .clickable(enabled = canEdit) { colorHex = swatch.hex }
                                .semantics { contentDescription = desc }
                                .testTag("mermaid_sticky_color_${swatch.name.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { if (canEdit) noteText = it },
                    readOnly = !canEdit,
                    label = { Text(stringResource(R.string.mermaid_sticky_input_label)) },
                    placeholder = { Text(stringResource(R.string.mermaid_sticky_input_hint)) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mermaid_sticky_text_input")
                )

                if (isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Author: ${initialNote.authorName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (resolved) "Marked resolved" else "Open brainstorm item",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (resolved) VerifiedEmerald else BridgeAmber
                            )
                        }
                        if (canEdit) {
                            Switch(
                                checked = resolved,
                                onCheckedChange = { resolved = it },
                                modifier = Modifier.testTag("mermaid_sticky_resolved_switch")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (canEdit) {
                Button(
                    onClick = {
                        val trimmed = noteText.trim()
                        if (trimmed.isNotEmpty()) {
                            val selectedNode = renderedNodes.firstOrNull { it.id == anchoredNodeId }
                            val finalU = if (!isEditing && selectedNode != null && initialAnchoredNode?.id != selectedNode.id) {
                                (selectedNode.u + selectedNode.w * 0.75f).coerceIn(0.05f, 0.95f)
                            } else {
                                initialNote?.u ?: initialU
                            }
                            val finalV = if (!isEditing && selectedNode != null && initialAnchoredNode?.id != selectedNode.id) {
                                (selectedNode.v + selectedNode.h * 0.25f).coerceIn(0.05f, 0.95f)
                            } else {
                                initialNote?.v ?: initialV
                            }
                            onSave(
                                DiagramStickyNote(
                                    id = initialNote?.id ?: "note_${UUID.randomUUID().toString().take(8)}",
                                    anchoredNodeId = anchoredNodeId,
                                    anchoredNodeLabel = anchoredNodeLabel,
                                    u = finalU,
                                    v = finalV,
                                    text = trimmed.take(400),
                                    colorHex = colorHex,
                                    tag = tag,
                                    authorUid = initialNote?.authorUid?.ifBlank { currentUserUid } ?: currentUserUid,
                                    authorName = initialNote?.authorName?.ifBlank { currentUserName } ?: currentUserName.ifBlank { "Researcher" },
                                    resolved = resolved,
                                    updatedAtMillis = System.currentTimeMillis()
                                )
                            )
                        }
                    },
                    enabled = noteText.isNotBlank(),
                    modifier = Modifier.testTag("mermaid_sticky_save_button")
                ) {
                    Text(stringResource(R.string.mermaid_sticky_save))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isEditing && canEdit && onDelete != null) {
                    TextButton(
                        onClick = { onDelete(initialNote.id) },
                        modifier = Modifier.testTag("mermaid_sticky_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (canEdit) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    )
}

/**
 * Collaborative brainstorming board summary listing all sticky notes pinned to nodes on the diagram.
 */
@Composable
fun BrainstormNotesListDialog(
    diagramTitle: String,
    board: DiagramAnnotationBoard,
    renderedNodes: List<DiagramRenderedNode>,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onEditNote: (DiagramStickyNote) -> Unit,
    onAddNoteOnNode: (DiagramRenderedNode?) -> Unit,
    onClearAllBoard: () -> Unit
) {
    var filterMode by remember { mutableStateOf("ALL") } // ALL, OPEN, RESOLVED
    val filteredNotes = remember(board.notes, filterMode) {
        when (filterMode) {
            "OPEN" -> board.notes.filter { !it.resolved }
            "RESOLVED" -> board.notes.filter { it.resolved }
            else -> board.notes
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.mermaid_brainstorm_board_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "$diagramTitle · ${board.notes.size} note(s), ${board.strokes.size} sketch(es)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL" to "All (${board.notes.size})", "OPEN" to "Open", "RESOLVED" to "Resolved").forEach { (key, label) ->
                        FilterChip(
                            selected = filterMode == key,
                            onClick = { filterMode = key },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (filteredNotes.isEmpty()) {
                    Text(
                        text = if (board.notes.isEmpty()) {
                            "No sticky notes pinned yet. Tap Sticky mode and tap any node in the diagram to start brainstorming."
                        } else {
                            "No notes match this filter."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            val swatch = MermaidAnnotationPalettes.parseColor(note.colorHex, Color(0xFFFDE68A))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, swatch.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEditNote(note) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(swatch)
                                        )
                                        Text(
                                            text = note.anchoredNodeLabel.ifBlank { "Diagram Canvas" },
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = note.tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (note.resolved) VerifiedEmerald else BridgeAmber
                                        )
                                    }
                                    Text(
                                        text = note.text,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            textDecoration = if (note.resolved) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "By ${note.authorName}${if (note.resolved) " · Resolved" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (canEdit && board.totalCount > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    TextButton(
                        onClick = onClearAllBoard,
                        modifier = Modifier.testTag("mermaid_clear_all_board_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Clear All Drawings & Sticky Notes", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (canEdit) {
                Button(
                    onClick = { onAddNoteOnNode(renderedNodes.firstOrNull()) },
                    modifier = Modifier.testTag("mermaid_board_add_note_btn")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Sticky")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (canEdit) {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        }
    )
}
