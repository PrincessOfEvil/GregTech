package gregtech.api.gui;

import gregtech.api.gui.impl.ModularUIContainer;
import gregtech.api.gui.impl.ModularUIGui;
import gregtech.api.net.NetworkHandler;
import gregtech.api.net.PacketUIOpen;
import gregtech.api.net.PacketUIWidgetUpdate;
import gregtech.api.util.GTControlledRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Implement and register to {@link #FACTORY_REGISTRY} to be able to create and open ModularUI's
 * createUITemplate should return equal gui both on server and client side, or sync will break!
 * @param <E> UI holder type
 */
public abstract class UIFactory<E extends IUIHolder> {

    public static final GTControlledRegistry<UIFactory<?>> FACTORY_REGISTRY = new GTControlledRegistry<>(Short.MAX_VALUE, false);

    public final void openUI(E holder, EntityPlayerMP player) {
        if (player instanceof FakePlayer) {
            return;
        }
        ModularUI uiTemplate = createUITemplate(holder, player);
        uiTemplate.initWidgets();

        player.getNextWindowId();
        player.closeContainer();
        int currentWindowId = player.currentWindowId;

        PacketBuffer serializedHolder = new PacketBuffer(Unpooled.buffer());
        writeHolderToSyncData(serializedHolder, holder);
        int uiFactoryId = FACTORY_REGISTRY.getIDForObject(this);

        ModularUIContainer container = new ModularUIContainer(uiTemplate);
        container.windowId = currentWindowId;
        //accumulate all initial updates of widgets in open packet
        container.accumulateWidgetUpdateData = true;
        uiTemplate.guiWidgets.values().forEach(Widget::detectAndSendChanges);
        container.accumulateWidgetUpdateData = false;
        ArrayList<PacketUIWidgetUpdate> updateData = new ArrayList<>(container.accumulatedUpdates);
        container.accumulatedUpdates.clear();

        PacketUIOpen packet = new PacketUIOpen(uiFactoryId, serializedHolder, currentWindowId, updateData);
        NetworkHandler.channel.sendTo(NetworkHandler.packet2proxy(packet), player);

        container.addListener(player);
        player.openContainer = container;

        //and fire forge event only in the end
        MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(player, container));
    }

    @SideOnly(Side.CLIENT)
    public final void initClientUI(PacketBuffer serializedHolder, int windowId, List<PacketUIWidgetUpdate> initialWidgetUpdates) {
        E holder = readHolderFromSyncData(serializedHolder);
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP entityPlayer = minecraft.player;

        ModularUI uiTemplate = createUITemplate(holder, entityPlayer);
        uiTemplate.initWidgets();
        ModularUIGui modularUIGui = new ModularUIGui(uiTemplate);
        modularUIGui.inventorySlots.windowId = windowId;
        Stitcher
        for(PacketUIWidgetUpdate packet : initialWidgetUpdates) {
            modularUIGui.handleWidgetUpdate(packet);
        }
        minecraft.addScheduledTask(() -> {
            minecraft.displayGuiScreen(modularUIGui);
            minecraft.player.openContainer.windowId = windowId;
        });
    }

    protected abstract ModularUI createUITemplate(E holder, EntityPlayer entityPlayer);

    @SideOnly(Side.CLIENT)
    protected abstract E readHolderFromSyncData(PacketBuffer syncData);
    protected abstract void writeHolderToSyncData(PacketBuffer syncData, E holder);

}
