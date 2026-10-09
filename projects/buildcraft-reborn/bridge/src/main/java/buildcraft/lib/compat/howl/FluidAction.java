package buildcraft.lib.compat.howl;

/** Original legacy handler simulation contract; shared by BCCE's item and fluid flows. */
public enum FluidAction {
    EXECUTE,
    SIMULATE;

    public boolean execute() { return this == EXECUTE; }
    public boolean simulate() { return this == SIMULATE; }
}
