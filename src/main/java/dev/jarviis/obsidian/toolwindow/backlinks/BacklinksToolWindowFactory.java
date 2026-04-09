package dev.jarviis.obsidian.toolwindow.backlinks;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Thin Java factory for the Backlinks tool window.
 * Written in Java so the JVM handles ToolWindowFactory interface defaults natively,
 * avoiding Kotlin bridge methods that trigger deprecated/experimental API warnings.
 */
public class BacklinksToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        BacklinksPanel panel = new BacklinksPanel(project);
        var content = ContentFactory.getInstance().createContent(panel, null, false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }
}
