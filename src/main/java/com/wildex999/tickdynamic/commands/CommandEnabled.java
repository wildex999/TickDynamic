package com.wildex999.tickdynamic.commands;

import java.util.ArrayList;
import java.util.List;

import com.wildex999.tickdynamic.TickDynamicMod;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class CommandEnabled implements ICommand{

	private TickDynamicMod mod;
	private List listYes;
	private List listNo;
	
	public CommandEnabled(TickDynamicMod mod) {
		this.mod = mod;
		
		listYes = new ArrayList();
		listYes.add("yes");
		listNo = new ArrayList();
		listNo.add("no");
	}
	
	@Override
	public String getName() {
		return "tickdynamic enabled";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "tickdynamic enabled [yes, y, no, n]";
	}

	@Override
	public List getAliases() {
		return null;
	}

	@Override
	public void execute(ICommandSender sender, String[] args) {
		if(args.length == 1)
		{
			if(mod.enabled)
				sender.addChatMessage(new ChatComponentText("Tick Dynamic is currently " + EnumChatFormatting.GREEN + " Enabled!"));
			else
				sender.addChatMessage(new ChatComponentText("Tick Dynamic is currently " + EnumChatFormatting.RED + " Disabled!"));
			sender.addChatMessage(new ChatComponentText("Usage: " + getCommandUsage(sender)));
			return;
		}
		
		if(args[1].equals("yes") || args[1].equals("y"))
		{
			if(mod.enabled)
			{
				sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "Tick Dynamic is already enabled!"));
				return;
			}
			mod.enabled = true;
			sender.addChatMessage(new ChatComponentText("Tick Dynamic is now " + EnumChatFormatting.GREEN + "Enabled!"));
			return;
		}
		else if(args[1].equals("no") || args[1].equals("n"))
		{
			if(!mod.enabled)
			{
				sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Tick Dynamic is already disabled!"));
				return;
			}
			mod.enabled = false;
			sender.addChatMessage(new ChatComponentText("Tick Dynamic is now " + EnumChatFormatting.RED + "Disabled!"));
			return;
		}
		
		sender.addChatMessage(new ChatComponentText("Unrecognized argument: " + args[1]));
	}

	@Override
	public boolean canCommandSenderUse(ICommandSender sender) {
		return sender.canUseCommand(1, getName());
	}
	
	@Override
	public List addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
		if(args[args.length-1].startsWith("y"))
			return listYes;
		else if(args[args.length-1].startsWith("n"))
			return listNo;
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
