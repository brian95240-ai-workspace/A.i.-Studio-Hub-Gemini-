package com.example.ui.components

import com.example.data.model.ProjectFile
import com.example.data.model.ResearchProject
import com.example.data.model.StageRecord

object SpecializedFormatEngine {

    const val PINNED_KATEX_JS_URL = "https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"
    const val PINNED_KATEX_CSS_URL = "https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css"
    const val PINNED_KATEX_FONT_PREFIX = "https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/fonts/"

    private val fencedBlockRegex = Regex(
        pattern = "(?:^|\\n)```([A-Za-z0-9_+.-]*)\\s*\\n([\\s\\S]*?)\\n```"
    )
    private val displayMathBlockRegex = Regex(
        pattern = "(?:^|\\n)\\$\\$\\s*\\n([\\s\\S]+?)\\n\\$\\$(?=\\n|$)"
    )

    fun summarizeArtifact(kind: SpecializedFormatKind, code: String, title: String): String {
        return when (kind) {
            SpecializedFormatKind.LATEX -> {
                val eqCount = Regex("""\$\$|\\\[|\\begin\{(?:equation|align|gather)""").findAll(code).count().coerceAtLeast(1)
                "KaTeX + SVG · $eqCount math ${if (eqCount == 1) "block" else "blocks"}"
            }
            SpecializedFormatKind.GEOSPATIAL -> {
                val geo = SpecializedFormatParser.parseGeospatial(code)
                "${geo.sourceDialect} · ${geo.features.size} ${if (geo.features.size == 1) "feature" else "features"}"
            }
            SpecializedFormatKind.CYPHER_AGE,
            SpecializedFormatKind.GRAPH_DOT,
            SpecializedFormatKind.GRAPH_EXCHANGE,
            SpecializedFormatKind.POSTGRES_SQL -> {
                val g = SpecializedFormatParser.parseToUnifiedGraph(kind, code, title)
                val dialect = g.sourceDialect.ifBlank { kind.badgeLabel }
                "$dialect · ${g.nodes.size} nodes · ${g.edges.size} edges"
            }
        }
    }

    fun extractSpecializedBlocks(
        markdown: String,
        sourceLabel: String
    ): List<SpecializedArtifactSpec> {
        if (markdown.isBlank()) return emptyList()
        val normalized = markdown.replace("\r\n", "\n")
        val results = mutableListOf<SpecializedArtifactSpec>()
        val seenCodes = HashSet<String>()

        fencedBlockRegex.findAll(normalized).forEachIndexed { idx, match ->
            val lang = match.groupValues.getOrNull(1)?.trim().orEmpty().lowercase()
            if (lang == "mermaid" || lang == "mmd") return@forEachIndexed
            val code = match.groupValues.getOrNull(2)?.trim().orEmpty()
            if (code.length < 6) return@forEachIndexed

            val kind = SpecializedFormatParser.detectKind(lang, code) ?: return@forEachIndexed
            val dedupKey = "${kind.name}:${code.hashCode()}"
            if (!seenCodes.add(dedupKey)) return@forEachIndexed

            val precedingHeading = normalized.substring(0, match.range.first).trimEnd()
                .lines()
                .lastOrNull { it.trim().startsWith("#") }
                ?.trim()?.trimStart('#')?.trim()?.take(48)

            val title = precedingHeading?.takeIf { it.isNotBlank() }
                ?: "$sourceLabel · ${kind.badgeLabel}"
            val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(36).ifBlank { "artifact" }
            val ext = resolvePreferredExtension(kind, lang)

            results.add(
                SpecializedArtifactSpec(
                    id = "${kind.name.lowercase()}_${idx}_${code.hashCode()}",
                    title = title,
                    subtitle = summarizeArtifact(kind, code, title),
                    kind = kind,
                    sourceLanguage = lang.ifBlank { ext },
                    code = code,
                    suggestedFileName = "research/${slug}_${idx + 1}.$ext"
                )
            )
        }

        // Also extract standalone $$ ... $$ display math blocks outside fenced code blocks
        val strippedFences = fencedBlockRegex.replace(normalized, "\n")
        displayMathBlockRegex.findAll(strippedFences).forEachIndexed { idx, match ->
            val expr = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (expr.length < 3) return@forEachIndexed
            val dedupKey = "LATEX:${expr.hashCode()}"
            if (!seenCodes.add(dedupKey)) return@forEachIndexed

            val title = "$sourceLabel · Equation ${idx + 1}"
            results.add(
                SpecializedArtifactSpec(
                    id = "latex_math_${idx}_${expr.hashCode()}",
                    title = title,
                    subtitle = "LaTeX Display Math · KaTeX + SVG",
                    kind = SpecializedFormatKind.LATEX,
                    sourceLanguage = "tex",
                    code = expr,
                    suggestedFileName = "research/equation_${idx + 1}.tex"
                )
            )
        }

        return results
    }

    fun collectProjectSpecializedArtifacts(
        stages: List<StageRecord>,
        synthesisMarkdown: String,
        project: ResearchProject?,
        projectFiles: List<ProjectFile>
    ): List<SpecializedArtifactSpec> {
        val specs = mutableListOf<SpecializedArtifactSpec>()
        val seenHashes = HashSet<String>()

        fun appendUnique(list: List<SpecializedArtifactSpec>) {
            list.forEach { spec ->
                val key = "${spec.kind.name}:${spec.code.trim().hashCode()}"
                if (seenHashes.add(key)) specs.add(spec)
            }
        }

        if (synthesisMarkdown.isNotBlank()) {
            appendUnique(extractSpecializedBlocks(synthesisMarkdown, "Synthesis"))
        }
        stages.sortedBy { it.stageNumber }.forEach { st ->
            if (st.content.isNotBlank()) {
                appendUnique(extractSpecializedBlocks(st.content, "Stage ${st.stageNumber}"))
            }
        }

        // Include dedicated repo artifacts (.tex, .cypher, .cql, .dot, .graphml, .gexf, .geojson, .wkt, .sql, graph .json)
        projectFiles.forEach { file ->
            val ext = file.relativePath.substringAfterLast('.', "").lowercase()
            if (ext == "mmd" || ext == "mermaid") return@forEach
            val kind = SpecializedFormatParser.detectKind(ext, file.content) ?: return@forEach
            val key = "${kind.name}:${file.content.trim().hashCode()}"
            if (seenHashes.add(key)) {
                val fileName = file.relativePath.substringAfterLast('/')
                specs.add(
                    SpecializedArtifactSpec(
                        id = "file_${file.relativePath.hashCode()}",
                        title = fileName,
                        subtitle = summarizeArtifact(kind, file.content, fileName),
                        kind = kind,
                        sourceLanguage = ext.ifBlank { kind.defaultExtension },
                        code = file.content,
                        suggestedFileName = file.relativePath
                    )
                )
            }
        }

        return specs.take(12)
    }

    fun resolvePreferredExtension(kind: SpecializedFormatKind, langHint: String): String {
        val clean = langHint.trim().lowercase().removePrefix(".")
        return when (kind) {
            SpecializedFormatKind.LATEX -> "tex"
            SpecializedFormatKind.CYPHER_AGE -> if (clean == "cql") "cql" else "cypher"
            SpecializedFormatKind.GRAPH_DOT -> if (clean == "gv") "gv" else "dot"
            SpecializedFormatKind.GRAPH_EXCHANGE -> when (clean) {
                "graphml" -> "graphml"
                "gexf" -> "gexf"
                else -> "json"
            }
            SpecializedFormatKind.GEOSPATIAL -> if (clean == "wkt" || clean == "ewkt") "wkt" else "geojson"
            SpecializedFormatKind.POSTGRES_SQL -> if (clean == "pgsql") "pgsql" else "sql"
        }
    }

    fun buildSpecializedHtml(
        kind: SpecializedFormatKind,
        rawCode: String,
        title: String,
        theme: MermaidThemePreset,
        initialLayout: String = "layered"
    ): String {
        return when (kind) {
            SpecializedFormatKind.LATEX -> buildLatexHtml(rawCode, title, theme)
            SpecializedFormatKind.GEOSPATIAL -> {
                val geo = SpecializedFormatParser.parseGeospatial(rawCode)
                buildGeoHtml(SpecializedFormatParser.toGeoPayloadJson(geo), title, theme)
            }
            SpecializedFormatKind.CYPHER_AGE,
            SpecializedFormatKind.GRAPH_DOT,
            SpecializedFormatKind.GRAPH_EXCHANGE,
            SpecializedFormatKind.POSTGRES_SQL -> {
                val graph = SpecializedFormatParser.parseToUnifiedGraph(kind, rawCode, title)
                buildGraphHtml(SpecializedFormatParser.toGraphPayloadJson(graph), theme, initialLayout)
            }
        }
    }

    private fun sharedExportBridgeJs(bgHex: String): String = """
      window.__vortexExports = {};
      function serializeSvg(svgEl) {
        if (!svgEl) return '';
        var clone = svgEl.cloneNode(true);
        if (!clone.getAttribute('xmlns')) clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
        var vb = clone.getAttribute('viewBox');
        var w = parseFloat(clone.getAttribute('width')) || 960;
        var h = parseFloat(clone.getAttribute('height')) || 600;
        if (vb) {
          var p = vb.split(/[\s,]+/).map(Number);
          if (p.length === 4 && p[2] > 0 && p[3] > 0) { w = p[2]; h = p[3]; }
        } else {
          clone.setAttribute('viewBox', '0 0 ' + w + ' ' + h);
        }
        clone.setAttribute('width', String(Math.ceil(w)));
        clone.setAttribute('height', String(Math.ceil(h)));
        var bg = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        bg.setAttribute('x', '0'); bg.setAttribute('y', '0');
        bg.setAttribute('width', '100%'); bg.setAttribute('height', '100%');
        bg.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(bgHex)});
        clone.insertBefore(bg, clone.firstChild);
        return '<?xml version="1.0" encoding="UTF-8"?>\n' + new XMLSerializer().serializeToString(clone);
      }
      window.vortexExportSvg = function() {
        var svg = window.__getExportSvg ? window.__getExportSvg() : document.querySelector('svg');
        return serializeSvg(svg);
      };
      window.vortexTakeExportPayload = function(id) {
        var v = window.__vortexExports[id] || '';
        delete window.__vortexExports[id];
        return v;
      };
      window.vortexExportPngAsync = function(id) {
        try {
          var svgStr = window.vortexExportSvg();
          if (!svgStr) { console.log('VORTEX_MERMAID:EXPORT_PNG_FALLBACK:' + id); return; }
          var svg = window.__getExportSvg ? window.__getExportSvg() : document.querySelector('svg');
          var w = Math.max(480, Math.min(2600, Math.ceil(parseFloat(svg.getAttribute('width')) || 960)));
          var h = Math.max(320, Math.min(2600, Math.ceil(parseFloat(svg.getAttribute('height')) || 600)));
          var scale = (w * 2 <= 3200 && h * 2 <= 3200) ? 2 : 1;
          var img = new Image();
          img.onload = function() {
            try {
              var canvas = document.createElement('canvas');
              canvas.width = w * scale;
              canvas.height = h * scale;
              var ctx = canvas.getContext('2d');
              ctx.fillStyle = ${MermaidDiagramEngine.toSafeJsStringLiteral(bgHex)};
              ctx.fillRect(0, 0, canvas.width, canvas.height);
              ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
              window.__vortexExports[id] = canvas.toDataURL('image/png');
              console.log('VORTEX_MERMAID:EXPORT_PNG_READY:' + id);
            } catch (err) {
              console.log('VORTEX_MERMAID:EXPORT_PNG_FALLBACK:' + id);
            }
          };
          img.onerror = function() { console.log('VORTEX_MERMAID:EXPORT_PNG_FALLBACK:' + id); };
          img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svgStr);
        } catch (err) {
          console.log('VORTEX_MERMAID:EXPORT_PNG_FALLBACK:' + id);
        }
      };
    """.trimIndent()

    private fun buildLatexHtml(rawCode: String, title: String, theme: MermaidThemePreset): String {
        val safeCode = MermaidDiagramEngine.toSafeJsStringLiteral(rawCode)
        val safeTitle = MermaidDiagramEngine.toSafeJsStringLiteral(title)
        val csp = "default-src 'none'; script-src 'unsafe-inline' https://cdn.jsdelivr.net; " +
            "style-src 'unsafe-inline' https://cdn.jsdelivr.net; font-src https://cdn.jsdelivr.net data:; " +
            "img-src data: blob:;"

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=4.0"/>
<meta http-equiv="Content-Security-Policy" content="$csp"/>
<link rel="stylesheet" href="$PINNED_KATEX_CSS_URL"/>
<script src="$PINNED_KATEX_JS_URL"></script>
<style>
  html, body {
    margin: 0; padding: 0; width: 100%; height: 100%;
    background: ${theme.canvasBgHex}; color: ${theme.textHex};
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    overflow: auto;
  }
  #stage {
    padding: 20px; box-sizing: border-box; transform-origin: top left;
    min-width: 100%;
  }
  .latex-card {
    background: ${theme.nodeBgHex}; border: 1px solid ${theme.nodeBorderHex}55;
    border-radius: 14px; padding: 18px 20px; margin-bottom: 14px;
    box-shadow: 0 6px 20px rgba(0,0,0,0.22);
  }
  .latex-heading {
    color: ${theme.nodeBorderHex}; font-weight: 700; font-size: 16px;
    margin: 0 0 10px 0; border-bottom: 1px solid ${theme.nodeBorderHex}33; padding-bottom: 6px;
  }
  .latex-para {
    font-size: 14px; line-height: 1.65; margin: 8px 0; color: ${theme.textHex};
  }
  .latex-display {
    overflow-x: auto; padding: 12px 10px; margin: 10px 0;
    background: ${theme.subgraphBgHex}; border-radius: 10px;
    border-left: 3px solid ${theme.nodeBorderHex}; text-align: center;
  }
  .fallback-math {
    font-family: "Times New Roman", Georgia, serif; font-size: 18px;
    letter-spacing: 0.3px; color: ${theme.textHex}; white-space: pre-wrap;
  }
  .katex { font-size: 1.18em; color: ${theme.textHex}; }
</style>
</head>
<body>
  <div id="stage"></div>
  <script>
    (function() {
      var RAW = $safeCode;
      var TITLE = $safeTitle;
      var zoom = 1.0;
      var stage = document.getElementById('stage');

      window.vortexZoomIn = function() { zoom = Math.min(2.6, zoom + 0.18); stage.style.transform = 'scale(' + zoom + ')'; };
      window.vortexZoomOut = function() { zoom = Math.max(0.55, zoom - 0.18); stage.style.transform = 'scale(' + zoom + ')'; };
      window.vortexZoomReset = function() { zoom = 1.0; stage.style.transform = 'scale(1)'; };

      function prettyUnicodeMath(tex) {
        var map = [
          [/\\alpha\b/g, 'α'], [/\\beta\b/g, 'β'], [/\\gamma\b/g, 'γ'], [/\\delta\b/g, 'δ'],
          [/\\epsilon\b/g, 'ε'], [/\\theta\b/g, 'θ'], [/\\lambda\b/g, 'λ'], [/\\mu\b/g, 'μ'],
          [/\\pi\b/g, 'π'], [/\\sigma\b/g, 'σ'], [/\\omega\b/g, 'ω'], [/\\Delta\b/g, 'Δ'],
          [/\\Sigma\b/g, 'Σ'], [/\\Omega\b/g, 'Ω'], [/\\sum\b/g, '∑'], [/\\prod\b/g, '∏'],
          [/\\int\b/g, '∫'], [/\\nabla\b/g, '∇'], [/\\partial\b/g, '∂'], [/\\infty\b/g, '∞'],
          [/\\approx\b/g, '≈'], [/\\neq\b/g, '≠'], [/\\leq\b/g, '≤'], [/\\geq\b/g, '≥'],
          [/\\times\b/g, '×'], [/\\cdot\b/g, '·'], [/\\pm\b/g, '±'], [/\\to\b|\\rightarrow\b/g, '→'],
          [/\\Rightarrow\b/g, '⇒'], [/\\in\b/g, '∈'], [/\\forall\b/g, '∀'], [/\\exists\b/g, '∃'],
          [/\\sqrt\{([^}]+)\}/g, '√($1)'], [/\\frac\{([^}]+)\}\{([^}]+)\}/g, '($1) / ($2)']
        ];
        var out = tex;
        for (var i = 0; i < map.length; i++) out = out.replace(map[i][0], map[i][1]);
        return out.replace(/\\(?:left|right|text|mathrm|mathbf|mathbb|mathcal)\b/g, '').replace(/[{}]/g, '').trim();
      }

      function parseLatexBlocks(src) {
        var cleaned = src
          .replace(/\\documentclass[\s\S]*?\\begin\{document\}/m, '')
          .replace(/\\end\{document\}/m, '')
          .replace(/\\maketitle/g, '')
          .trim();
        var blocks = [];
        var hasEnv = /\\section|\\subsection|\$\$|\\\[|\\begin\{(?:equation|align|gather)/i.test(cleaned);
        if (!hasEnv) {
          blocks.push({ type: 'math', text: cleaned });
          return blocks;
        }
        var parts = cleaned.split(/(\$\$[\s\S]*?\$\$|\\\[[\s\S]*?\\\]|\\begin\{(?:equation\*?|align\*?|gather\*?)\}[\s\S]*?\\end\{(?:equation\*?|align\*?|gather\*?)\})/g);
        for (var i = 0; i < parts.length; i++) {
          var p = (parts[i] || '').trim();
          if (!p) continue;
          if (p.startsWith('$$') && p.endsWith('$$')) {
            blocks.push({ type: 'math', text: p.slice(2, -2).trim() });
          } else if (p.startsWith('\\[') && p.endsWith('\\]')) {
            blocks.push({ type: 'math', text: p.slice(2, -2).trim() });
          } else if (p.startsWith('\\begin{')) {
            blocks.push({ type: 'math', text: p });
          } else {
            var lines = p.split(/\n+/);
            for (var j = 0; j < lines.length; j++) {
              var ln = lines[j].trim();
              if (!ln) continue;
              var sec = ln.match(/^\\(?:sub)?section\*?\{([^}]+)\}/);
              if (sec) blocks.push({ type: 'heading', text: sec[1] });
              else blocks.push({ type: 'text', text: ln });
            }
          }
        }
        return blocks.length ? blocks : [{ type: 'math', text: cleaned }];
      }

      var blocks = parseLatexBlocks(RAW);
      var card = document.createElement('div');
      card.className = 'latex-card';

      function renderMathInto(el, expr, displayMode) {
        if (window.katex && typeof window.katex.render === 'function') {
          try {
            window.katex.render(expr, el, { displayMode: displayMode, throwOnError: false, trust: false, strict: 'warn' });
            return;
          } catch (e) {}
        }
        var span = document.createElement('span');
        span.className = 'fallback-math';
        span.textContent = prettyUnicodeMath(expr);
        el.appendChild(span);
      }

      blocks.forEach(function(b) {
        if (b.type === 'heading') {
          var h = document.createElement('div');
          h.className = 'latex-heading';
          h.textContent = b.text;
          card.appendChild(h);
        } else if (b.type === 'math') {
          var d = document.createElement('div');
          d.className = 'latex-display';
          renderMathInto(d, b.text, true);
          card.appendChild(d);
        } else {
          var p = document.createElement('div');
          p.className = 'latex-para';
          var tokens = b.text.split(/(\$[^$]+\$)/g);
          tokens.forEach(function(tok) {
            if (tok.startsWith('$') && tok.endsWith('$') && tok.length > 2) {
              var inlineHost = document.createElement('span');
              renderMathInto(inlineHost, tok.slice(1, -1), false);
              p.appendChild(inlineHost);
            } else {
              p.appendChild(document.createTextNode(tok));
            }
          });
          card.appendChild(p);
        }
      });
      stage.appendChild(card);

      // Build crisp standalone export SVG representation (works 100% offline + lossless vector export)
      window.__getExportSvg = function() {
        var ns = 'http://www.w3.org/2000/svg';
        var svg = document.createElementNS(ns, 'svg');
        var width = 920;
        var y = 64;
        var items = [];
        blocks.forEach(function(b) {
          var txt = b.type === 'math' ? prettyUnicodeMath(b.text) : b.text;
          var lines = txt.split('\n');
          items.push({ type: b.type, lines: lines });
        });
        var height = Math.max(260, 120 + items.reduce(function(acc, it) {
          return acc + (it.type === 'math' ? 54 : 30) * Math.max(1, it.lines.length) + 24;
        }, 0));
        svg.setAttribute('xmlns', ns);
        svg.setAttribute('width', String(width));
        svg.setAttribute('height', String(height));
        svg.setAttribute('viewBox', '0 0 ' + width + ' ' + height);

        var frame = document.createElementNS(ns, 'rect');
        frame.setAttribute('x', '24'); frame.setAttribute('y', '24');
        frame.setAttribute('width', String(width - 48)); frame.setAttribute('height', String(height - 48));
        frame.setAttribute('rx', '16'); frame.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.nodeBgHex)});
        frame.setAttribute('stroke', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.nodeBorderHex)});
        frame.setAttribute('stroke-width', '1.5');
        svg.appendChild(frame);

        var header = document.createElementNS(ns, 'text');
        header.setAttribute('x', '48'); header.setAttribute('y', String(y));
        header.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.nodeBorderHex)});
        header.setAttribute('font-family', 'sans-serif'); header.setAttribute('font-size', '18');
        header.setAttribute('font-weight', '700');
        header.textContent = TITLE + '  [LaTeX]';
        svg.appendChild(header);
        y += 32;

        items.forEach(function(it) {
          if (it.type === 'math') {
            var boxH = Math.max(48, it.lines.length * 32 + 24);
            var box = document.createElementNS(ns, 'rect');
            box.setAttribute('x', '48'); box.setAttribute('y', String(y));
            box.setAttribute('width', String(width - 96)); box.setAttribute('height', String(boxH));
            box.setAttribute('rx', '10'); box.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.subgraphBgHex)});
            box.setAttribute('stroke', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.nodeBorderHex)});
            box.setAttribute('stroke-opacity', '0.45');
            svg.appendChild(box);
            it.lines.forEach(function(ln, idx) {
              var t = document.createElementNS(ns, 'text');
              t.setAttribute('x', String(width / 2)); t.setAttribute('y', String(y + 32 + idx * 30));
              t.setAttribute('text-anchor', 'middle');
              t.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.textHex)});
              t.setAttribute('font-family', 'Georgia, serif'); t.setAttribute('font-size', '20');
              t.textContent = ln.slice(0, 88);
              svg.appendChild(t);
            });
            y += boxH + 18;
          } else {
            it.lines.forEach(function(ln) {
              var t = document.createElementNS(ns, 'text');
              t.setAttribute('x', '48'); t.setAttribute('y', String(y + 18));
              t.setAttribute('fill', it.type === 'heading' ? ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.accentEmeraldHex)} : ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.textHex)});
              t.setAttribute('font-family', 'sans-serif');
              t.setAttribute('font-size', it.type === 'heading' ? '16' : '14');
              if (it.type === 'heading') t.setAttribute('font-weight', '700');
              t.textContent = ln.slice(0, 102);
              svg.appendChild(t);
              y += 26;
            });
            y += 8;
          }
        });
        return svg;
      };

      ${sharedExportBridgeJs(theme.canvasBgHex)}
    })();
  </script>
</body>
</html>
        """.trimIndent()
    }

    private fun buildGraphHtml(
        payloadJson: String,
        theme: MermaidThemePreset,
        initialLayout: String
    ): String {
        val safePayload = MermaidDiagramEngine.toSafeJsStringLiteral(payloadJson)
        val safeLayout = MermaidDiagramEngine.toSafeJsStringLiteral(initialLayout)
        val csp = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:;"

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=4.0"/>
<meta http-equiv="Content-Security-Policy" content="$csp"/>
<style>
  html, body {
    margin: 0; padding: 0; width: 100%; height: 100%;
    background: ${theme.canvasBgHex}; color: ${theme.textHex};
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    overflow: auto;
  }
  #wrap {
    width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
    padding: 8px; box-sizing: border-box;
  }
  svg { display: block; max-width: none; }
</style>
</head>
<body>
  <div id="wrap"></div>
  <script>
    (function() {
      var DATA = JSON.parse($safePayload);
      var layoutMode = $safeLayout || 'layered';
      var zoom = 1.0;
      var wrap = document.getElementById('wrap');
      var ns = 'http://www.w3.org/2000/svg';
      var currentSvg = null;

      var PALETTE = [
        ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.nodeBorderHex)},
        ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.accentEmeraldHex)},
        ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.accentAmberHex)},
        ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.edgeHex)},
        '#A78BFA', '#F472B6'
      ];
      var groupColors = {};
      var groupIdx = 0;
      function colorForGroup(g) {
        var key = (g || 'default').toLowerCase();
        if (!groupColors[key]) {
          groupColors[key] = PALETTE[groupIdx % PALETTE.length];
          groupIdx++;
        }
        return groupColors[key];
      }

      function computeLayout(nodes, edges, mode) {
        var positions = {};
        if (!nodes.length) return { positions: positions, width: 640, height: 320 };

        if (mode === 'force') {
          var W = Math.max(820, nodes.length * 140);
          var H = Math.max(520, nodes.length * 95);
          var cx = W / 2, cy = H / 2, r = Math.min(W, H) * 0.34;
          nodes.forEach(function(n, i) {
            var a = (2 * Math.PI * i) / nodes.length;
            positions[n.id] = { x: cx + Math.cos(a) * r, y: cy + Math.sin(a) * r, vx: 0, vy: 0 };
          });
          for (var iter = 0; iter < 90; iter++) {
            for (var i = 0; i < nodes.length; i++) {
              for (var j = i + 1; j < nodes.length; j++) {
                var p1 = positions[nodes[i].id], p2 = positions[nodes[j].id];
                var dx = p1.x - p2.x, dy = p1.y - p2.y;
                var dist = Math.sqrt(dx * dx + dy * dy) || 1;
                var rep = 42000 / (dist * dist);
                var fx = (dx / dist) * rep, fy = (dy / dist) * rep;
                p1.vx += fx; p1.vy += fy; p2.vx -= fx; p2.vy -= fy;
              }
            }
            edges.forEach(function(e) {
              var s = positions[e.source], t = positions[e.target];
              if (!s || !t) return;
              var dx = t.x - s.x, dy = t.y - s.y;
              var dist = Math.sqrt(dx * dx + dy * dy) || 1;
              var pull = (dist - 230) * 0.035;
              var fx = (dx / dist) * pull, fy = (dy / dist) * pull;
              s.vx += fx; s.vy += fy; t.vx -= fx; t.vy -= fy;
            });
            nodes.forEach(function(n) {
              var p = positions[n.id];
              p.vx += (cx - p.x) * 0.008;
              p.vy += (cy - p.y) * 0.008;
              p.x += Math.max(-24, Math.min(24, p.vx * 0.32));
              p.y += Math.max(-24, Math.min(24, p.vy * 0.32));
              p.vx *= 0.6; p.vy *= 0.6;
            });
          }
          var minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
          nodes.forEach(function(n) {
            var p = positions[n.id];
            minX = Math.min(minX, p.x - 120); maxX = Math.max(maxX, p.x + 120);
            minY = Math.min(minY, p.y - 80); maxY = Math.max(maxY, p.y + 80);
          });
          nodes.forEach(function(n) {
            positions[n.id].x = positions[n.id].x - minX + 40;
            positions[n.id].y = positions[n.id].y - minY + 60;
          });
          return { positions: positions, width: Math.max(760, maxX - minX + 80), height: Math.max(460, maxY - minY + 110) };
        }

        // Topological layered layout
        var indeg = {}, adj = {};
        nodes.forEach(function(n) { indeg[n.id] = 0; adj[n.id] = []; });
        edges.forEach(function(e) {
          if (adj[e.source] && indeg[e.target] !== undefined) {
            adj[e.source].push(e.target);
            indeg[e.target]++;
          }
        });
        var layer = {};
        var q = nodes.filter(function(n) { return indeg[n.id] === 0; }).map(function(n) { return n.id; });
        if (!q.length) q.push(nodes[0].id);
        q.forEach(function(id) { layer[id] = 0; });
        var visited = 0;
        while (q.length && visited < nodes.length * 4) {
          var cur = q.shift();
          visited++;
          (adj[cur] || []).forEach(function(nxt) {
            var nextL = (layer[cur] || 0) + 1;
            if (layer[nxt] === undefined || nextL > layer[nxt]) {
              layer[nxt] = Math.min(nextL, nodes.length);
              q.push(nxt);
            }
          });
        }
        nodes.forEach(function(n, idx) {
          if (layer[n.id] === undefined) layer[n.id] = idx % 3;
        });
        var cols = {};
        var maxCol = 0;
        nodes.forEach(function(n) {
          var c = layer[n.id];
          maxCol = Math.max(maxCol, c);
          if (!cols[c]) cols[c] = [];
          cols[c].push(n);
        });
        var maxRows = 1;
        Object.keys(cols).forEach(function(c) { maxRows = Math.max(maxRows, cols[c].length); });
        var colSpacing = 270;
        var rowSpacing = 148;
        Object.keys(cols).forEach(function(cKey) {
          var c = Number(cKey);
          var list = cols[cKey];
          var totalH = (list.length - 1) * rowSpacing;
          var startY = ((maxRows - 1) * rowSpacing - totalH) / 2 + 115;
          list.forEach(function(n, rowIdx) {
            positions[n.id] = { x: 155 + c * colSpacing, y: startY + rowIdx * rowSpacing };
          });
        });
        return {
          positions: positions,
          width: Math.max(760, (maxCol + 1) * colSpacing + 120),
          height: Math.max(420, maxRows * rowSpacing + 140)
        };
      }

      function renderGraph() {
        while (wrap.firstChild) wrap.removeChild(wrap.firstChild);
        var nodes = DATA.nodes || [];
        var edges = DATA.edges || [];
        var res = computeLayout(nodes, edges, layoutMode);
        var pos = res.positions;
        var W = res.width, H = res.height;

        var svg = document.createElementNS(ns, 'svg');
        svg.setAttribute('xmlns', ns);
        svg.setAttribute('width', String(Math.round(W * zoom)));
        svg.setAttribute('height', String(Math.round(H * zoom)));
        svg.setAttribute('viewBox', '0 0 ' + W + ' ' + H);

        // Defs: arrowhead & dot grid
        var defs = document.createElementNS(ns, 'defs');
        var marker = document.createElementNS(ns, 'marker');
        marker.setAttribute('id', 'arrow');
        marker.setAttribute('viewBox', '0 0 10 10');
        marker.setAttribute('refX', '9'); marker.setAttribute('refY', '5');
        marker.setAttribute('markerWidth', '7'); marker.setAttribute('markerHeight', '7');
        marker.setAttribute('orient', 'auto-start-reverse');
        var arrowPath = document.createElementNS(ns, 'path');
        arrowPath.setAttribute('d', 'M 0 1 L 10 5 L 0 9 z');
        arrowPath.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.edgeHex)});
        marker.appendChild(arrowPath);
        defs.appendChild(marker);

        var pat = document.createElementNS(ns, 'pattern');
        pat.setAttribute('id', 'grid'); pat.setAttribute('width', '28'); pat.setAttribute('height', '28');
        pat.setAttribute('patternUnits', 'userSpaceOnUse');
        var dot = document.createElementNS(ns, 'circle');
        dot.setAttribute('cx', '14'); dot.setAttribute('cy', '14'); dot.setAttribute('r', '1');
        dot.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.edgeHex)});
        dot.setAttribute('opacity', '0.18');
        pat.appendChild(dot);
        defs.appendChild(pat);
        svg.appendChild(defs);

        var gridRect = document.createElementNS(ns, 'rect');
        gridRect.setAttribute('width', String(W)); gridRect.setAttribute('height', String(H));
        gridRect.setAttribute('fill', 'url(#grid)');
        svg.appendChild(gridRect);

        // Header badge
        var hud = document.createElementNS(ns, 'text');
        hud.setAttribute('x', '24'); hud.setAttribute('y', '30');
        hud.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.nodeBorderHex)});
        hud.setAttribute('font-size', '13'); hud.setAttribute('font-weight', '700');
        hud.textContent = (DATA.dialect || 'Graph') + '  ·  ' + nodes.length + ' nodes  ·  ' + edges.length + ' edges';
        svg.appendChild(hud);

        if (!nodes.length) {
          var emptyT = document.createElementNS(ns, 'text');
          emptyT.setAttribute('x', String(W / 2)); emptyT.setAttribute('y', String(H / 2));
          emptyT.setAttribute('text-anchor', 'middle');
          emptyT.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.textHex)});
          emptyT.setAttribute('font-size', '15');
          emptyT.textContent = 'No graph nodes or table definitions detected in this snippet.';
          svg.appendChild(emptyT);
          wrap.appendChild(svg);
          currentSvg = svg;
          return;
        }

        // Draw edges first
        edges.forEach(function(e) {
          var s = pos[e.source], t = pos[e.target];
          if (!s || !t) return;
          var dx = t.x - s.x, dy = t.y - s.y;
          var dist = Math.sqrt(dx * dx + dy * dy) || 1;
          var pad = 68;
          var x1 = s.x + (dx / dist) * pad;
          var y1 = s.y + (dy / dist) * 32;
          var x2 = t.x - (dx / dist) * pad;
          var y2 = t.y - (dy / dist) * 32;
          var mx = (x1 + x2) / 2 - dy * 0.12;
          var my = (y1 + y2) / 2 + dx * 0.08;

          var path = document.createElementNS(ns, 'path');
          path.setAttribute('d', 'M ' + x1 + ' ' + y1 + ' Q ' + mx + ' ' + my + ' ' + x2 + ' ' + y2);
          path.setAttribute('fill', 'none');
          path.setAttribute('stroke', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.edgeHex)});
          path.setAttribute('stroke-width', '2');
          path.setAttribute('stroke-opacity', '0.88');
          if (e.directed !== false) path.setAttribute('marker-end', 'url(#arrow)');
          svg.appendChild(path);

          if (e.label) {
            var lbl = String(e.label).slice(0, 24);
            var lw = Math.max(46, lbl.length * 6.8 + 14);
            var pill = document.createElementNS(ns, 'rect');
            pill.setAttribute('x', String(mx - lw / 2)); pill.setAttribute('y', String(my - 11));
            pill.setAttribute('width', String(lw)); pill.setAttribute('height', '20');
            pill.setAttribute('rx', '10');
            pill.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.subgraphBgHex)});
            pill.setAttribute('stroke', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.edgeHex)});
            pill.setAttribute('stroke-opacity', '0.6');
            svg.appendChild(pill);

            var et = document.createElementNS(ns, 'text');
            et.setAttribute('x', String(mx)); et.setAttribute('y', String(my + 3));
            et.setAttribute('text-anchor', 'middle');
            et.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.textHex)});
            et.setAttribute('font-size', '10.5'); et.setAttribute('font-weight', '600');
            et.textContent = lbl;
            svg.appendChild(et);
          }
        });

        // Draw nodes
        nodes.forEach(function(n) {
          var p = pos[n.id];
          if (!p) return;
          var details = (n.details || []).slice(0, 4);
          var nw = 196;
          var nh = 54 + details.length * 16;
          var x = p.x - nw / 2;
          var y = p.y - nh / 2;
          var accent = colorForGroup(n.group);

          var g = document.createElementNS(ns, 'g');

          var rect = document.createElementNS(ns, 'rect');
          rect.setAttribute('x', String(x)); rect.setAttribute('y', String(y));
          rect.setAttribute('width', String(nw)); rect.setAttribute('height', String(nh));
          rect.setAttribute('rx', '12');
          rect.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.nodeBgHex)});
          rect.setAttribute('stroke', accent);
          rect.setAttribute('stroke-width', '2');
          g.appendChild(rect);

          var bar = document.createElementNS(ns, 'rect');
          bar.setAttribute('x', String(x)); bar.setAttribute('y', String(y));
          bar.setAttribute('width', String(nw)); bar.setAttribute('height', '6');
          bar.setAttribute('rx', '3'); bar.setAttribute('fill', accent);
          g.appendChild(bar);

          var titleEl = document.createElementNS(ns, 'text');
          titleEl.setAttribute('x', String(x + 12)); titleEl.setAttribute('y', String(y + 26));
          titleEl.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.textHex)});
          titleEl.setAttribute('font-size', '13'); titleEl.setAttribute('font-weight', '700');
          titleEl.textContent = String(n.label || n.id).slice(0, 24);
          g.appendChild(titleEl);

          if (n.group) {
            var grp = document.createElementNS(ns, 'text');
            grp.setAttribute('x', String(x + 12)); grp.setAttribute('y', String(y + 42));
            grp.setAttribute('fill', accent);
            grp.setAttribute('font-size', '10.5'); grp.setAttribute('font-weight', '600');
            grp.textContent = String(n.group).slice(0, 26);
            g.appendChild(grp);
          }

          details.forEach(function(d, idx) {
            var dt = document.createElementNS(ns, 'text');
            dt.setAttribute('x', String(x + 12)); dt.setAttribute('y', String(y + 58 + idx * 15));
            dt.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.textHex)});
            dt.setAttribute('opacity', '0.78');
            dt.setAttribute('font-family', 'monospace'); dt.setAttribute('font-size', '10');
            dt.textContent = String(d).slice(0, 28);
            g.appendChild(dt);
          });

          svg.appendChild(g);
        });

        wrap.appendChild(svg);
        currentSvg = svg;
      }

      window.__getExportSvg = function() { return currentSvg; };
      window.vortexZoomIn = function() { zoom = Math.min(2.6, zoom + 0.2); renderGraph(); };
      window.vortexZoomOut = function() { zoom = Math.max(0.45, zoom - 0.2); renderGraph(); };
      window.vortexZoomReset = function() { zoom = 1.0; renderGraph(); };
      window.vortexSetGraphLayout = function(mode) { layoutMode = mode; renderGraph(); };

      renderGraph();
      ${sharedExportBridgeJs(theme.canvasBgHex)}
    })();
  </script>
</body>
</html>
        """.trimIndent()
    }

    private fun buildGeoHtml(
        payloadJson: String,
        title: String,
        theme: MermaidThemePreset
    ): String {
        val safePayload = MermaidDiagramEngine.toSafeJsStringLiteral(payloadJson)
        val safeTitle = MermaidDiagramEngine.toSafeJsStringLiteral(title)
        val csp = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:;"

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=4.0"/>
<meta http-equiv="Content-Security-Policy" content="$csp"/>
<style>
  html, body {
    margin: 0; padding: 0; width: 100%; height: 100%;
    background: ${theme.canvasBgHex}; color: ${theme.textHex};
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    overflow: auto;
  }
  #wrap { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; padding: 8px; box-sizing: border-box; }
  svg { display: block; max-width: none; }
</style>
</head>
<body>
  <div id="wrap"></div>
  <script>
    (function() {
      var GEO = JSON.parse($safePayload);
      var TITLE = $safeTitle;
      var zoom = 1.0;
      var wrap = document.getElementById('wrap');
      var ns = 'http://www.w3.org/2000/svg';
      var currentSvg = null;

      function renderMap() {
        while (wrap.firstChild) wrap.removeChild(wrap.firstChild);
        var W = 900, H = 540, pad = 64;
        var svg = document.createElementNS(ns, 'svg');
        svg.setAttribute('xmlns', ns);
        svg.setAttribute('width', String(Math.round(W * zoom)));
        svg.setAttribute('height', String(Math.round(H * zoom)));
        svg.setAttribute('viewBox', '0 0 ' + W + ' ' + H);

        var minLon = GEO.minLon, maxLon = GEO.maxLon, minLat = GEO.minLat, maxLat = GEO.maxLat;
        var spanLon = Math.max(0.002, maxLon - minLon);
        var spanLat = Math.max(0.002, maxLat - minLat);
        var padLon = spanLon * 0.14, padLat = spanLat * 0.14;
        minLon -= padLon; maxLon += padLon; minLat -= padLat; maxLat += padLat;

        var drawW = W - pad * 2, drawH = H - pad * 2 - 20;
        var scale = Math.min(drawW / (maxLon - minLon), drawH / (maxLat - minLat));
        var offsetX = pad + (drawW - (maxLon - minLon) * scale) / 2;
        var offsetY = pad + 18 + (drawH - (maxLat - minLat) * scale) / 2;

        function proj(lon, lat) {
          return [
            offsetX + (lon - minLon) * scale,
            offsetY + (maxLat - lat) * scale
          ];
        }

        // Map frame & graticule
        var frame = document.createElementNS(ns, 'rect');
        frame.setAttribute('x', String(pad)); frame.setAttribute('y', String(pad + 10));
        frame.setAttribute('width', String(drawW)); frame.setAttribute('height', String(drawH + 10));
        frame.setAttribute('rx', '12');
        frame.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.subgraphBgHex)});
        frame.setAttribute('stroke', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.edgeHex)});
        frame.setAttribute('stroke-opacity', '0.4');
        svg.appendChild(frame);

        for (var g = 0; g <= 4; g++) {
          var gx = pad + (drawW * g) / 4;
          var vLine = document.createElementNS(ns, 'line');
          vLine.setAttribute('x1', String(gx)); vLine.setAttribute('y1', String(pad + 10));
          vLine.setAttribute('x2', String(gx)); vLine.setAttribute('y2', String(pad + drawH + 20));
          vLine.setAttribute('stroke', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.edgeHex)});
          vLine.setAttribute('stroke-opacity', '0.2'); vLine.setAttribute('stroke-dasharray', '4 4');
          svg.appendChild(vLine);

          var lonTick = minLon + ((maxLon - minLon) * g) / 4;
          var lonLbl = document.createElementNS(ns, 'text');
          lonLbl.setAttribute('x', String(gx)); lonLbl.setAttribute('y', String(H - 18));
          lonLbl.setAttribute('text-anchor', 'middle'); lonLbl.setAttribute('font-size', '10');
          lonLbl.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.textHex)});
          lonLbl.setAttribute('opacity', '0.7');
          lonLbl.textContent = lonTick.toFixed(3) + '°';
          svg.appendChild(lonLbl);

          var gy = pad + 18 + (drawH * g) / 4;
          var hLine = document.createElementNS(ns, 'line');
          hLine.setAttribute('x1', String(pad)); hLine.setAttribute('y1', String(gy));
          hLine.setAttribute('x2', String(pad + drawW)); hLine.setAttribute('y2', String(gy));
          hLine.setAttribute('stroke', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.edgeHex)});
          hLine.setAttribute('stroke-opacity', '0.2'); hLine.setAttribute('stroke-dasharray', '4 4');
          svg.appendChild(hLine);

          var latTick = maxLat - ((maxLat - minLat) * g) / 4;
          var latLbl = document.createElementNS(ns, 'text');
          latLbl.setAttribute('x', String(pad - 8)); latLbl.setAttribute('y', String(gy + 3));
          latLbl.setAttribute('text-anchor', 'end'); latLbl.setAttribute('font-size', '10');
          latLbl.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.textHex)});
          latLbl.setAttribute('opacity', '0.7');
          latLbl.textContent = latTick.toFixed(3) + '°';
          svg.appendChild(latLbl);
        }

        // HUD Title
        var hud = document.createElementNS(ns, 'text');
        hud.setAttribute('x', String(pad)); hud.setAttribute('y', '36');
        hud.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.nodeBorderHex)});
        hud.setAttribute('font-size', '14'); hud.setAttribute('font-weight', '700');
        hud.textContent = TITLE + '  ·  ' + (GEO.dialect || 'PostGIS') + '  ·  SRID:' + (GEO.srid || 4326) + '  ·  ' + (GEO.features || []).length + ' features';
        svg.appendChild(hud);

        var COLORS = [
          ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.nodeBorderHex)},
          ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.accentEmeraldHex)},
          ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.accentAmberHex)},
          '#A78BFA', '#F472B6'
        ];

        (GEO.features || []).forEach(function(feat, fIdx) {
          var color = COLORS[fIdx % COLORS.length];
          var labelPt = null;
          (feat.shapes || []).forEach(function(sh) {
            if (sh.type === 'Polygon') {
              var d = '';
              (sh.rings || []).forEach(function(ring) {
                ring.forEach(function(pt, i) {
                  var xy = proj(pt[0], pt[1]);
                  if (!labelPt) labelPt = xy;
                  d += (i === 0 ? 'M ' : 'L ') + xy[0].toFixed(1) + ' ' + xy[1].toFixed(1) + ' ';
                });
                d += 'Z ';
              });
              var poly = document.createElementNS(ns, 'path');
              poly.setAttribute('d', d);
              poly.setAttribute('fill', color); poly.setAttribute('fill-opacity', '0.24');
              poly.setAttribute('stroke', color); poly.setAttribute('stroke-width', '2.4');
              svg.appendChild(poly);
            } else if (sh.type === 'LineString') {
              var pts = (sh.coords || []).map(function(pt) {
                var xy = proj(pt[0], pt[1]);
                if (!labelPt) labelPt = xy;
                return xy[0].toFixed(1) + ',' + xy[1].toFixed(1);
              }).join(' ');
              var pl = document.createElementNS(ns, 'polyline');
              pl.setAttribute('points', pts);
              pl.setAttribute('fill', 'none');
              pl.setAttribute('stroke', color); pl.setAttribute('stroke-width', '3');
              pl.setAttribute('stroke-linecap', 'round'); pl.setAttribute('stroke-linejoin', 'round');
              svg.appendChild(pl);
            } else if (sh.type === 'Point') {
              var xy = proj(sh.lon, sh.lat);
              labelPt = xy;
              var halo = document.createElementNS(ns, 'circle');
              halo.setAttribute('cx', String(xy[0])); halo.setAttribute('cy', String(xy[1]));
              halo.setAttribute('r', '10'); halo.setAttribute('fill', color); halo.setAttribute('fill-opacity', '0.22');
              svg.appendChild(halo);
              var c = document.createElementNS(ns, 'circle');
              c.setAttribute('cx', String(xy[0])); c.setAttribute('cy', String(xy[1]));
              c.setAttribute('r', '5.5'); c.setAttribute('fill', color);
              c.setAttribute('stroke', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.canvasBgHex)});
              c.setAttribute('stroke-width', '2');
              svg.appendChild(c);
            }
          });
          if (labelPt && feat.label) {
            var lbl = document.createElementNS(ns, 'text');
            lbl.setAttribute('x', String(labelPt[0] + 10)); lbl.setAttribute('y', String(labelPt[1] - 8));
            lbl.setAttribute('fill', ${MermaidDiagramEngine.toSafeJsStringLiteral(theme.textHex)});
            lbl.setAttribute('font-size', '11.5'); lbl.setAttribute('font-weight', '600');
            lbl.textContent = String(feat.label).slice(0, 28);
            svg.appendChild(lbl);
          }
        });

        wrap.appendChild(svg);
        currentSvg = svg;
      }

      window.__getExportSvg = function() { return currentSvg; };
      window.vortexZoomIn = function() { zoom = Math.min(2.6, zoom + 0.2); renderMap(); };
      window.vortexZoomOut = function() { zoom = Math.max(0.5, zoom - 0.2); renderMap(); };
      window.vortexZoomReset = function() { zoom = 1.0; renderMap(); };

      renderMap();
      ${sharedExportBridgeJs(theme.canvasBgHex)}
    })();
  </script>
</body>
</html>
        """.trimIndent()
    }
}
