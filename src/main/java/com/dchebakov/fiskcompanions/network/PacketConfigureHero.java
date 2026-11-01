package com.dchebakov.fiskcompanions.network;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.dchebakov.fiskcompanions.FiskCompanions;
import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.dchebakov.fiskcompanions.item.ItemHeroConfigurator;
import com.fiskmods.heroes.common.hero.HeroIteration;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Client → server: apply the configurator screen to a hero mob. Everything is re-validated on the server. */
public class PacketConfigureHero implements IMessage {

    private static final double MAX_REACH_SQ = 8.0D * 8.0D;

    private int entityId;
    private String heroKey;
    private int alignment;
    private boolean bindToSender;

    public PacketConfigureHero() {}

    public PacketConfigureHero(EntityFiskHero hero, String heroKey, Alignment alignment, boolean bindToSender) {
        this.entityId = hero.getEntityId();
        this.heroKey = heroKey;
        this.alignment = alignment.ordinal();
        this.bindToSender = bindToSender;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        heroKey = ByteBufUtils.readUTF8String(buf);
        alignment = buf.readByte();
        bindToSender = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        ByteBufUtils.writeUTF8String(buf, heroKey);
        buf.writeByte(alignment);
        buf.writeBoolean(bindToSender);
    }

    public static class Handler implements IMessageHandler<PacketConfigureHero, IMessage> {

        @Override
        public IMessage onMessage(PacketConfigureHero msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            Entity entity = player.worldObj.getEntityByID(msg.entityId);
            ItemStack held = player.getCurrentEquippedItem();
            if (!(entity instanceof EntityFiskHero) || held == null
                || !(held.getItem() instanceof ItemHeroConfigurator)
                || player.getDistanceSqToEntity(entity) > MAX_REACH_SQ) {
                FiskCompanions.LOG.info(
                    "Ignored configure request from {}: entity={} held={} distSq={}",
                    player.getCommandSenderName(),
                    entity,
                    held,
                    entity != null ? player.getDistanceSqToEntity(entity) : -1);
                return null;
            }
            EntityFiskHero hero = (EntityFiskHero) entity;
            if (hero.getOwnerId() != null && !hero.getOwnerId()
                .equals(player.getUniqueID()) && !player.capabilities.isCreativeMode) {
                player.addChatMessage(new ChatComponentTranslation("fiskcompanions.msg.not_owner"));
                return null;
            }
            HeroIteration iter = FiskBridge.lookup(msg.heroKey);
            if (iter != null && !msg.heroKey.equals(hero.getHeroKey())) {
                hero.setHero(iter);
            }
            hero.setAlignment(Alignment.byId(msg.alignment));
            if (msg.bindToSender) {
                hero.setOwnerId(player.getUniqueID());
            }
            return null;
        }
    }
}
