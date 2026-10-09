package buildcraft.lib.compat.howl.storage;

import buildcraft.lib.platform.storage.MutableItemStorage;

/** Retains original BCCE mutable-handler signatures without a NeoForge runtime. */
public interface IItemHandlerModifiable extends IItemHandler, MutableItemStorage {}
