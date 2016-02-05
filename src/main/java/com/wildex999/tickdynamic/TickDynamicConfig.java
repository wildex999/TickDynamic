package com.wildex999.tickdynamic;

import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.wildex999.tickdynamic.listinject.EntityGroup;
import com.wildex999.tickdynamic.listinject.EntityType;
import com.wildex999.tickdynamic.listinject.ListManager;
import com.wildex999.tickdynamic.timemanager.ITimed;
import com.wildex999.tickdynamic.timemanager.TimeManager;
import com.wildex999.tickdynamic.timemanager.TimedEntities;
import com.wildex999.tickdynamic.timemanager.TimedGroup;

public class TickDynamicConfig {
	
	public static void loadConfig(TickDynamicMod mod, boolean groups) {
		//mod.config.load();
    	mod.config = new Configuration(mod.config.getConfigFile());
		
    	//--GENERAL CONFIG--
		mod.config.getCategory("general");
		mod.config.setCategoryComment("general", "WEBSITE: http://mods.stjerncraft.com/tickdynamic   <- Head here for the documentation, if you have problems or if you have questions."
    			+ "\n\n"
    			+ "Slices are the way you control the time allotment to each world, and within each world, to Entities and TileEntities.\n"
    			+ "Each tick the time for a tick(By default 50ms) will be distributed among all the worlds, according to how many slices they have.\n"
    			+ "If you have 3 worlds, each with 100 slices, then each world will get 100/300 = ~33% of the time.\n"
    			+ "So you can thus give the Overworld a maxSlices of 300, while giving the other two 100 each. This way the Overworld will get 60% of the time.\n"
    			+ "\n"
    			+ "Of the time given to the world, this is further distributed to TileEntities and Entities according to their slices, the same way.\n"
    			+ "TileEntities and Entities are given a portion of the time first given to the world, so their slices are only relative to each other within that world."
    			+ "If any group has unused time, then that time will be distributed to the remaining groups.\n"
    			+ "So even if you give 1000 slices to TileEntities and 100 to Entities, as long as as TileEntities aren't using it's full time,\n"
    			+ "Entities will be able to use more than 100 slices of time.\n"
    			+ "\n"
    			+ "So the formula for slices to time percentage is: (singleGroupInWorld.maxSlices/combinedGroupsInWorld.maxSlices)*100\n"
    			+ "\n"
    			+ "Note: maxSlices = 0 has a special meaning. It means that the group's time usage is accounted for, but not limited.\n"
    			+ "Basically it can take all the time it needs, even if it goes above the parent maxTime, pushing its siblings down to minimumObjects.");
    	
		mod.enabled = mod.config.get("general", "enabled", true, "").getBoolean();
    	
		mod.debug = mod.config.get("general", "debug", mod.debug, "Debug output. Warning: Might output a lot of data to console!").getBoolean();
		
		mod.debugGroups = mod.config.get("general", "debugGroups", mod.debugGroups, "Debug Group mapping and assignment. Will spam during world load and config reload!!!").getBoolean();
		
		mod.debugTimer = mod.config.get("general", "debugTimer", mod.debugTimer, "Debug output from time allocation and calculation. Warning: Setting this to true will cause a lot of console spam.\n"
    			+ "Only do it if developer or someone else asks for the output!").getBoolean();
    	
		mod.defaultWorldSlicesMax = mod.config.get("general", "defaultWorldSlicesMax", mod.defaultWorldSlicesMax, "The default maxSlices for a new automatically added world.").getInt();
    	
		mod.defaultAverageTicks = mod.config.get("general", "averageTicks", mod.defaultAverageTicks, "How many ticks of data to use when averaging for time balancing.\n"
    			+ "A higher number will make it take regular spikes into account, however will make it slower to adjust to changes.").getInt();
    	
    	//-- WORLDS CONFIG --
		mod.defaultTickTime = mod.config.get("worlds", "tickTime", mod.defaultTickTime, "The time allotted to a tick in milliseconds. 20 Ticks per second means 50ms per tick.\n"
    			+ "This is the base time allotment it will use when balancing the time usage between worlds and objects.\n"
    			+ "You can set this to less than 50ms if you want to leave a bit of buffer time for other things, or don't want to use 100% cpu.").getInt();

    	//-- GROUPS CONFIG --
		/*
		mod.config.setCategoryComment(mod.configCategoryDefaultEntities, "The default values for Entities in a world, which does not belong to any other group.");
		mod.defaultEntitySlicesMax = mod.config.get(mod.configCategoryDefaultEntities, TimedGroup.configKeySlicesMax, mod.defaultEntitySlicesMax, 
    			"The number of time slices given to the group.").getInt();
		mod.defaultEntityMinimumObjects = mod.config.get(mod.configCategoryDefaultEntities, TimedGroup.configKeyMinimumObjects, mod.defaultEntityMinimumObjects, 
    			"The minimum number of Entities to update per tick, independent of time given.").getInt();
    	
		mod.config.setCategoryComment(mod.configCategoryDefaultTileEntities, "The default values for TileEntities in a world, which does not belong to any other group.");
		mod.defaultTileEntitySlicesMax = mod.config.get(mod.configCategoryDefaultTileEntities, TimedGroup.configKeySlicesMax, mod.defaultTileEntitySlicesMax, 
    			"The number of time slices given to the group.").getInt();
		mod.defaultTileEntityMinimumObjects = mod.config.get(mod.configCategoryDefaultTileEntities, TimedGroup.configKeyMinimumObjects, mod.defaultTileEntityMinimumObjects, 
    			"The minimum number of TileEntities to update per tick, independent of time given.").getInt();*/
    	
		//Load New, Reload and Remove old groups
		if(groups)
		{
			loadGlobalGroups(mod);

			//Default example for Entities and TileEntities in dim0(Overworld)
			if(!mod.config.hasCategory("worlds.dim0.entity"))
			{
				mod.config.get("worlds.dim0.entity", ITimed.configKeySlicesMax, mod.defaultEntitySlicesMax);
				mod.config.get("worlds.dim0.entity", EntityGroup.config_groupType, EntityType.Entity.toString());
			}
			if(!mod.config.hasCategory("worlds.dim0.tileentity"))
			{
				mod.config.get("worlds.dim0.tileentity", ITimed.configKeySlicesMax, mod.defaultEntitySlicesMax);
				mod.config.get("worlds.dim0.tileentity", EntityGroup.config_groupType, EntityType.TileEntity.toString());
			}

			//Reload local groups
			WorldServer[] worlds = DimensionManager.getWorlds();
			for(WorldServer world : worlds) {
				if(mod.debug)
					System.out.println("Reloading " + world.provider.getDimensionName());

				if(world.loadedEntityList instanceof ListManager) {
					ListManager entityList = (ListManager)world.loadedEntityList;
					if(mod.debug)
						System.out.println("Reloading " + entityList.size() + " Entities...");
					entityList.reloadGroups();
				}
				if(world.loadedTileEntityList instanceof ListManager) {
					ListManager tileList = (ListManager)world.loadedTileEntityList;
					if(mod.debug)
						System.out.println("Reloading " + tileList.size() + " TileEntities...");
					tileList.reloadGroups();
				}
			}

			if(mod.debug)
				System.out.println("Done reloading worlds");

			//Reload Timed
			for(ITimed timed : mod.timedObjects.values())
			{
				if(timed instanceof TimedEntities)
				{
					TimedEntities timedGroup = (TimedEntities)timed;
					if(!timedGroup.getEntityGroup().valid) {
						mod.timedObjects.remove(timedGroup);
						continue;
					}
				}
				timed.loadConfig(false);
			}

			if(mod.root != null)
				mod.root.setTimeMax(mod.defaultTickTime * TimeManager.timeMilisecond);
		}
    	
    	//Save any new defaults
    	mod.config.save();
	}
	
	//Load the config of Global Groups
	public static void loadGlobalGroups(TickDynamicMod mod) {

		mod.config.setCategoryComment("groups", "Groups define a list of Entities and/or TileEntities and the configuration for them.\n"
    			+ "You can define the groups here, and they will automatically be part of every world.\n"
    			+ "\n"
    			+ "If you wish to override the settings for a group in a specific world, you can simply include a group with the same name in the world,\n"
    			+ "and then provide the new values. Any value you do not define will be read from the global group.\n"
    			+ "So you can for example define a group for all Animal mobs, and define them to get less time than other Entities in all worlds,\n"
    			+ "but then define them to get even less time in a certain world without having to re-define the list of Entities.\n"
    			+ "\n"
    			+ "Note that the groups 'entity' and 'tileentity' are special groups. Any TileEntity or Entity which are not included in any other group,\n"
    			+ "will be automatically included in these two groups.");
		
		//Load/Create default entity and tileentity groups
		loadDefaultGlobalGroups(mod);
		
		//Load Global groups
		loadGroups(mod, "groups");
	}
	
	//Load all groups under the given category
	public static void loadGroups(TickDynamicMod mod, String category) {
		ConfigCategory groupsCat = mod.config.getCategory(category);
		Set<ConfigCategory> groups = groupsCat.getChildren();
		
		//Remove every group which is no longer in groups set
		ArrayList<String> toRemove = new ArrayList<String>();
		for(String groupPath : mod.entityGroups.keySet())
		{
			if(!groupPath.startsWith(category))
				continue; //We only care about groups in the same category
			
			int nameIndex = groupPath.lastIndexOf(".");
			String groupName;
			if(nameIndex == -1)
				groupName = groupPath;
			else
				groupName = groupPath.substring(nameIndex+1);
			
			boolean remove = true;
			if(mod.config.hasCategory(groupPath))
			{
				System.out.println("HAS: " + groupPath);
				remove = false;
			}
			
			//Check if local copy of a global group
			if(remove) {
				EntityGroup entityGroup = mod.entityGroups.get(groupPath);
				if(entityGroup != null && entityGroup.base != null) {
					//Check if the global group still exists
					if(mod.config.hasCategory("groups." + groupName))
						remove = false;
				}
			}
			
			//Mark for removal after loop
			if(remove)
			{

				if(mod.debug)
					System.out.println("Remove Group: " + groupPath);
				toRemove.add(groupPath);
			}
		}
		
		//Remove after due to ConcurrentException
		for(String groupPath : toRemove) {
			EntityGroup groupRemoved = mod.entityGroups.remove(groupPath);
			if(groupRemoved != null)
				groupRemoved.valid = false;
		}
		
		//Load new groups
		ArrayList<EntityGroup> updateGroups = new ArrayList<EntityGroup>();
		for(ConfigCategory group : groups)
		{
			//Check if group already exists
			String groupPath = category + "." + group.getName();
			EntityGroup entityGroup = mod.getEntityGroup(groupPath);
			if(entityGroup == null)
			{
				if(mod.debug)
					System.out.println("Loading group: " + groupPath);
				entityGroup = new EntityGroup(mod, null, null, group.getName(), groupPath, EntityType.Entity, null);
				mod.entityGroups.put(groupPath, entityGroup);
				if(mod.debug)
					System.out.println("New Group: " + groupPath);
			}
			else
			{
				//Add to list of groups to update
				updateGroups.add(entityGroup);
				if(mod.debug)
					System.out.println("Update Group: " + groupPath);
			}
		}

		//Update old
		for(EntityGroup entityGroup : updateGroups)
			entityGroup.readConfig(false);
	}
	
	public static void loadDefaultGlobalGroups(TickDynamicMod mod) {
		EntityGroup group;
		TimedEntities timedGroup;
		String groupPath;
		
		groupPath = "groups.entity";
		group = mod.getEntityGroup(groupPath);
		if(group == null)
		{
			timedGroup = new TimedEntities(mod, null, "entity", groupPath, null);
			timedGroup.init();
			group = new EntityGroup(mod, null, timedGroup, "entity", groupPath, EntityType.Entity, null);
			mod.entityGroups.put(groupPath, group);
		}
		
    	//Player group accounts the time used by players(Usually not limited, just used for accounting)
		groupPath = "groups.players";
		group = mod.getEntityGroup(groupPath);
		if(group == null)
		{
			//Write new defaults before creating group
			mod.config.get(groupPath, TimedEntities.configKeySlicesMax, 0); //No limit by default
			String[] entityClasses = {EntityPlayer.class.getName(), EntityPlayerMP.class.getName()};
			mod.config.get(groupPath, EntityGroup.config_classNames, entityClasses);
			
			timedGroup = new TimedEntities(mod, null, "players", groupPath, null);
			timedGroup.init();
			
			group = new EntityGroup(mod, null, timedGroup, "players", groupPath, EntityType.Entity, null);
			mod.entityGroups.put(groupPath, group);
		}
		
		groupPath = "groups.tileentity";
		group = mod.getEntityGroup(groupPath);
		if(group == null)
		{
			timedGroup = new TimedEntities(mod, null, "tileentity", groupPath, null);
			timedGroup.init();
			group = new EntityGroup(mod, null, timedGroup, "tileentity", groupPath, EntityType.TileEntity, null);
			mod.entityGroups.put(groupPath, group);
		}
		
	}
	
}
