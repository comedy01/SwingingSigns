package dev.swingingsigns.showcase;

import net.fabricmc.api.ClientModInitializer;

public final class SwingingSignsShowcaseFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Showcase.start();
    }
}
