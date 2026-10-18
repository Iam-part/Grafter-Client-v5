package com.grafters.mods;

import com.grafters.GraftersConfig;
import com.grafters.Modules;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** On-screen overlay: watermark, coordinates, FPS and the list of enabled modules. */
public final class Hud {
    private Hud() {}

    private static final int GREEN = 0xFF00FF00;
    private static final int TEXT = 0xFFE6EDE8;

    private static int frames, fps;
    private static long lastSecond = System.currentTimeMillis();

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("grafters", "hud"), (g, dt) -> render(g));
    }

    private static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        GraftersConfig c = GraftersConfig.get();

        frames++;
        long now = System.currentTimeMillis();
        if (now - lastSecond >= 1000) { fps = frames; frames = 0; lastSecond = now; }

        if (!c.hud || mc.player == null) return;
        Font font = mc.font;
        int x = 4, y = 4;

        if (c.hudWatermark) {
            g.fill(x, y, x + 2, y + 10, GREEN);
            g.text(font, "Grafter Client", x + 6, y + 1, GREEN, true);
            y += 14;
        }
        if (c.hudCoords) {
            String s = "XYZ  " + mc.player.getBlockX() + "  " + mc.player.getBlockY() + "  " + mc.player.getBlockZ();
            g.text(font, s, x, y, TEXT, true);
            y += 11;
        }
        if (c.hudFps) {
            g.text(font, fps + " FPS", x, y, TEXT, true);
        }

        if (c.hudModules) {
            List<String> names = new ArrayList<>();
            for (Modules.Feature f : Modules.ALL)
                if (f.cat != Modules.Category.APPEARANCE && f.get.getAsBoolean()) names.add(f.id);
            names.sort(Comparator.comparingInt((String n) -> font.width(n)).reversed());

            int right = mc.getWindow().getGuiScaledWidth() - 3;
            int ry = 3;
            for (String n : names) {
                int w = font.width(n);
                g.fill(right - w - 5, ry - 1, right + 1, ry + 10, 0x90000000);
                g.fill(right, ry - 1, right + 2, ry + 10, GREEN);
                g.text(font, n, right - w - 2, ry + 1, GREEN, true);
                ry += 11;
            }
        }
    }
}
