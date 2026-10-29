package dev.swingingsigns.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.swingingsigns.client.BakedBoard;
import dev.swingingsigns.client.SignPose;
import dev.swingingsigns.client.Swing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignRenderer.class)
public abstract class AbstractSignRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/SignRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void swingingsigns$begin(SignRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        BlockState block = mc.level == null ? null : mc.level.getBlockState(state.blockPos);
        boolean hidden = Swing.hidden(block);
        SignPose.begin(poseStack, state.blockPos, block, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), hidden);
        if (hidden && SignPose.pushed()) {
            BakedBoard.draw(poseStack, collector, block, state.lightCoords);
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/SignRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("RETURN"))
    private void swingingsigns$end(SignRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        SignPose.end(poseStack);
    }
}
