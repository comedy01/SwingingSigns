package dev.swingingsigns.mixin;

import dev.swingingsigns.client.BakedBoard;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class BakedTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void swingingsigns$rebuild(CallbackInfo ci) {
        BakedBoard.tick((Minecraft) (Object) this);
    }
}
