package dev.swingingsigns.selftest.neoforge;

import dev.swingingsigns.selftest.SwingingSignsSelfTest;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "swingingsigns_selftest", dist = Dist.CLIENT)
public final class SwingingSignsSelfTestNeoForge {
    public SwingingSignsSelfTestNeoForge() {
        SwingingSignsSelfTest test = new SwingingSignsSelfTest();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> test.tick());
    }
}
