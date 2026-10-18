package com.grafters.mods;

import com.grafters.GraftersConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

import java.util.Locale;

/**
 * Text settings are typed in chat with a leading dot, e.g.  .set ah spawner
 * Only the commands below are intercepted; any other chat message is sent normally.
 */
public final class Commands {
    private Commands() {}

    public static void register() {
        ClientSendMessageEvents.ALLOW_CHAT.register(msg -> {
            if (!msg.startsWith(".")) return true;
            String body = msg.substring(1).trim();
            String[] parts = body.split("\\s+", 3);
            String cmd = parts[0].toLowerCase(Locale.ROOT);
            if (!cmd.equals("set") && !cmd.equals("help") && !cmd.equals("show")) return true;

            switch (cmd) {
                case "help" -> help();
                case "show" -> show();
                default -> set(parts);
            }
            return false;   // handled: do not send to the server
        });
    }

    private static void help() {
        Util.chat(".set <key> <value>   (no value = clear)   .show lists current values");
        Util.chat("ah = auction sniper item   sell = auto sell items   spawner = spawner sell items");
        Util.chat("drop = item dropper items   rtp = rtp command   tpa = trusted tpa names");
        Util.chat("ahprice = exact auction sniper max price   order = order filler items   orderprice = min $ each");
        Util.chat("webhook = Discord webhook that gets your last placed block when you disconnect");
        Util.chat("Items: comma separated ids or names, e.g.  .set sell sea_pickle,bone");
    }

    private static void show() {
        GraftersConfig c = GraftersConfig.get();
        Util.chat("ah = " + c.sniperItem + "  |  sell = " + c.sellItems + "  |  spawner = " + c.spawnerItems);
        Util.chat("drop = " + c.dropItems + "  |  rtp = " + c.rtpCommand + "  |  tpa = " + c.tpaNames);
        Util.chat("ahprice = " + (c.sniperCustom > 0 ? "$" + Util.formatAmount(c.sniperCustom) : "(preset)")
                + "  |  order = " + c.orderItem + "  |  orderprice = $" + Util.formatAmount(c.orderMinPrice));
        Util.chat("webhook = " + (c.webhookUrl.isBlank() ? "(none)" : "(set)"));
    }

    private static void set(String[] parts) {
        if (parts.length < 2) { help(); return; }
        GraftersConfig c = GraftersConfig.get();
        String key = parts[1].toLowerCase(Locale.ROOT);
        String value = parts.length > 2 ? parts[2].trim() : "";

        switch (key) {
            case "ah" -> c.sniperItem = value;
            case "sell" -> c.sellItems = value;
            case "spawner" -> c.spawnerItems = value;
            case "drop" -> c.dropItems = value;
            case "rtp" -> c.rtpCommand = value.isEmpty() ? "rtp" : value;
            case "tpa" -> c.tpaNames = value;
            case "order" -> c.orderItem = value;
            case "ahprice", "orderprice" -> {
                double amount = value.isEmpty() ? 0 : Util.parseAmount(value);
                if (amount < 0) { Util.chat("Type an amount like 5000, 5k or 2.5m"); return; }
                if (key.equals("ahprice")) c.sniperCustom = amount;
                else if (amount > 0) c.orderMinPrice = amount;
                else { Util.chat("Min price must be more than 0"); return; }
                c.save();
                Util.chat(key + " set to " + (amount > 0 ? "$" + Util.formatAmount(amount) : "(preset)"));
                return;
            }
            case "webhook" -> {
                if (!value.isEmpty() && !value.matches("^https://((ptb|canary)\\.)?discord(app)?\\.com/api/webhooks/\\S+$")) {
                    Util.chat("That is not a Discord webhook URL (https://discord.com/api/webhooks/...)");
                    return;
                }
                c.webhookUrl = value;
                c.save();
                Util.chat(value.isEmpty() ? "Webhook cleared"
                        : "Webhook saved. Your last placed block's coordinates are posted there when you disconnect.");
                return;
            }
            default -> { Util.chat("Unknown key '" + key + "'. Type .help"); return; }
        }
        c.save();
        Util.chat(key + " set to: " + (value.isEmpty() ? "(empty)" : value));
    }
}
