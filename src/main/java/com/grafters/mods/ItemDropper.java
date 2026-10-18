package com.grafters.mods;

import com.grafters.GraftersConfig;
import com.grafters.GraftersState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;

/** Drops whole stacks of the items in your filter, one stack every few ticks. */
public final class ItemDropper {
    private ItemDropper() {}

    private static int timer;

    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        LocalPlayer p = mc.player;
        if (p == null || GraftersState.screen(mc) != null) return;
        if (timer-- > 0) return;
        timer = c.dropDelay;

        int last = c.dropHotbar ? 44 : 35;            // inventory menu: 9-35 main, 36-44 hotbar
        for (int slot = 9; slot <= last; slot++) {
            if (Util.matches(p.inventoryMenu.getSlot(slot).getItem(), c.dropItems)) {
                Util.click(p.inventoryMenu.containerId, slot, 1, ContainerInput.THROW);
                return;
            }
        }
    }
}
