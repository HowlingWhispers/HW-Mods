package dev.howlingwhispers.buildcraft;

import buildcraft.api.v2.energy.MjAmount;
import java.util.Objects;

/*
 * H.O.W.L. compatibility implementation of BCCE's redstone engine state.
 *
 * Original behavioral sources (BCCE 8.0.23, MPL-2.0):
 *  source-families/modern/src/main/java/buildcraft/core/blockEntity/TileEngineRedstone_BC8.java
 *  source-platforms/neoforge/src/main/java/buildcraft/lib/engine/TileEngineBase_BC8.java
 *
 * Copyright (c) 2016-2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of Mozilla Public License, v. 2.0.
 *
 * No invented 20-tick chest teleportation here. Adapted only at the world,
 * persistence, and MJ receiver boundaries. Render state and an actual
 * Minecraft BlockEntity still require the H.O.W.L. integration layer.
 */
public final class BuildCraftRedstoneEngine {
    public static final long MICRO_MJ_PER_MJ = MjAmount.MICRO_MJ_PER_MJ;
    public static final long OUTPUT_PER_TICK = MICRO_MJ_PER_MJ / 20L;
    public static final long MAX_POWER = MICRO_MJ_PER_MJ;
    public static final long MAX_POWER_EXTRACTED = MICRO_MJ_PER_MJ;
    public static final long MIN_POWER_RECEIVED = MICRO_MJ_PER_MJ / 10L;
    public static final long MAX_POWER_RECEIVED = MICRO_MJ_PER_MJ * 4L;
    public static final double MIN_HEAT = 20.0;
    public static final double MAX_HEAT = 250.0;

    /** Narrow port of BCCE API2's simulate/execute MJ output handshake. */
    public interface MjEndpoint {
        boolean redstoneReceiver();
        long simulateInsert(long offeredMicroMj);
        long executeInsert(long offeredMicroMj);

        MjEndpoint NONE = new MjEndpoint() {
            @Override public boolean redstoneReceiver() { return false; }
            @Override public long simulateInsert(long offered) { return 0; }
            @Override public long executeInsert(long offered) { return 0; }
        };
    }

    public enum Stage { BLUE, GREEN, YELLOW, RED, OVERHEAT }

    /** Full tick-exact state to survive a world restart mid-piston-stroke. */
    public record Snapshot(double heat, long powerMicroMj, float progress,
                           int progressPart, boolean powered, boolean pumping) {
        public Snapshot {
            if (!Double.isFinite(heat) || heat < MIN_HEAT || heat > MAX_HEAT + 4
                    || powerMicroMj < 0 || powerMicroMj > MAX_POWER
                    || !Float.isFinite(progress) || progress < 0 || progress >= 1
                    || progressPart < 0 || progressPart > 2)
                throw new IllegalArgumentException("Invalid BCCE redstone engine snapshot");
        }
    }

    public record TickResult(long transferredMicroMj, boolean midpointPulse, boolean pumping,
                             Stage stage, float pistonProgress) {}

    private double heat = MIN_HEAT;
    private long power;
    private float progress;
    private int progressPart;
    private boolean powered;
    private boolean pumping;

    public BuildCraftRedstoneEngine() {}

    public BuildCraftRedstoneEngine(Snapshot saved) {
        Objects.requireNonNull(saved, "saved engine");
        heat = saved.heat();
        power = saved.powerMicroMj();
        progress = saved.progress();
        progressPart = saved.progressPart();
        powered = saved.powered();
        pumping = saved.pumping();
    }

    public Snapshot snapshot() {
        return new Snapshot(heat, power, progress, progressPart, powered, pumping);
    }

    public Stage stage() {
        double level = (heat - MIN_HEAT) / (MAX_HEAT - MIN_HEAT);
        if (level < 0.25) return Stage.BLUE;
        if (level < 0.5) return Stage.GREEN;
        if (level < 0.75) return Stage.YELLOW;
        if (level < 0.85) return Stage.RED;
        return Stage.OVERHEAT;
    }

    public long storedMicroMj() { return power; }
    public double heat() { return heat; }

    /** Original BC8 server piston speed, halved for the redstone engine. */
    public double pistonSpeed() {
        double level = (heat - MIN_HEAT) / (MAX_HEAT - MIN_HEAT);
        return Math.max(0.16 * level, 0.01) / 2.0;
    }

    private long offeredTo(MjEndpoint receiver) {
        if (receiver == MjEndpoint.NONE) return 0;
        long offer = Math.min(power, MAX_POWER_EXTRACTED);
        if (offer == 0) return 0;
        long accepted = receiver.simulateInsert(offer);
        if (accepted < 0 || accepted > offer)
            throw new IllegalStateException("MJ receiver SIMULATE broke energy bounds");
        return accepted;
    }

    private long sendPower(MjEndpoint receiver) {
        long offered = offeredTo(receiver);
        if (offered == 0) return 0;
        long accepted = receiver.executeInsert(offered);
        if (accepted < 0 || accepted > offered)
            throw new IllegalStateException("MJ receiver EXECUTE broke energy bounds");
        power -= accepted; // Ownership transfers only after a valid execute result.
        return accepted;
    }

    /** One authoritative server tick, with BCCE piston-midpoint pulse timing. */
    public TickResult tick(long worldGameTick, boolean hasRedstonePower, MjEndpoint receiver) {
        if (worldGameTick < 0) throw new IllegalArgumentException("Negative world tick");
        Objects.requireNonNull(receiver, "MJ receiver");
        powered = hasRedstonePower;

        // TileEngineBase_BC8.update() decays stored power on redstone loss.
        if (!powered) power = Math.max(0, power - MICRO_MJ_PER_MJ);

        // TileEngineRedstone_BC8 overrides heat cooling and power generation.
        if (heat > MIN_HEAT) heat = Math.max(MIN_HEAT, heat - 0.2);
        if (powered) {
            power = Math.min(MAX_POWER, power + OUTPUT_PER_TICK);
            if (worldGameTick % 16 == 0 &&
                    (heat - MIN_HEAT) / (MAX_HEAT - MIN_HEAT) < 0.8) {
                heat += 4;
            }
        } else {
            power = 0;
        }

        boolean pulsedReceiver = receiver.redstoneReceiver();
        boolean midpoint = false;
        long sent = 0;
        if (progressPart != 0) {
            progress += pistonSpeed();
            if (progress > 0.5 && progressPart == 1) {
                progressPart = 2;
                if (pulsedReceiver) {
                    sent += sendPower(receiver);
                    midpoint = true;
                }
            } else if (progress >= 1) {
                progress = 0;
                progressPart = 0;
            }
        } else if (powered && offeredTo(receiver) > 0) {
            progressPart = 1;
            pumping = true;
        } else {
            pumping = false;
        }
        // BCCE sends ordinary MJ continuously and redstone-receiver MJ only
        // once on the stroke midpoint, including after snapshot restoration.
        if (!pulsedReceiver && powered) sent += sendPower(receiver);
        return new TickResult(sent, midpoint, pumping, stage(), progress);
    }
}
