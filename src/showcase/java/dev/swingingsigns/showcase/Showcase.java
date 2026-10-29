package dev.swingingsigns.showcase;

import com.mojang.datafixers.util.Pair;
import dev.swingingsigns.client.SwingingSignsClient;
import dev.swingingsigns.config.SwingingSignsConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

public final class Showcase {
    private static final String WORLD = "swingingsigns-showcase";
    private static final long SETTLE_NANOS = 3_000_000_000L;
    private static final int EDGE = 30;
    private static final double EYE = 1.62;
    private static final String TEXT = "{front_text:{messages:[\"\",\"General\",\"Store\",\"\"]},"
            + "back_text:{messages:[\"\",\"General\",\"Store\",\"\"]},is_waxed:1b}";
    private static Showcase instance;

    private final Path out;
    private final Recorder recorder;
    private final long seed;
    private final boolean preview;
    private final List<Action> actions = new ArrayList<>();
    private final AtomicReference<BlockPos> site = new AtomicReference<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int frame;
    private long actionStart;
    private Path pendingStill;

    private interface Action {
        boolean run(int frame);
    }

    private Showcase(Path out, String ffmpeg, long seed, boolean preview) {
        this.out = out;
        this.recorder = new Recorder(ffmpeg);
        this.seed = seed;
        this.preview = preview;
    }

    public static void start() {
        String ffmpeg = System.getProperty("swingingsigns.ffmpeg", "ffmpeg");
        long seed = Long.parseLong(System.getProperty("swingingsigns.seed", "20261012"));
        instance = new Showcase(Path.of(System.getProperty("swingingsigns.showcase")), ffmpeg, seed,
                Boolean.parseBoolean(System.getProperty("swingingsigns.preview", "false")));
    }

    public static boolean hideHand() {
        return instance != null && instance.started;
    }

    public static boolean hideHud() {
        return instance != null && instance.started;
    }

    public static void frame() {
        if (instance != null) {
            instance.onFrame();
        }
    }

    private void onFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 120) {
                started = true;
                setUpOptions(mc);
                plan(mc);
                log("creating world with seed " + seed);
                deleteWorld(mc);
                Worlds.create(mc, WORLD, false, seed);
            }
            return;
        }
        Clock.step();
        mc.gui.toastManager().clear();
        try {
            while (index < actions.size()) {
                if (frame == 0) {
                    actionStart = System.nanoTime();
                }
                if (!actions.get(index).run(frame++)) {
                    return;
                }
                index++;
                frame = 0;
            }
        } catch (Throwable e) {
            log("FAILED: " + e);
            e.printStackTrace();
        }
        finish(mc);
    }

    private void finish(Minecraft mc) {
        finished = true;
        Clock.release();
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(90_000L);
            } catch (InterruptedException e) {
                return;
            }
            log("the game did not close, forcing it");
            Runtime.getRuntime().halt(1);
        }, "swingingsigns-showcase-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        SwingingSignsClient.config().resetToDefaults();
        SwingingSignsClient.saveConfig();
        log("showcase done");
        mc.stop();
    }

    private static void setUpOptions(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.options.renderDistance().set(12);
        mc.options.bobView().set(false);
        mc.options.particles().set(ParticleStatus.ALL);
        mc.options.gamma().set(Double.parseDouble(System.getProperty("swingingsigns.gamma", "0.75")));
        mc.options.fov().set(Integer.getInteger("swingingsigns.fov", 60));
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.guiScale().set(3);
        mc.resizeGui();
    }

    private void once(Runnable action) {
        actions.add(f -> {
            action.run();
            return true;
        });
    }

    private void until(BooleanSupplier ready) {
        actions.add(f -> ready.getAsBoolean());
    }

    private void holdFor(int frames, Runnable hold) {
        actions.add(f -> {
            hold.run();
            return f >= frames;
        });
    }

    private void settle(Minecraft mc, Runnable hold) {
        actions.add(f -> {
            hold.run();
            return f >= 90 && System.nanoTime() - actionStart > SETTLE_NANOS
                    && (mc.levelRenderer.hasRenderedAllSections() || System.nanoTime() - actionStart > 20 * SETTLE_NANOS);
        });
    }

    private void record(String name, int frames, IntConsumer script) {
        int length = preview ? 2 : frames;
        actions.add(f -> {
            if (f == 0) {
                log("recording " + name);
                recorder.start(out.resolve("clips").resolve(name + ".mp4"));
            } else {
                Path still = pendingStill;
                pendingStill = null;
                recorder.capture(still);
            }
            if (f < length) {
                script.accept(preview ? frames / 2 : f);
                if (f == 0 && preview) {
                    pendingStill = out.resolve("stills").resolve(name + ".png");
                }
                return false;
            }
            return true;
        });
        until(recorder::drained);
        once(recorder::stop);
    }

    static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    static double ease(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.add(b.subtract(a).scale(t));
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void cmd(Minecraft mc, String format, Object... args) {
        run(mc, String.format(Locale.ROOT, format, args));
    }

    private static void face(Minecraft mc, double yaw, double pitch) {
        LocalPlayer player = mc.player;
        player.setYRot((float) yaw);
        player.setXRot((float) pitch);
        player.yRotO = (float) yaw;
        player.xRotO = (float) pitch;
        player.setYHeadRot((float) yaw);
        player.yHeadRotO = (float) yaw;
        player.yBodyRot = (float) yaw;
        player.yBodyRotO = (float) yaw;
    }

    private static void camera(Minecraft mc, Vec3 eye, Vec3 target) {
        LocalPlayer player = mc.player;
        Vec3 feet = eye.subtract(0.0, EYE, 0.0);
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(feet.x, feet.y, feet.z);
        player.xo = feet.x;
        player.yo = feet.y;
        player.zo = feet.z;
        player.xOld = feet.x;
        player.yOld = feet.y;
        player.zOld = feet.z;
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        face(mc, Math.toDegrees(Math.atan2(-dx, dz)), -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private static boolean wanted(String scene) {
        String only = System.getProperty("swingingsigns.scenes");
        return only == null || List.of(only.split(",")).contains(scene);
    }

    private Vec3 at(double dx, double dy, double dz) {
        BlockPos c = site.get();
        return new Vec3(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
    }

    private static int height(ServerLevel level, int x, int z) {
        return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
    }

    private void locate(Minecraft mc) {
        once(() -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerLevel level = server.overworld();
                Pair<BlockPos, Holder<Biome>> pair = level.findClosestBiome3d(h -> h.is(Biomes.TAIGA),
                        BlockPos.ZERO, 6400, 32, 64);
                BlockPos found = pair == null ? BlockPos.ZERO : pair.getFirst();
                site.set(found);
                log("taiga at " + found.toShortString());
            });
        });
        until(() -> site.get() != null);
    }

    private static int mode(List<Integer> values) {
        Map<Integer, Integer> counts = new HashMap<>();
        int best = values.get(0);
        for (int value : values) {
            int count = counts.merge(value, 1, Integer::sum);
            if (count > counts.get(best)) {
                best = value;
            }
        }
        return best;
    }

    private void level(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        int ground = server.submit(() -> {
            ServerLevel level = server.overworld();
            BlockPos c = site.get();
            List<Integer> heights = new ArrayList<>();
            for (int dx = -EDGE; dx <= EDGE; dx += 2) {
                for (int dz = -EDGE; dz <= EDGE; dz += 2) {
                    heights.add(height(level, c.getX() + dx, c.getZ() + dz));
                }
            }
            return mode(heights);
        }).join();
        BlockPos c = site.get();
        site.set(new BlockPos(c.getX(), ground + 1, c.getZ()));
        log("clearing at y " + (ground + 1));
    }

    private void fill(Minecraft mc, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        BlockPos c = site.get();
        int ax = c.getX() + Math.min(x1, x2);
        int bx = c.getX() + Math.max(x1, x2);
        int az = c.getZ() + Math.min(z1, z2);
        int bz = c.getZ() + Math.max(z1, z2);
        int lo = c.getY() + Math.min(y1, y2);
        int hi = c.getY() + Math.max(y1, y2);
        int layers = Math.max(1, 32768 / ((bx - ax + 1) * (bz - az + 1)));
        for (int y = lo; y <= hi; y += layers) {
            cmd(mc, "fill %d %d %d %d %d %d %s", ax, y, az, bx, Math.min(y + layers - 1, hi), bz, block);
        }
    }

    private void set(Minecraft mc, int dx, int dy, int dz, String block) {
        BlockPos c = site.get();
        cmd(mc, "setblock %d %d %d %s", c.getX() + dx, c.getY() + dy, c.getZ() + dz, block);
    }

    private void buildClearing(Minecraft mc) {
        fill(mc, -EDGE, 0, -EDGE, EDGE, 40, EDGE, "minecraft:air");
        fill(mc, -EDGE, -6, -EDGE, EDGE, -2, EDGE, "minecraft:dirt");
        fill(mc, -EDGE, -1, -EDGE, EDGE, -1, EDGE, "minecraft:grass_block");
        Random random = new Random(seed);
        for (int i = 0; i < 260; i++) {
            int dx = random.nextInt(EDGE * 2 + 1) - EDGE;
            int dz = random.nextInt(EDGE * 2 + 1) - EDGE;
            if (Math.abs(dx) + Math.abs(dz) < 14) {
                continue;
            }
            set(mc, dx, -1, dz, random.nextBoolean() ? "minecraft:podzol" : "minecraft:coarse_dirt");
        }
        for (int dz = 3; dz <= 24; dz++) {
            int wobble = dz < 9 ? 0 : (int) Math.round(Math.sin(dz * 0.4) * 0.8);
            for (int dx = -1; dx <= 1; dx++) {
                double roll = random.nextDouble();
                String block = dz < 9 ? (roll < 0.7 ? "minecraft:dirt_path" : "minecraft:coarse_dirt")
                        : roll < 0.55 ? "minecraft:dirt_path" : roll < 0.8 ? "minecraft:coarse_dirt" : "minecraft:gravel";
                set(mc, dx + wobble, -1, dz, block);
            }
        }
        String[] kinds = {"spruce", "pine", "spruce", "mega_spruce"};
        int[][] trees = {{-13, -12}, {-8, -15}, {0, -16}, {9, -14}, {14, -9}, {-16, -3}, {17, -1}, {-17, 8}, {18, 9},
                {-12, 15}, {13, 16}, {-21, -17}, {22, -16}, {-25, 2}, {25, 3}, {-20, 21}, {21, 22}, {4, -23},
                {-5, -24}, {-15, 26}, {16, 27}, {-27, -9}, {28, -7}, {-10, 24}, {11, -20}};
        for (int i = 0; i < trees.length; i++) {
            BlockPos c = site.get();
            cmd(mc, "place feature minecraft:%s %d %d %d", kinds[i % kinds.length], c.getX() + trees[i][0], c.getY(),
                    c.getZ() + trees[i][1]);
        }
        for (int dx = -EDGE + 1; dx <= EDGE - 1; dx++) {
            for (int dz = -EDGE + 1; dz <= EDGE - 1; dz++) {
                boolean yard = Math.abs(dx) <= 7 && dz >= -8 && dz <= 24;
                double roll = random.nextDouble();
                if (yard || roll > 0.16) {
                    continue;
                }
                String block = roll < 0.07 ? "minecraft:fern" : roll < 0.13 ? "minecraft:short_grass"
                        : roll < 0.145 ? "minecraft:sweet_berry_bush[age=3]" : "minecraft:large_fern[half=lower]";
                if (block.startsWith("minecraft:large_fern")) {
                    set(mc, dx, 0, dz, "minecraft:large_fern[half=lower]");
                    set(mc, dx, 1, dz, "minecraft:large_fern[half=upper]");
                } else {
                    set(mc, dx, 0, dz, block + " keep");
                }
            }
        }
        buildShop(mc);
        run(mc, "kill @e[type=!minecraft:player]");
    }

    private void buildShop(Minecraft mc) {
        fill(mc, -4, -1, -5, 4, -1, 2, "minecraft:cobblestone");
        fill(mc, -4, 0, -5, 4, 3, 2, "minecraft:spruce_planks");
        fill(mc, -3, 0, -4, 3, 3, 1, "minecraft:air");
        fill(mc, -3, -1, -4, 3, -1, 1, "minecraft:dark_oak_planks");
        for (int x : new int[]{-4, 4}) {
            for (int z : new int[]{-5, 2}) {
                fill(mc, x, 0, z, x, 3, z, "minecraft:dark_oak_log");
            }
        }
        fill(mc, -4, 3, -5, 4, 3, -5, "minecraft:stripped_dark_oak_log[axis=x]");
        fill(mc, -4, 3, 2, 4, 3, 2, "minecraft:stripped_dark_oak_log[axis=x]");
        fill(mc, -4, 3, -4, -4, 3, 1, "minecraft:stripped_dark_oak_log[axis=z]");
        fill(mc, 4, 3, -4, 4, 3, 1, "minecraft:stripped_dark_oak_log[axis=z]");
        int[][] gable = {{4, 3}, {5, 2}, {6, 1}, {7, 0}};
        for (int[] g : gable) {
            fill(mc, -g[1], g[0], -5, g[1], g[0], -5, "minecraft:spruce_planks");
            fill(mc, -g[1], g[0], 2, g[1], g[0], 2, "minecraft:spruce_planks");
        }
        int[][] slopes = {{5, 3}, {4, 4}, {3, 5}, {2, 6}, {1, 7}};
        for (int[] s : slopes) {
            fill(mc, -s[0], s[1], -6, -s[0], s[1], 3, "minecraft:dark_oak_stairs[facing=east]");
            fill(mc, s[0], s[1], -6, s[0], s[1], 3, "minecraft:dark_oak_stairs[facing=west]");
        }
        fill(mc, 0, 7, -6, 0, 7, 3, "minecraft:dark_oak_planks");
        fill(mc, 0, 8, -6, 0, 8, 3, "minecraft:dark_oak_slab");
        fill(mc, -3, 1, 2, -2, 2, 2, "minecraft:glass_pane");
        fill(mc, 2, 1, 2, 3, 2, 2, "minecraft:glass_pane");
        fill(mc, -4, 1, -2, -4, 2, -1, "minecraft:glass_pane");
        fill(mc, 4, 1, -2, 4, 2, -1, "minecraft:glass_pane");
        set(mc, 0, 4, 2, "minecraft:glass_pane");
        set(mc, 0, 0, 2, "minecraft:spruce_door[facing=north,half=lower,hinge=left]");
        set(mc, 0, 1, 2, "minecraft:spruce_door[facing=north,half=upper,hinge=left]");
        set(mc, 0, 2, 2, "minecraft:stripped_dark_oak_log[axis=x]");
        fill(mc, -3, 0, 3, -2, 0, 3, "minecraft:spruce_trapdoor[facing=south,half=top]");
        set(mc, -3, 1, 3, "minecraft:potted_red_tulip");
        set(mc, -2, 1, 3, "minecraft:potted_azure_bluet");
        fill(mc, 2, 0, 3, 3, 0, 3, "minecraft:spruce_trapdoor[facing=south,half=top]");
        set(mc, 2, 1, 3, "minecraft:potted_orange_tulip");
        set(mc, 3, 1, 3, "minecraft:potted_fern");
        set(mc, -5, 0, 3, "minecraft:barrel[facing=up]");
        set(mc, -5, 1, 3, "minecraft:lantern");
        set(mc, -5, 0, 2, "minecraft:barrel[facing=south]");
        set(mc, 5, 0, 3, "minecraft:barrel[facing=up]");
        set(mc, 5, 0, 2, "minecraft:hay_block");
        set(mc, 5, 1, 2, "minecraft:carved_pumpkin[facing=south]");
        set(mc, 6, 0, 2, "minecraft:composter[level=7]");
        set(mc, -1, 2, 3, "minecraft:lantern[hanging=true]");
        set(mc, 1, 2, 3, "minecraft:lantern[hanging=true]");
        fill(mc, -3, 0, -4, 3, 1, -4, "minecraft:bookshelf");
        set(mc, -3, 2, -4, "minecraft:barrel[facing=south]");
        set(mc, 3, 2, -4, "minecraft:barrel[facing=south]");
        fill(mc, -2, 0, -1, 2, 0, -1, "minecraft:spruce_planks");
        set(mc, -1, 1, -1, "minecraft:lantern");
        set(mc, 1, 1, -1, "minecraft:potted_cornflower");
        set(mc, -3, 0, 0, "minecraft:chest[facing=east]");
        set(mc, 3, 0, 0, "minecraft:barrel[facing=up]");
        set(mc, 3, 1, 0, "minecraft:lantern");
        fill(mc, -2, 4, -1, 2, 4, -1, "minecraft:spruce_log[axis=x]");
        set(mc, 0, 3, -1, "minecraft:lantern[hanging=true]");
        fill(mc, 3, 4, -4, 3, 8, -4, "minecraft:cobblestone");
        set(mc, 3, 9, -4, "minecraft:campfire[lit=true]");
        fill(mc, -2, 0, 5, -2, 1, 5, "minecraft:spruce_log");
        fill(mc, 2, 0, 5, 2, 1, 5, "minecraft:spruce_log");
        fill(mc, -2, 2, 5, 2, 2, 5, "minecraft:stripped_spruce_log[axis=x]");
        set(mc, 0, 1, 5, "minecraft:spruce_hanging_sign[rotation=0]" + TEXT);
        set(mc, -3, 0, 9, "minecraft:spruce_fence");
        set(mc, -3, 1, 9, "minecraft:lantern");
        set(mc, 3, 0, 14, "minecraft:spruce_fence");
        set(mc, 3, 1, 14, "minecraft:lantern");
        set(mc, -6, 0, -1, "minecraft:spruce_log[axis=z]");
        set(mc, -6, 0, 0, "minecraft:spruce_log[axis=z]");
        set(mc, -6, 1, 0, "minecraft:spruce_log[axis=z]");
    }

    private void walker(Minecraft mc, Vec3 spot) {
        cmd(mc, "summon minecraft:mannequin %.3f %.3f %.3f {Rotation:[180.0f,0.0f],NoGravity:1b,hide_description:1b,"
                        + "Tags:[\"walker\"],equipment:{"
                        + "head:{id:\"minecraft:leather_helmet\",components:{\"minecraft:dyed_color\":8405024}},"
                        + "chest:{id:\"minecraft:leather_chestplate\",components:{\"minecraft:dyed_color\":3949738}},"
                        + "legs:{id:\"minecraft:leather_leggings\",components:{\"minecraft:dyed_color\":5585460}},"
                        + "feet:{id:\"minecraft:leather_boots\",components:{\"minecraft:dyed_color\":3155489}}}}",
                spot.x, spot.y, spot.z);
    }

    private void walk(Minecraft mc, String name, Vec3 from, Vec3 to, int walkFrames, int frames, Vec3 eyeFrom, Vec3 eyeTo,
                      Vec3 lookFrom, Vec3 lookTo) {
        float yaw = (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
        once(() -> walker(mc, from));
        holdFor(30, () -> camera(mc, eyeFrom, lookFrom));
        record(name, frames, f -> {
            double t = Math.min(1.0, f / (double) walkFrames);
            double along = t < 0.9 ? t / 0.95 : (0.9 + 0.05 * smooth((t - 0.9) / 0.1)) / 0.95;
            Vec3 p = lerp(from, to, along);
            cmd(mc, "tp @e[tag=walker,limit=1] %.3f %.3f %.3f %.1f 0", p.x, p.y, p.z, yaw);
            double c = ease(f / (double) frames);
            camera(mc, lerp(eyeFrom, eyeTo, c), lerp(lookFrom, lookTo, c));
        });
        once(() -> {
            Vec3 away = at(0, -40, 0);
            cmd(mc, "tp @e[tag=walker] %.1f %.1f %.1f", away.x, away.y, away.z);
        });
        holdFor(10, () -> {
        });
        once(() -> run(mc, "kill @e[tag=walker]"));
        holdFor(120, () -> {
        });
    }

    private void plan(Minecraft mc) {
        SwingingSignsConfig config = SwingingSignsClient.config();
        until(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null);
        once(() -> {
            log("world loaded");
            Clock.fix();
            config.resetToDefaults();
            run(mc, "time set 6000");
            run(mc, "weather clear 1000000");
            run(mc, "gamerule advance_time false");
            run(mc, "gamerule advance_weather false");
            run(mc, "gamerule spawn_mobs false");
            run(mc, "gamerule random_tick_speed 0");
            run(mc, "difficulty peaceful");
        });
        locate(mc);
        once(() -> {
            BlockPos c = site.get();
            cmd(mc, "forceload add %d %d %d %d", c.getX() - 48, c.getZ() - 48, c.getX() + 48, c.getZ() + 48);
            cmd(mc, "tp @p %d %d %d", c.getX(), c.getY() + 45, c.getZ());
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        });
        settle(mc, () -> {
        });
        once(() -> level(mc));
        once(() -> buildClearing(mc));
        holdFor(60, () -> {
        });
        once(() -> {
            run(mc, "gamemode spectator @p");
            cmd(mc, "time set %d", Integer.getInteger("swingingsigns.time", 12300));
            config.setWindStrength(Float.parseFloat(System.getProperty("swingingsigns.wind", "2.2")));
            config.setPushStrength(1.6F);
        });
        settle(mc, () -> camera(mc, at(0.5, 1.6, 20), at(0.5, 2, 0)));
        holdFor(240, () -> camera(mc, at(0.5, 1.6, 20), at(0.5, 2, 0)));
        once(() -> film(mc));
    }

    private void film(Minecraft mc) {
        SwingingSignsConfig config = SwingingSignsClient.config();
        Vec3 sign = at(0.5, 1.35, 5.5);
        Vec3 shop = at(0.5, 2.4, 2.0);
        if (wanted("arrival")) {
            Vec3 from = at(-0.4, 1.1, 22.0);
            Vec3 to = at(0.9, 1.55, 9.6);
            record("01-arrival", 600, f -> {
                double t = ease(f / 600.0);
                camera(mc, lerp(from, to, t), lerp(shop, sign.add(0, 0.3, 0), t));
            });
        }
        if (wanted("orbit")) {
            record("02-orbit", 600, f -> {
                double t = ease(f / 600.0);
                double angle = Math.toRadians(-48 + 96 * t);
                double radius = 3.6 - 0.5 * t;
                Vec3 eye = sign.add(Math.sin(angle) * radius, 0.25 - 0.15 * t, Math.cos(angle) * radius);
                camera(mc, eye, sign.add(0, 0.05, 0));
            });
        }
        if (wanted("close")) {
            Vec3 from = at(1.9, 1.15, 7.4);
            Vec3 to = at(1.2, 1.3, 6.9);
            record("03-close", 480, f -> {
                double t = ease(f / 480.0);
                camera(mc, lerp(from, to, t), lerp(sign.add(0, 0.25, 0), sign.add(0, 0.05, 0), t));
            });
        }
        if (wanted("walk")) {
            walk(mc, "04-walk", at(0.5, 0, 15.0), at(0.5, 0, 3.4), 360, 520,
                    at(3.1, 1.5, 9.8), at(2.5, 1.5, 8.0), at(0.5, 1.1, 9.5), at(0.5, 1.3, 5.4));
        }
        if (wanted("through")) {
            walk(mc, "05-through", at(0.5, 0, 3.2), at(0.5, 0, 13.0), 330, 480,
                    at(-2.6, 1.5, 7.4), at(-2.4, 1.55, 10.6), at(0.5, 1.3, 4.8), at(0.5, 1.2, 8.5));
        }
        if (wanted("storm")) {
            once(() -> {
                config.setWindStrength(Float.parseFloat(System.getProperty("swingingsigns.stormwind", "1.4")));
                run(mc, "weather thunder 1000000");
            });
            holdFor(preview ? 260 : 420, () -> camera(mc, at(2.9, 1.2, 9.9), sign));
            Vec3 from = at(2.9, 1.2, 9.9);
            Vec3 to = at(1.9, 1.35, 7.7);
            record("06-storm", 540, f -> {
                double t = ease(f / 540.0);
                camera(mc, lerp(from, to, t), lerp(sign.add(0, 0.1, -0.6), sign.add(0, 0.05, 0), t));
                if (f == 200) {
                    Vec3 bolt = at(16, 0, -26);
                    cmd(mc, "summon minecraft:lightning_bolt %.1f %.1f %.1f", bolt.x, bolt.y, bolt.z);
                }
            });
        }
        if (wanted("outro")) {
            Vec3 from = at(0.9, 1.6, 8.8);
            Vec3 to = at(0.2, 6.5, 28.0);
            record("07-outro", 660, f -> {
                double t = ease(f / 660.0);
                camera(mc, lerp(from, to, t), lerp(sign, shop, t));
                if (f == 380) {
                    Vec3 bolt = at(-18, 0, -24);
                    cmd(mc, "summon minecraft:lightning_bolt %.1f %.1f %.1f", bolt.x, bolt.y, bolt.z);
                }
            });
        }
    }

    private static void deleteWorld(Minecraft mc) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
        if (Files.exists(dir)) {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    static void log(String message) {
        System.out.println("[swingingsigns-showcase] " + message);
    }
}
