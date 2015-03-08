package com.wildex999.tickdynamic.commands;

import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.wildex999.tickdynamic.TickDynamicMod;
import com.wildex999.tickdynamic.commands.CommandHandler.SubCommands;
import com.wildex999.tickdynamic.timemanager.ITimed;
import com.wildex999.tickdynamic.timemanager.TimeManager;
import com.wildex999.tickdynamic.timemanager.TimedGroup;
import com.wildex999.tickdynamic.timemanager.TimedEntities;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;

public class CommandList implements ICommand {

	private TickDynamicMod mod;
	private String[] commands = {"dimnames", "time", "entitiesrun", "tps", "maxslices", "minimumentities"};
	String commandList;
	
	private String formatCode = "\u00a7"; //Ignore this when counting length
	private String rc = " "; //RepeatChar
	private int maxWorldNameWidth; //Max width of the world name
	private int maxWorldWidth; //World data width
	private int maxTileWidth;
	private int maxEntityWidth;
	private boolean gotWorldData;
	
	private int rowsPerPage = 6;
	private int currentPage;
	private int maxPages;
	
	private class ListData {
		public String worldName;
		public String worldData;
		public String tileData;
		public String entityData;
	}
	
	public CommandList(TickDynamicMod mod) {
		this.mod = mod;
		gotWorldData = false;
		
		StringBuilder commandListBuilder = new StringBuilder();
		for(String command : commands)
			commandListBuilder.append(command).append(", ");
		commandListBuilder.delete(commandListBuilder.length()-2, commandListBuilder.length());
		commandList = commandListBuilder.toString();
	}
	
	@Override
	public String getCommandName() {
		return "tickdynamic list";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "tickdynamic list [" + commandList + "] [page]";
	}

	@Override
	public List getCommandAliases() {
		return null;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		if(args.length == 1)
		{
			sender.addChatMessage(new ChatComponentText("Usage: " + getCommandUsage(sender)));
			return;
		}
		
		//Clear per command
		maxWorldNameWidth = 5;
		maxWorldWidth = 10;
		maxTileWidth = 12;
		maxEntityWidth = 8;
		gotWorldData = false;
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
		
		StringBuilder outputBuilder = new StringBuilder();
		
		//Generate data
		LinkedList<ListData> listData;
		if(args[1].equals("time"))
			listData = getTimeData();
		else if(args[1].equals("tps"))
			listData = getTPSData();
		else if(args[1].equals("entitiesrun"))
			listData = getEntitiesRunData();
		else if(args[1].equals("maxslices"))
			listData = getMaxSlicesData();
		else if(args[1].equals("minimumentities"))
			listData = getMinimumObjectsData();
		else if(args[1].equals("dimnames"))
		{
			writeDimNames(outputBuilder);
			splitAndSend(sender, outputBuilder);
			return;
		}
		else
		{
			sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "No handler for the subCommand " + EnumChatFormatting.ITALIC + args[1]));
			return;
		}
		
		if(!gotWorldData)
			maxWorldWidth = 0;
		if(listData.size() > rowsPerPage)
			maxPages = (int)Math.ceil(listData.size()/(float)rowsPerPage);
		else
			maxPages = 1;
		
		if(currentPage > maxPages)
			currentPage = maxPages;
		
		//Write header
		writeHeader(outputBuilder, args[1]);
		
		//Write data
		int pageOffset = (currentPage-1)*rowsPerPage;
		int rowsLeft = rowsPerPage;
		for(Iterator<ListData> it = listData.listIterator(pageOffset); it.hasNext(); ) {
			ListData data = it.next();
			
			outputBuilder.append(EnumChatFormatting.GRAY + "| ").append(EnumChatFormatting.RESET + data.worldName)
				.append(EnumChatFormatting.GRAY + StringUtils.repeat(rc, maxWorldNameWidth-getVisibleLength(data.worldName)));
			if(gotWorldData)
				outputBuilder.append("|| ").append(EnumChatFormatting.RESET + data.worldData)
					.append(EnumChatFormatting.GRAY + StringUtils.repeat(rc, maxWorldWidth-getVisibleLength(data.worldData)));
			outputBuilder.append("|| ").append(EnumChatFormatting.RESET + data.tileData)
				.append(EnumChatFormatting.GRAY + StringUtils.repeat(rc, maxTileWidth-getVisibleLength(data.tileData)));
			outputBuilder.append("|| ").append(EnumChatFormatting.RESET + data.entityData)
				.append(EnumChatFormatting.GRAY + StringUtils.repeat(rc, maxEntityWidth-getVisibleLength(data.entityData)));
			outputBuilder.append(" |\n");
			
			if(--rowsLeft <= 0)
				break;
		}
		
		//Write footer
		writeFooter(outputBuilder, args[1]);
		
		
		splitAndSend(sender, outputBuilder);
	}
	
	public void writeHeader(StringBuilder builder, String command) {
		if(command.equals("time"))
			builder.append(EnumChatFormatting.GREEN + "Time used / Time given (In Milliseconds)\n");
		else if(command.equals("tps"))
			builder.append("Server Avg. TPS: ").append(CommandHandler.getTPSFormatted(mod)).append("\n");
		else if(command.equals("entitiesrun"))
			builder.append(EnumChatFormatting.GREEN + "Average number of Entities that update per tick\n");
		else if(command.equals("maxslices"))
			builder.append(EnumChatFormatting.GREEN + "Slices for the different worlds and groups from config\n");
		else if(command.equals("minimumentities"))
			builder.append(EnumChatFormatting.GREEN + "Minimum number of entities to run, from config\n");
		
		builder.append(EnumChatFormatting.GRAY + "+" + StringUtils.repeat("=", (maxWorldNameWidth+maxWorldWidth+maxTileWidth+maxEntityWidth+15)/2) + "+\n");
		builder.append(EnumChatFormatting.GRAY + "| ").append(EnumChatFormatting.RESET + "World").append(EnumChatFormatting.GRAY + StringUtils.repeat(rc, maxWorldNameWidth-5));
		
		if(gotWorldData)
			builder.append(" || ").append(EnumChatFormatting.RESET + "World Data").append(EnumChatFormatting.GRAY + StringUtils.repeat(rc, maxWorldWidth-10));
		
		builder.append(" || ").append(EnumChatFormatting.RESET + "TileEntities").append(EnumChatFormatting.GRAY + StringUtils.repeat(rc, maxTileWidth-12))
			.append(" || ").append(EnumChatFormatting.RESET + "Entities").append(EnumChatFormatting.GRAY + StringUtils.repeat(rc, maxEntityWidth-8))
			.append(" |\n");
	}
	
	public void writeFooter(StringBuilder builder, String command) {
		if(maxPages == 0)
			builder.append(EnumChatFormatting.GRAY + "+" + StringUtils.repeat("=", (maxWorldNameWidth+maxWorldWidth+maxTileWidth+maxEntityWidth+15)/2) + "+\n");
		else
		{
			String pagesStr = EnumChatFormatting.GREEN + "Page " + currentPage + "/" + maxPages;
			int pagesLength = getVisibleLength(pagesStr);
			int otherLength = ((maxWorldNameWidth+maxWorldWidth+maxTileWidth+maxEntityWidth+15)/2) - pagesLength;
			builder.append(EnumChatFormatting.GRAY + "+" + StringUtils.repeat("=", otherLength/2));
			builder.append(pagesStr);
			builder.append(EnumChatFormatting.GRAY + StringUtils.repeat("=", otherLength/2) + "+\n");
		}
	}
	
	
	public LinkedList<ListData> getTimeData() {
		LinkedList<ListData> dataList = new LinkedList<ListData>();
		TimeManager currentManager = mod.root;
		DecimalFormat decimalFormat = new DecimalFormat("#.00");
		
		gotWorldData = true;
		
		ListData newData;
		//Go through worlds in root
		for(ITimed timed : currentManager.getChildren())
		{
			if(!timed.isManager())
				continue;
			newData = new ListData();
			TimeManager manager = (TimeManager)timed;
			
			//World name
			if(manager.world != null)
			{
				if(manager.world.isRemote)
					continue; //Ignore client world
				newData.worldName = "DIM" + manager.world.provider.dimensionId;
			}
			else
				newData.worldName = "NULL";
			
			//World time data
			newData.worldData = decimalFormat.format(manager.getTimeUsedAverage()/(double)manager.timeMilisecond) + " / " 
			+ decimalFormat.format(manager.getTimeMax()/(double)manager.timeMilisecond);
			
			//TileEntities time data
			TimedEntities timedTile = mod.getWorldTileEntities(manager.world).getTimedGroup();
			newData.tileData = decimalFormat.format(timedTile.getTimeUsedAverage()/(double)timedTile.timeMilisecond) + " / " 
					+ decimalFormat.format(timedTile.getTimeMax()/(double)timedTile.timeMilisecond);
			
			//Entities time data
			TimedEntities timedEntity = mod.getWorldEntities(manager.world).getTimedGroup();
			newData.entityData = decimalFormat.format(timedEntity.getTimeUsedAverage()/(double)timedEntity.timeMilisecond) + " / " 
					+ decimalFormat.format(timedEntity.getTimeMax()/(double)timedEntity.timeMilisecond);
			
			//Max length update
			int length = getVisibleLength(newData.worldName); //Ignore formating
			if(maxWorldNameWidth < length)
				maxWorldNameWidth = length;
			length = getVisibleLength(newData.worldData);
			if(maxWorldWidth < length)
				maxWorldWidth = length;
			length = getVisibleLength(newData.tileData);
			if(maxTileWidth < length)
				maxTileWidth = length;
			length = getVisibleLength(newData.entityData);
			if(maxEntityWidth < length)
				maxEntityWidth = length;
			
			dataList.add(newData);
		}
		
		//Add Other
		TimedGroup other = mod.getTimedGroup("other");
		if(other != null)
		{
			newData = new ListData();
			newData.worldName = "(Other)";
			newData.tileData = "N/A";
			newData.entityData = "N/A";
			newData.worldData = decimalFormat.format(other.getTimeUsedAverage()/(double)other.timeMilisecond) + "ms";
			dataList.add(newData);
		}
		
		//Add External
		TimedGroup external = mod.getTimedGroup("external");
		if(other != null)
		{
			newData = new ListData();
			newData.worldName = "(External)";
			newData.tileData = "N/A";
			newData.entityData = "N/A";
			newData.worldData = decimalFormat.format(external.getTimeUsedAverage()/(double)external.timeMilisecond) + "ms";
			dataList.add(newData);
		}
		
		return dataList;
	}
	
	public LinkedList<ListData> getTPSData() {
		LinkedList<ListData> dataList = new LinkedList<ListData>();
		TimeManager currentManager = mod.root;
		DecimalFormat decimalFormat = new DecimalFormat("#.00");
		
		ListData newData;
		//Go through worlds in root
		for(ITimed timed : currentManager.getChildren())
		{
			if(!timed.isManager())
				continue;
			newData = new ListData();
			TimeManager manager = (TimeManager)timed;
			
			//World name
			if(manager.world != null)
			{
				if(manager.world.isRemote)
					continue; //Ignore client world
				newData.worldName = "DIM" + manager.world.provider.dimensionId;
			}
			else
				newData.worldName = "NULL";
			
			String color;
			

			
			//TileEntities TPS data
			TimedEntities timedTile = mod.getWorldTileEntities(manager.world).getTimedGroup();
			if(timedTile.averageTPS >= 19)
				color = EnumChatFormatting.GREEN.toString();
			else if(timedTile.averageTPS > 10)
				color = EnumChatFormatting.YELLOW.toString();
			else
				color = EnumChatFormatting.RED.toString();
			newData.tileData = color + decimalFormat.format(timedTile.averageTPS) + EnumChatFormatting.RESET + "TPS";
			
			//Entities time data
			TimedEntities timedEntity = mod.getWorldEntities(manager.world).getTimedGroup();
			if(timedEntity.averageTPS >= 19)
				color = EnumChatFormatting.GREEN.toString();
			else if(timedEntity.averageTPS > 10)
				color = EnumChatFormatting.YELLOW.toString();
			else
				color = EnumChatFormatting.RED.toString();
			newData.entityData = color + decimalFormat.format(timedEntity.averageTPS) + EnumChatFormatting.RESET + "TPS";
			
			//Max length update
			int length = getVisibleLength(newData.worldName);
			if(maxWorldNameWidth < length)
				maxWorldNameWidth = length;
			length = getVisibleLength(newData.tileData);
			if(maxTileWidth < length)
				maxTileWidth = length;
			length = getVisibleLength(newData.entityData);
			if(maxEntityWidth < length)
				maxEntityWidth = length;
			
			dataList.add(newData);
		}
		
		return dataList;
	}
	
	public LinkedList<ListData> getEntitiesRunData() {
		LinkedList<ListData> dataList = new LinkedList<ListData>();
		TimeManager currentManager = mod.root;
		
		ListData newData;
		//Go through worlds in root
		for(ITimed timed : currentManager.getChildren())
		{
			if(!timed.isManager())
				continue;
			newData = new ListData();
			TimeManager manager = (TimeManager)timed;
			
			//World name
			if(manager.world != null)
			{
				if(manager.world.isRemote)
					continue; //Ignore client world
				newData.worldName = "DIM" + manager.world.provider.dimensionId;
			}
			else
				newData.worldName = "NULL";
			
			//TileEntities TPS data
			TimedEntities timedTile = mod.getWorldTileEntities(manager.world).getTimedGroup();
			newData.tileData = "" + timedTile.getObjectsRunAverage();
			
			//Entities time data
			TimedEntities timedEntity = mod.getWorldEntities(manager.world).getTimedGroup();
			newData.entityData = "" + timedEntity.getObjectsRunAverage();
			
			//Max length update
			int length = getVisibleLength(newData.worldName);
			if(maxWorldNameWidth < length)
				maxWorldNameWidth = length;
			length = getVisibleLength(newData.tileData);
			if(maxTileWidth < length)
				maxTileWidth = length;
			length = getVisibleLength(newData.entityData);
			if(maxEntityWidth < length)
				maxEntityWidth = length;
			
			dataList.add(newData);
		}
		
		return dataList;
	}
	
	public LinkedList<ListData> getMaxSlicesData() {
		LinkedList<ListData> dataList = new LinkedList<ListData>();
		TimeManager currentManager = mod.root;
		
		gotWorldData = true;
		
		ListData newData;
		//Go through worlds in root
		for(ITimed timed : currentManager.getChildren())
		{
			if(!timed.isManager())
				continue;
			newData = new ListData();
			TimeManager manager = (TimeManager)timed;
			
			//World name
			if(manager.world != null)
			{
				if(manager.world.isRemote)
					continue; //Ignore client world
				newData.worldName = "DIM" + manager.world.provider.dimensionId;
			}
			else
				newData.worldName = "NULL";
			
			//World time data
			newData.worldData = "" + manager.getSliceMax();
			
			//TileEntities TPS data
			TimedEntities timedTile = mod.getWorldTileEntities(manager.world).getTimedGroup();
			newData.tileData = "" + timedTile.getSliceMax();
			
			//Entities time data
			TimedEntities timedEntity = mod.getWorldEntities(manager.world).getTimedGroup();
			newData.entityData = "" + timedEntity.getSliceMax();
			
			//Max length update
			int length = getVisibleLength(newData.worldName);
			if(maxWorldNameWidth < length)
				maxWorldNameWidth = length;
			length = getVisibleLength(newData.worldData);
			if(maxWorldWidth < length)
				maxWorldWidth = length;
			length = getVisibleLength(newData.tileData);
			if(maxTileWidth < length)
				maxTileWidth = length;
			length = getVisibleLength(newData.entityData);
			if(maxEntityWidth < length)
				maxEntityWidth = length;
			
			dataList.add(newData);
		}
		
		return dataList;
	}
	
	public LinkedList<ListData> getMinimumObjectsData() {
		LinkedList<ListData> dataList = new LinkedList<ListData>();
		TimeManager currentManager = mod.root;
		
		ListData newData;
		//Go through worlds in root
		for(ITimed timed : currentManager.getChildren())
		{
			if(!timed.isManager())
				continue;
			newData = new ListData();
			TimeManager manager = (TimeManager)timed;
			
			//World name
			if(manager.world != null)
			{
				if(manager.world.isRemote)
					continue; //Ignore client world
				newData.worldName = "DIM" + manager.world.provider.dimensionId;
			}
			else
				newData.worldName = "NULL";
			
			//TileEntities TPS data
			TimedEntities timedTile = mod.getWorldTileEntities(manager.world).getTimedGroup();
			newData.tileData = "" + timedTile.getMinimumObjects();
			
			//Entities time data
			TimedEntities timedEntity = mod.getWorldEntities(manager.world).getTimedGroup();
			newData.entityData = "" + timedEntity.getMinimumObjects();
			
			//Max length update
			int length = getVisibleLength(newData.worldName);
			if(maxWorldNameWidth < length)
				maxWorldNameWidth = length;
			length = getVisibleLength(newData.tileData);
			if(maxTileWidth < length)
				maxTileWidth = length;
			length = getVisibleLength(newData.entityData);
			if(maxEntityWidth < length)
				maxEntityWidth = length;
			
			dataList.add(newData);
		}
		
		return dataList;
	}
	
	public void writeDimNames(StringBuilder builder) {
		if(mod.server == null)
		{
			builder.append(EnumChatFormatting.RED + "Error: Server is null!");
			return;
		}
		
		for(WorldServer world : mod.server.worldServers)
		{
			String name = "NULL";
			String dimId = "NULL";
			if(world == null || world.isRemote)
				continue; //Ignore local remote
			if(world.provider != null)
			{
				dimId = ""+world.provider.dimensionId;
				name = world.provider.getDimensionName();
			}
			builder.append("DIM").append(dimId).append(": ").append(name).append("\n");
		}
	}
	
	public void splitAndSend(ICommandSender sender, StringBuilder outputBuilder) {
		//Split newline and send
		String[] chatLines = outputBuilder.toString().split("\n");
		for(String chatLine : chatLines)
			sender.addChatMessage(new ChatComponentText(chatLine));
	}
	
	public int getVisibleLength(String str) {
		return (str.length() - (StringUtils.countMatches(str, formatCode)*2));
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return sender.canCommandSenderUseCommand(1, getCommandName());
	}

	@Override
	public List addTabCompletionOptions(ICommandSender sender, String[] args) {
		if(args.length == 2)
		{
			List listOut = new LinkedList();
			String lastArg = args[args.length-1];
			for(String command : commands)
			{
				if(command.contains(lastArg))
					listOut.add(command.toString());
			}
			
			return listOut;
		}
		
		return null;
	}

	@Override
	public boolean isUsernameIndex(String[] p_82358_1_, int p_82358_2_) {
		// TODO Auto-generated method stub
		return false;
	}
	
	@Override
	public int compareTo(Object o) {
		// TODO Auto-generated method stub
		return 0;
	}

}
