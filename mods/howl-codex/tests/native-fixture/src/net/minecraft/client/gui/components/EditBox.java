package net.minecraft.client.gui.components;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

public final class EditBox implements GuiEventListener {
    private String value = "";
    private Consumer<String> responder = ignored -> {};
    public EditBox(Object font, int x, int y, int width, int height, Component label) {}
    public void setMaxLength(int max) {}
    public void setValue(String value) { this.value = value; responder.accept(value); }
    public void setResponder(Consumer<String> responder) { this.responder = responder; }
    public String getValue() { return value; }
}