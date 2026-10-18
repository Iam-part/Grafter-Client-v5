package com.grafters.mixin;

import com.grafters.mods.ToolAngle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tool Angle hook. 26.2 renamed renderArmWithItem to submitArmWithItem. This mixin lives in its own
 * non-required config (grafters.visual.mixins.json), so if the target ever changes the game still starts.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = {"submitArmWithItem", "renderArmWithItem"}, at = @At("HEAD"))
    private void grafters$toolAngle(AbstractClientPlayer player, float partialTick, float pitch,
                                    InteractionHand hand, float swingProgress, ItemStack stack,
                                    float equipProgress, PoseStack poseStack,
                                    SubmitNodeCollector collector, int light, CallbackInfo ci) {
        ToolAngle.apply(poseStack, player, hand, stack);
    }
}
