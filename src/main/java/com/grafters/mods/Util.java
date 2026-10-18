package com.grafters.mods;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small helpers shared by the module classes. */
public final class Util {
    private Util() {}

    private static final Pattern PRICE = Pattern.compile("\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kKmMbBtT])?");

    public static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.sendSystemMessage(Component.literal("\u00A7a[Grafters] \u00A7f" + msg));
    }

    public static void command(String cmd) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
    }

    public static void click(int containerId, int slot, int button, ContainerInput type) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleContainerInput(containerId, slot, button, type, mc.player);
    }

    /** The open chest-style menu (server GUIs use these too), or null. */
    public static ChestMenu chestMenu(Minecraft mc) {
        if (mc.player != null && mc.player.containerMenu instanceof ChestMenu cm) return cm;
        return null;
    }

    public static String itemId(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
    }

    /** Comma separated filter: matches the item id or name. Empty filter matches everything. */
    public static boolean matches(ItemStack s, String filter) {
        if (s.isEmpty()) return false;
        if (filter == null || filter.isBlank()) return true;
        String id = itemId(s);
        String name = s.getHoverName().getString().toLowerCase(Locale.ROOT).replace(' ', '_');
        for (String tok : filter.toLowerCase(Locale.ROOT).split(",")) {
            tok = tok.trim().replace(' ', '_');
            if (tok.isEmpty()) continue;
            if (id.contains(tok) || name.contains(tok)) return true;
        }
        return false;
    }

    /** Reads a price like "$1.5K" or "Price: 2,500,000" from the item's lore. -1 if none. */
    public static double price(ItemStack s) {
        ItemLore lore = s.get(DataComponents.LORE);
        if (lore == null) return -1;
        for (Component line : lore.lines()) {
            String t = line.getString();
            String low = t.toLowerCase(Locale.ROOT);
            if (!low.contains("price") && !t.contains("$")) continue;
            Matcher m = PRICE.matcher(t);
            if (!m.find()) continue;
            double v;
            try { v = Double.parseDouble(m.group(1).replace(",", "")); }
            catch (NumberFormatException e) { continue; }
            String suf = m.group(2);
            if (suf != null) {
                switch (Character.toLowerCase(suf.charAt(0))) {
                    case 'k' -> v *= 1e3;
                    case 'm' -> v *= 1e6;
                    case 'b' -> v *= 1e9;
                    case 't' -> v *= 1e12;
                    default -> { }
                }
            }
            return v;
        }
        return -1;
    }

    /** Parses amounts like 5k, 2.5M, $1,000 or 750. Returns -1 if it is not a number. */
    public static double parseAmount(String text) {
        if (text == null) return -1;
        String t = text.trim().replace("$", "").replace(",", "");
        Matcher m = Pattern.compile("^([0-9]*\\.?[0-9]+)\\s*([kKmMbBtT])?$").matcher(t);
        if (!m.matches()) return -1;
        double v = Double.parseDouble(m.group(1));
        if (m.group(2) != null) {
            switch (Character.toLowerCase(m.group(2).charAt(0))) {
                case 'k' -> v *= 1e3;
                case 'm' -> v *= 1e6;
                case 'b' -> v *= 1e9;
                case 't' -> v *= 1e12;
                default -> { }
            }
        }
        return v;
    }

    /** 1500 -> 1.5K, 2000000 -> 2M. */
    public static String formatAmount(double v) {
        String[] units = {"", "K", "M", "B", "T"};
        int i = 0;
        while (v >= 1000 && i < units.length - 1) { v /= 1000; i++; }
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + units[i];
    }

    /**
     * The price PER ITEM from an order's lore. Prefers lines that say "each"/"per", then
     * lines that say "price", then any line with a $ amount. -1 if none.
     */
    public static double unitPrice(ItemStack s) {
        ItemLore lore = s.get(DataComponents.LORE);
        if (lore == null) return -1;
        double fallback = -1;
        for (Component line : lore.lines()) {
            String t = line.getString();
            String low = t.toLowerCase(Locale.ROOT);
            if (!t.contains("$") && !low.contains("price") && !low.contains("each") && !low.contains("per")) continue;
            Matcher m = PRICE.matcher(t);
            if (!m.find()) continue;
            double v;
            try { v = Double.parseDouble(m.group(1).replace(",", "")); }
            catch (NumberFormatException e) { continue; }
            if (m.group(2) != null) {
                switch (Character.toLowerCase(m.group(2).charAt(0))) {
                    case 'k' -> v *= 1e3;
                    case 'm' -> v *= 1e6;
                    case 'b' -> v *= 1e9;
                    case 't' -> v *= 1e12;
                    default -> { }
                }
            }
            if (low.contains("each") || low.contains("per")) return v;
            if (fallback < 0 || low.contains("price")) fallback = v;
        }
        return fallback;
    }
}
