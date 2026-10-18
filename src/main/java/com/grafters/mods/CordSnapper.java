package com.grafters.mods;

import net.minecraft.client.Minecraft;

/** One-shot: copies your coordinates to the clipboard. */
public final class CordSnapper {
    private CordSnapper() {}

    public static void run() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String xyz = mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();
        mc.keyboardHandler.setClipboard(xyz);
        Util.chat("Copied coordinates: " + xyz);
    }
}
