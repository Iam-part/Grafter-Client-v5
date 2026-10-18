package com.grafters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GraftersConfig {
    private static GraftersConfig INSTANCE = new GraftersConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Modules
    public boolean invMove = false;
    public boolean playerEsp = false;
    public boolean chestEsp = false;
    public boolean susChunks = false;
    public transient boolean freecam = false;   // never persisted

    // Module settings
    public float freecamSpeed = 0.6f;
    public boolean freecamNoCull = true;     // see through terrain / caves
    public int freecamMaxDist = 160;         // how far the camera may drift from your body
    public int espRange = 128;
    public int chestRange = 64;
    public boolean espEyeLine = true;
    public int playerColor = 0x00FF00;
    public int susRadius = 10;
    public int susAlpha = 70;
    public boolean susPlayers = true;
    public boolean susBlocks = false;

    // Chest ESP extras
    public boolean chestHoppers = true;

    // Tracers (line from your crosshair to the target)
    public boolean playerTracers = false, chestTracers = false, spawnerTracers = false;

    // Spawner ESP
    public boolean spawnerEsp = false;
    public int spawnerRange = 128;
    public boolean spawnerMob = true, spawnerTrial = true;

    // Block ESP
    public boolean blockEsp = false;
    public int blockRange = 96;
    public int blockAlpha = 50;
    public boolean tgtAnchor = true, tgtDebris = true, tgtPiglin = true, tgtNetherite = true,
                   tgtDiamondBlock = true, tgtDiamondOre = true, tgtBeacon = true, tgtRedstone = true;

    // SUS indicators
    public boolean kelp = true, caveVines = true, vines = true, amethyst = true,
                   bamboo = true, cocoa = true, beeNest = true;

    // ---- Misc modules ----
    public boolean autoEat = false;      public int eatHunger = 14;
    public boolean autoFirework = false; public int fireworkDelay = 40; public float fireworkMinSpeed = 0.6f;
    public boolean autoLog = false;      public int logHealth = 6; public boolean logPlayers = false; public int logRange = 32;
    public boolean autoLoot = false;     public int lootDelay = 2; public boolean lootClose = true;
    public boolean autoMine = false;
    public boolean autoTool = false;     public boolean toolSwitchBack = true;
    public boolean autoTpa = false;      public int tpaDelay = 10; public boolean tpaWhitelist = true; public String tpaNames = "";
    public boolean elytraGlide = false;  public float glideMin = 0.8f, glideMax = 1.6f, glideDive = 25f, glideClimb = 30f;

    public boolean schematicBuilder = false;
    public int sbScan = 6; public float sbReach = 4.0f; public int sbDelay = 4; public float sbRotSpeed = 25f;
    public boolean sbWalk = true, sbBottomUp = true, sbJitter = true;
    public String webhookUrl = "";

    // ---- Donut modules ----
    public boolean antiTrap = false;     public float trapRange = 2.5f; public int trapDelay = 4;
    public boolean trapStands = true, trapMinecarts = true;
    public boolean auctionSniper = false; public int sniperPrice = 5; public int sniperRefresh = 40; public int sniperClick = 3;
    public String sniperItem = ""; public double sniperCustom = 0;
    public boolean orderFiller = false; public String orderItem = ""; public double orderMinPrice = 100;
    public int orderRefresh = 60, orderClick = 3, orderPages = 3;
    public boolean autoSell = false;     public int sellInterval = 100; public String sellItems = "sea_pickle";
    public boolean autoSpawnerSell = false; public int spawnerInterval = 600; public String spawnerItems = "bone";
    public boolean itemDropper = false;  public int dropDelay = 2; public boolean dropHotbar = false; public String dropItems = "bone";
    public boolean rtpBaseFinder = false; public int rtpDelay = 300; public int rtpMinStorage = 10; public boolean rtpSpawners = true;
    public String rtpCommand = "rtp";

    // ---- Visual: SlowSwing + Tool Angle ----
    public boolean slowSwing = false; public float slowSwingFactor = 2.0f;
    public boolean toolAngle = false; public int taTool = 0;
    public float[] taX = new float[9], taY = new float[9], taZ = new float[9];

    // ---- HUD ----
    public boolean hud = true, hudWatermark = true, hudCoords = true, hudFps = true, hudModules = true;

    // UI
    public boolean uiSounds = true;
    public boolean panelGlow = true;
    public int panelOpacity = 100;   // percent
    public int menuDim = 69;         // percent
    public Set<String> favorites = new HashSet<>();
    public Map<String, Integer> binds = new HashMap<>();

    public static GraftersConfig get() { return INSTANCE; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("grafters.json");
    }

    public static void load() {
        try {
            if (Files.exists(path())) {
                GraftersConfig c = GSON.fromJson(Files.newBufferedReader(path()), GraftersConfig.class);
                if (c != null) {
                    if (c.favorites == null) c.favorites = new HashSet<>();
                    if (c.binds == null) c.binds = new HashMap<>();
                    INSTANCE = c;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void save() {
        try { Files.writeString(path(), GSON.toJson(this)); }
        catch (IOException e) { e.printStackTrace(); }
    }

    public boolean isOn(BlockEsp.Target t) {
        return switch (t) {
            case RESPAWN_ANCHOR -> tgtAnchor;
            case ANCIENT_DEBRIS -> tgtDebris;
            case PIGLIN_HEAD -> tgtPiglin;
            case NETHERITE_BLOCK -> tgtNetherite;
            case DIAMOND_BLOCK -> tgtDiamondBlock;
            case DIAMOND_ORE -> tgtDiamondOre;
            case BEACON -> tgtBeacon;
            case REDSTONE_BLOCK -> tgtRedstone;
        };
    }

    public boolean isOn(SusChunks.Indicator i) {
        return switch (i) {
            case KELP -> kelp;
            case CAVE_VINES -> caveVines;
            case VINES -> vines;
            case AMETHYST -> amethyst;
            case BAMBOO -> bamboo;
            case COCOA -> cocoa;
            case BEE_NEST -> beeNest;
        };
    }
}
