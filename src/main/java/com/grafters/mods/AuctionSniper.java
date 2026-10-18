package com.grafters.mods;

import com.grafters.GraftersConfig;
import com.grafters.GraftersState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Opens /ah (with your item as the search), buys the first matching listing at or
 * below your max price, clicks the confirm button, then refreshes. Written from
 * public descriptions of how DonutSMP's auction menu works: layout and wording may
 * need tuning on the server.
 */
public final class AuctionSniper {
    private AuctionSniper() {}

    public static final String[] PRICE_LABELS = {"$1K", "$5K", "$10K", "$25K", "$50K", "$100K", "$250K",
            "$500K", "$1M", "$5M", "$10M", "$50M", "$100M", "$1B"};
    public static final double[] PRICES = {1e3, 5e3, 1e4, 2.5e4, 5e4, 1e5, 2.5e5, 5e5, 1e6, 5e6, 1e7, 5e7, 1e8, 1e9};

    /** Exact price from .set ahprice if you set one, otherwise the preset you picked. */
    public static double maxPrice(GraftersConfig c) {
        if (c.sniperCustom > 0) return c.sniperCustom;
        return PRICES[Math.max(0, Math.min(PRICES.length - 1, c.sniperPrice))];
    }

    private enum S { IDLE, WAIT_AH, SCAN, WAIT_CONFIRM, CONFIRM }

    private static S state = S.IDLE;
    private static int timer, wait, delay, lastId;
    private static boolean warned;

    public static void reset() { state = S.IDLE; timer = 0; wait = 0; warned = false; }

    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        LocalPlayer p = mc.player;
        if (p == null) return;

        if (c.sniperItem.isBlank()) {
            if (!warned) { warned = true; Util.chat("Auction Sniper: pick an item first, e.g. .set ah spawner"); }
            return;
        }
        if (timer > 0) { timer--; return; }

        ChestMenu cm = Util.chestMenu(mc);
        int idx = Math.max(0, Math.min(PRICES.length - 1, c.sniperPrice));

        switch (state) {
            case IDLE -> {
                if (GraftersState.screen(mc) != null) return;
                Util.command("ah " + c.sniperItem.trim());
                state = S.WAIT_AH; wait = 0;
            }
            case WAIT_AH -> {
                if (cm != null) { state = S.SCAN; delay = 4; lastId = cm.containerId; }
                else if (++wait > 60) { state = S.IDLE; timer = c.sniperRefresh; }
            }
            case SCAN -> {
                if (cm == null) { state = S.IDLE; timer = c.sniperRefresh; return; }
                if (delay-- > 0) return;
                for (int i = 0; i < cm.getRowCount() * 9; i++) {
                    ItemStack st = cm.getSlot(i).getItem();
                    if (st.isEmpty() || !Util.matches(st, c.sniperItem)) continue;
                    double price = Util.price(st);
                    if (price < 0 || price > maxPrice(c)) continue;
                    Util.click(cm.containerId, i, 0, ContainerInput.PICKUP);
                    state = S.WAIT_CONFIRM; wait = 0; lastId = cm.containerId;
                    return;
                }
                p.closeContainer();
                state = S.IDLE; timer = c.sniperRefresh;
            }
            case WAIT_CONFIRM -> {
                if (cm != null && cm.containerId != lastId) { state = S.CONFIRM; delay = c.sniperClick; }
                else if (++wait > 30) { p.closeContainer(); state = S.IDLE; timer = c.sniperRefresh; }
            }
            case CONFIRM -> {
                if (cm == null) { state = S.IDLE; timer = c.sniperRefresh; return; }
                if (delay-- > 0) return;
                for (int i = 0; i < cm.getRowCount() * 9; i++) {
                    ItemStack st = cm.getSlot(i).getItem();
                    if (st.isEmpty()) continue;
                    String name = st.getHoverName().getString().toLowerCase(Locale.ROOT);
                    if (name.contains("confirm") || Util.itemId(st).contains("lime")) {
                        Util.click(cm.containerId, i, 0, ContainerInput.PICKUP);
                        Util.chat("Sniper bought a listing (max $" + Util.formatAmount(maxPrice(c)) + ")");
                        break;
                    }
                }
                p.closeContainer();
                state = S.IDLE; timer = 10;
            }
        }
    }
}
