package com.grafters.mixin;

import com.grafters.BlockEsp;
import com.grafters.SusChunks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void grafters$block(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        SusChunks.markBlockActivity(packet.getPos());
        BlockEsp.onBlockChange(packet.getPos());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void grafters$delta(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        packet.runUpdates((pos, state) -> { SusChunks.markBlockActivity(pos); BlockEsp.onBlockChange(pos); });
    }
}
