package dev.swingingsigns.client;

import dev.swingingsigns.config.SwingingSignsConfig;
import dev.swingingsigns.core.SignSpring;
import dev.swingingsigns.core.SwayMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class Swing {
    private static final int FORGET_TICKS = 40;
    private static final int SHELTER_REFRESH = 100;
    private static final double MIN_SPEED = 0.003;
    private static final float COUPLING = 0.35f;

    private static final Map<Long, SignState> SIGNS = new HashMap<>();
    private static ClientLevel lastLevel;
    private static long time;
    private static float rain;
    private static float thunder;
    private static volatile boolean hideCeiling;
    private static volatile boolean hideWall;

    private Swing() {
    }

    public static void clear() {
        SIGNS.clear();
        lastLevel = null;
    }

    public static boolean hideCeiling() {
        return hideCeiling;
    }

    public static boolean hideWall() {
        return hideWall;
    }

    public static boolean hidden(BlockState state) {
        if (state == null) {
            return false;
        }
        if (hideCeiling && state.getBlock() instanceof CeilingHangingSignBlock) {
            return true;
        }
        return hideWall && state.getBlock() instanceof WallHangingSignBlock;
    }

    public static boolean updateHiding(boolean supported) {
        boolean active = supported && SwingingSignsClient.active();
        boolean ceiling = active;
        boolean wall = active && SwingingSignsClient.config().wallSigns();
        boolean changed = ceiling != hideCeiling || wall != hideWall;
        hideCeiling = ceiling;
        hideWall = wall;
        return changed;
    }

    public static boolean swings(BlockState state) {
        if (state == null || !SwingingSignsClient.active()) {
            return false;
        }
        if (state.getBlock() instanceof CeilingHangingSignBlock) {
            return true;
        }
        return state.getBlock() instanceof WallHangingSignBlock && SwingingSignsClient.config().wallSigns();
    }

    public static boolean wall(BlockState state) {
        return state.getBlock() instanceof WallHangingSignBlock;
    }

    public static float yawDegrees(BlockState state) {
        if (state.getBlock() instanceof CeilingHangingSignBlock) {
            return RotationSegment.convertToDegrees(state.getValue(CeilingHangingSignBlock.ROTATION));
        }
        if (state.getBlock() instanceof WallHangingSignBlock) {
            return state.getValue(WallHangingSignBlock.FACING).toYRot();
        }
        return 0f;
    }

    public static float pivotY(BlockState state) {
        return wall(state) ? 0.9375f : 1.0f;
    }

    public static float angle(BlockPos pos, BlockState state, float partialTick) {
        if (pos == null || !swings(state)) {
            return 0f;
        }
        long key = pos.asLong();
        SignState sign = SIGNS.get(key);
        float yaw = (float) Math.toRadians(yawDegrees(state));
        if (sign == null) {
            sign = new SignState(key, yaw);
            SIGNS.put(key, sign);
        }
        sign.yaw = yaw;
        sign.lastSeen = time;
        SwingingSignsConfig config = SwingingSignsClient.config();
        boolean weather = config.weather();
        float sway = SwayMath.sway(sign.seed, time + partialTick, yaw,
                weather ? rain : 0f, weather ? thunder : 0f, sign.sheltered, config.windStrength());
        float result = sway + sign.spring.angle(partialTick);
        return Float.isFinite(result) ? result : 0f;
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || !SwingingSignsClient.active()) {
            if (!SIGNS.isEmpty() || lastLevel != null) {
                clear();
            }
            return;
        }
        if (level != lastLevel) {
            SIGNS.clear();
            lastLevel = level;
        }
        time++;
        rain = level.getRainLevel(1f);
        thunder = level.getThunderLevel(1f);
        if (SIGNS.isEmpty()) {
            return;
        }
        Iterator<SignState> it = SIGNS.values().iterator();
        while (it.hasNext()) {
            SignState sign = it.next();
            if (time - sign.lastSeen > FORGET_TICKS) {
                it.remove();
                continue;
            }
            if (time >= sign.shelterCheck) {
                sign.shelterCheck = time + SHELTER_REFRESH;
                sign.sheltered = !level.canSeeSky(BlockPos.of(sign.key).above(2));
            }
            sign.spring.step();
        }
        float push = SwingingSignsClient.config().pushStrength();
        if (push <= 0f) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == null || entity.isSpectator() || entity instanceof Display) {
                continue;
            }
            double dx = entity.getX() - entity.xo;
            double dz = entity.getZ() - entity.zo;
            if (dx * dx + dz * dz < MIN_SPEED * MIN_SPEED) {
                continue;
            }
            AABB box = entity.getBoundingBox();
            int minX = (int) Math.floor(box.minX - 0.15);
            int maxX = (int) Math.floor(box.maxX + 0.15);
            int minY = (int) Math.floor(box.minY);
            int maxY = (int) Math.floor(box.maxY + 0.2);
            int minZ = (int) Math.floor(box.minZ - 0.15);
            int maxZ = (int) Math.floor(box.maxZ + 0.15);
            if ((long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > 64) {
                continue;
            }
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        SignState sign = SIGNS.get(BlockPos.asLong(x, y, z));
                        if (sign == null) {
                            continue;
                        }
                        AABB zone = new AABB(x - 0.15, y - 0.2, z - 0.15, x + 1.15, y + 1.0, z + 1.15);
                        if (zone.intersects(box)) {
                            sign.spring.drag(SwayMath.pushTarget(dx, dz, sign.yaw, push), COUPLING);
                        }
                    }
                }
            }
        }
    }

    public static int tracked() {
        return SIGNS.size();
    }

    public static SignSpring spring(BlockPos pos) {
        SignState sign = SIGNS.get(pos.asLong());
        return sign == null ? null : sign.spring;
    }

    private static final class SignState {
        final long key;
        final int seed;
        final SignSpring spring = new SignSpring();
        float yaw;
        long lastSeen;
        long shelterCheck;
        boolean sheltered;

        SignState(long key, float yaw) {
            this.key = key;
            this.seed = SwayMath.seed(key);
            this.yaw = yaw;
        }
    }
}
