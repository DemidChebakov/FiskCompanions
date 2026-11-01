package com.dchebakov.fiskcompanions.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.BaseSkin;
import com.dchebakov.fiskcompanions.item.ItemHeroConfigurator;
import com.dchebakov.fiskcompanions.world.Spawning;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Client → server: spawn one or more characters from the spawner menu (single hero x count, or a squad). */
public class PacketSpawnHeroes implements IMessage {

    private final List<Spawning.Entry> entries = new ArrayList<>();
    private boolean bindToSender;

    public PacketSpawnHeroes() {}

    public PacketSpawnHeroes(List<Spawning.Entry> entries, boolean bindToSender) {
        this.entries.addAll(entries.subList(0, Math.min(entries.size(), Spawning.MAX_PER_REQUEST)));
        this.bindToSender = bindToSender;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        bindToSender = buf.readBoolean();
        int n = Math.min(buf.readUnsignedByte(), Spawning.MAX_PER_REQUEST);
        for (int i = 0; i < n; ++i) {
            String key = ByteBufUtils.readUTF8String(buf);
            Alignment alignment = Alignment.byId(buf.readByte());
            entries.add(new Spawning.Entry(key, alignment, BaseSkin.byId(buf.readByte())));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(bindToSender);
        buf.writeByte(entries.size());
        for (Spawning.Entry e : entries) {
            ByteBufUtils.writeUTF8String(buf, e.heroKey);
            buf.writeByte(e.alignment.ordinal());
            buf.writeByte(e.skin.ordinal());
        }
    }

    public static class Handler implements IMessageHandler<PacketSpawnHeroes, IMessage> {

        @Override
        public IMessage onMessage(PacketSpawnHeroes msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ItemStack held = player.getCurrentEquippedItem();
            if (held == null || !(held.getItem() instanceof ItemHeroConfigurator) || msg.entries.isEmpty()) {
                return null;
            }
            int n = Spawning.spawn(player, msg.entries, msg.bindToSender);
            player.addChatMessage(new ChatComponentTranslation("fiskcompanions.msg.spawned", n));
            return null;
        }
    }
}
