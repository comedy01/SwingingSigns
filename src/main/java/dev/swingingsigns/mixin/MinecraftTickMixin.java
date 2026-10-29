package dev.swingingsigns.mixin;

import dev.swingingsigns.client.SwingingSignsClient;
import dev.swingingsigns.client.Swing;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void swingingsigns$tick(CallbackInfo ci) {
        try {
            Swing.tick((Minecraft) (Object) this);
        } catch (RuntimeException e) {
            SwingingSignsClient.fail(e);
        }
    }
}
