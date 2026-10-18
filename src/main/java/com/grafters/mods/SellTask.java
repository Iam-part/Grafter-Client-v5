package com.grafters.mods;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Runs /sell, shift-clicks every matching item from your inventory into the menu, then closes it. */
final class SellTask {
    private enum S { OFF, WAIT, MOVE }

    private S state = S.OFF;
    private int wait, delay, clicks;
    private String filter = "";

    boolean active() { return state != S.OFF; }

    void start(String filter) {
        this.filter = filter;
        state = S.WAIT; wait = 0; clicks = 0;
        Util.command("sell");
    }

    /** @return true when finished (or gave up). */
    boolean tick(Minecraft mc) {
        if (state == S.OFF) return true;
        ChestMenu cm = Util.chestMenu(mc);

        if (state == S.WAIT) {
            if (cm != null) { state = S.MOVE; delay = 4; }
            else if (++wait > 60) { state = S.OFF; return true; }
            return false;
        }

        if (cm == null) { state = S.OFF; return true; }
        if (delay-- > 0) return false;

        int start = cm.getRowCount() * 9;
        for (int i = start; i < start + 36; i++) {
            ItemStack s = cm.getSlot(i).getItem();
            if (!Util.matches(s, filter)) continue;
            if (++clicks > 150) { mc.player.closeContainer(); state = S.OFF; return true; }  // menu full
            Util.click(cm.containerId, i, 0, ContainerInput.QUICK_MOVE);
            delay = 1;
            return false;
        }
        mc.player.closeContainer();
        state = S.OFF;
        return true;
    }
}
