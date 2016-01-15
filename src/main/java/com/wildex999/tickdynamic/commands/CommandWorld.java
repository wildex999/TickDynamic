package com.wildex999.tickdynamic.commands;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.wildex999.tickdynamic.TickDynamicMod;
import com.wildex999.tickdynamic.listinject.EntityGroup;
import com.wildex999.tickdynamic.listinject.ListManager;
import com.wildex999.tickdynamic.timemanager.TimeManager;
import com.wildex999.tickdynamic.timemanager.TimedEntities;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

public class CommandWorld implements ICommand {

	private TickDynamicMod mod;
	private int borderWidth;
	private World world;
	
	private int rowsPerPage = 6;
	private int currentPage;
	private int maxPages;
	
	private final DecimalFormat decimalFormat = new DecimalFormat("#.00");
	private final String formatCode = "\u00a7"; //Ignore this when counting length
	
	public CommandWorld(TickDynamicMod mod) {
		this.mod = mod;
		this.borderWidth = 50;
	}

	@Override
	public String getCommandName() {
		return "tickdynamic world";
	}

	@Override
	public String getCommandUsage(ICommandSender p_71518_1_) {
		return "tickdynamic world (world dim) [page]";
	}

	@Override
	public List getCommandAliases() {
		return null;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		if(args.length <= 1)
		{
			sender.addChatMessage(new ChatComponentText("Usage: " + getCommandUsage(sender)));
			return;
		}
		
		StringBuilder outputBuilder = new StringBuilder();
		currentPage = 1;
		maxPages = 0;
		
		//Get current page if set
		if(args.length == 3)
		{
			try {
			currentPage = Integer.parseInt(args[2]);
			if(currentPage <= 0)
			{
				sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Page number must be 1 and up, got: " + args[2]));
				currentPage = 1;
			}
			} catch(Exception e) {
				sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Expected a page number, got: " + args[2]));
				return;
			}
		}
		
		//Get world by dim
		//TODO: Allow passing in world name(And add auto-complete for it)
		String worldDimStr = args[1];
		int worldDim;
		if(worldDimStr.startsWith("dim"))
			worldDimStr = worldDimStr.substring(3);
		try {
			worldDim = Integer.parseInt(worldDimStr);
		} catch(Exception e) {
			sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Expected a world dimension(Ex: dim0 or just 0), got: " + worldDimStr));
			return;
		}
		world = DimensionManager.getWorld(worldDim);
		
		if(world == null)
		{
			sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "No world with dimension id: " + worldDimStr));
			return;
		}
		
		
		writeHeader(outputBuilder);
		
		int extraCount = 2; //External and Other
		int listSize = mod.server.worldServers.length + extraCount;
		if(listSize > rowsPerPage)
			maxPages = (int)Math.ceil(listSize/(float)rowsPerPage);
		else
			maxPages = 1;
		
		if(currentPage > maxPages)
			currentPage = maxPages;
		
		//Get groups from world
		List<EntityGroup> groups = new ArrayList<EntityGroup>();
		if(world.loadedEntityList instanceof ListManager)
			addGroupsFromList(groups, (ListManager)world.loadedEntityList);
		if(world.loadedTileEntityList instanceof ListManager)
			addGroupsFromList(groups, (ListManager)world.loadedTileEntityList);
		
		//Sort the groups, so we don't just have Entities followed by TileEntities
		//TODO: More sorting options
		Collections.sort(groups, new Comparator(){
			public int compare(Object o1, Object o2) {
				EntityGroup group1 = (EntityGroup)o1;
				EntityGroup group2 = (EntityGroup)o2;
				return group1.getName().compareTo(group2.getName());
			}
		});
		
		//Write stats
		int toSkip = (currentPage-1) * rowsPerPage;
		for(EntityGroup group : groups) {
			//Skip for pages
			if(toSkip-- > 0)
				continue;
			
			writeGroup(outputBuilder, group);
		}
		
		writeFooter(outputBuilder);
		
		splitAndSend(sender, outputBuilder);
	}
	
	private void addGroupsFromList(List<EntityGroup> targetList, ListManager worldList) {
		Iterator<EntityGroup> it = worldList.getGroupIterator();
		while(it.hasNext())
			targetList.add(it.next());
	}

	private void writeHeader(StringBuilder builder) {
		builder.append(EnumChatFormatting.GREEN + "Groups for world: ").append(EnumChatFormatting.RESET + world.provider.getDimensionName()).
				append("(DIM: ").append(world.provider.dimensionId).append(")\n");
		
		builder.append(EnumChatFormatting.GRAY + "+" + StringUtils.repeat("=", borderWidth) + "+\n");
		builder.append(EnumChatFormatting.GRAY + "| ").append(EnumChatFormatting.GOLD + "Group").append(EnumChatFormatting.GRAY);
		
		builder.append(" || " ).append(EnumChatFormatting.GOLD + "Time(Avg.)").append(EnumChatFormatting.GRAY);
		builder.append(" || " ).append(EnumChatFormatting.GOLD + "EntitiesRun(Avg.)").append(EnumChatFormatting.GRAY);
		builder.append(" || " ).append(EnumChatFormatting.GOLD + "MaxSlices").append(EnumChatFormatting.GRAY);
		builder.append(" || " ).append(EnumChatFormatting.GOLD + "TPS(Avg.)").append(EnumChatFormatting.GRAY);
		builder.append("\n");
	}
	
	private void writeGroup(StringBuilder builder, EntityGroup group) {
		TimedEntities timedGroup = group.timedGroup;
		builder.append(EnumChatFormatting.GRAY + "| ").append(EnumChatFormatting.RESET + group.getName());
		
		if(timedGroup == null)
		{ //No Timed data
			builder.append(EnumChatFormatting.RED + " N/A\n");
			return;
		}
		
		String usedTime = decimalFormat.format(timedGroup.getTimeUsedAverage()/(double)TimeManager.timeMilisecond);
		String maxTime = decimalFormat.format(timedGroup.getTimeMax()/(double)TimeManager.timeMilisecond);
		builder.append(EnumChatFormatting.GRAY + " || ").append(EnumChatFormatting.RESET).append(usedTime).append("/").append(maxTime);
		
		int runObjects = timedGroup.getObjectsRunAverage();
		int countObjects = group.entities.size();
		builder.append(EnumChatFormatting.GRAY + " || ").append(EnumChatFormatting.RESET).append(runObjects).append("/").append(countObjects);
		builder.append(EnumChatFormatting.GRAY + " || ").append(EnumChatFormatting.RESET).append(timedGroup.getSliceMax());
		
		//TPS coloring
		String color;
		if(timedGroup.averageTPS >= 19)
			color = EnumChatFormatting.GREEN.toString();
		else if(timedGroup.averageTPS > 10)
			color = EnumChatFormatting.YELLOW.toString();
		else
			color = EnumChatFormatting.RED.toString();
		builder.append(EnumChatFormatting.GRAY + " || ").append(color).append(decimalFormat.format(timedGroup.averageTPS)).append(EnumChatFormatting.RESET + "TPS");
		
		builder.append("\n");
	}
	
	private void writeFooter(StringBuilder builder) {
		if(maxPages == 0)
			builder.append(EnumChatFormatting.GRAY + "+" + StringUtils.repeat("=", borderWidth) + "+\n");
		else
		{
			String pagesStr = EnumChatFormatting.GREEN + "Page " + currentPage + "/" + maxPages;
			int pagesLength = getVisibleLength(pagesStr);
			int otherLength = borderWidth - pagesLength;
			builder.append(EnumChatFormatting.GRAY + "+" + StringUtils.repeat("=", otherLength/2));
			builder.append(pagesStr);
			builder.append(EnumChatFormatting.GRAY + StringUtils.repeat("=", otherLength/2) + "+\n");
		}
	}
	
	public int getVisibleLength(String str) {
		return (str.length() - (StringUtils.countMatches(str, formatCode)*2));
	}
	
	public void splitAndSend(ICommandSender sender, StringBuilder outputBuilder) {
		//Split newline and send
		String[] chatLines = outputBuilder.toString().split("\n");
		for(String chatLine : chatLines)
			sender.addChatMessage(new ChatComponentText(chatLine));
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return sender.canCommandSenderUseCommand(1, getCommandName());
	}

	@Override
	public List addTabCompletionOptions(ICommandSender p_71516_1_,
			String[] p_71516_2_) {
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
