package dev.swingingsigns.selftest.fabric;

import dev.swingingsigns.selftest.SwingingSignsSelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class SwingingSignsSelfTestFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SwingingSignsSelfTest test = new SwingingSignsSelfTest();
        ClientTickEvents.END_CLIENT_TICK.register(client -> test.tick());
    }
}
