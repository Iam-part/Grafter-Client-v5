package com.grafters.mods;

import com.grafters.GraftersConfig;
import com.grafters.GraftersState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sniper for buy orders. It searches /order for the item you chose, looks at what each order
 * pays PER ITEM, picks the best one that pays at least your minimum, opens its Deliver menu,
 * shift-clicks your matching items in, closes it, and presses Confirm. Then it refreshes.
 * Written from public descriptions of DonutSMP's /orders menus: wording may need tuning.
 */
public final class OrderFiller {
    private OrderFiller() {}

    public static final String[] PRICE_LABELS = {"$1", "$5", "$10", "$50", "$100", "$500", "$1K", "$5K",
            "$10K", "$50K", "$100K", "$500K", "$1M", "Custom"};
    public static final double[] PRICES = {1, 5, 10, 50, 100, 500, 1e3, 5e3, 1e4, 5e4, 1e5, 5e5, 1e6};

    public static int priceIndex() {
        double v = GraftersConfig.get().orderMinPrice;
        for (int i = 0; i < PRICES.length; i++) if (Math.abs(PRICES[i] - v) < 1e-9) return i;
        return PRICE_LABELS.length - 1;
    }

    public static void setPriceIndex(int i) {
        if (i >= 0 && i < PRICES.length) GraftersConfig.get().orderMinPrice = PRICES[i];
    }

    private enum S { IDLE, WAIT_LIST, SCAN, WAIT_PAGE, WAIT_DELIVER, DELIVER, WAIT_CONFIRM, CONFIRM }

    private static S state = S.IDLE;
    private static int timer, wait, delay, lastId, page, clicks, itemIdx;
    private static String current = "";
    private static double chosenPrice;
    private static boolean warned;

    public static void reset() { state = S.IDLE; timer = 0; wait = 0; page = 0; warned = false; }

    private static List<String> items(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String s : text.split(",")) if (!s.isBlank()) out.add(s.trim());
        return out;
    }

    private static boolean carries(LocalPlayer p, String token) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (Util.matches(p.getInventory().getItem(i), token)) return true;
        return false;
    }

    private static int findButton(ChestMenu cm, String word) {
        for (int i = 0; i < cm.getRowCount() * 9; i++) {
            ItemStack st = cm.getSlot(i).getItem();
            if (!st.isEmpty() && st.getHoverName().getString().toLowerCase(Locale.ROOT).contains(word)) return i;
        }
        return -1;
    }

    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        LocalPlayer p = mc.player;
        if (p == null) return;

        List<String> items = items(c.orderItem);
        if (items.isEmpty()) {
            if (!warned) { warned = true; Util.chat("Order Filler: pick an item first, e.g. .set order bone"); }
            return;
        }
        if (timer > 0) { timer--; return; }
        ChestMenu cm = Util.chestMenu(mc);

        switch (state) {
            case IDLE -> {
                if (GraftersState.screen(mc) != null) return;
                String pick = null;                       // next item you are actually carrying
                for (int k = 0; k < items.size(); k++) {
                    String it = items.get((itemIdx + k) % items.size());
                    if (carries(p, it)) { pick = it; itemIdx = (itemIdx + k + 1) % items.size(); break; }
                }
                if (pick == null) { timer = 40; return; }  // nothing to deliver
                current = pick;
                page = 0;
                Util.command("order " + current.replace('_', ' '));
                state = S.WAIT_LIST; wait = 0;
            }
            case WAIT_LIST -> {
                if (cm != null) { state = S.SCAN; delay = 4; wait = 0; lastId = cm.containerId; }
                else if (++wait > 60) { state = S.IDLE; timer = c.orderRefresh; }
            }
            case SCAN -> {
                if (cm == null) {
                    if (++wait > 20) { state = S.IDLE; timer = c.orderRefresh; }
                    return;
                }
                if (delay-- > 0) return;
                int bestSlot = -1;
                double bestPrice = -1;
                for (int i = 0; i < cm.getRowCount() * 9; i++) {
                    ItemStack st = cm.getSlot(i).getItem();
                    if (st.isEmpty() || !Util.matches(st, current)) continue;
                    double unit = Util.unitPrice(st);
                    if (unit < c.orderMinPrice) continue;
                    if (unit > bestPrice) { bestPrice = unit; bestSlot = i; }
                }
                if (bestSlot >= 0) {
                    chosenPrice = bestPrice;
                    lastId = cm.containerId;
                    Util.click(cm.containerId, bestSlot, 0, ContainerInput.PICKUP);
                    state = S.WAIT_DELIVER; wait = 0;
                    return;
                }
                if (page < c.orderPages - 1) {            // try the next page of orders
                    int next = findButton(cm, "next");
                    if (next >= 0) {
                        page++;
                        Util.click(cm.containerId, next, 0, ContainerInput.PICKUP);
                        state = S.WAIT_PAGE; wait = 0;
                        return;
                    }
                }
                p.closeContainer();
                state = S.IDLE; timer = c.orderRefresh;
            }
            case WAIT_PAGE -> {
                if (++wait >= 8) { state = S.SCAN; delay = 2; wait = 0; }
            }
            case WAIT_DELIVER -> {
                if (cm != null && cm.containerId != lastId) { state = S.DELIVER; delay = c.orderClick; clicks = 0; lastId = cm.containerId; }
                else if (++wait > 40) { p.closeContainer(); state = S.IDLE; timer = c.orderRefresh; }
            }
            case DELIVER -> {
                if (cm == null) { state = S.IDLE; timer = c.orderRefresh; return; }
                if (delay-- > 0) return;
                int start = cm.getRowCount() * 9;
                for (int i = start; i < start + 36; i++) {
                    if (!Util.matches(cm.getSlot(i).getItem(), current)) continue;
                    if (++clicks > 120) { p.closeContainer(); state = S.IDLE; timer = c.orderRefresh; return; }
                    Util.click(cm.containerId, i, 0, ContainerInput.QUICK_MOVE);
                    delay = 1;
                    return;
                }
                p.closeContainer();                          // closing opens the Confirm Delivery menu
                state = S.WAIT_CONFIRM; wait = 0;
            }
            case WAIT_CONFIRM -> {
                if (cm != null) { state = S.CONFIRM; delay = c.orderClick; lastId = cm.containerId; }
                else if (++wait > 40) { state = S.IDLE; timer = c.orderRefresh; }
            }
            case CONFIRM -> {
                if (cm == null) { state = S.IDLE; timer = c.orderRefresh; return; }
                if (delay-- > 0) return;
                for (int i = 0; i < cm.getRowCount() * 9; i++) {
                    ItemStack st = cm.getSlot(i).getItem();
                    if (st.isEmpty()) continue;
                    String name = st.getHoverName().getString().toLowerCase(Locale.ROOT);
                    if (name.contains("confirm") || Util.itemId(st).contains("lime")) {
                        Util.click(cm.containerId, i, 0, ContainerInput.PICKUP);
                        Util.chat("Order Filler delivered " + current + " to an order paying $" + Util.formatAmount(chosenPrice) + " each");
                        break;
                    }
                }
                p.closeContainer();
                state = S.IDLE; timer = Math.max(10, c.orderRefresh / 2);
            }
        }
    }
}
