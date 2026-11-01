package com.dchebakov.fiskcompanions.network;

import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.dchebakov.fiskcompanions.item.ItemHeroConfigurator;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → server: remove every character in the player's world that the player may remove: unowned ones and their
 * own; in Creative, singleplayer or as an op, all of them. Suits are never dropped.
 */
public class PacketRemoveAll implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketRemoveAll, IMessage> {

        @Override
        public IMessage onMessage(PacketRemoveAll msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ItemStack held = player.getCurrentEquippedItem();
            if (held == null || !(held.getItem() instanceof ItemHeroConfigurator)) {
                return null;
            }
            boolean all = player.capabilities.isCreativeMode || PacketUpdateSettings.canEdit(player);
            int removed = 0;
            for (Object o : new ArrayList<Object>(player.worldObj.loadedEntityList)) {
                if (!(o instanceof EntityFiskHero)) {
                    continue;
                }
                EntityFiskHero hero = (EntityFiskHero) o;
                UUID owner = hero.getOwnerId();
                if (all || owner == null || owner.equals(player.getUniqueID())) {
                    hero.setDead();
                    ++removed;
                }
            }
            player.addChatMessage(new ChatComponentTranslation("fiskcompanions.msg.removed", removed));
            return null;
        }
    }
}
