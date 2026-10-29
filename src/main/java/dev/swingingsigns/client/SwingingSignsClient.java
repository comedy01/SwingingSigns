package dev.swingingsigns.client;

import dev.swingingsigns.config.SwingingSignsConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public final class SwingingSignsClient {
    public static final String MOD_ID = "swingingsigns";
    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static SwingingSignsConfig config = new SwingingSignsConfig();
    private static Path configPath;
    private static boolean failed;

    private SwingingSignsClient() {
    }

    public static void init(Path configDir) {
        configPath = configDir.resolve(SwingingSignsConfig.FILE_NAME);
        config = SwingingSignsConfig.load(configPath);
    }

    public static SwingingSignsConfig config() {
        return config;
    }

    public static void saveConfig() {
        config.saveQuietly(configPath);
    }

    public static boolean active() {
        return !failed && config.enabled();
    }

    public static void fail(Throwable e) {
        if (!failed) {
            failed = true;
            Swing.clear();
            LOGGER.error("Swinging Hanging Signs hit an error and turned itself off for this session", e);
        }
    }
}
