package dev.swingingsigns.client.gui;

import dev.swingingsigns.client.SwingingSignsClient;
import dev.swingingsigns.client.Texts;
import dev.swingingsigns.config.SwingingSignsConfig;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

public final class SwingingSignsSettingsScreen extends SwingingSignsOptionsScreen {
    private static final int WIDTH = 150;
    private static final float[] STRENGTHS = {0f, 0.5f, 1f, 1.5f, 2f, 3f};

    public SwingingSignsSettingsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, Texts.translatable("swingingsigns.options.title"));
    }

    @Override
    protected void addOptions() {
        SwingingSignsConfig config = SwingingSignsClient.config();

        addRow(
                toggle("swingingsigns.options.enabled", config::enabled, config::setEnabled),
                toggle("swingingsigns.options.wall", config::wallSigns, config::setWallSigns));
        addRow(
                cycle("swingingsigns.options.wind", STRENGTHS, config::windStrength, config::setWindStrength, SwingingSignsSettingsScreen::percent),
                cycle("swingingsigns.options.push", STRENGTHS, config::pushStrength, config::setPushStrength, SwingingSignsSettingsScreen::percent));
        addRow(
                toggle("swingingsigns.options.weather", config::weather, config::setWeather),
                null);
        addRow(resetButton(config), null);
    }

    @Override
    public void removed() {
        super.removed();
        SwingingSignsClient.saveConfig();
    }

    private AbstractWidget resetButton(SwingingSignsConfig config) {
        return tooltip(button(Texts.translatable("swingingsigns.options.reset"), WIDTH, button -> {
            config.resetToDefaults();
            ScreenOpener.open(minecraft, new SwingingSignsSettingsScreen(lastScreen, options));
        }), Texts.translatable("swingingsigns.options.reset.tooltip"));
    }

    private AbstractWidget cycle(String key, float[] values, Supplier<Float> getter, Consumer<Float> setter, DoubleFunction<Component> format) {
        return tooltip(button(valueLabel(key, format.apply(getter.get())), WIDTH, button -> {
            float current = getter.get();
            float next = values[0];
            for (float value : values) {
                if (value > current + 1.0E-3f) {
                    next = value;
                    break;
                }
            }
            setter.accept(next);
            button.setMessage(valueLabel(key, format.apply(getter.get())));
        }), Texts.translatable(key + ".tooltip"));
    }

    private AbstractWidget toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        return tooltip(button(toggleLabel(key, getter.getAsBoolean()), WIDTH, button -> {
            setter.accept(!getter.getAsBoolean());
            button.setMessage(toggleLabel(key, getter.getAsBoolean()));
        }), Texts.translatable(key + ".tooltip"));
    }

    private static Component percent(double value) {
        return Texts.literal(Math.round(value * 100) + "%");
    }

    private static Component valueLabel(String key, Component value) {
        return Texts.translatable("options.generic_value", Texts.translatable(key), value);
    }

    private static Component toggleLabel(String key, boolean on) {
        return valueLabel(key, Texts.translatable(on ? "options.on" : "options.off"));
    }
}
