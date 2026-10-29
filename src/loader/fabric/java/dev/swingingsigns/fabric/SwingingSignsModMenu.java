package dev.swingingsigns.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.swingingsigns.client.gui.SwingingSignsSettingsScreen;
import net.minecraft.client.Minecraft;

public final class SwingingSignsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new SwingingSignsSettingsScreen(parent, Minecraft.getInstance().options);
    }
}
