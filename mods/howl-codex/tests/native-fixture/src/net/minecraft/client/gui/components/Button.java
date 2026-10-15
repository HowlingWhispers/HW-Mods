package net.minecraft.client.gui.components;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

public final class Button implements GuiEventListener {
    public interface OnPress { void onPress(Button button); }
    public final String label;
    private final OnPress onPress;
    public final int x, y, width, height;
    private Button(Component label, OnPress onPress, int x, int y, int width, int height) {
        this.label = label.getString();
        this.onPress = onPress;
        this.x = x; this.y = y; this.width = width; this.height = height;
    }
    public void click() { onPress.onPress(this); }
    public static Builder builder(Component label, OnPress handler) { return new Builder(label, handler); }
    public static final class Builder {
        private final Component label;
        private final OnPress handler;
        private int x, y, width, height;
        public Builder(Component label, OnPress handler) {
            this.label = label; this.handler = handler;
        }
        public Builder bounds(int x, int y, int width, int height) {
            this.x=x; this.y=y; this.width=width; this.height=height; return this;
        }
        public Button build() { return new Button(label, handler, x, y, width, height); }
    }
}