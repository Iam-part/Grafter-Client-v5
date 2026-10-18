package com.grafters.mods;

import net.minecraft.world.level.BlockGetter;

import java.lang.reflect.Method;
import java.util.Collection;

/** Talks to Litematica by reflection, so Grafters still loads (and builds) without it installed. */
final class LitematicaBridge {
    private LitematicaBridge() {}

    private static final String HANDLER = "fi.dy.masa.litematica.world.SchematicWorldHandler";
    private static final String DATA = "fi.dy.masa.litematica.data.DataManager";

    static boolean installed() {
        try { Class.forName(HANDLER); return true; }
        catch (Throwable t) { return false; }
    }

    /** The world that holds the loaded schematic's blocks, or null. */
    static BlockGetter world() {
        try {
            Method m = Class.forName(HANDLER).getMethod("getSchematicWorld");
            Object o = m.invoke(null);
            return o instanceof BlockGetter bg ? bg : null;
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean hasPlacements() {
        try {
            Object mgr = Class.forName(DATA).getMethod("getSchematicPlacementManager").invoke(null);
            Object list = mgr.getClass().getMethod("getAllSchematicsPlacements").invoke(mgr);
            return !(list instanceof Collection<?> c) || !c.isEmpty();
        } catch (Throwable t) {
            return true;   // can't tell: assume there is one
        }
    }
}
