package dev.swingingsigns.mixin;

import dev.swingingsigns.client.Swing;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void swingingsigns$hide(CallbackInfoReturnable<RenderShape> cir) {
        if ((Swing.hideCeiling() || Swing.hideWall()) && Swing.hidden((BlockState) (Object) this)) {
            cir.setReturnValue(RenderShape.INVISIBLE);
        }
    }
}
