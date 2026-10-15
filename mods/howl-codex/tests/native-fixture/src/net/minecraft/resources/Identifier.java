package net.minecraft.resources;
public record Identifier(String value) {
    public static Identifier parse(String id) { return new Identifier(id); }
    @Override public String toString() { return value; }
}