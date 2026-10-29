package dev.swingingsigns.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class BakedBoard {
    private static final RandomSource RANDOM = RandomSource.create(42L);
    private static final List<BlockStateModelPart> PARTS = new ArrayList<>();

    private BakedBoard() {
    }

    public static void tick(Minecraft mc) {
        try {
            if (Swing.updateHiding(true) && mc.levelExtractor != null) {
                mc.levelExtractor.allChanged();
            }
        } catch (RuntimeException e) {
            SwingingSignsClient.fail(e);
        }
    }

    public static void draw(PoseStack pose, SubmitNodeCollector collector, BlockState state, int light) {
        try {
            PARTS.clear();
            RANDOM.setSeed(42L);
            Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state).collectParts(RANDOM, PARTS);
            if (!PARTS.isEmpty()) {
                collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), new ArrayList<>(PARTS), BlockModelRenderState.EMPTY_TINTS, light, OverlayTexture.NO_OVERLAY, 0);
            }
        } catch (RuntimeException e) {
            SwingingSignsClient.fail(e);
        }
    }
}
