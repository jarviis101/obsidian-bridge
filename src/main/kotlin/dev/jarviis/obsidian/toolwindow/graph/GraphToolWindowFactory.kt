package dev.jarviis.obsidian.toolwindow.graph

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import dev.jarviis.obsidian.ObsidianBundle
import dev.jarviis.obsidian.vault.VaultManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.awt.BorderLayout
import javax.swing.SwingUtilities

class GraphToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val component = if (JBCefApp.isSupported()) {
            GraphPanel(project)
        } else {
            JBPanel<JBPanel<*>>(BorderLayout()).apply {
                add(
                    JBLabel(ObsidianBundle.message("graph.jcef.unsupported")).apply {
                        horizontalAlignment = JBLabel.CENTER
                    },
                    BorderLayout.CENTER
                )
            }
        }
        val content = ContentFactory.getInstance().createContent(component, null, false)
        toolWindow.contentManager.addContent(content)
    }
}

class GraphPanel(private val project: Project) : JBPanel<GraphPanel>(BorderLayout()) {

    private val browser = JBCefBrowser()
    private val jsQuery = JBCefJSQuery.create(browser as com.intellij.ui.jcef.JBCefBrowserBase)
    private val debounceAlarm = com.intellij.util.Alarm(com.intellij.util.Alarm.ThreadToUse.SWING_THREAD)

    init {
        add(browser.component, BorderLayout.CENTER)
        loadHtml()

        // Handle JS → Kotlin messages (e.g., node click)
        jsQuery.addHandler { request ->
            val noteName = request.trim()
            openNoteByName(noteName)
            null
        }

        // Reload graph when vault changes — debounced to avoid rapid re-renders
        service<VaultManager>().addChangeListener {
            debounceAlarm.cancelAllRequests()
            debounceAlarm.addRequest({ pushGraphData() }, 300)
        }
    }

    private fun loadHtml() {
        var html = javaClass.getResource("/graph/index.html")?.readText()
            ?: "<html><body>Graph resources not found.</body></html>"

        // Inline D3.js — loadHTML uses a blob URL so relative <script src="..."> never loads
        val d3Content = javaClass.getResource("/graph/d3.min.js")?.readText()
        if (d3Content != null) {
            html = html.replace("""<script src="d3.min.js"></script>""", "<script>$d3Content</script>")
        }

        // 'n' must match the JS function parameter name used in __bridge.click(n)
        val bridgeJs = jsQuery.inject("n")
        html = html.replace("</body>", "<script>window.__bridge={click:function(n){$bridgeJs}}</script></body>")

        browser.loadHTML(html)

        // Push data once the browser has had time to initialize
        debounceAlarm.addRequest({ pushGraphData() }, 750)
    }

    private fun pushGraphData() {
        val manager = service<VaultManager>()
        val notes = manager.allIndices().flatMap { it.allNotes() }

        val nodesJson = buildJsonArray {
            for (note in notes) add(buildJsonObject {
                put("id", note.path.toString())
                put("name", note.name)
                put("vault", note.vaultName)
            })
        }
        val seenEdges = mutableSetOf<String>()
        val linksJson = buildJsonArray {
            for (note in notes) {
                for (link in note.outgoingLinks) {
                    val target = manager.resolve(link.target, note.path) ?: continue
                    if (target.path == note.path) continue  // skip self-links
                    val edgeKey = minOf(note.path.toString(), target.path.toString()) +
                            "→" + maxOf(note.path.toString(), target.path.toString())
                    if (!seenEdges.add(edgeKey)) continue   // deduplicate
                    add(buildJsonObject {
                        put("source", note.path.toString())
                        put("target", target.path.toString())
                    })
                }
            }
        }

        val edgeCount = seenEdges.size
        com.intellij.openapi.diagnostic.Logger.getInstance(GraphPanel::class.java)
            .info("ObsidianBridge: graph pushGraphData — ${notes.size} nodes, $edgeCount edges")

        val js = "window.updateGraph(${nodesJson},${linksJson});"
        browser.cefBrowser.executeJavaScript(js, "", 0)
    }

    private fun openNoteByName(name: String) {
        val manager = service<VaultManager>()
        val note = manager.allIndices()
            .firstNotNullOfOrNull { it.allNotes().firstOrNull { n -> n.name == name } }
            ?: return
        val vFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByNioFile(note.path) ?: return
        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(vFile, true)
    }
}
