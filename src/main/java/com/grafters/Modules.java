package com.grafters;

import com.grafters.mods.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** The module list shown in the menu, plus keybind handling and per-tick module updates. */
public final class Modules {
    private Modules() {}

    public enum Category { RENDER, MISC, DONUT, APPEARANCE }

    public interface Setting {}
    public record Header(String label) implements Setting {}
    public record Toggle(String label, BooleanSupplier get, Consumer<Boolean> set) implements Setting {}
    public record Slider(String label, double min, double max, double step, int decimals, String unit,
                         DoubleSupplier get, DoubleConsumer set) implements Setting {}
    public record ColorPick(String label, IntSupplier get, IntConsumer set) implements Setting {}
    public record Choice(String label, String[] options, IntSupplier get, IntConsumer set) implements Setting {}
    public record Text(String label, Supplier<String> get) implements Setting {}

    public static final class Feature {
        public final String id;
        public final Category cat;
        public final boolean keyable;
        public final BooleanSupplier get;
        public final Consumer<Boolean> set;
        public final List<Setting> settings = new ArrayList<>();
        public Consumer<Minecraft> ticker;
        public boolean expanded;
        boolean lastDown;

        Feature(String id, Category cat, boolean keyable, BooleanSupplier get, Consumer<Boolean> set) {
            this.id = id; this.cat = cat; this.keyable = keyable; this.get = get; this.set = set;
        }

        Feature tick(Consumer<Minecraft> t) { this.ticker = t; return this; }
    }

    public static final List<Feature> ALL = new ArrayList<>();

    private static GraftersConfig c() { return GraftersConfig.get(); }
    private static Minecraft mc() { return Minecraft.getInstance(); }

    private static Feature add(String id, Category cat, boolean keyable, BooleanSupplier g, Consumer<Boolean> s) {
        Feature f = new Feature(id, cat, keyable, g, s);
        ALL.add(f);
        return f;
    }

    /** A button-style module: switching it on runs the action once and it stays off. */
    private static Feature action(String id, Category cat, Runnable r) {
        return add(id, cat, true, () -> false, v -> { if (v) r.run(); });
    }

    private static Slider intSlider(String label, double min, double max, double step, String unit,
                                    IntSupplier g, IntConsumer s) {
        return new Slider(label, min, max, step, 0, unit, g::getAsInt, v -> s.accept((int) Math.round(v)));
    }

    private static Slider floatSlider(String label, double min, double max, double step, int dec, String unit,
                                      java.util.function.Supplier<Float> g, Consumer<Float> s) {
        return new Slider(label, min, max, step, dec, unit, () -> g.get(), v -> s.accept((float) v));
    }

    static {
        // ======================= RENDER =======================
        Feature pesp = add("Player ESP", Category.RENDER, true, () -> c().playerEsp, v -> c().playerEsp = v);
        pesp.settings.add(new Header("ESP"));
        pesp.settings.add(intSlider("Range", 16, 256, 8, " blocks", () -> c().espRange, v -> c().espRange = v));
        pesp.settings.add(new Toggle("Eye line", () -> c().espEyeLine, v -> c().espEyeLine = v));
        pesp.settings.add(new Toggle("Tracers", () -> c().playerTracers, v -> c().playerTracers = v));
        pesp.settings.add(new ColorPick("Color", () -> c().playerColor, v -> c().playerColor = v));

        Feature cesp = add("Chest ESP", Category.RENDER, true, () -> c().chestEsp, v -> c().chestEsp = v);
        cesp.settings.add(new Header("ESP"));
        cesp.settings.add(intSlider("Range", 16, 256, 8, " blocks", () -> c().chestRange, v -> c().chestRange = v));
        cesp.settings.add(new Toggle("Hoppers", () -> c().chestHoppers, v -> c().chestHoppers = v));
        cesp.settings.add(new Toggle("Tracers", () -> c().chestTracers, v -> c().chestTracers = v));

        Feature spesp = add("Spawner ESP", Category.RENDER, true, () -> c().spawnerEsp, v -> c().spawnerEsp = v);
        spesp.settings.add(new Header("ESP"));
        spesp.settings.add(intSlider("Range", 16, 256, 8, " blocks", () -> c().spawnerRange, v -> c().spawnerRange = v));
        spesp.settings.add(new Toggle("Tracers", () -> c().spawnerTracers, v -> c().spawnerTracers = v));
        spesp.settings.add(new Header("TYPES"));
        spesp.settings.add(new Toggle("Mob spawners", () -> c().spawnerMob, v -> c().spawnerMob = v));
        spesp.settings.add(new Toggle("Trial spawners", () -> c().spawnerTrial, v -> c().spawnerTrial = v));

        Feature besp = add("Block ESP", Category.RENDER, true, () -> c().blockEsp, v -> {
            c().blockEsp = v;
            if (v) BlockEsp.clear();
        });
        besp.settings.add(new Header("ESP"));
        besp.settings.add(intSlider("Range", 16, 256, 16, " blocks", () -> c().blockRange, v -> c().blockRange = v));
        besp.settings.add(intSlider("Fill alpha", 0, 255, 5, "", () -> c().blockAlpha, v -> c().blockAlpha = v));
        besp.settings.add(new Header("TARGETS"));
        besp.settings.add(new Toggle("Respawn anchors", () -> c().tgtAnchor, v -> c().tgtAnchor = v));
        besp.settings.add(new Toggle("Ancient debris", () -> c().tgtDebris, v -> c().tgtDebris = v));
        besp.settings.add(new Toggle("Piglin heads", () -> c().tgtPiglin, v -> c().tgtPiglin = v));
        besp.settings.add(new Toggle("Netherite blocks", () -> c().tgtNetherite, v -> c().tgtNetherite = v));
        besp.settings.add(new Toggle("Diamond blocks", () -> c().tgtDiamondBlock, v -> c().tgtDiamondBlock = v));
        besp.settings.add(new Toggle("Diamond ore", () -> c().tgtDiamondOre, v -> c().tgtDiamondOre = v));
        besp.settings.add(new Toggle("Beacons", () -> c().tgtBeacon, v -> c().tgtBeacon = v));
        besp.settings.add(new Toggle("Redstone blocks", () -> c().tgtRedstone, v -> c().tgtRedstone = v));

        Feature fc = add("Freecam", Category.RENDER, true, () -> c().freecam, v -> {
            c().freecam = v;
            Freecam.onToggle(mc(), v);
        });
        fc.settings.add(new Header("MOVEMENT"));
        fc.settings.add(floatSlider("Speed", 0.1, 3.0, 0.1, 1, "x", () -> c().freecamSpeed, v -> c().freecamSpeed = v));
        fc.settings.add(intSlider("Max distance", 16, 256, 16, " blocks", () -> c().freecamMaxDist, v -> c().freecamMaxDist = v));
        fc.settings.add(new Header("VIEW"));
        fc.settings.add(new Toggle("See through terrain", () -> c().freecamNoCull, v -> c().freecamNoCull = v));

        Feature sus = add("SUS Chunks", Category.RENDER, true, () -> c().susChunks, v -> {
            c().susChunks = v;
            if (v) SusChunks.clear();
        });
        sus.settings.add(new Header("DETECTION"));
        sus.settings.add(intSlider("Scan radius", 4, 16, 1, " chunks", () -> c().susRadius, v -> c().susRadius = v));
        sus.settings.add(intSlider("Fill alpha", 0, 255, 5, "", () -> c().susAlpha, v -> c().susAlpha = v));
        sus.settings.add(new Toggle("Player activity", () -> c().susPlayers, v -> c().susPlayers = v));
        sus.settings.add(new Toggle("Block updates", () -> c().susBlocks, v -> c().susBlocks = v));
        sus.settings.add(new Header("INDICATORS"));
        sus.settings.add(new Toggle("Kelp", () -> c().kelp, v -> c().kelp = v));
        sus.settings.add(new Toggle("Cave vines", () -> c().caveVines, v -> c().caveVines = v));
        sus.settings.add(new Toggle("Vines", () -> c().vines, v -> c().vines = v));
        sus.settings.add(new Toggle("Amethyst", () -> c().amethyst, v -> c().amethyst = v));
        sus.settings.add(new Toggle("Bamboo", () -> c().bamboo, v -> c().bamboo = v));
        sus.settings.add(new Toggle("Cocoa", () -> c().cocoa, v -> c().cocoa = v));
        sus.settings.add(new Toggle("Bee nest", () -> c().beeNest, v -> c().beeNest = v));

        Feature swing = add("SlowSwing", Category.RENDER, true, () -> c().slowSwing, v -> c().slowSwing = v)
                .tick(SlowSwing::tick);
        swing.settings.add(new Header("ANIMATION"));
        swing.settings.add(floatSlider("Slowness", 1.0, 4.0, 0.25, 2, "x", () -> c().slowSwingFactor, v -> c().slowSwingFactor = v));

        Feature ta = add("Tool Angle", Category.RENDER, true, () -> c().toolAngle, v -> c().toolAngle = v);
        ta.settings.add(new Header("TOOL"));
        ta.settings.add(new Choice("Tool", ToolAngle.TOOLS, () -> c().taTool, v -> c().taTool = v));
        ta.settings.add(new Header("ROTATION"));
        ta.settings.add(new Slider("Rotate X", -180, 180, 5, 0, " deg",
                () -> c().taX[c().taTool], v -> c().taX[c().taTool] = (float) v));
        ta.settings.add(new Slider("Rotate Y", -180, 180, 5, 0, " deg",
                () -> c().taY[c().taTool], v -> c().taY[c().taTool] = (float) v));
        ta.settings.add(new Slider("Rotate Z", -180, 180, 5, 0, " deg",
                () -> c().taZ[c().taTool], v -> c().taZ[c().taTool] = (float) v));

        Feature hud = add("HUD", Category.RENDER, true, () -> c().hud, v -> c().hud = v);
        hud.settings.add(new Header("ELEMENTS"));
        hud.settings.add(new Toggle("Watermark", () -> c().hudWatermark, v -> c().hudWatermark = v));
        hud.settings.add(new Toggle("Coordinates", () -> c().hudCoords, v -> c().hudCoords = v));
        hud.settings.add(new Toggle("FPS", () -> c().hudFps, v -> c().hudFps = v));
        hud.settings.add(new Toggle("Module list", () -> c().hudModules, v -> c().hudModules = v));

        // ======================= MISC =======================
        Feature eat = add("Auto Eat", Category.MISC, true, () -> c().autoEat, v -> {
            c().autoEat = v;
            if (!v) AutoEat.stop(mc());
        }).tick(AutoEat::tick);
        eat.settings.add(new Header("HUNGER"));
        eat.settings.add(intSlider("Eat below", 1, 19, 1, "", () -> c().eatHunger, v -> c().eatHunger = v));

        Feature fw = add("Auto Firework", Category.MISC, true, () -> c().autoFirework, v -> c().autoFirework = v)
                .tick(AutoFirework::tick);
        fw.settings.add(new Header("ELYTRA"));
        fw.settings.add(intSlider("Delay", 10, 100, 5, " ticks", () -> c().fireworkDelay, v -> c().fireworkDelay = v));
        fw.settings.add(floatSlider("Min speed", 0.2, 1.5, 0.1, 1, " b/t", () -> c().fireworkMinSpeed, v -> c().fireworkMinSpeed = v));

        Feature log = add("Auto Log", Category.MISC, true, () -> c().autoLog, v -> c().autoLog = v)
                .tick(AutoLog::tick);
        log.settings.add(new Header("DISCONNECT WHEN"));
        log.settings.add(intSlider("Health below", 1, 19, 1, " hp", () -> c().logHealth, v -> c().logHealth = v));
        log.settings.add(new Toggle("Player nearby", () -> c().logPlayers, v -> c().logPlayers = v));
        log.settings.add(intSlider("Player range", 8, 64, 4, " blocks", () -> c().logRange, v -> c().logRange = v));

        Feature loot = add("Auto Loot", Category.MISC, true, () -> c().autoLoot, v -> c().autoLoot = v)
                .tick(AutoLoot::tick);
        loot.settings.add(new Header("CONTAINERS"));
        loot.settings.add(intSlider("Delay", 0, 10, 1, " ticks", () -> c().lootDelay, v -> c().lootDelay = v));
        loot.settings.add(new Toggle("Auto close", () -> c().lootClose, v -> c().lootClose = v));

        add("Auto Mine", Category.MISC, true, () -> c().autoMine, v -> {
            c().autoMine = v;
            if (!v) AutoMine.release(mc());
        }).tick(AutoMine::tick);

        Feature tool = add("Auto Tool", Category.MISC, true, () -> c().autoTool, v -> {
            c().autoTool = v;
            if (!v) AutoTool.restore(mc());
        }).tick(AutoTool::tick);
        tool.settings.add(new Header("TOOLS"));
        tool.settings.add(new Toggle("Switch back", () -> c().toolSwitchBack, v -> c().toolSwitchBack = v));

        Feature tpa = add("Auto TPA", Category.MISC, true, () -> c().autoTpa, v -> c().autoTpa = v)
                .tick(AutoTpa::tick);
        tpa.settings.add(new Header("REQUESTS"));
        tpa.settings.add(intSlider("Delay", 0, 60, 5, " ticks", () -> c().tpaDelay, v -> c().tpaDelay = v));
        tpa.settings.add(new Toggle("Whitelist only", () -> c().tpaWhitelist, v -> c().tpaWhitelist = v));
        tpa.settings.add(new Header("NAMES  (.set tpa a,b)"));
        tpa.settings.add(new Text("List", () -> c().tpaNames));

        action("Cord Snapper", Category.MISC, CordSnapper::run);

        Feature glide = add("Elytra Glide", Category.MISC, true, () -> c().elytraGlide, v -> c().elytraGlide = v)
                .tick(ElytraGlide::tick);
        glide.settings.add(new Header("SPEED"));
        glide.settings.add(floatSlider("Dive below", 0.3, 1.5, 0.1, 1, " b/t", () -> c().glideMin, v -> c().glideMin = v));
        glide.settings.add(floatSlider("Climb above", 0.8, 2.5, 0.1, 1, " b/t", () -> c().glideMax, v -> c().glideMax = v));
        glide.settings.add(new Header("ANGLES"));
        glide.settings.add(floatSlider("Dive angle", 5, 60, 5, 0, " deg", () -> c().glideDive, v -> c().glideDive = v));
        glide.settings.add(floatSlider("Climb angle", 5, 60, 5, 0, " deg", () -> c().glideClimb, v -> c().glideClimb = v));

        Feature sb = add("Schematic Builder", Category.MISC, true, () -> c().schematicBuilder, v -> {
            c().schematicBuilder = v;
            SchematicBuilder.reset(mc());
        }).tick(SchematicBuilder::tick);
        sb.settings.add(new Header("BUILDING  (needs Litematica)"));
        sb.settings.add(intSlider("Scan radius", 3, 8, 1, " blocks", () -> c().sbScan, v -> c().sbScan = v));
        sb.settings.add(floatSlider("Reach", 2.0, 4.5, 0.5, 1, " blocks", () -> c().sbReach, v -> c().sbReach = v));
        sb.settings.add(intSlider("Place delay", 1, 20, 1, " ticks", () -> c().sbDelay, v -> c().sbDelay = v));
        sb.settings.add(floatSlider("Turn speed", 5, 60, 5, 0, " deg", () -> c().sbRotSpeed, v -> c().sbRotSpeed = v));
        sb.settings.add(new Toggle("Walk to targets", () -> c().sbWalk, v -> c().sbWalk = v));
        sb.settings.add(new Toggle("Build bottom-up", () -> c().sbBottomUp, v -> c().sbBottomUp = v));
        sb.settings.add(new Toggle("Human jitter", () -> c().sbJitter, v -> c().sbJitter = v));
        sb.settings.add(new Header("DISCORD  (.set webhook url)"));
        sb.settings.add(new Text("Webhook", () -> c().webhookUrl.isBlank() ? "" : "(set)"));

        add("Inventory Move", Category.MISC, true, () -> c().invMove, v -> c().invMove = v);

        // ======================= DONUT =======================
        Feature trap = add("Anti Trap", Category.DONUT, true, () -> c().antiTrap, v -> c().antiTrap = v)
                .tick(AntiTrap::tick);
        trap.settings.add(new Header("ESCAPE"));
        trap.settings.add(floatSlider("Range", 1.0, 4.0, 0.5, 1, " blocks", () -> c().trapRange, v -> c().trapRange = v));
        trap.settings.add(intSlider("Delay", 1, 20, 1, " ticks", () -> c().trapDelay, v -> c().trapDelay = v));
        trap.settings.add(new Toggle("Armor stands", () -> c().trapStands, v -> c().trapStands = v));
        trap.settings.add(new Toggle("Chest minecarts", () -> c().trapMinecarts, v -> c().trapMinecarts = v));

        Feature ah = add("Auction Sniper", Category.DONUT, true, () -> c().auctionSniper, v -> {
            c().auctionSniper = v;
            AuctionSniper.reset();
        }).tick(AuctionSniper::tick);
        ah.settings.add(new Header("BUYING"));
        ah.settings.add(new Choice("Max price", AuctionSniper.PRICE_LABELS, () -> c().sniperPrice, v -> { c().sniperPrice = v; c().sniperCustom = 0; }));
        ah.settings.add(new Text("Buying up to", () -> "$" + Util.formatAmount(AuctionSniper.maxPrice(c()))));
        ah.settings.add(intSlider("Refresh", 10, 100, 5, " ticks", () -> c().sniperRefresh, v -> c().sniperRefresh = v));
        ah.settings.add(intSlider("Click delay", 1, 10, 1, " ticks", () -> c().sniperClick, v -> c().sniperClick = v));
        ah.settings.add(new Header("EXACT PRICE  (.set ahprice 7.5k)"));
        ah.settings.add(new Header("ITEM  (.set ah name)"));
        ah.settings.add(new Text("Item", () -> c().sniperItem));

        Feature ord = add("Order Filler", Category.DONUT, true, () -> c().orderFiller, v -> {
            c().orderFiller = v;
            OrderFiller.reset();
        }).tick(OrderFiller::tick);
        ord.settings.add(new Header("FILLING"));
        ord.settings.add(new Choice("Min price", OrderFiller.PRICE_LABELS, OrderFiller::priceIndex, OrderFiller::setPriceIndex));
        ord.settings.add(new Text("Paying at least", () -> "$" + Util.formatAmount(c().orderMinPrice) + " each"));
        ord.settings.add(intSlider("Refresh", 20, 200, 10, " ticks", () -> c().orderRefresh, v -> c().orderRefresh = v));
        ord.settings.add(intSlider("Click delay", 1, 10, 1, " ticks", () -> c().orderClick, v -> c().orderClick = v));
        ord.settings.add(intSlider("Pages", 1, 10, 1, "", () -> c().orderPages, v -> c().orderPages = v));
        ord.settings.add(new Header("EXACT PRICE  (.set orderprice 5k)"));
        ord.settings.add(new Header("ITEMS  (.set order a,b)"));
        ord.settings.add(new Text("Items", () -> c().orderItem));

        Feature sell = add("Auto Sell", Category.DONUT, true, () -> c().autoSell, v -> c().autoSell = v)
                .tick(AutoSell::tick);
        sell.settings.add(new Header("SELLING"));
        sell.settings.add(intSlider("Interval", 20, 600, 20, " ticks", () -> c().sellInterval, v -> c().sellInterval = v));
        sell.settings.add(new Header("ITEMS  (.set sell a,b)"));
        sell.settings.add(new Text("Items", () -> c().sellItems));

        Feature sps = add("Auto Spawner Sell", Category.DONUT, true, () -> c().autoSpawnerSell, v -> {
            c().autoSpawnerSell = v;
            AutoSpawnerSell.reset();
        }).tick(AutoSpawnerSell::tick);
        sps.settings.add(new Header("LOOP"));
        sps.settings.add(intSlider("Interval", 100, 2400, 100, " ticks", () -> c().spawnerInterval, v -> c().spawnerInterval = v));
        sps.settings.add(new Header("ITEMS  (.set spawner x)"));
        sps.settings.add(new Text("Items", () -> c().spawnerItems));

        Feature drop = add("Item Dropper", Category.DONUT, true, () -> c().itemDropper, v -> c().itemDropper = v)
                .tick(ItemDropper::tick);
        drop.settings.add(new Header("DROPPING"));
        drop.settings.add(intSlider("Delay", 1, 20, 1, " ticks", () -> c().dropDelay, v -> c().dropDelay = v));
        drop.settings.add(new Toggle("Include hotbar", () -> c().dropHotbar, v -> c().dropHotbar = v));
        drop.settings.add(new Header("ITEMS  (.set drop x)"));
        drop.settings.add(new Text("Items", () -> c().dropItems));

        Feature rtp = add("RTP Base Finder", Category.DONUT, true, () -> c().rtpBaseFinder, v -> {
            c().rtpBaseFinder = v;
            RtpBaseFinder.reset();
        }).tick(RtpBaseFinder::tick);
        rtp.settings.add(new Header("DETECTION"));
        rtp.settings.add(intSlider("RTP delay", 100, 1200, 100, " ticks", () -> c().rtpDelay, v -> c().rtpDelay = v));
        rtp.settings.add(intSlider("Min storage", 1, 30, 1, " blocks", () -> c().rtpMinStorage, v -> c().rtpMinStorage = v));
        rtp.settings.add(new Toggle("Spawners count", () -> c().rtpSpawners, v -> c().rtpSpawners = v));
        rtp.settings.add(new Header("COMMAND  (.set rtp x)"));
        rtp.settings.add(new Text("Command", () -> c().rtpCommand));

        // ======================= APPEARANCE =======================
        add("UI Sounds", Category.APPEARANCE, false, () -> c().uiSounds, v -> c().uiSounds = v);
        add("Panel Glow", Category.APPEARANCE, false, () -> c().panelGlow, v -> c().panelGlow = v);

        // The switch is a quick "transparent / solid" toggle; the sliders fine-tune it. Open by default.
        Feature trans = add("Transparency", Category.APPEARANCE, false, () -> c().panelOpacity < 100,
                v -> c().panelOpacity = v ? 70 : 100);
        trans.settings.add(new Header("MENU"));
        trans.settings.add(intSlider("Panel opacity", 20, 100, 5, "%", () -> c().panelOpacity, v -> c().panelOpacity = v));
        trans.settings.add(intSlider("Background dim", 0, 100, 5, "%", () -> c().menuDim, v -> c().menuDim = v));
        trans.expanded = true;
    }

    public static int activeCount() {
        int n = 0;
        for (Feature f : ALL) if (f.cat != Category.APPEARANCE && f.get.getAsBoolean()) n++;
        return n;
    }

    public static int totalCount() {
        int n = 0;
        for (Feature f : ALL) if (f.cat != Category.APPEARANCE) n++;
        return n;
    }

    /** Keybinds and module updates. Runs every client tick. */
    public static void tick(Minecraft mc) {
        GraftersConfig cfg = GraftersConfig.get();
        boolean canToggle = mc.player != null && GraftersState.screen(mc) == null;

        for (Feature f : ALL) {
            Integer k = cfg.binds.get(f.id);
            if (k == null || !f.keyable) { f.lastDown = false; }
            else {
                boolean down = GLFW.glfwGetKey(mc.getWindow().handle(), k) == GLFW.GLFW_PRESS;
                if (down && !f.lastDown && canToggle) f.set.accept(!f.get.getAsBoolean());
                f.lastDown = down;
            }

            if (f.ticker != null && f.get.getAsBoolean() && mc.player != null && mc.level != null) {
                try {
                    f.ticker.accept(mc);
                } catch (RuntimeException e) {          // never crash the game because of a module
                    e.printStackTrace();
                    f.set.accept(false);
                    Util.chat(f.id + " crashed and was turned off: " + e.getClass().getSimpleName());
                }
            }
        }
    }
}
