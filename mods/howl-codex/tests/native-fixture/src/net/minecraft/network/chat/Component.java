package net.minecraft.network.chat;
public final class Component {
    private final String value;
    private Component(String value) { this.value = value; }
    public static Component literal(String text) { return new Component(text); }
    public String getString() { return value; }
}