#!/usr/bin/env python3
"""Static worldgen pack contract checks, requiring no Minecraft runtime."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "mods" / "hw-quiet-underground" / "datapack"
def read(path):
    return json.loads((PACK / path).read_text(encoding="utf-8"))
def walk(node):
    if isinstance(node, dict):
        yield node
        for val in node.values():
            yield from walk(val)
    elif isinstance(node, list):
        for val in node:
            yield from walk(val)
def main():
    meta = read("pack.mcmeta")
    assert meta["pack"]["min_format"] == 123
    assert meta["pack"]["max_format"] == 123
    d = read("data/minecraft/worldgen/density_function/overworld/final_density.json")
    gates = [n for n in walk(d) if n.get("type") == "minecraft:range_choice"
             and isinstance(n.get("input"), dict)
             and n["input"].get("noise") == "minecraft:cave_cheese"]
    assert len(gates) == 1, len(gates)
    gate = gates[0]
    assert gate["when_out_of_range"] == 1.0
    assert gate["min_inclusive"] == 0.40
    assert gate["when_in_range"]["type"] == "minecraft:clamp"
    canyon = read("data/minecraft/worldgen/carver/canyon.json")
    assert canyon["type"] == "minecraft:canyon"
    assert canyon["probability"] == 0.002
    for name, probability in (("cave", 0.15), ("cave_extra_underground", 0.07)):
        cave = read(f"data/minecraft/worldgen/carver/{name}.json")
        assert cave["type"] == "minecraft:cave"
        assert cave["probability"] == probability
        assert cave["weird_thickness_bias"] is False
    print("PASS: format 123, 1 cheese gate, 5x rarer ravines, normal cave frequency retained")
if __name__ == "__main__":
    main()
