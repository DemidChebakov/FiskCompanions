package com.dchebakov.fiskcompanions.debug;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

/** {@code /fcselftest} — runs {@link SelfTest} in the overworld around spawn (ops only). */
public class CommandSelfTest extends CommandBase {

    @Override
    public String getCommandName() {
        return "fcselftest";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/fcselftest";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        boolean started = SelfTest.start(MinecraftServer.getServer(), false);
        sender.addChatMessage(
            new ChatComponentText(
                started ? "Fisk Companions self-test started; results in the server log"
                    : "Self-test already running"));
    }
}
