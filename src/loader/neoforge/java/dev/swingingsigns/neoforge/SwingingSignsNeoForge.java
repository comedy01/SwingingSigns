package dev.swingingsigns.neoforge;

import dev.swingingsigns.client.SwingingSignsClient;
import dev.swingingsigns.client.gui.SwingingSignsSettingsScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = SwingingSignsClient.MOD_ID, dist = Dist.CLIENT)
public final class SwingingSignsNeoForge {
    public SwingingSignsNeoForge(IEventBus modBus, ModContainer container) {
        SwingingSignsClient.init(FMLPaths.CONFIGDIR.get());

        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, parent) -> new SwingingSignsSettingsScreen(parent, Minecraft.getInstance().options));
    }
}
