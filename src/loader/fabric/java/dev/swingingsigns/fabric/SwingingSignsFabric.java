package dev.swingingsigns.fabric;

import dev.swingingsigns.client.SwingingSignsClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class SwingingSignsFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SwingingSignsClient.init(FabricLoader.getInstance().getConfigDir());
    }
}
