package com.grafters.mixin;

import com.grafters.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /** Mouse rotates the free camera, not the real player. */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void grafters$freecamLook(double yRot, double xRot, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && Freecam.isActive()) {
            Freecam.look(yRot, xRot);
            ci.cancel();
        }
    }
}
