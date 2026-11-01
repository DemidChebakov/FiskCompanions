package com.dchebakov.fiskcompanions.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import com.dchebakov.fiskcompanions.CommonProxy;
import com.dchebakov.fiskcompanions.world.CompanionSettings;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Client → server: change the world's friendly-fire rules (singleplayer host or ops only). */
public class PacketUpdateSettings implements IMessage {

    private boolean fightBackPlayers;
    private boolean allyFriendlyFire;

    public PacketUpdateSettings() {}

    public PacketUpdateSettings(boolean fightBackPlayers, boolean allyFriendlyFire) {
        this.fightBackPlayers = fightBackPlayers;
        this.allyFriendlyFire = allyFriendlyFire;
    }

    public static boolean canEdit(EntityPlayerMP player) {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && server.isSinglePlayer() || player.canCommandSenderUseCommand(2, "fiskcompanions");
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        fightBackPlayers = buf.readBoolean();
        allyFriendlyFire = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(fightBackPlayers);
        buf.writeBoolean(allyFriendlyFire);
    }

    public static class Handler implements IMessageHandler<PacketUpdateSettings, IMessage> {

        @Override
        public IMessage onMessage(PacketUpdateSettings msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (!canEdit(player)) {
                player.addChatMessage(new ChatComponentTranslation("fiskcompanions.msg.no_permission"));
                return null;
            }
            CompanionSettings.get(player.worldObj)
                .set(msg.fightBackPlayers, msg.allyFriendlyFire);
            for (Object o : MinecraftServer.getServer()
                .getConfigurationManager().playerEntityList) {
                EntityPlayerMP p = (EntityPlayerMP) o;
                CommonProxy.network.sendTo(PacketSettingsSync.of(p), p);
            }
            return null;
        }
    }
}
