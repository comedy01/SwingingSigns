package dev.swingingsigns.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SwingingSignsConfig {
    public static final String FILE_NAME = "swingingsigns.json";

    private static final Logger LOGGER = LogManager.getLogger("swingingsigns");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean enabled = true;
    private float windStrength = 1.0f;
    private float pushStrength = 1.0f;
    private boolean weather = true;
    private boolean wallSigns = true;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public float windStrength() {
        return clamp(windStrength, 0f, 3f);
    }

    public void setWindStrength(float value) {
        windStrength = value;
    }

    public float pushStrength() {
        return clamp(pushStrength, 0f, 3f);
    }

    public void setPushStrength(float value) {
        pushStrength = value;
    }

    public boolean weather() {
        return weather;
    }

    public void setWeather(boolean value) {
        weather = value;
    }

    public boolean wallSigns() {
        return wallSigns;
    }

    public void setWallSigns(boolean value) {
        wallSigns = value;
    }

    public void resetToDefaults() {
        SwingingSignsConfig fresh = new SwingingSignsConfig();
        enabled = fresh.enabled;
        windStrength = fresh.windStrength;
        pushStrength = fresh.pushStrength;
        weather = fresh.weather;
        wallSigns = fresh.wallSigns;
    }

    private static float clamp(float value, float min, float max) {
        return Float.isNaN(value) ? min : Math.max(min, Math.min(max, value));
    }

    public static SwingingSignsConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            SwingingSignsConfig fresh = new SwingingSignsConfig();
            fresh.saveQuietly(file);
            return fresh;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            SwingingSignsConfig loaded = GSON.fromJson(reader, SwingingSignsConfig.class);
            if (loaded == null) {
                throw new JsonParseException("config file is empty");
            }
            return loaded;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}; using defaults. {}", file, e.toString());
            moveAside(file);
            SwingingSignsConfig fresh = new SwingingSignsConfig();
            fresh.saveQuietly(file);
            return fresh;
        }
    }

    public void save(Path file) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Files.write(temp, (GSON.toJson(this) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void saveQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            save(file);
        } catch (IOException e) {
            LOGGER.warn("Could not save {}: {}", file, e.toString());
        }
    }

    private static void moveAside(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not back up unreadable config {}: {}", file, e.toString());
        }
    }
}
