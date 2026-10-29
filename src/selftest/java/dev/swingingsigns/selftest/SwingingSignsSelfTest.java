package dev.swingingsigns.selftest;

import dev.swingingsigns.client.Swing;
import dev.swingingsigns.client.SwingingSignsClient;
import dev.swingingsigns.client.gui.SwingingSignsSettingsScreen;
import dev.swingingsigns.config.SwingingSignsConfig;
import dev.swingingsigns.core.SignSpring;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class SwingingSignsSelfTest {
    private static final String WORLD = "swingingsigns-selftest";
    private static final int TIMEOUT = 2400;

    private static volatile int frames;
    public static volatile boolean hideHud;

    private final List<Step> steps = new ArrayList<>();
    private final List<String> shots = new ArrayList<>();
    private final Set<String> existingShots = new HashSet<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int delay;
    private int waited;
    private int seenFrames;
    private int patience;

    private record Step(int delay, BooleanSupplier ready, Runnable action) {
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 40) {
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.tutorialStep = TutorialSteps.NONE;
                ServerCompat.hideChat(mc);
                rememberShots(mc);
                plan(mc);
                delay = steps.get(0).delay();
                log("creating world");
                deleteWorld(mc, WORLD);
                Worlds.create(mc, WORLD, true, 0L);
            }
            return;
        }
        int drawn = frames;
        if (drawn == seenFrames) {
            return;
        }
        seenFrames = drawn;
        if (delay > 0) {
            delay--;
            return;
        }
        Step step = steps.get(index);
        if (!step.ready().getAsBoolean()) {
            if (++waited > TIMEOUT) {
                finish(mc, new AssertionError("timed out at step " + index));
            }
            return;
        }
        waited = 0;
        index++;
        try {
            step.action().run();
        } catch (Throwable e) {
            finish(mc, e);
            return;
        }
        if (index >= steps.size()) {
            finish(mc, null);
        } else {
            delay = steps.get(index).delay();
        }
    }

    public static void frame() {
        frames++;
    }

    private void then(int ticks, Runnable action) {
        steps.add(new Step(ticks, () -> true, action));
    }

    private void when(BooleanSupplier ready, Runnable action) {
        steps.add(new Step(0, ready, action));
    }

    private void within(int ticks, BooleanSupplier ready, Runnable action) {
        then(1, () -> patience = ticks);
        when(() -> ready.getAsBoolean() || --patience < 0, action);
    }

    private void finish(Minecraft mc, Throwable failure) {
        finished = true;
        nameShots(mc);
        if (failure == null) {
            log("ALL CHECKS PASSED");
        } else {
            log("FAILED: " + failure);
            failure.printStackTrace();
        }
        SwingingSignsConfig config = SwingingSignsClient.config();
        config.resetToDefaults();
        SwingingSignsClient.saveConfig();
        mc.stop();
    }

    private void plan(Minecraft mc) {
        SwingingSignsConfig config = SwingingSignsClient.config();
        int floor = ServerCompat.FLOOR;
        int y = floor + 1;
        int z = -5;
        BlockPos left = new BlockPos(-2, y, z);
        BlockPos middle = new BlockPos(0, y, z);
        BlockPos right = new BlockPos(2, y, z);
        BlockPos wall = new BlockPos(5, y, z);

        when(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null, () -> log("world loaded"));
        then(40, () -> {
            config.resetToDefaults();
            run(mc, "gamerule doDaylightCycle false");
            run(mc, "gamerule advance_time false");
            run(mc, "time set 6000");
            run(mc, "weather clear");
            run(mc, "tp @p 0.5 " + floor + " 0.5 180 10");
        });
        if (Boolean.getBoolean("swingingsigns.icon")) {
            iconScene(mc);
            return;
        }
        then(20, () -> {
            run(mc, "fill -3 " + (floor + 2) + " -6 3 " + (floor + 2) + " -4 minecraft:oak_planks");
            run(mc, "setblock " + left.getX() + " " + y + " " + z + " minecraft:oak_hanging_sign");
            run(mc, "setblock " + middle.getX() + " " + y + " " + z + " minecraft:spruce_hanging_sign");
            run(mc, "setblock " + right.getX() + " " + y + " " + z + " minecraft:birch_hanging_sign");
            run(mc, "setblock " + wall.getX() + " " + y + " " + (z - 1) + " minecraft:stone");
            run(mc, "setblock " + wall.getX() + " " + y + " " + z + " minecraft:oak_wall_hanging_sign[facing=east]");
        });
        within(200, () -> Swing.tracked() >= 4, () -> {
            check(Swing.tracked() >= 4, "only " + Swing.tracked() + " of the 4 rendered signs are simulated");
            check(Swing.spring(middle) != null, "the middle sign is not simulated");
        });

        float[] leftAngles = new float[100];
        float[] middleAngles = new float[100];
        for (int i = 0; i < leftAngles.length; i++) {
            int n = i;
            then(1, () -> {
                leftAngles[n] = angle(mc, left);
                middleAngles[n] = angle(mc, middle);
            });
        }
        float[] idlePeak = new float[1];
        then(1, () -> {
            float leftRange = range(leftAngles);
            float middleRange = range(middleAngles);
            float apart = 0f;
            for (int i = 0; i < leftAngles.length; i++) {
                apart = Math.max(apart, Math.abs(leftAngles[i] - middleAngles[i]));
                idlePeak[0] = Math.max(idlePeak[0], Math.max(Math.abs(leftAngles[i]), Math.abs(middleAngles[i])));
            }
            log(String.format(Locale.ROOT, "idle: left range %.4f, middle range %.4f, apart %.4f, peak %.4f",
                    leftRange, middleRange, apart, idlePeak[0]));
            check(leftRange > 0.004f && middleRange > 0.004f, "the signs do not sway when idle");
            check(apart > 0.002f, "two neighbouring signs sway in sync");
            check(idlePeak[0] < 0.1f, "idle sway is too strong: " + idlePeak[0]);
        });
        then(5, () -> screenshot(mc, "idle"));

        float[] pushPeak = new float[2];
        then(20, () -> {
            SignSpring spring = Swing.spring(middle);
            pushPeak[0] = spring == null ? 0f : Math.abs(spring.angle());
            run(mc, "summon minecraft:arrow 0.5 " + (y + 0.4) + " " + (z - 4) + " {NoGravity:1b,Motion:[0.0,0.0,0.6],pickup:0b}");
        });
        for (int i = 0; i < 60; i++) {
            then(1, () -> {
                SignSpring spring = Swing.spring(middle);
                if (spring != null) {
                    pushPeak[1] = Math.max(pushPeak[1], Math.abs(spring.angle()));
                }
            });
        }
        then(1, () -> {
            log(String.format(Locale.ROOT, "push: before %.4f, peak %.4f", pushPeak[0], pushPeak[1]));
            check(pushPeak[1] > pushPeak[0] + 0.02f, "an arrow flying past the sign did not push it: peak " + pushPeak[1]);
            check(pushPeak[1] <= SignSpring.MAX_ANGLE + 0.001f, "the push swung past the limit: " + pushPeak[1]);
            run(mc, "kill @e[type=minecraft:arrow]");
        });
        within(200, () -> {
            SignSpring spring = Swing.spring(middle);
            return spring != null && spring.atRest();
        }, () -> {
            SignSpring spring = Swing.spring(middle);
            check(spring != null && spring.atRest(), "the pushed sign never settled");
        });

        then(1, () -> run(mc, "weather thunder"));
        within(400, () -> mc.level.getThunderLevel(1f) > 0.95f, () -> {
        });
        float[] stormPeak = new float[1];
        for (int i = 0; i < 160; i++) {
            then(1, () -> stormPeak[0] = Math.max(stormPeak[0], Math.max(Math.abs(angle(mc, left)), Math.abs(angle(mc, middle)))));
        }
        then(1, () -> {
            log(String.format(Locale.ROOT, "storm: peak %.4f, idle peak %.4f", stormPeak[0], idlePeak[0]));
            check(stormPeak[0] > idlePeak[0] * 2f, "the signs do not swing harder in a thunderstorm");
            check(stormPeak[0] <= SignSpring.MAX_ANGLE + 0.3f, "storm sway is out of range: " + stormPeak[0]);
            screenshot(mc, "storm");
        });
        then(5, () -> run(mc, "weather clear"));
        within(400, () -> mc.level.getRainLevel(1f) < 0.05f, () -> {
        });

        then(1, () -> run(mc, "tp @p 2.5 " + floor + " -1.5 150 10"));
        then(40, () -> {
            check(angle(mc, wall) != 0f, "the wall sign does not swing");
            screenshot(mc, "wall");
            config.setWallSigns(false);
        });
        then(5, () -> {
            check(angle(mc, wall) == 0f, "the wall sign still swings with wall signs turned off");
            check(angle(mc, middle) != 0f, "turning off wall signs stopped the ceiling signs");
            config.setWallSigns(true);
            config.setEnabled(false);
        });
        then(5, () -> {
            check(angle(mc, middle) == 0f, "the signs still swing with the mod turned off");
            check(angle(mc, wall) == 0f, "the wall sign still swings with the mod turned off");
            config.setEnabled(true);
        });
        then(5, () -> check(angle(mc, middle) != 0f, "the signs did not start swinging again"));

        then(20, () -> Screens.open(mc, new SwingingSignsSettingsScreen(null, mc.options)));
        then(20, () -> {
            check(Screens.current(mc) instanceof SwingingSignsSettingsScreen, "the settings screen did not open");
            screenshot(mc, "settings");
        });
        then(5, () -> Screens.open(mc, null));
    }

    private void iconScene(Minecraft mc) {
        int floor = ServerCompat.FLOOR;
        int y = floor + 2;
        String[] woods = {"oak", "spruce", "cherry", "birch", "crimson"};
        then(5, () -> {
            hideHud = true;
            mc.options.fov().set(55);
            run(mc, "fill -5 " + (y + 1) + " -7 5 " + (y + 1) + " -5 minecraft:dark_oak_log");
            for (int i = 0; i < woods.length; i++) {
                run(mc, "setblock " + (i * 2 - 4) + " " + y + " -6 minecraft:" + woods[i] + "_hanging_sign");
            }
            run(mc, "time set 12500");
            run(mc, "weather thunder");
            run(mc, "tp @p 0.5 " + floor + " -1.5 180 -12");
        });
        for (int i = 0; i < 12; i++) {
            String name = "icon-row-" + i;
            then(i == 0 ? 200 : 6, () -> screenshot(mc, name));
        }
        then(1, () -> run(mc, "tp @p 0.5 " + (floor + 1) + " -4.2 180 -10"));
        for (int i = 0; i < 12; i++) {
            String name = "icon-close-" + i;
            then(i == 0 ? 40 : 6, () -> screenshot(mc, name));
        }
        then(1, () -> {
            hideHud = false;
            mc.options.fov().set(70);
            run(mc, "weather clear");
        });
    }

    private static float angle(Minecraft mc, BlockPos pos) {
        return Swing.angle(pos, mc.level.getBlockState(pos), 0f);
    }

    private static float range(float[] values) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return max - min;
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> ServerCompat.runCommand(server, command));
    }

    private void screenshot(Minecraft mc, String name) {
        log("screenshot: " + name);
        shots.add(name);
        ServerCompat.screenshot(mc, Screens.renderTarget(mc));
    }

    private void rememberShots(Minecraft mc) {
        File[] files = new File(mc.gameDirectory, "screenshots").listFiles();
        if (files != null) {
            for (File file : files) {
                existingShots.add(file.getName());
            }
        }
    }

    private void nameShots(Minecraft mc) {
        File dir = new File(mc.gameDirectory, "screenshots");
        File[] files = dir.listFiles((parent, name) -> name.endsWith(".png") && !existingShots.contains(name));
        if (files == null) {
            return;
        }
        List<File> fresh = new ArrayList<>(List.of(files));
        fresh.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        for (int i = 0; i < fresh.size() && i < shots.size(); i++) {
            File target = new File(dir, String.format(Locale.ROOT, "ss-%02d-%s.png", i, shots.get(i)));
            if ((!target.exists() || target.delete()) && fresh.get(i).renameTo(target)) {
                log("saved " + target.getName());
            }
        }
    }

    private static void deleteWorld(Minecraft mc, String name) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(name);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void log(String message) {
        System.out.println("[swingingsigns-selftest] " + message);
    }
}
