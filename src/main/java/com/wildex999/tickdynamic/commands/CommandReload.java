package com.wildex999.tickdynamic.commands;

import java.util.List;

import com.wildex999.tickdynamic.TickDynamicMod;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

public class CommandReload implements ICommand {

	private TickDynamicMod mod;
	
	public CommandReload(TickDynamicMod mod) {
		this.mod = mod;
	}
	

	@Override
	public String getName() {
		return "tickdynamic reload";
	}

	@Override
	public String getCommandUsage(ICommandSender p_71518_1_) {
		return "tickdynamic reload";
	}

	@Override
	public List getAliases() {
		return null;
	}

	@Override
	public void execute(ICommandSender sender, String[] args) {
		sender.addChatMessage(new ChatComponentText("Reloading configuration..."));
		mod.loadConfig(true);
		sender.addChatMessage(new ChatComponentText("Configuration reloaded!"));
	}

	@Override
	public boolean canCommandSenderUse(ICommandSender sender) {
		return sender.canUseCommand(1, getName());
	}
	
	@Override
	public List addTabCompletionOptions(ICommandSender sender, String[] args,BlockPos pos) {
		return null;
	}

	@Override
	public boolean isUsernameIndex(String[] p_82358_1_, int p_82358_2_) {
		return false;
	}
	
	@Override
	public int compareTo(Object arg0) {
		return 0;
	}

}
