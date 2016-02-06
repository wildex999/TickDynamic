package com.wildex999.tickdynamic.commands;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.wildex999.tickdynamic.TickDynamicMod;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public class CommandHandler implements ICommand {

	private List<String> aliases;
	private Map<String, ICommand> subCommandHandlers;
	private String listSubCommands;
	private TickDynamicMod mod;
	
	public enum SubCommands {
		tps,
		listworlds,
		world,
		identify,
		reload,
		reloadgroups,
		enabled,
		help
	}
	
	public CommandHandler(TickDynamicMod mod) {
		this.mod = mod;
		
		aliases = new ArrayList<String>();
		aliases.add("tickdynamic");
		aliases.add("td");
		
		subCommandHandlers = new HashMap<String, ICommand>();
		subCommandHandlers.put("reload", new CommandReload(mod));
		subCommandHandlers.put("reloadgroups", new CommandReloadGroups(mod));
		subCommandHandlers.put("listworlds", new CommandListWorlds(mod));
		subCommandHandlers.put("world", new CommandWorld(mod));
		subCommandHandlers.put("enabled", new CommandEnabled(mod));
		
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
		if(args.length == 0)
		{
			sender.addChatMessage(new ChatComponentText("Usage: " + getCommandUsage(sender)));
			return;
		}
		
		if(args[0].equals("tps"))
		{
			
			sender.addChatMessage(new ChatComponentText("Average TPS: " + getTPSFormatted(mod) + " TPS"));
			return;
		} else if(args[0].equals("identify")) {
			sender.addChatMessage(new ChatComponentText("Command not yet implemented! This will allow you to check what group a Tile or Entity belongs to by right clicking it.(And other info, like TPS)"));
			return;
		} else if(args[0].equals("help")) {
			sender.addChatMessage(new ChatComponentText("You can find the documentation over at http://mods.stjerncraft.com/tickdynamic"));
			return;
		}
		
		//Send it over to subCommand handler
		ICommand subHandler = subCommandHandlers.get(args[0]);
		if(subHandler == null)
		{
			sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "No handler for the command " + EnumChatFormatting.ITALIC + args[0]));
			return;
		}
		subHandler.processCommand(sender, args);
	}


	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return sender.canCommandSenderUseCommand(1, getCommandName());
	}

	@Override
	public List addTabCompletionOptions(ICommandSender sender, String[] args) {
		if(args.length == 1)
		{
			List listOut = new LinkedList();
			String lastArg = args[args.length-1];
			SubCommands[] subCommands = SubCommands.values();
			for(SubCommands command : subCommands)
			{
				if(command.toString().contains(lastArg))
					listOut.add(command.toString());
			}
			
			return listOut;
		}
		else
		{
			//Send it over to subCommand handler
			ICommand subHandler = subCommandHandlers.get(args[0]);
			if(subHandler == null)
				return null;
			return subHandler.addTabCompletionOptions(sender, args);
		}
	}

	@Override
	public boolean isUsernameIndex(String[] args, int index) {
		//TODO: Pass on to subCommand
		return false;
	}
	
	@Override
	public int compareTo(Object arg0) {
		return 0;
	}
	
	public static String getTPSFormatted(TickDynamicMod mod) {
		String tpsOut;
		String color;
		
		if(mod.averageTPS >= 19)
			color = EnumChatFormatting.GREEN.toString();
		else if(mod.averageTPS > 10)
			color = EnumChatFormatting.YELLOW.toString();
		else
			color = EnumChatFormatting.RED.toString();
		
		DecimalFormat tpsFormat = new DecimalFormat("#.00");
		tpsOut = color + tpsFormat.format(mod.averageTPS) + EnumChatFormatting.RESET;
		return tpsOut;
	}

}
