package com.grafters.gui;

import com.grafters.GraftersClient;
import com.grafters.GraftersConfig;
import com.grafters.GraftersSounds;
import com.grafters.Modules;
import com.grafters.Modules.ColorPick;
import com.grafters.Modules.Feature;
import com.grafters.Modules.Header;
import com.grafters.Modules.Setting;
import com.grafters.Modules.Choice;
import com.grafters.Modules.Slider;
import com.grafters.Modules.Text;
import com.grafters.Modules.Toggle;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Sidebar + module cards layout, green & black. Left click toggles, right click opens settings. */
public class GraftersMenuScreen extends Screen {
    // palette
    private static final int ACCENT     = 0xFF00FF00;
    private static final int ACCENT_DIM = 0xFF00B33C;
    private static final int BG         = 0xFF070B08;
    private static final int SIDE_BG    = 0xFF050806;
    private static final int CARD       = 0xFF0B110D;
    private static final int CARD_HOV   = 0xFF101A13;
    private static final int BORDER     = 0xFF16301F;
    private static final int BORDER_ON  = 0xFF0F5A26;
    private static final int TEXT       = 0xFFE6EDE8;
    private static final int DIM        = 0xFF84948A;
    private static final int FAINT      = 0xFF4C6455;
    private static final int CHIP_BG    = 0xFF080D0A;
    private static final int CHIP_BD    = 0xFF1B3A26;
    private static final int SEL_BG     = 0xFF0F2A17;

    private static final int SIDE_W = 112, CARD_H = 38, GAP = 8;
    private static final int[] PALETTE = {0x00FF00, 0x00FFFF, 0xFF3030, 0xFFFF00, 0xFF55FF, 0xFFFFFF, 0xFF8800};

    private enum Page {
        RENDER("Render modules"), MISC("Misc modules"), DONUT("Donut modules"),
        FAVORITES("Favorites"), APPEARANCE("Appearance");
        final String title;
        Page(String t) { title = t; }
    }

    private interface Click { void run(int button, double mx); }

    private record Region(int x, int y, int w, int h, boolean content, Click click) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final GraftersConfig cfg = GraftersConfig.get();
    private final List<Region> regions = new ArrayList<>();
    private final Map<Object, Float> anims = new IdentityHashMap<>();

    private Page page = Page.RENDER;
    private EditBox search;
    private int px, py, pw, ph, mainX, mainW, contentTop, contentBottom;
    private int scroll, maxScroll;

    private Feature listening;       // waiting for a key to bind
    private Slider dragging;
    private int dragX, dragW;

    public GraftersMenuScreen() { super(Component.literal("Grafter Client")); }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 470);
        ph = Math.min(height - 16, 290);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        mainX = px + SIDE_W + 10;
        mainW = pw - SIDE_W - 20;
        contentTop = py + 34;
        contentBottom = py + ph - 22;

        int sx = px + pw - 8 - 22 - 130;
        search = new EditBox(font, sx + 16, py + 11, 110, 10, Component.literal("Search"));
        search.setBordered(false);
        search.setHint(Component.literal("Search modules..."));
        search.setMaxLength(20);
        search.setTextColor(TEXT);
        addRenderableWidget(search);
    }

    // ------------------------------------------------------------------
    //  Drawing
    // ------------------------------------------------------------------
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        regions.clear();
        Minecraft mc = Minecraft.getInstance();

        g.fill(0, 0, width, height, (Mth.clamp(cfg.menuDim, 0, 100) * 255 / 100) << 24);
        if (cfg.panelGlow) {
            g.outline(px - 3, py - 3, pw + 6, ph + 6, 0x1500FF00);
            g.outline(px - 2, py - 2, pw + 4, ph + 4, 0x2D00FF00);
            g.outline(px - 1, py - 1, pw + 2, ph + 2, 0x6000FF00);
        }
        g.fill(px, py, px + pw, py + ph, op(BG));
        g.outline(px, py, pw, ph, 0xFF1A6B34);

        drawSidebar(g, mx, my);
        drawHeader(g, mx, my);
        drawContent(g, mx, my);

        g.text(font, "Left click: toggle   /   Right click: settings", mainX, py + ph - 14, FAINT, false);
        String esc = "ESC Close";
        g.text(font, esc, px + pw - 10 - font.width(esc), py + ph - 14, FAINT, false);

        // slider dragging (polled so it keeps working while the mouse moves)
        if (dragging != null) {
            boolean down = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            if (down) applySlider(dragging, mx); else dragging = null;
        }
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(px + 1, py + 1, px + SIDE_W, py + ph - 1, op(SIDE_BG));
        g.fill(px + SIDE_W, py + 1, px + SIDE_W + 1, py + ph - 1, BORDER);

        // logo
        int lx = px + 12, ly = py + 11;
        g.fill(lx + 3, ly, lx + 19, ly + 22, ACCENT);
        g.fill(lx, ly + 3, lx + 22, ly + 19, ACCENT);
        g.fill(lx + 5, ly + 4, lx + 17, ly + 18, 0xFF000000);
        g.text(font, "G", lx + 8, ly + 7, ACCENT, true);
        g.text(font, "GRAFTER", lx + 29, ly + 2, TEXT, true);
        g.text(font, "CLIENT", lx + 29, ly + 13, ACCENT_DIM, false);
        g.fill(px + 10, py + 42, px + SIDE_W - 10, py + 43, BORDER);

        int y = py + 52;
        g.text(font, "MODULES", px + 14, y, FAINT, false);
        y += 12;
        nav(g, "Render", Page.RENDER, y, mx, my);       y += 22;
        nav(g, "Misc", Page.MISC, y, mx, my);           y += 22;
        nav(g, "Donut", Page.DONUT, y, mx, my);         y += 22;
        nav(g, "Favorites", Page.FAVORITES, y, mx, my); y += 26;
        g.text(font, "GENERAL", px + 14, y, FAINT, false);
        y += 12;
        nav(g, "Appearance", Page.APPEARANCE, y, mx, my);

        // footer card
        int fx = px + 8, fy = py + ph - 42, fw = SIDE_W - 16;
        g.fill(fx, fy, fx + fw, fy + 34, op(CARD));
        g.outline(fx, fy, fw, 34, BORDER);
        g.fill(fx + 8, fy + 8, fx + 13, fy + 13, ACCENT);
        g.text(font, "Grafter Client", fx + 18, fy + 6, TEXT, false);
        g.text(font, Modules.activeCount() + " / " + Modules.totalCount() + " active", fx + 18, fy + 19, DIM, false);
    }

    private void nav(GuiGraphicsExtractor g, String label, Page target, int y, int mx, int my) {
        int x = px + 8, w = SIDE_W - 16, h = 20;
        boolean sel = page == target && search.getValue().isBlank();
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        if (sel) {
            g.fill(x, y, x + w, y + h, SEL_BG);
            g.fill(x, y + 2, x + 2, y + h - 2, ACCENT);
        } else if (hov) {
            g.fill(x, y, x + w, y + h, 0xFF0A140D);
        }
        g.outline(x + 8, y + 6, 8, 8, sel ? ACCENT : FAINT);
        if (sel) g.fill(x + 10, y + 8, x + 14, y + 12, ACCENT);
        g.text(font, label, x + 22, y + 6, sel ? TEXT : DIM, false);
        regions.add(new Region(x, y, w, h, false, (b, m) -> {
            page = target; scroll = 0; search.setValue("");
            GraftersSounds.click(1.0f);
        }));
    }

    private void drawHeader(GuiGraphicsExtractor g, int mx, int my) {
        String title = search.getValue().isBlank() ? page.title : "Search results";
        g.text(font, title, mainX + 2, py + 12, TEXT, true);

        // search box background (the EditBox itself is drawn on top by the screen)
        int sx = px + pw - 8 - 22 - 130;
        g.fill(sx, py + 7, sx + 130, py + 23, op(CARD));
        g.outline(sx, py + 7, 130, 16, search.isFocused() ? ACCENT_DIM : CHIP_BD);
        g.outline(sx + 5, py + 11, 6, 6, DIM);
        g.fill(sx + 10, py + 16, sx + 12, py + 18, DIM);

        // close button
        int cx = px + pw - 8 - 16, cy = py + 7;
        boolean hov = mx >= cx && mx < cx + 16 && my >= cy && my < cy + 16;
        g.text(font, "x", cx + 6, cy + 4, hov ? ACCENT : DIM, false);
        regions.add(new Region(cx, cy, 16, 16, false, (b, m) -> { GraftersSounds.click(1.0f); onClose(); }));

        g.fill(mainX, py + 28, mainX + mainW, py + 29, BORDER);
        g.fill(mainX, py + 28, mainX + 40, py + 29, ACCENT);
    }

    private List<Feature> visible() {
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Feature> out = new ArrayList<>();
        for (Feature f : Modules.ALL) {
            if (!q.isEmpty()) {
                if (f.id.toLowerCase(Locale.ROOT).contains(q)) out.add(f);
                continue;
            }
            boolean ok = switch (page) {
                case RENDER -> f.cat == Modules.Category.RENDER;
                case MISC -> f.cat == Modules.Category.MISC;
                case DONUT -> f.cat == Modules.Category.DONUT;
                case APPEARANCE -> f.cat == Modules.Category.APPEARANCE;
                case FAVORITES -> cfg.favorites.contains(f.id);
            };
            if (ok) out.add(f);
        }
        return out;
    }

    private void drawContent(GuiGraphicsExtractor g, int mx, int my) {
        List<Feature> list = visible();
        if (list.isEmpty()) {
            String msg = page == Page.FAVORITES && search.getValue().isBlank()
                    ? "Star a module to pin it here" : "No modules found";
            g.text(font, msg, mainX + (mainW - font.width(msg)) / 2, contentTop + 40, FAINT, false);
            return;
        }

        int cardW = (mainW - GAP) / 2;
        int[] colY = {contentTop - scroll, contentTop - scroll};

        g.enableScissor(mainX - 1, contentTop, mainX + mainW + 1, contentBottom);
        for (Feature f : list) {
            int col = colY[0] <= colY[1] ? 0 : 1;
            int x = mainX + col * (cardW + GAP);
            int h = drawCard(g, f, x, colY[col], cardW, mx, my);
            colY[col] += h + GAP;
        }
        g.disableScissor();

        int contentH = Math.max(colY[0], colY[1]) + scroll - contentTop;
        maxScroll = Math.max(0, contentH - (contentBottom - contentTop));
        scroll = Mth.clamp(scroll, 0, maxScroll);

        if (maxScroll > 0) {   // thin scrollbar
            int trackH = contentBottom - contentTop;
            int barH = Math.max(14, trackH * trackH / (trackH + maxScroll));
            int barY = contentTop + (int) ((trackH - barH) * (scroll / (float) maxScroll));
            g.fill(mainX + mainW + 3, barY, mainX + mainW + 5, barY + barH, ACCENT_DIM);
        }
    }

    private int drawCard(GuiGraphicsExtractor g, Feature f, int x, int y, int w, int mx, int my) {
        boolean on = f.get.getAsBoolean();
        boolean expandable = !f.settings.isEmpty();
        boolean open = f.expanded && expandable;
        int h = CARD_H + (open ? settingsHeight(f) + 6 : 0);
        boolean visible = y + h > contentTop && y < contentBottom;
        boolean inView = my >= contentTop && my < contentBottom;
        boolean hov = inView && mx >= x && mx < x + w && my >= y && my < y + h;

        g.fill(x, y, x + w, y + h, op(hov ? CARD_HOV : CARD));
        g.outline(x, y, w, h, on ? BORDER_ON : BORDER);
        if (on) g.fill(x, y + 3, x + 2, y + CARD_H - 3, ACCENT);
        g.text(font, f.id, x + 10, y + 8, TEXT, true);

        if (visible) {
            // card body: left = toggle, right = settings
            regions.add(new Region(x, y, w, CARD_H, true, (b, m) -> {
                if (b == 0) toggle(f);
                else if (b == 1 && expandable) { f.expanded = !f.expanded; GraftersSounds.click(1.1f); }
            }));
        }

        // keybind chip
        if (f.keyable) {
            String label = listening == f ? "Press a key..." : "Key: " + keyName(f);
            int cw = font.width(label) + 10;
            boolean bound = cfg.binds.containsKey(f.id);
            g.fill(x + 10, y + 22, x + 10 + cw, y + 34, CHIP_BG);
            g.outline(x + 10, y + 22, cw, 12, listening == f ? ACCENT : CHIP_BD);
            g.text(font, label, x + 15, y + 25, listening == f ? ACCENT : (bound ? TEXT : DIM), false);
            if (visible) regions.add(new Region(x + 10, y + 22, cw, 12, true, (b, m) -> {
                if (b == 0) { listening = f; GraftersSounds.click(1.0f); }
                else if (b == 1) { cfg.binds.remove(f.id); listening = null; GraftersSounds.click(0.8f); }
            }));
        }

        // right side: +/-, star, switch
        int swW = 24, swH = 12;
        int swX = x + w - 8 - swW, swY = y + 13;
        pill(g, swX, swY, swW, swH, anim(f, on));
        if (visible) regions.add(new Region(swX, swY, swW, swH, true, (b, m) -> { if (b == 0) toggle(f); }));

        if (f.cat != Modules.Category.APPEARANCE) {
            boolean fav = cfg.favorites.contains(f.id);
            int starX = swX - 16;
            g.text(font, fav ? "\u2605" : "\u2606", starX, y + 15, fav ? ACCENT : DIM, false);
            if (visible) regions.add(new Region(starX - 2, y + 11, 14, 16, true, (b, m) -> {
                if (b != 0) return;
                if (!cfg.favorites.remove(f.id)) cfg.favorites.add(f.id);
                GraftersSounds.click(1.2f);
            }));
        }
        if (expandable) {
            g.text(font, open ? "-" : "+", x + w - 12, y + 4, DIM, false);
            if (visible) regions.add(new Region(x + w - 16, y + 1, 14, 11, true, (b, m) -> {
                f.expanded = !f.expanded; GraftersSounds.click(1.1f);
            }));
        }

        if (open) {
            int sy = y + CARD_H;
            g.fill(x + 8, sy - 1, x + w - 8, sy, BORDER);
            sy += 3;
            for (Setting s : f.settings) sy += drawSetting(g, s, x, sy, w, visible);
        }
        return h;
    }

    private int settingsHeight(Feature f) {
        int h = 0;
        for (Setting s : f.settings) h += rowHeight(s);
        return h;
    }

    private int rowHeight(Setting s) {
        if (s instanceof Header) return 16;
        if (s instanceof Slider) return 26;
        return 18;
    }

    private int drawSetting(GuiGraphicsExtractor g, Setting s, int x, int y, int w, boolean visible) {
        int h = rowHeight(s);
        int lx = x + 10, rw = w - 20;

        if (s instanceof Header hd) {
            g.text(font, hd.label(), lx, y + 5, FAINT, false);
        } else if (s instanceof Toggle t) {
            boolean v = t.get().getAsBoolean();
            g.text(font, t.label(), lx, y + 5, v ? TEXT : DIM, false);
            pill(g, x + w - 10 - 20, y + 4, 20, 10, anim(t, v));
            if (visible) regions.add(new Region(x + 4, y, w - 8, h, true, (b, m) -> {
                if (b != 0) return;
                boolean nv = !t.get().getAsBoolean();
                t.set().accept(nv);
                GraftersSounds.toggle(nv);
            }));
        } else if (s instanceof Slider sl) {
            double v = sl.get().getAsDouble();
            double frac = Mth.clamp((v - sl.min()) / (sl.max() - sl.min()), 0, 1);
            String val = String.format(Locale.ROOT, "%." + sl.decimals() + "f", v) + sl.unit();
            g.text(font, sl.label(), lx, y + 3, DIM, false);
            g.text(font, val, lx + rw - font.width(val), y + 3, DIM, false);
            int ty = y + 16;
            g.fill(lx, ty, lx + rw, ty + 3, 0xFF16241B);
            g.fill(lx, ty, lx + (int) (rw * frac), ty + 3, ACCENT_DIM);
            int kx = lx + (int) (rw * frac);
            g.fill(kx - 2, ty - 2, kx + 2, ty + 5, dragging == sl ? ACCENT : TEXT);
            if (visible) regions.add(new Region(lx - 2, y + 11, rw + 4, 14, true, (b, m) -> {
                if (b != 0) return;
                dragging = sl; dragX = lx; dragW = rw;
                applySlider(sl, m);
            }));
        } else if (s instanceof Choice ch) {
            int n = ch.options().length;
            int idx = Mth.clamp(ch.get().getAsInt(), 0, n - 1);
            String val = ch.options()[idx];
            g.text(font, ch.label(), lx, y + 5, DIM, false);
            int cw = font.width(val) + 12;
            int cx = x + w - 10 - cw;
            g.fill(cx, y + 2, cx + cw, y + 15, CHIP_BG);
            g.outline(cx, y + 2, cw, 13, CHIP_BD);
            g.text(font, val, cx + 6, y + 5, TEXT, false);
            if (visible) regions.add(new Region(cx, y + 2, cw, 13, true, (b, m) -> {
                int ni = (idx + (b == 1 ? n - 1 : 1)) % n;
                ch.set().accept(ni);
                GraftersSounds.click(1.1f);
            }));
        } else if (s instanceof Text tx) {
            String val = tx.get().get();
            if (val == null || val.isBlank()) val = "(none)";
            g.text(font, tx.label(), lx, y + 5, DIM, false);
            int maxW = rw - font.width(tx.label()) - 8;
            g.text(font, fit(val, maxW), x + w - 10 - font.width(fit(val, maxW)), y + 5, TEXT, false);
        } else if (s instanceof ColorPick cp) {
            int col = cp.get().getAsInt();
            g.text(font, cp.label(), lx, y + 5, DIM, false);
            String hex = String.format("#%06X", col & 0xFFFFFF);
            int cw = font.width(hex) + 20;
            int cx = x + w - 10 - cw;
            g.fill(cx, y + 2, cx + cw, y + 15, CHIP_BG);
            g.outline(cx, y + 2, cw, 13, CHIP_BD);
            g.fill(cx + 4, y + 5, cx + 11, y + 12, 0xFF000000 | col);
            g.text(font, hex, cx + 14, y + 5, TEXT, false);
            if (visible) regions.add(new Region(cx, y + 2, cw, 13, true, (b, m) -> {
                int idx = 0;
                for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == (col & 0xFFFFFF)) idx = i;
                idx = (idx + (b == 1 ? PALETTE.length - 1 : 1)) % PALETTE.length;
                cp.set().accept(PALETTE[idx]);
                GraftersSounds.click(1.1f);
            }));
        }
        return h;
    }

    private String fit(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "..") > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, float t) {
        g.fill(x, y, x + w, y + h, lerp(0xFF1B241E, ACCENT, t));
        g.outline(x, y, w, h, lerp(0xFF2C3A31, ACCENT, t));
        int kx = x + 2 + Math.round(t * (w - h));
        g.fill(kx, y + 2, kx + h - 4, y + h - 2, lerp(0xFF6B7A70, 0xFF000000, t));
    }

    private float anim(Object key, boolean on) {
        float target = on ? 1f : 0f;
        float cur = anims.getOrDefault(key, target);
        cur += (target - cur) * 0.3f;
        anims.put(key, cur);
        return cur;
    }

    /** Applies the panel opacity setting to a colour's alpha. */
    private int op(int argb) {
        int a = (int) (((argb >>> 24) & 255) * Mth.clamp(cfg.panelOpacity, 0, 100) / 100f);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int lerp(int a, int b, float t) {
        int r = 0;
        for (int s = 0; s < 32; s += 8) {
            int ca = (a >> s) & 255, cb = (b >> s) & 255;
            r |= ((int) (ca + (cb - ca) * t) & 255) << s;
        }
        return r;
    }

    // ------------------------------------------------------------------
    //  Logic
    // ------------------------------------------------------------------
    private void toggle(Feature f) {
        boolean v = !f.get.getAsBoolean();
        f.set.accept(v);
        GraftersSounds.toggle(v);                       // audio feedback on every toggle
    }

    private String keyName(Feature f) {
        Integer k = cfg.binds.get(f.id);
        if (k == null) return "None";
        return InputConstants.Type.KEYSYM.getOrCreate(k).getDisplayName().getString();
    }

    private void applySlider(Slider s, double mx) {
        double frac = Mth.clamp((mx - dragX) / dragW, 0, 1);
        double v = s.min() + frac * (s.max() - s.min());
        v = Math.round(v / s.step()) * s.step();
        v = Mth.clamp(v, s.min(), s.max());
        s.set().accept(v);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;     // search box
        double mx = event.x(), my = event.y();
        int btn = event.button();
        listening = null;
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (r.content() && (my < contentTop || my >= contentBottom)) continue;
            if (r.hit(mx, my)) {
                search.setFocused(false);
                r.click().run(btn, mx);
                return true;
            }
        }
        search.setFocused(false);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) (scrollY * 14), 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening != null) {
            int k = event.key();
            if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE) {
                cfg.binds.remove(listening.id);
            } else {
                cfg.binds.put(listening.id, k);
            }
            listening = null;
            GraftersSounds.click(1.2f);
            return true;
        }
        if (!search.isFocused() && GraftersClient.MENU_KEY.matches(event)) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { cfg.save(); }
}
