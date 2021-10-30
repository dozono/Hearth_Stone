package dozono.hearthstone.client;

import dozono.hearthstone.client.gui.HeartStoneGui;
import net.minecraftforge.common.MinecraftForge;

public class HearthStoneClientSetup {
    public static void setup() {
        MinecraftForge.EVENT_BUS.register(new HeartStoneGui());
    }
}
