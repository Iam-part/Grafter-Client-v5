package com.grafters.mixin;

import com.grafters.Freecam;
import com.grafters.GraftersState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Final say on the player's movement input:
 *  - Freecam: the real player gets no movement (but keeps crouching if it was)
 *  - InvMove: movement comes from the real keyboard while a screen is open
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void grafters$override(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (Object) mc.player.input != (Object) this) return;

        Input in = this.keyPresses;
        boolean f = in.forward(), b = in.backward(), l = in.left(), r = in.right();
        boolean j = in.jump(), sh = in.shift(), sp = in.sprint();
        boolean changed = false;

        if (Freecam.isActive()) {
            f = false; b = false; l = false; r = false; j = false; sp = false;
            sh = Freecam.sneakLatched();
            changed = true;
        } else if (GraftersState.invMoveActive(mc)) {
            Options o = mc.options;
            f = GraftersState.physical(mc, o.keyUp);
            b = GraftersState.physical(mc, o.keyDown);
            l = GraftersState.physical(mc, o.keyLeft);
            r = GraftersState.physical(mc, o.keyRight);
            j = GraftersState.physical(mc, o.keyJump);
            sp = GraftersState.physical(mc, o.keySprint);
            sh = GraftersState.physical(mc, o.keyShift);
            changed = true;
        }

        if (changed) {
            this.keyPresses = new Input(f, b, l, r, j, sh, sp);
            float fwd = (f ? 1f : 0f) - (b ? 1f : 0f);
            float side = (l ? 1f : 0f) - (r ? 1f : 0f);
            this.moveVector = new Vec2(side, fwd).normalized();
        }
    }
}
