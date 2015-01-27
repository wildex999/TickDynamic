package com.wildex999.tickdynamic.commands;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

public class CommandHandler implements ICommand {

	private List<String> aliases;
	private String listSubCommands;
	
	public enum SubCommands {
		tps,
		list,
		world,
		reload,
		enabled
	}
	
	public CommandHandler() {
		aliases = new ArrayList<String>();
		aliases.add("tickdynamic");
		aliases.add("td");
		
		StringBuilder builderSubCommands = new StringBuilder();
		SubCommands[] subs = SubCommands.values();
		for(SubCommands command : subs) {
			builderSubCommands.append(command).append(", ");
		}
		builderSubCommands.delete(builderSubCommands.length()-2, builderSubCommands.length());
		listSubCommands = builderSubCommands.toString();
	}

	@Override
	public String getCommandName() {
		return "tickdynamic";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "tickdynamic [" + listSubCommands + "]";
	}

	@Override
	public List getCommandAliases() {
		return aliases;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return sender.canCommandSenderUseCommand(1, getCommandName());
	}

	@Override
	public List addTabCompletionOptions(ICommandSender p_71516_1_,
			String[] p_71516_2_) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isUsernameIndex(String[] p_82358_1_, int p_82358_2_) {
		// TODO Auto-generated method stub
		return false;
	}
	
	@Override
	public int compareTo(Object arg0) {
		return 0;
	}

}
