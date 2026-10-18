package com.grafters.mods;

import com.grafters.Freecam;
import com.grafters.GraftersConfig;
import com.grafters.GraftersState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;

/** Breaks armor stands and chest minecarts that are right next to you (the usual trap pieces). */
public final class AntiTrap {
    private AntiTrap() {}

    private static int cooldown;

    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || GraftersState.screen(mc) != null || Freecam.isActive()) return;
        if (cooldown > 0) { cooldown--; return; }

        List<Entity> near = mc.level.getEntities(p, p.getBoundingBox().inflate(c.trapRange), e -> true);
        for (Entity e : near) {
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            boolean stand = c.trapStands && type.equals("armor_stand");
            boolean cart = c.trapMinecarts && type.equals("chest_minecart");
            if (!stand && !cart) continue;
            if (p.distanceTo(e) > 4.5f) continue;
            mc.gameMode.attack(p, e);
            p.swing(InteractionHand.MAIN_HAND);
            cooldown = c.trapDelay;
            return;
        }
    }
}
