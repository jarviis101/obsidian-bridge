package dev.jarviis.obsidian.toolwindow.graph

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.ui.ColorUtil
import com.intellij.util.messages.MessageBusConnection
import dev.jarviis.obsidian.ObsidianBundle
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

    inner class Node(val id: String, val name: String) {
        @Volatile var x: Double = 0.0
        @Volatile var y: Double = 0.0
        @Volatile var degree: Int = 0
        @Volatile var fx: Double? = null
        @Volatile var fy: Double? = null
        val r: Float get() = (5f + sqrt(degree.toFloat()) * 1.8f).coerceIn(5f, 22f)
    }

    inner class Edge(val source: Node, val target: Node)

    @Volatile private var nodes: List<Node> = emptyList()
    @Volatile private var edges: List<Edge> = emptyList()
    @Volatile private var settled = false

    private var selected: Node? = null
    private var hovered: Node? = null

    private var scale = 1.0
    private var panX  = 0.0
    private var panY  = 0.0

    private var dragNode: Node? = null
    private var dragBg = false
    private var lastMx = 0
    private var lastMy = 0

    private val simRunning = AtomicBoolean(false)
    private val paintTimer = Timer(16) { repaint() }
    private var busConnection: MessageBusConnection? = null
    private var vaultChangeListener: VaultManager.VaultChangeListener? = null

    private val cBg      = JBColor(Color(250, 250, 252), Color(43, 45, 48))
    private val cNode    = JBColor(Color(97, 175, 239),  Color(86, 156, 214))
    private val cSel     = JBColor(Color(229, 192, 123), Color(229, 192, 123))
    private val cHover   = JBColor(Color(198, 120, 221), Color(198, 120, 221))
    private val cNbr     = JBColor(Color(152, 195, 121), Color(152, 195, 121))
    private val cEdge    = JBColor(Color(180, 185, 200, 120), Color(80, 90, 110, 100))
    private val cLabel   = JBColor(Color(40,  44,  52),  Color(171, 178, 191))
    private val cHud     = JBColor(Color(160, 160, 165), Color(110, 115, 120))

    init {
        isOpaque = true
        setupMouse()
    }

    override fun addNotify() {
        super.addNotify()
        busConnection = project.messageBus.connect()
        busConnection?.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                val path = event.newFile?.path
                SwingUtilities.invokeLater { selectByPath(path) }
            }
        })
        val listener = VaultManager.VaultChangeListener { SwingUtilities.invokeLater { loadGraph() } }
        vaultChangeListener = listener
        service<VaultManager>().addChangeListener(listener)
        loadGraph()
    }

    override fun removeNotify() {
        vaultChangeListener?.let { service<VaultManager>().removeChangeListener(it) }
        vaultChangeListener = null
        busConnection?.disconnect()
        busConnection = null
        simRunning.set(false)
        paintTimer.stop()
        if (settled) saveLayout()
        super.removeNotify()
    }

    private fun selectByPath(path: String?) {
        selected = if (path != null) nodes.firstOrNull { it.id == path } else null
        repaint()
    }

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

        val initW   = width.takeIf  { it > 50 } ?: 800
        val initH   = height.takeIf { it > 50 } ?: 600
        val spreadX = initW * 0.28
        val spreadY = initH * 0.44
        val cx = initW / 2.0; val cy = initH / 2.0
        val rng = java.util.Random(42)
        val nodeList = nodeMap.values.toList()
        for (node in nodeList) {
            val pushR = node.r + 20.0
            var attempts = 0
            do {
                val angle  = rng.nextDouble() * 2 * PI
                val radius = sqrt(rng.nextDouble())
                node.x = cx + cos(angle) * radius * spreadX
                node.y = cy + sin(angle) * radius * spreadY
                val tooClose = nodeList.any { other ->
                    other !== node && other.x != 0.0 &&
                    hypot(node.x - other.x, node.y - other.y) < pushR * 2
                }
                attempts++
            } while (tooClose && attempts < 12)
        }

        nodes    = nodeMap.values.toList()
        edges    = edgeList
        settled  = false
        hovered  = null
        scale    = 1.0; panX = 0.0; panY = 0.0

        val currentPath = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.path
        selected = if (currentPath != null) nodes.firstOrNull { it.id == currentPath } else null

        if (restoreLayout()) {
            settled = true
            SwingUtilities.invokeLater { fitToView() }
        } else {
            runSimulation()
        }
    }

    private fun runSimulation() {
        simRunning.set(true)
        SwingUtilities.invokeLater { paintTimer.start() }

        AppExecutorUtil.getAppExecutorService().submit {
            forceLayout()
            separateLabels()
            stretchHorizontally()
            resolveCollisions()
            simRunning.set(false)
            settled = true
            saveLayout()
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

        val k       = sqrt(w.toDouble() * h / n) * 0.9
        var temp    = sqrt(w.toDouble() * h) * 0.10
        val cooling = 0.97
        val minTemp = 1.5
        val iters   = when {
            n > 600 -> 140
            n > 200 -> 220
            else    -> 320
        }

        val nodeIdx = HashMap<Node, Int>(n * 2)
        ln.forEachIndexed { i, nd -> nodeIdx[nd] = i }
        val eSrc = IntArray(le.size) { nodeIdx[le[it].source] ?: 0 }
        val eTgt = IntArray(le.size) { nodeIdx[le[it].target] ?: 0 }

        val charW  = 7.5;  val labelHH = 9.0
        val effR   = DoubleArray(n) { i ->
            val nd  = ln[i]
            val hlw = minOf(nd.name.length, 26) * charW / 2.0
            max(nd.r.toDouble(), sqrt(hlw * hlw + labelHH * labelHH))
        }

        val dx = DoubleArray(n)
        val dy = DoubleArray(n)

        repeat(iters) {
            if (!simRunning.get()) return

            dx.fill(0.0); dy.fill(0.0)

            for (i in 0 until n) {
                val vi = ln[i]
                for (j in i + 1 until n) {
                    val vj  = ln[j]
                    val ddx = vi.x - vj.x
                    val ddy = vi.y - vj.y
                    val d   = max(sqrt(ddx * ddx + ddy * ddy), 0.5)
                    val f   = k * k / d
                    val combined = effR[i] + effR[j]
                    val labelF   = if (d < combined) (combined - d) * 4.5 else 0.0
                    val total    = (f + labelF) / d
                    dx[i] += ddx * total;  dy[i] += ddy * total
                    dx[j] -= ddx * total;  dy[j] -= ddy * total
                }
            }

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

            val gcx = w / 2.0; val gcy = h / 2.0
            for (i in 0 until n) {
                val g = if (ln[i].degree == 0) 0.06 else 0.008
                dx[i] += (gcx - ln[i].x) * g
                dy[i] += (gcy - ln[i].y) * g
            }

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

    private fun separateLabels() {
        val ln = nodes
        val n  = ln.size
        if (n < 2) return

        val vw = width.takeIf  { it > 50 } ?: 800
        val vh = height.takeIf { it > 50 } ?: 600

        val minX = ln.minOf { it.x } - 40.0
        val maxX = ln.maxOf { it.x } + 40.0
        val minY = ln.minOf { it.y } - 40.0
        val maxY = ln.maxOf { it.y } + 40.0
        val gw = (maxX - minX).coerceAtLeast(1.0)
        val gh = (maxY - minY).coerceAtLeast(1.0)
        val estScale = min(vw / gw, vh / gh).coerceIn(0.05, 2.0)

        val charW  = 7.5  / estScale
        val labelH = 15.0 / estScale
        val padX   = 8.0  / estScale
        val padY   = 5.0  / estScale

        fun halfLW(node: Node) = (minOf(node.name.length, 26) * charW) / 2.0

        repeat(300) {
            if (!simRunning.get()) return
            var stable = true

            for (i in 0 until n) {
                val a  = ln[i]
                val ar = a.r.toDouble()
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

                    val dx = b.x - a.x
                    val dy = b.y - a.y
                    val d  = max(sqrt(dx * dx + dy * dy), 1.0)
                    val push = max(overlapX, overlapY) * 0.6 + 3.0 / estScale

                    if (a.fx == null) { a.x -= dx / d * push * 0.5; a.y -= dy / d * push * 0.5 }
                    if (b.fx == null) { b.x += dx / d * push * 0.5; b.y += dy / d * push * 0.5 }
                    stable = false
                }
            }

            if (stable) return
        }
    }

    private fun resolveCollisions(pinned: Node? = null, maxIter: Int = 80) {
        val ln = nodes
        val n  = ln.size
        if (n < 2) return

        repeat(maxIter) {
            var stable = true
            for (i in 0 until n) {
                val a  = ln[i]
                val aR = a.r + 20.0
                for (j in i + 1 until n) {
                    val b   = ln[j]
                    val bR  = b.r + 20.0
                    val dx  = b.x - a.x
                    val dy  = b.y - a.y
                    val d   = sqrt(dx * dx + dy * dy)
                    val min = aR + bR
                    if (d < min && d > 0.01) {
                        val push = (min - d) / d * 0.6
                        val moveA = a !== pinned && a.fx == null
                        val moveB = b !== pinned && b.fx == null
                        if (moveA && moveB) {
                            a.x -= dx * push * 0.5; a.y -= dy * push * 0.5
                            b.x += dx * push * 0.5; b.y += dy * push * 0.5
                        } else if (moveA) {
                            a.x -= dx * push; a.y -= dy * push
                        } else if (moveB) {
                            b.x += dx * push; b.y += dy * push
                        }
                        stable = false
                    }
                }
            }
            if (stable) return
        }
    }

    private fun stretchHorizontally() {
        val ln = nodes
        if (ln.isEmpty()) return
        val cx = ln.sumOf { it.x } / ln.size
        val cy = ln.sumOf { it.y } / ln.size
        for (node in ln) {
            node.x = cx + (node.x - cx) * 0.6
            node.y = cy + (node.y - cy) * 1.25
        }
    }

    private fun layoutKey(): String? {
        val index = service<VaultManager>().indexForProject(project) ?: return null
        return "obsidian.graph.layout.${index.descriptor.rootPathString}"
    }

    private fun saveLayout() {
        val key = layoutKey() ?: return
        val ln  = nodes
        if (ln.isEmpty()) return
        val data = ln.joinToString("\n") { "${it.id}\t${it.x}\t${it.y}" }
        PropertiesComponent.getInstance(project).setValue(key, data)
    }

    private fun restoreLayout(): Boolean {
        val key  = layoutKey() ?: return false
        val data = PropertiesComponent.getInstance(project).getValue(key) ?: return false
        val ln   = nodes
        if (ln.isEmpty()) return false

        val saved = data.lines().mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size == 3) {
                val x = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                val y = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                parts[0] to (x to y)
            } else null
        }.toMap()

        if (saved.isEmpty()) return false

        var hit = 0
        for (node in ln) {
            val pos = saved[node.id] ?: continue
            node.x = pos.first
            node.y = pos.second
            hit++
        }
        return hit >= ln.size / 2
    }

    private fun fitToView() {
        val ln = nodes.filter { it.degree > 0 }.takeIf { it.isNotEmpty() } ?: nodes
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

        val baseEdgeAlpha = when {
            le.size > 500 -> 38
            le.size > 200 -> 55
            le.size > 80  -> 72
            else          -> 100
        }

        for (edge in le) {
            val isHl  = sel != null && (edge.source === sel || edge.target === sel)
            val isDim = sel != null && !isHl
            g2.color  = ColorUtil.withAlpha(cEdge, (if (isHl) 200 else if (isDim) 18 else baseEdgeAlpha) / 255.0)
            g2.stroke = BasicStroke(if (isHl) 1.4f else 0.6f)
            g2.drawLine(
                edge.source.x.toInt(), edge.source.y.toInt(),
                edge.target.x.toInt(), edge.target.y.toInt()
            )
        }

        fun drawCircle(node: Node) {
            val r  = node.r.toInt().coerceAtLeast(2)
            val cx = node.x.toInt(); val cy = node.y.toInt()
            val isImportant = node === sel || node === hovered || node in neighbors
            val col: Color = when {
                node === sel      -> cSel
                node === hovered  -> cHover
                node in neighbors -> cNbr
                sel != null       -> ColorUtil.withAlpha(cNode, 50 / 255.0)
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
            g2.color = ColorUtil.withAlpha(cLabel, alpha / 255.0)
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
        val msg  = ObsidianBundle.message("graph.empty.state")
        g2.drawString(msg, (width - g2.fontMetrics.stringWidth(msg)) / 2, height / 2)
    }

    private fun drawHud(g2: Graphics2D, noteCount: Int, edgeCount: Int) {
        g2.font  = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        g2.color = cHud

        val status = ObsidianBundle.message("graph.hud.status", noteCount, edgeCount) +
            if (!settled) ObsidianBundle.message("graph.hud.laying.out") else ""
        g2.drawString(status, 10, height - 10)

        val hint = ObsidianBundle.message("graph.hud.hint")
        val hfm  = g2.fontMetrics
        g2.drawString(hint, width - hfm.stringWidth(hint) - 10, height - 10)

        val sel = selected
        if (sel != null) {
            g2.color = cLabel
            g2.font  = Font(Font.SANS_SERIF, Font.BOLD, 12)
            g2.drawString(sel.name, 10, 18)
            g2.font  = Font(Font.SANS_SERIF, Font.PLAIN, 11)
            g2.color = cHud
            g2.drawString(ObsidianBundle.message("graph.hud.connections", sel.degree), 10, 33)
        }
    }

    private fun pushNeighbors(dragged: Node) {
        resolveCollisions(pinned = dragged, maxIter = 15)
    }

    private fun setupMouse() {
        val adapter = object : MouseAdapter() {

            override fun mousePressed(e: MouseEvent) {
                lastMx = e.x; lastMy = e.y
                val hit = nodeAt(e.x, e.y)
                if (hit != null) {
                    dragNode = hit
                    selected = hit
                    repaint()
                } else {
                    dragBg = true
                    val currentPath = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.path
                    selected = if (currentPath != null) nodes.firstOrNull { it.id == currentPath } else null
                    repaint()
                }
            }

            override fun mouseDragged(e: MouseEvent) {
                val dn = dragNode
                if (dn != null) {
                    dn.x = toGx(e.x); dn.y = toGy(e.y)
                    pushNeighbors(dn)
                    repaint()
                } else if (dragBg) {
                    panX += e.x - lastMx
                    panY += e.y - lastMy
                    repaint()
                }
                lastMx = e.x; lastMy = e.y
            }

            override fun mouseReleased(e: MouseEvent) {
                if (dragNode != null && settled) saveLayout()
                dragNode = null
                dragBg   = false
            }

            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount >= 2) {
                    nodeAt(e.x, e.y)?.let { openNote(it) }
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
            val rotation = e.preciseWheelRotation
            if (rotation == 0.0) return@addMouseWheelListener
            val factor = exp(-rotation * 0.11)
            val mx = e.x.toDouble()
            val my = e.y.toDouble()
            panX  = mx - (mx - panX) * factor
            panY  = my - (my - panY) * factor
            scale = (scale * factor).coerceIn(0.04, 15.0)
            repaint()
        }
    }

    private fun toGx(sx: Int) = (sx - panX) / scale
    private fun toGy(sy: Int) = (sy - panY) / scale

    private fun nodeAt(sx: Int, sy: Int): Node? {
        val gx = toGx(sx); val gy = toGy(sy)
        return nodes.lastOrNull { n ->
            val hitR = n.r.toDouble() + 5.0 / scale
            val dx = gx - n.x; val dy = gy - n.y
            dx * dx + dy * dy <= hitR * hitR
        }
    }

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
