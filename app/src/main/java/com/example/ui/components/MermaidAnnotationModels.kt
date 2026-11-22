package com.example.ui.components

import androidx.compose.ui.graphics.Color
import com.example.data.model.VortexJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import kotlin.math.hypot
import kotlin.math.sqrt

enum class MermaidAnnotationTool(val label: String) {
    INTERACT("Pan"),
    DRAW("Draw"),
    STICKY("Sticky"),
    ERASE("Erase")
}

enum class AnnotationStrokeStyle(
    val label: String,
    val widthDp: Float,
    val alpha: Float
) {
    PEN("Fine", 2.5f, 1.0f),
    MARKER("Bold", 5.5f, 0.95f),
    HIGHLIGHTER("Highlight", 14.0f, 0.36f)
}

enum class StickyNoteTag(val label: String) {
    IDEA("Idea"),
    QUESTION("Question"),
    RISK("Risk"),
    ACTION("Action")
}

data class AnnotationColorOption(
    val name: String,
    val hex: String,
    val color: Color,
    val textColor: Color = Color(0xFF0F172A)
)

object MermaidAnnotationPalettes {
    val drawColors = listOf(
        AnnotationColorOption("Cyan", "#00E5FF", Color(0xFF00E5FF)),
        AnnotationColorOption("Amber", "#FFB74D", Color(0xFFFFB74D)),
        AnnotationColorOption("Emerald", "#34D399", Color(0xFF34D399)),
        AnnotationColorOption("Coral", "#FF5370", Color(0xFFFF5370)),
        AnnotationColorOption("Violet", "#A78BFA", Color(0xFFA78BFA)),
        AnnotationColorOption("White", "#F8FAFC", Color(0xFFF8FAFC))
    )

    val stickyColors = listOf(
        AnnotationColorOption("Canary", "#FDE68A", Color(0xFFFDE68A), Color(0xFF1E293B)),
        AnnotationColorOption("Cyan", "#A5F3FC", Color(0xFFA5F3FC), Color(0xFF0F172A)),
        AnnotationColorOption("Mint", "#A7F3D0", Color(0xFFA7F3D0), Color(0xFF064E3B)),
        AnnotationColorOption("Rose", "#FECDD3", Color(0xFFFECDD3), Color(0xFF4C0519)),
        AnnotationColorOption("Lavender", "#DDD6FE", Color(0xFFDDD6FE), Color(0xFF2E1065))
    )

    fun parseColor(hex: String, fallback: Color): Color = runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrDefault(fallback)

    fun stickyTextColorFor(hex: String): Color =
        stickyColors.firstOrNull { it.hex.equals(hex, ignoreCase = true) }?.textColor
            ?: Color(0xFF0F172A)
}

@JsonClass(generateAdapter = true)
data class DiagramStrokePoint(
    val u: Float = 0f,
    val v: Float = 0f
)

@JsonClass(generateAdapter = true)
data class DiagramStroke(
    val id: String = "",
    val points: List<DiagramStrokePoint> = emptyList(),
    val colorHex: String = "#00E5FF",
    val widthDp: Float = 3.0f,
    val alpha: Float = 1.0f,
    val authorUid: String = "",
    val authorName: String = "Researcher",
    val createdAtMillis: Long = 0L
)

@JsonClass(generateAdapter = true)
data class DiagramStickyNote(
    val id: String = "",
    val anchoredNodeId: String = "",
    val anchoredNodeLabel: String = "",
    val u: Float = 0.5f,
    val v: Float = 0.5f,
    val text: String = "",
    val colorHex: String = "#FDE68A",
    val tag: String = StickyNoteTag.IDEA.label,
    val authorUid: String = "",
    val authorName: String = "Researcher",
    val resolved: Boolean = false,
    val updatedAtMillis: Long = 0L
)

@JsonClass(generateAdapter = true)
data class DiagramAnnotationBoard(
    val diagramId: String = "",
    val diagramTitle: String = "",
    val strokes: List<DiagramStroke> = emptyList(),
    val notes: List<DiagramStickyNote> = emptyList(),
    val updatedAtMillis: Long = 0L
) {
    val totalCount: Int get() = strokes.size + notes.size
    val isEmpty: Boolean get() = strokes.isEmpty() && notes.isEmpty()
}

@JsonClass(generateAdapter = true)
data class DiagramRenderedNode(
    val id: String = "",
    val label: String = "",
    val u: Float = 0f,
    val v: Float = 0f,
    val w: Float = 0f,
    val h: Float = 0f
) {
    val centerU: Float get() = u + w * 0.5f
    val centerV: Float get() = v + h * 0.5f
}

data class DiagramViewportMetrics(
    val relLeft: Float = 0f,
    val relTop: Float = 0f,
    val relWidth: Float = 1f,
    val relHeight: Float = 1f,
    val ready: Boolean = false
) {
    fun toDiagramU(px: Float, overlayWidth: Float): Float {
        if (overlayWidth <= 1f || relWidth <= 0.001f) return 0.5f
        return ((px / overlayWidth) - relLeft) / relWidth
    }

    fun toDiagramV(py: Float, overlayHeight: Float): Float {
        if (overlayHeight <= 1f || relHeight <= 0.001f) return 0.5f
        return ((py / overlayHeight) - relTop) / relHeight
    }

    fun toOverlayX(u: Float, overlayWidth: Float): Float = (relLeft + u * relWidth) * overlayWidth
    fun toOverlayY(v: Float, overlayHeight: Float): Float = (relTop + v * relHeight) * overlayHeight
}

object MermaidBoardCodec {
    private val boardAdapter = VortexJson.moshi.adapter(DiagramAnnotationBoard::class.java)
    private val nodeListType = Types.newParameterizedType(List::class.java, DiagramRenderedNode::class.java)
    private val nodeListAdapter = VortexJson.moshi.adapter<List<DiagramRenderedNode>>(nodeListType)

    fun encodeBoard(board: DiagramAnnotationBoard): String = boardAdapter.toJson(board)

    fun decodeBoard(json: String?): DiagramAnnotationBoard? {
        if (json.isNullOrBlank()) return null
        return runCatching { boardAdapter.fromJson(json) }.getOrNull()
    }

    fun decodeNodes(json: String?): List<DiagramRenderedNode> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { nodeListAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
    }

    fun boardRepoPath(diagramTitle: String, suggestedFileName: String = ""): String {
        val rawBase = suggestedFileName.substringBeforeLast('.').ifBlank { diagramTitle }
        val slug = rawBase.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(48)
            .ifBlank { "diagram" }
        return "docs/diagrams/annotations/$slug.board.json"
    }

    /**
     * Finds the rendered Mermaid node containing (u, v), or within a forgiving margin
     * around the node's bounding box so tapping near a node snaps cleanly onto it.
     */
    fun findNodeAt(
        nodes: List<DiagramRenderedNode>,
        u: Float,
        v: Float,
        hitMargin: Float = 0.045f
    ): DiagramRenderedNode? {
        if (nodes.isEmpty()) return null
        // 1. Prefer exact bounding box hit (smallest area if nested)
        val exact = nodes.filter { n ->
            u >= n.u && u <= n.u + n.w && v >= n.v && v <= n.v + n.h
        }.minByOrNull { it.w * it.h }
        if (exact != null) return exact

        // 2. Otherwise snap if within forgiving margin around node box
        return nodes.filter { n ->
            u >= n.u - hitMargin && u <= n.u + n.w + hitMargin &&
                v >= n.v - hitMargin && v <= n.v + n.h + hitMargin
        }.minByOrNull { n ->
            hypot((u - n.centerU).toDouble(), (v - n.centerV).toDouble())
        }
    }

    /**
     * Computes minimum distance from point (px, py) to segment (ax, ay)-(bx, by) in pixel space.
     */
    fun distanceToSegmentPx(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 0.0001f) return hypot((px - ax).toDouble(), (py - ay).toDouble()).toFloat()
        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = ax + t * dx
        val projY = ay + t * dy
        return sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY))
    }
}
