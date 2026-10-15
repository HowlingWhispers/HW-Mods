package net.minecraft.client.gui.screens;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.events.GuiEventListener;

public class Screen {
    public int width = 960, height = 540;
    protected final List<GuiEventListener> renderables = new ArrayList<>();
    protected final List<GuiEventListener> narratables = new ArrayList<>();
    private final List<GuiEventListener> children = new ArrayList<>();
    public List<GuiEventListener> children() { return children; }
    protected <T extends GuiEventListener> T addRenderableWidget(T widget) {
        children.add(widget);
        renderables.add(widget);
        narratables.add(widget);
        return widget;
    }
    public void rebuildWidgets() {
        children.clear(); renderables.clear(); narratables.clear();
    }
}