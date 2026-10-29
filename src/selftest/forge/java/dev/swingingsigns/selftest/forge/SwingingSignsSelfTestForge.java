package dev.swingingsigns.selftest.forge;

import dev.swingingsigns.selftest.SwingingSignsSelfTest;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("swingingsigns_selftest")
public final class SwingingSignsSelfTestForge {
    public SwingingSignsSelfTestForge() {
        SwingingSignsSelfTest test = new SwingingSignsSelfTest();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                test.tick();
            }
        });
    }
}
