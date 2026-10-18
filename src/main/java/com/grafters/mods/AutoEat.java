package com.grafters.mods;

import com.grafters.Freecam;
import com.grafters.GraftersConfig;
import com.grafters.GraftersState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/** Eats the best hotbar food when hunger drops below the threshold. */
public final class AutoEat {
    private AutoEat() {}

    private static boolean eating;
    private static int prevSlot = -1;
    private static int ticks;

    private static final Set<Item> NEVER = Set.of(
            Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH,
            Items.CHICKEN, Items.SUSPICIOUS_STEW, Items.CHORUS_FRUIT,
            Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);

    public static void stop(Minecraft mc) {
        if (!eating) return;
        eating = false;
        mc.options.keyUse.setDown(false);
        if (prevSlot >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(prevSlot);
        prevSlot = -1;
    }

    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        LocalPlayer p = mc.player;
        if (p == null || GraftersState.screen(mc) != null || Freecam.isActive()) { stop(mc); return; }

        int food = p.getFoodData().getFoodLevel();
        if (eating) {
            ticks++;
            mc.options.keyUse.setDown(true);
            if (food >= 20 || ticks > 100 || (ticks > 6 && !p.isUsingItem())) stop(mc);
            return;
        }
        if (food > c.eatHunger || p.isUsingItem()) return;

        int best = -1, bestNutrition = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            FoodProperties fp = s.get(DataComponents.FOOD);
            if (fp == null || NEVER.contains(s.getItem())) continue;
            if (fp.nutrition() > bestNutrition) { bestNutrition = fp.nutrition(); best = i; }
        }
        if (best < 0) return;

        prevSlot = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(best);
        mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);   // starts eating even when looking at a block
        mc.options.keyUse.setDown(true);                      // keeps eating
        eating = true;
        ticks = 0;
    }
}
