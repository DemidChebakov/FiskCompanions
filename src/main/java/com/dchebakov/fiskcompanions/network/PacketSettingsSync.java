package com.dchebakov.fiskcompanions.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.dchebakov.fiskcompanions.world.CompanionSettings;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Server → client: the world's friendly-fire rules and whether this player may change them (for the Settings tab). */
public class PacketSettingsSync implements IMessage {

    /** Last values received; read by the spawner GUI. */
    public static volatile boolean clientFightBackPlayers, clientAllyFriendlyFire, clientCanEdit;

    private boolean fightBackPlayers, allyFriendlyFire, canEdit;

    public PacketSettingsSync() {}

    public static PacketSettingsSync of(EntityPlayerMP player) {
        CompanionSettings s = CompanionSettings.get(player.worldObj);
        PacketSettingsSync p = new PacketSettingsSync();
        p.fightBackPlayers = s.fightBackPlayers;
        p.allyFriendlyFire = s.allyFriendlyFire;
        p.canEdit = PacketUpdateSettings.canEdit(player);
        return p;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        fightBackPlayers = buf.readBoolean();
        allyFriendlyFire = buf.readBoolean();
        canEdit = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(fightBackPlayers);
        buf.writeBoolean(allyFriendlyFire);
        buf.writeBoolean(canEdit);
    }

    public static class Handler implements IMessageHandler<PacketSettingsSync, IMessage> {

        @Override
        public IMessage onMessage(PacketSettingsSync msg, MessageContext ctx) {
            clientFightBackPlayers = msg.fightBackPlayers;
            clientAllyFriendlyFire = msg.allyFriendlyFire;
            clientCanEdit = msg.canEdit;
            return null;
        }
    }
}
