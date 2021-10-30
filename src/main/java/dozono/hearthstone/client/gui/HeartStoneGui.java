package dozono.hearthstone.client.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import dozono.hearthstone.HearthStoneMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class HeartStoneGui extends AbstractGui {
    private Minecraft mc = Minecraft.getInstance();

    @SubscribeEvent
    public void onRenderForeground(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        this.drawProgress(event.getMatrixStack());
    }

    public void drawProgress(MatrixStack matrixStack) {
        Integer prog = mc.player.getEntityData().get(HearthStoneMod.DATA_PLAYER_HEARTH_STONE_CHARGE);
        if (prog > 0) {
            mc.getTextureManager().bind(new ResourceLocation(HearthStoneMod.MODID, "textures/gui/progress_bar_gui.png"));
            int x = 10;
            int y = 100;
            this.blit(matrixStack, x, y, 0, 0, 128, 11);
            this.blit(matrixStack, x, y + 1, 0, 12, (int) (109 * prog / 80D), 11);
        }
    }
}
