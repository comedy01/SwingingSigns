package dev.swingingsigns.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

public final class SignPose {
    private static boolean pushed;

    private SignPose() {
    }

    public static void begin(PoseStack pose, BlockPos pos, BlockState state, float partialTick) {
        begin(pose, pos, state, partialTick, false);
    }

    public static void begin(PoseStack pose, BlockPos pos, BlockState state, float partialTick, boolean always) {
        pushed = false;
        if (!SwingingSignsClient.active() || pos == null || state == null) {
            return;
        }
        try {
            float a = Swing.swings(state) ? Swing.angle(pos, state, partialTick) : 0.0F;
            if (a == 0.0F && !always) {
                return;
            }
            double yaw = Math.toRadians(Swing.yawDegrees(state));
            pose.pushPose();
            pushed = true;
            if (a != 0.0F) {
                pose.rotateAround(new Quaternionf().rotationAxis(-a, (float) Math.cos(yaw), 0.0F, (float) Math.sin(yaw)), 0.5F, Swing.pivotY(state), 0.5F);
            }
        } catch (RuntimeException e) {
            SwingingSignsClient.fail(e);
        }
    }

    public static boolean pushed() {
        return pushed;
    }

    public static void end(PoseStack pose) {
        if (pushed) {
            pushed = false;
            pose.popPose();
        }
    }
}
