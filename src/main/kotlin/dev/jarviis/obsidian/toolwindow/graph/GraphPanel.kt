package dev.jarviis.obsidian.toolwindow.graph

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.AppExecutorUtil
import dev.jarviis.obsidian.vault.VaultManager
import java.awt.*
import java.awt.event.*
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.*

/**
 * Pure Swing/Java2D graph panel.
 *
 * Renders an interactive force-directed graph of vault notes and their wiki-link
 * connections. No JCEF / browser required.
 *
 * Interactions:
 *  - Scroll           → zoom towards cursor
 *  - Drag background  → pan
 *  - Drag node        → reposition
 *  - Click node       → select + highlight neighbours
 *  - Double-click     → open note in editor
 */
class GraphPanel(private val project: Project) : JPanel() {

    // ── Model ──────────────────────────────────────────────────────────────────

    inner class Node(val id: String, val name: String) {
        @Volatile var x: Double = 0.0
        @Volatile var y: Double = 0.0
        @Volatile var degree: Int = 0

        /** Fixed position while the user is dragging this node. */
        @Volatile var fx: Double? = null
        @Volatile var fy: Double? = null

        val r: Float get() = (5f + sqrt(degree.toFloat()) * 1.8f).coerceIn(5f, 22f)
    }

    inner class Edge(val source: Node, val target: Node)

    // ── Graph state ────────────────────────────────────────────────────────────

    @Volatile private var nodes: List<Node> = emptyList()
    @Volatile private var edges: List<Edge> = emptyList()
    @Volatile private var settled = false

    // ── UI state ───────────────────────────────────────────────────────────────

    private var selected: Node? = null
    private var hovered: Node? = null

    // ── View transform ─────────────────────────────────────────────────────────

    private var scale = 1.0
    private var panX  = 0.0
    private var panY  = 0.0

    // ── Drag state ─────────────────────────────────────────────────────────────

    private var dragNode: Node? = null
    private var dragBg = false
    private var lastMx = 0
    private var lastMy = 0

    // ── Simulation ─────────────────────────────────────────────────────────────

    private val simRunning = AtomicBoolean(false)

    /**
     * Fires at ~60 fps while the simulation is running, so the layout animates
     * smoothly. Stopped as soon as the simulation converges.
     */
    private val paintTimer = Timer(16) { repaint() }

    // ── Colors (light/dark theme aware) ────────────────────────────────────────

    private val cBg      = JBColor(Color(250, 250, 252), Color(43, 45, 48))
    private val cNode    = JBColor(Color(97, 175, 239),  Color(86, 156, 214))
    private val cSel     = JBColor(Color(229, 192, 123), Color(229, 192, 123))
    private val cHover   = JBColor(Color(198, 120, 221), Color(198, 120, 221))
    private val cNbr     = JBColor(Color(152, 195, 121), Color(152, 195, 121))
    private val cEdge    = JBColor(Color(180, 185, 200, 120), Color(80, 90, 110, 100))
    private val cEdgeHi  = JBColor(Color(97, 175, 239, 200),  Color(86, 156, 214, 180))
    private val cLabel   = JBColor(Color(40,  44,  52),  Color(171, 178, 191))
    private val cHud     = JBColor(Color(160, 160, 165), Color(110, 115, 120))

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        isOpaque = true
        setupMouse()
    }

    override fun addNotify() {
        super.addNotify()
        loadGraph()
    }

    override fun removeNotify() {
        simRunning.set(false)
        paintTimer.stop()
        super.removeNotify()
    }

    // ── Graph loading ──────────────────────────────────────────────────────────

    fun loadGraph() {
        simRunning.set(false)
        paintTimer.stop()
        AppExecutorUtil.getAppExecutorService().submit { buildGraph() }
    }

    private fun buildGraph() {
        val manager = service<VaultManager>()
        val index   = manager.indexForProject(project)

        if (index == null) {
            nodes = emptyList(); edges = emptyList()
            SwingUtilities.invokeLater { repaint() }
            return
        }

        val noteList = index.allNotes()
        val nodeMap  = LinkedHashMap<String, Node>(noteList.size * 2)
        for (note in noteList) {
            nodeMap[note.path.toString()] = Node(note.path.toString(), note.name)
        }

        val edgeList = ArrayList<Edge>()
        val seen     = HashSet<String>()

        for (note in noteList) {
            val src = nodeMap[note.path.toString()] ?: continue
            for (link in note.outgoingLinks) {
                val resolved = index.resolve(link.target, note.path) ?: continue
                val tgt      = nodeMap[resolved.path.toString()] ?: continue
                if (src === tgt) continue
                val key = if (src.id < tgt.id) "${src.id}→${tgt.id}" else "${tgt.id}→${src.id}"
                if (seen.add(key)) {
                    edgeList.add(Edge(src, tgt))
                    src.degree++
                    tgt.degree++
                }
            }
        }

        // Spread nodes in a circle so the initial layout looks reasonable
        val initW = width.takeIf  { it > 50 } ?: 800
        val initH = height.takeIf { it > 50 } ?: 600
        val spread = minOf(initW, initH) * 0.38
        val cx = initW / 2.0; val cy = initH / 2.0
        val rng = java.util.Random(42)
        for (node in nodeMap.values) {
            val angle  = rng.nextDouble() * 2 * PI
            val radius = sqrt(rng.nextDouble()) * spread
            node.x = cx + cos(angle) * radius
            node.y = cy + sin(angle) * radius
        }

        nodes    = nodeMap.values.toList()
        edges    = edgeList
        settled  = false
        selected = null
        hovered  = null
        scale    = 1.0; panX = 0.0; panY = 0.0

        runSimulation()
    }

    // ── Force-directed layout (Fruchterman–Reingold) ───────────────────────────

    private fun runSimulation() {
        simRunning.set(true)
        SwingUtilities.invokeLater { paintTimer.start() }

        AppExecutorUtil.getAppExecutorService().submit {
            forceLayout()
            separateLabels()
            simRunning.set(false)
            settled = true
            SwingUtilities.invokeLater {
                paintTimer.stop()
                fitToView()
            }
        }
    }

    private fun forceLayout() {
        val ln = nodes
        val le = edges
        val n  = ln.size
        if (n == 0) return

        val w = width.takeIf  { it > 50 } ?: 800
        val h = height.takeIf { it > 50 } ?: 600

        // k = ideal distance between connected nodes
        val k       = sqrt(w.toDouble() * h / n) * 0.78
        var temp    = sqrt(w.toDouble() * h) * 0.09
        val cooling = 0.97
        val minTemp = 0.6
        val iters   = when {
            n > 600 -> 120
            n > 200 -> 180
            else    -> 260
        }

        // Pre-index edges as int pairs for O(1) access
        val nodeIdx = HashMap<Node, Int>(n * 2)
        ln.forEachIndexed { i, nd -> nodeIdx[nd] = i }
        val eSrc = IntArray(le.size) { nodeIdx[le[it].source] ?: 0 }
        val eTgt = IntArray(le.size) { nodeIdx[le[it].target] ?: 0 }

        // Effective radius per node = circle radius + label half-diagonal
        // Used as an additional short-range repulsion so labels don't overlap
        val charW  = 7.0;  val labelHH = 8.0   // approx char width, label half-height
        val effR   = DoubleArray(n) { i ->
            val nd  = ln[i]
            val hlw = minOf(nd.name.length, 24) * charW / 2.0
            nd.r.toDouble() + sqrt(hlw * hlw + labelHH * labelHH) * 0.5
        }

        val dx = DoubleArray(n)
        val dy = DoubleArray(n)

        repeat(iters) {
            if (!simRunning.get()) return

            dx.fill(0.0); dy.fill(0.0)

            // Repulsion — O(n²), acceptable for vaults up to ~600 notes
            for (i in 0 until n) {
                val vi = ln[i]
                for (j in i + 1 until n) {
                    val vj  = ln[j]
                    val ddx = vi.x - vj.x
                    val ddy = vi.y - vj.y
                    val d   = max(sqrt(ddx * ddx + ddy * ddy), 0.5)
                    // FR base repulsion
                    val f   = k * k / d
                    // Extra push when label bounding boxes would overlap
                    val combined = effR[i] + effR[j]
                    val labelF   = if (d < combined) (combined - d) * 1.8 else 0.0
                    val total    = (f + labelF) / d
                    dx[i] += ddx * total;  dy[i] += ddy * total
                    dx[j] -= ddx * total;  dy[j] -= ddy * total
                }
            }

            // Attraction along edges
            for (e in le.indices) {
                val si  = eSrc[e]; val ti = eTgt[e]
                val vs  = ln[si];  val vt = ln[ti]
                val ddx = vs.x - vt.x
                val ddy = vs.y - vt.y
                val d   = max(sqrt(ddx * ddx + ddy * ddy), 0.5)
                val f   = d * d / k
                dx[si] -= ddx / d * f;  dy[si] -= ddy / d * f
                dx[ti] += ddx / d * f;  dy[ti] += ddy / d * f
            }

            // Apply displacement with temperature cap + boundary clamping
            for (i in 0 until n) {
                val v = ln[i]
                val fx = v.fx; val fy = v.fy
                if (fx != null && fy != null) { v.x = fx; v.y = fy; continue }
                val mag = sqrt(dx[i] * dx[i] + dy[i] * dy[i])
                if (mag > 0.001) {
                    val t = min(mag, temp) / mag
                    v.x = (v.x + dx[i] * t).coerceIn(0.0, w.toDouble())
                    v.y = (v.y + dy[i] * t).coerceIn(0.0, h.toDouble())
                }
            }

            temp = max(temp * cooling, minTemp)
        }
    }

    /**
     * Post-processing pass: pushes nodes apart until no two label bounding boxes overlap.
     * Uses approximate character metrics (no Graphics2D needed on background thread).
     *
     * Runs after [forceLayout] converges — the FR forces are already balanced, so this
     * only moves nodes by the minimum distance needed to clear label collisions.
     */
    private fun separateLabels() {
        val ln = nodes
        val n  = ln.size
        if (n < 2) return

        // Approximate label dimensions in graph coordinates (assuming ~scale 1 rendering)
        val charW   = 7.0   // approx px per character at font size 10
        val labelH  = 15.0  // approx label height
        val padX    = 6.0   // horizontal clearance between labels
        val padY    = 4.0   // vertical clearance

        fun halfLW(node: Node) = (minOf(node.name.length, 24) * charW) / 2.0

        repeat(120) { iter ->
            if (!simRunning.get()) return
            var stable = true

            for (i in 0 until n) {
                val a  = ln[i]
                val ar = a.r.toDouble()
                // Label box for a: horizontally centred, just below the circle
                val aX1 = a.x - halfLW(a) - padX;  val aX2 = a.x + halfLW(a) + padX
                val aY1 = a.y + ar + 2;             val aY2 = aY1 + labelH + padY

                for (j in i + 1 until n) {
                    val b  = ln[j]
                    val br = b.r.toDouble()
                    val bX1 = b.x - halfLW(b) - padX;  val bX2 = b.x + halfLW(b) + padX
                    val bY1 = b.y + br + 2;             val bY2 = bY1 + labelH + padY

                    val overlapX = minOf(aX2, bX2) - maxOf(aX1, bX1)
                    val overlapY = minOf(aY2, bY2) - maxOf(aY1, bY1)
                    if (overlapX <= 0 || overlapY <= 0) continue

                    // Push apart along the direction between node centres
                    val dx = b.x - a.x
                    val dy = b.y - a.y
                    val d  = max(sqrt(dx * dx + dy * dy), 1.0)
                    // Resolve along the axis with less overlap so movement is minimal
                    val push = (if (overlapX < overlapY) overlapX else overlapY) * 0.55 + 1.0

                    if (a.fx == null) { a.x -= dx / d * push; a.y -= dy / d * push }
                    if (b.fx == null) { b.x += dx / d * push; b.y += dy / d * push }
                    stable = false
                }
            }

            if (stable) return
        }
    }

    /** Zoom/pan so all nodes fit in the visible area after the layout converges. */
    private fun fitToView() {
        val ln = nodes
        if (ln.isEmpty() || width == 0 || height == 0) return

        val minX = ln.minOf { it.x } - 40.0
        val maxX = ln.maxOf { it.x } + 40.0
        val minY = ln.minOf { it.y } - 40.0
        val maxY = ln.maxOf { it.y } + 40.0

        val gw = maxX - minX
        val gh = maxY - minY
        if (gw < 1.0 || gh < 1.0) return

        scale = min(width / gw, height / gh).coerceIn(0.08, 2.0)
        panX  = (width  - gw * scale) / 2.0 - minX * scale
        panY  = (height - gh * scale) / 2.0 - minY * scale
        repaint()
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.color = cBg
        g2.fillRect(0, 0, width, height)

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY)

        val ln = nodes
        val le = edges

        if (ln.isEmpty()) { drawEmptyState(g2); return }

        val savedTx = g2.transform
        g2.translate(panX, panY)
        g2.scale(scale, scale)

        val sel = selected
        val neighbors: Set<Node> = if (sel != null)
            le.filter { it.source === sel || it.target === sel }
              .flatMapTo(HashSet()) { listOf(it.source, it.target) }
        else emptySet()

        // Edge base alpha scales with density — fewer edges = more opaque
        val baseEdgeAlpha = when {
            le.size > 500 -> 38
            le.size > 200 -> 55
            le.size > 80  -> 72
            else          -> 100
        }

        // ── Edges ──────────────────────────────────────────────────────────────
        for (edge in le) {
            val isHl  = sel != null && (edge.source === sel || edge.target === sel)
            val isDim = sel != null && !isHl
            g2.color  = Color(cEdge.red, cEdge.green, cEdge.blue,
                              if (isHl) 200 else if (isDim) 18 else baseEdgeAlpha)
            g2.stroke = BasicStroke(if (isHl) 1.4f else 0.6f)
            g2.drawLine(
                edge.source.x.toInt(), edge.source.y.toInt(),
                edge.target.x.toInt(), edge.target.y.toInt()
            )
        }

        // ── Pass 1: draw node circles (dimmed first, important on top) ──────────
        fun drawCircle(node: Node) {
            val r  = node.r.toInt().coerceAtLeast(2)
            val cx = node.x.toInt(); val cy = node.y.toInt()
            val isImportant = node === sel || node === hovered || node in neighbors
            val col: Color = when {
                node === sel      -> cSel
                node === hovered  -> cHover
                node in neighbors -> cNbr
                sel != null       -> Color(cNode.red, cNode.green, cNode.blue, 50)
                else              -> cNode
            }
            g2.color = col
            g2.fillOval(cx - r, cy - r, r * 2, r * 2)
            if (r >= 5 || isImportant) {
                g2.color  = col.darker()
                g2.stroke = BasicStroke(if (isImportant) 1.2f else 0.6f)
                g2.drawOval(cx - r, cy - r, r * 2, r * 2)
            }
        }

        if (sel != null) {
            for (node in ln) {
                if (node !== sel && node !in neighbors && node !== hovered) drawCircle(node)
            }
            for (node in ln) {
                if (node === sel || node in neighbors || node === hovered) drawCircle(node)
            }
        } else {
            for (node in ln) drawCircle(node)
        }

        // ── Pass 2: draw labels (nodes already separated by separateLabels()) ────

        // LOD: label visibility based on zoom and degree
        fun labelVisible(node: Node): Boolean {
            if (node === sel || node === hovered || node in neighbors) return true
            return when {
                scale >= 1.0  -> true
                scale >= 0.55 -> node.degree >= 2
                scale >= 0.35 -> node.degree >= 5
                scale >= 0.2  -> node.degree >= 10
                else          -> false
            }
        }

        for (node in ln) {
            if (!labelVisible(node)) continue
            val r  = node.r.toInt().coerceAtLeast(2)
            val cx = node.x.toInt(); val cy = node.y.toInt()
            val isImportant = node === sel || node === hovered || node in neighbors
            val alpha = if (sel != null && !isImportant) 55 else 210
            g2.color = Color(cLabel.red, cLabel.green, cLabel.blue, alpha)
            val fs    = (10.0 / scale).toInt().coerceIn(9, 14)
            g2.font   = Font(Font.SANS_SERIF, if (node === sel) Font.BOLD else Font.PLAIN, fs)
            val fm    = g2.fontMetrics
            val label = if (node.name.length > 26) node.name.take(23) + "…" else node.name
            g2.drawString(label,
                (cx - fm.stringWidth(label) / 2).toFloat(),
                (cy + r + fm.ascent + 2).toFloat()
            )
        }

        g2.transform = savedTx
        drawHud(g2, ln.size, le.size)
    }

    private fun drawEmptyState(g2: Graphics2D) {
        g2.color = cHud
        g2.font  = Font(Font.SANS_SERIF, Font.PLAIN, 13)
        val msg  = "No vault linked to this project"
        g2.drawString(msg, (width - g2.fontMetrics.stringWidth(msg)) / 2, height / 2)
    }

    private fun drawHud(g2: Graphics2D, noteCount: Int, edgeCount: Int) {
        g2.font  = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        g2.color = cHud

        // Bottom-left: stats
        val status = "$noteCount notes  ·  $edgeCount links" + if (!settled) "  ·  laying out…" else ""
        g2.drawString(status, 10, height - 10)

        // Bottom-right: hint
        val hint = "Scroll: zoom  ·  Drag: pan  ·  Click: select  ·  Dbl-click: open"
        val hfm  = g2.fontMetrics
        g2.drawString(hint, width - hfm.stringWidth(hint) - 10, height - 10)

        // Top-left: selected node info
        val sel = selected
        if (sel != null) {
            g2.color = cLabel
            g2.font  = Font(Font.SANS_SERIF, Font.BOLD, 12)
            g2.drawString(sel.name, 10, 18)
            g2.font  = Font(Font.SANS_SERIF, Font.PLAIN, 11)
            g2.color = cHud
            g2.drawString("${sel.degree} connection(s)", 10, 33)
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    private fun setupMouse() {
        val adapter = object : MouseAdapter() {

            override fun mousePressed(e: MouseEvent) {
                lastMx = e.x; lastMy = e.y
                val hit = nodeAt(e.x, e.y)
                if (hit != null) {
                    dragNode = hit
                } else {
                    dragBg = true
                }
            }

            override fun mouseDragged(e: MouseEvent) {
                val dn = dragNode
                if (dn != null) {
                    dn.x = toGx(e.x); dn.y = toGy(e.y)
                    repaint()
                } else if (dragBg) {
                    panX += e.x - lastMx
                    panY += e.y - lastMy
                    repaint()
                }
                lastMx = e.x; lastMy = e.y
            }

            override fun mouseReleased(e: MouseEvent) {
                dragNode = null
                dragBg   = false
            }

            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount >= 2) {
                    nodeAt(e.x, e.y)?.let { openNote(it) }
                } else {
                    val hit = nodeAt(e.x, e.y)
                    selected = if (hit === selected) null else hit
                    repaint()
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                val prev = hovered
                hovered  = nodeAt(e.x, e.y)
                cursor   = if (hovered != null)
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                else Cursor.getDefaultCursor()
                if (hovered !== prev) repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                if (hovered != null) { hovered = null; repaint() }
            }
        }

        addMouseListener(adapter)
        addMouseMotionListener(adapter)

        addMouseWheelListener { e ->
            // preciseWheelRotation works with both regular mouse wheel (integer steps)
            // and trackpad smooth scroll (fractional values). Negative = scroll up = zoom in.
            val rotation = e.preciseWheelRotation
            if (rotation == 0.0) return@addMouseWheelListener
            // Exponential scaling: each unit of rotation = ~11% zoom, scales smoothly
            val factor = exp(-rotation * 0.11)
            val mx = e.x.toDouble()
            val my = e.y.toDouble()
            panX  = mx - (mx - panX) * factor
            panY  = my - (my - panY) * factor
            scale = (scale * factor).coerceIn(0.04, 15.0)
            repaint()
        }
    }

    // ── Coordinate helpers ────────────────────────────────────────────────────

    private fun toGx(sx: Int) = (sx - panX) / scale
    private fun toGy(sy: Int) = (sy - panY) / scale

    /** Returns the node at screen position (sx, sy), or null. */
    private fun nodeAt(sx: Int, sy: Int): Node? {
        val gx = toGx(sx); val gy = toGy(sy)
        // Search in reverse paint order so topmost node wins
        return nodes.lastOrNull { n ->
            val hitR = n.r.toDouble() + 5.0 / scale   // slightly generous
            val dx = gx - n.x; val dy = gy - n.y
            dx * dx + dy * dy <= hitR * hitR
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun openNote(node: Node) {
        val manager = service<VaultManager>()
        val index   = manager.indexForProject(project) ?: return
        val note    = index.findByPath(Paths.get(node.id)) ?: return
        val vf      = LocalFileSystem.getInstance().findFileByNioFile(note.path) ?: return
        SwingUtilities.invokeLater {
            FileEditorManager.getInstance(project).openFile(vf, true)
        }
    }
}
