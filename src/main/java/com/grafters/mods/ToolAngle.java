package com.grafters.mods;

import com.grafters.GraftersConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/** Rotates the item in your first-person hand, separately for each kind of tool. */
public final class ToolAngle {
    private ToolAngle() {}

    public static final String[] TOOLS = {"Sword", "Pickaxe", "Axe", "Shovel", "Hoe", "Mace", "Trident", "Spear", "Other"};

    public static int classify(ItemStack s) {
        String id = Util.itemId(s);
        if (id.endsWith("_sword")) return 0;
        if (id.endsWith("_pickaxe")) return 1;
        if (id.endsWith("_axe")) return 2;
        if (id.endsWith("_shovel")) return 3;
        if (id.endsWith("_hoe")) return 4;
        if (id.equals("mace")) return 5;
        if (id.equals("trident")) return 6;
        if (id.endsWith("_spear")) return 7;
        return 8;
    }

    /** Called before the first-person item is drawn, with the base pose stack. */
    public static void apply(PoseStack ps, AbstractClientPlayer player, InteractionHand hand, ItemStack stack) {
        GraftersConfig c = GraftersConfig.get();
        if (!c.toolAngle || stack.isEmpty()) return;
        int i = classify(stack);
        if (i >= c.taX.length || i >= c.taY.length || i >= c.taZ.length) return;
        float ax = c.taX[i], ay = c.taY[i], az = c.taZ[i];
        if (ax == 0 && ay == 0 && az == 0) return;

        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;

        // turn around where the hand holds the item, not around the camera
        ps.translate(side * 0.56, -0.52, -0.72);
        ps.mulPose(Axis.XP.rotationDegrees(ax));
        ps.mulPose(Axis.YP.rotationDegrees(ay * side));
        ps.mulPose(Axis.ZP.rotationDegrees(az * side));
        ps.translate(-side * 0.56, 0.52, 0.72);
    }
}
