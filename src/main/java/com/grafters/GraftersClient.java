package com.grafters;

import com.grafters.gui.GraftersMenuScreen;
import com.grafters.mods.AutoTpa;
import com.grafters.mods.Commands;
import com.grafters.mods.Hud;
import com.grafters.mods.SchematicBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class GraftersClient implements ClientModInitializer {
    public static final String MOD_ID = "grafters";
    public static KeyMapping MENU_KEY;

    @Override
    public void onInitializeClient() {
        GraftersConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        MENU_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.grafters.menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category));

        // Runs before the player's input is processed: InvMove + Freecam movement
        ClientTickEvents.START_CLIENT_TICK.register(GraftersState::startTick);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (MENU_KEY.consumeClick()) {
                if (GraftersState.screen(mc) == null) mc.gui.setScreen(new GraftersMenuScreen());
            }
            SusChunks.tick(mc);
            Modules.tick(mc);
            BlockEsp.tick(mc);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            SchematicBuilder.reportLast();
            SusChunks.clear();
            BlockEsp.clear();
            Freecam.reset();
            GraftersState.reset();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> SchematicBuilder.reportLast());

        GraftersRenderer.register();
        Hud.register();
        Commands.register();
        AutoTpa.register();
    }
}
