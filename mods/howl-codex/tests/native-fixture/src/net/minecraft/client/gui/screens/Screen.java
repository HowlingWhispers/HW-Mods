package net.minecraft.client.gui.screens;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;

public class Screen {
    public int width = 960, height = 540;
    private final List<Object> renderables = new ArrayList<>();
    private final List<GuiEventListener> narratables = new ArrayList<>();
    private final List<GuiEventListener> children = new ArrayList<>();
    public List<GuiEventListener> children() { return children; }
    protected <T extends GuiEventListener> T addRenderableWidget(T widget) {
        children.add(widget);
        renderables.add(widget);
        narratables.add(widget);
        return widget;
    }
    protected <T extends Renderable> T addRenderableOnly(T widget) {
        renderables.add(widget);
        return widget;
    }
    public int testIconRenderCount() {
        var graphics = new GuiGraphicsExtractor();
        for (Object widget : renderables)
            if (widget instanceof Renderable icon)
                icon.extractRenderState(graphics, 0, 0, 0);
        return graphics.drawn;
    }
    public void rebuildWidgets() {
        children.clear(); renderables.clear(); narratables.clear();
    }
}