package com.grafters.mods;

import com.grafters.GraftersConfig;
import com.grafters.GraftersState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Periodically sells the items in your filter through /sell. */
public final class AutoSell {
    private AutoSell() {}

    private static final SellTask TASK = new SellTask();
    private static int timer;
    private static boolean warned;

    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        LocalPlayer p = mc.player;
        if (p == null) return;

        if (TASK.active()) { TASK.tick(mc); return; }
        if (c.sellItems.isBlank()) {
            if (!warned) { warned = true; Util.chat("Auto Sell: set items first, e.g. .set sell sea_pickle,bone"); }
            return;
        }
        if (timer-- > 0 || GraftersState.screen(mc) != null) return;
        timer = c.sellInterval;

        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (Util.matches(p.getInventory().getItem(i), c.sellItems)) { TASK.start(c.sellItems); return; }
        }
    }
}
