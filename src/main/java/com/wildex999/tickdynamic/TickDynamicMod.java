package com.wildex999.tickdynamic;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.*;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AtomicDouble;
import com.wildex999.patcher.PatchParser;
import com.wildex999.tickdynamic.commands.CommandHandler;
import com.wildex999.tickdynamic.listinject.EntityGroup;
import com.wildex999.tickdynamic.listinject.EntityType;
import com.wildex999.tickdynamic.timemanager.ITimed;
import com.wildex999.tickdynamic.timemanager.TimeManager;
import com.wildex999.tickdynamic.timemanager.TimedGroup;
import com.wildex999.tickdynamic.timemanager.TimedGroup.GroupType;
import com.wildex999.tickdynamic.timemanager.TimedEntities;

import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;

//Written by: Wildex999 ( wildex999@gmail.com )

/*
 * Later ideas:
 * - Entities far away from players tick less often.
 * - Entities and TileEntities grouped by owner(Player), and limits can be set per player.
 */

public class TickDynamicMod extends DummyModContainer
{
    public static final String MODID = "tickDynamic";
    public static final String VERSION = "0.2.0-dev5";
    public static boolean debug = false;
    public static boolean debugGroups = false;
    public static boolean debugTimer = false;
    public static TickDynamicMod tickDynamic;
    
    
    public Map<String, ITimed> timedObjects;
    public Map<String, EntityGroup> entityGroups;
    public TimeManager root;
    public boolean enabled;
    public MinecraftServer server;
    public WorldEventHandler eventHandler;
    
    VersionChecker versionChecker;
    public boolean versionCheckDone;
    
    public Semaphore tpsMutex;
    public Timer tpsTimer;
    public int tickCounter;
    public double averageTPS;
    public int tpsAverageSeconds = 5; //Seconds to average TPS over
    public LinkedList<Integer> tpsList; //List of latest TPS for calculating average
    
    //Config
    public Configuration config;
    public boolean saveConfig;
    
    public static final String configCategoryDefaultEntities = "groups.entity";
    public static final String configCategoryDefaultTileEntities = "groups.tileentity";
    public int defaultTickTime = 50;
    public int defaultEntitySlicesMax = 100;
    public int defaultEntityMinimumObjects = 100;
    public float defaultEntityMinimumTPS = 0;
    public float defaultEntityMinimumTime = 0;
    public int defaultWorldSlicesMax = 100;
    public int defaultAverageTicks = 20;
    
    public TickDynamicMod() {
    	super(new ModMetadata());
    	ModMetadata meta = getMetadata();
    	meta.version = VERSION;
    	meta.modId = MODID;
    	meta.name = "Tick Dynamic";
    	meta.description = "Dynamic control of the world tickrate to reduce apparent lag.";
    	meta.authorList.add("Wildex999 ( wildex999@gmail.com )");
    	meta.updateUrl = "http://mods.stjerncraft.com/tickdynamic";
    	meta.url = "http://mods.stjerncraft.com/tickdynamic";
    	
    	tickDynamic = this;
    	tpsMutex = new Semaphore(1);
    	tpsTimer = new Timer();
    	tpsList = new LinkedList<Integer>();
    	versionChecker = new VersionChecker();
    }
    
    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
    	bus.register(this);
    	return true;
    }
    
    @Subscribe
    public void preInit(FMLPreInitializationEvent event) {
    	config = new Configuration(event.getSuggestedConfigurationFile());
    }
    
    //Load the configuration file
    //groups: Whether to (re)load the groups
    public void loadConfig(boolean groups) {
    	//TODO: Separate Initial load, reload and write
    	TickDynamicConfig.loadConfig(this, groups);
    }
    
    public void writeConfig(boolean saveFile) {
    	//TODO
    }
    
    //Queue to save any changes done to the config
    public void queueSaveConfig() {
    	saveConfig = true;
    }
    
    @Subscribe
    public void init(FMLInitializationEvent event) {
    	FMLCommonHandler.instance().bus().register(this);
    	timedObjects = new HashMap<String, ITimed>();
    	entityGroups = new HashMap<String, EntityGroup>();
    	
    	loadConfig(true);
    	
    	root = new TimeManager(this, null, "root", null);
    	root.init();
    	root.setTimeMax(defaultTickTime * TimeManager.timeMilisecond);
    	
    	//Other group accounts the time used in a tick, but not for Entities or TileEntities
    	TimedGroup otherTimed = new TimedGroup(this, null, "other", "other");
    	otherTimed.setSliceMax(0); //Make it get unlimited time
    	root.addChild(otherTimed);
    	
    	//External group accounts the time used between ticks due to external load
    	TimedGroup externalTimed = new TimedGroup(this, null, "external", "external");
    	externalTimed.setSliceMax(0);
    	root.addChild(externalTimed);
    	
    	eventHandler = new WorldEventHandler(this);
    	MinecraftForge.EVENT_BUS.register(eventHandler);
    	FMLCommonHandler.instance().bus().register(eventHandler);
    }
    
    
    @Subscribe
    public void serverStart(FMLServerStartingEvent event) {
    	event.registerServerCommand(new CommandHandler(this));
    	
    	tpsTimer.schedule(new TimerTickTask(this), 1000, 1000);
    	versionCheckDone = false;
    	versionChecker.runVersionCheck();
    	
    	
    	server = event.getServer();
    }
    
    @Subscribe
    public void serverStop(FMLServerStoppingEvent event) {
    	tpsTimer.cancel();
    	server = null;
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void tickEventStart(ServerTickEvent event) {
    	if(event.phase == Phase.START)
    	{
    		if(!versionCheckDone)
    		{
    			VersionChecker.VersionData versionData = versionChecker.getVersionData();
    			if(versionData != null)
    			{
    				versionCheckDone = true;
    				if(versionData.checkOk)
    				{
	    				//TODO: Parse versions, split at ',', then split version numbers at '.'
	    				System.out.println("TickDynamic version check: Latest version = " + versionData.modVersion + ". Download URL: http://" + versionData.updateUrl);
    				}
    				else
    					System.out.println("TickDynamic version check: Error while checking latest version!");
    			}
    		}
    		
    		TimedGroup externalGroup = getTimedGroup("external");
    		externalGroup.endTimer();
    		
    		//Set the correct externalGroup time
    		long msPerTick = 50;
    		long overTime = externalGroup.getTimeUsed() - (msPerTick*externalGroup.timeMilisecond); //overTime = time used above given tick time
    		long overTimeTick = (msPerTick*externalGroup.timeMilisecond) - (root.getTimeUsed() - externalGroup.getTimeUsed());
    		if(overTimeTick < 0)
    			overTime += overTimeTick;
    		/*System.out.println("TickTime: " + ((root.getTimeUsed()-externalGroup.getTimeUsed())/(double)externalGroup.timeMilisecond) + 
    				" Full Tick time: " + (externalGroup.getTimeUsed()/(double)externalGroup.timeMilisecond) +
    				" External time used: " + (overTime/(double)externalGroup.timeMilisecond)+"ms");*/
    		if(overTime < 0)
    			externalGroup.setTimeUsed(0);
    		else
    			externalGroup.setTimeUsed(overTime);
    		
    		externalGroup.startTimer();
    		
    		
	        //Clear any values from the previous tick for all worlds.
    		root.newTick(true);
    		
    		getTimedGroup("other").startTimer();
    	}
    }
    
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void tickEventEnd(ServerTickEvent event) {	
    	if(event.phase == Phase.END)
    	{
	     	getTimedGroup("other").endTimer();
	     	root.endTick(true);
	     	
	     	if(debugTimer)
	     		System.out.println("Tick time used: " + (root.getTimeUsed()/root.timeMilisecond) + "ms");
	     	
	     	//After every world is done ticking, re-balance the time slices according
	     	//to the data gathered during the tick.
	     	root.balanceTime();
	     	
	     	//Calculate TPS
	     	updateTPS();
	     	
	     	if(saveConfig)
	     	{
	     		saveConfig = false;
	     		config.save();
	     	}
    	}
    }
    
    //Calculate the new average TPS
    //Note: acquires a mutex due to contention with timer thread on tickCounter and tpsList.
    public void updateTPS() {
		try {
			tpsMutex.acquire();
			tickCounter++;
			
			//Calculate average from list
			averageTPS = 0;
			for(int tps : tpsList) {
				averageTPS += tps;
			}
			averageTPS = averageTPS / tpsList.size();
			
			tpsMutex.release();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    }
    
    //Get the named TimedGroup.
    //Return: Null if not loaded
    public TimedGroup getTimedGroup(String name) {
    	return (TimedGroup)timedObjects.get(name);
    }
    
    //Get a named EntityGroup which possibly does not belong to a world
    //Return: Null if doesn't exist in config
    public EntityGroup getEntityGroup(String name) {
    	//All Global Groups are loaded during config load/reload
    	EntityGroup group = entityGroups.get(name);
    	
    	return group;
    }
    
    public TimeManager getTimeManager(String name) {
    	return (TimeManager)timedObjects.get(name);
    }
    
    private String getEntityGroupName(World world, String name) {
    	String remote = "";
    	if(world.isRemote)
    		remote = "client_";
    	StringBuilder strBuilder = new StringBuilder().append("worlds.").append(remote).append("dim").append(world.provider.dimensionId);
    	if(name != null && name.length() > 0)
    		strBuilder.append(".").append(name);

    	return strBuilder.toString();
    }
    
    //Get the TimeManager for a world.
    //Will create if it doesn't exist.
    public TimeManager getWorldTimeManager(World world) {
    	String managerName = getEntityGroupName(world, null);
    	TimeManager worldManager = getTimeManager(managerName);
    	
    	if(worldManager == null)
    	{
    		worldManager = new TimeManager(this, world, managerName, managerName);
    		worldManager.init();
    		if(world.isRemote)
    			worldManager.setSliceMax(0);
    		
    		config.setCategoryComment(managerName, world.provider.getDimensionName());

    		root.addChild(worldManager);
    	}
    	
    	return worldManager;
    }
    
    //Get the named TimedGroup from the given world.
    //canCreate: Whether to create if it does not exist
    //hasConfig: Whether to create with config entry
    public TimedEntities getWorldTimedGroup(World world, String name, boolean canCreate, boolean hasConfig) {
    	String groupName = getEntityGroupName(world, name);
    	TimedGroup group = getTimedGroup(groupName);
    	
    	if((group == null || !(group instanceof TimedEntities)) && canCreate)
    	{
    		String baseGroupName = new StringBuilder().append("groups.").append(name).toString();
    		TimedGroup baseGroup = getTimedGroup(baseGroupName);
    		group = new TimedEntities(this, world, name, hasConfig ? groupName : null, baseGroup);
    		group.init();
    		
    		TimeManager worldManager = getWorldTimeManager(world);
    		worldManager.addChild(group);
    	}
    	
    	return (TimedEntities)group;
    }
    
    //groupType: The type to make the new Group if it doesn't already exist
    //Will return existing group even if type doesn't match.
    //canCreate: Whether to create the group if it does not exist
    //hasConfig: Whether to create with config entry
    public EntityGroup getWorldEntityGroup(World world, String name, EntityType groupType, boolean canCreate, boolean hasConfig) {
    	String groupName = getEntityGroupName(world, name);
    	EntityGroup group = getEntityGroup(groupName);
    	
    	if(group == null && canCreate) //Create group for world
    	{
    		String baseGroupName = new StringBuilder().append("groups.").append(name).toString();
    		EntityGroup baseGroup = getEntityGroup(baseGroupName);
    		group = new EntityGroup(this, world, getWorldTimedGroup(world, name, true, hasConfig), name, hasConfig ? groupName : null, groupType, baseGroup);
    		entityGroups.put(groupName, group);
    	}
    	
    	return group;
    }
    
    //Get all EntityGroups for the given world
    public List<EntityGroup> getWorldEntityGroups(World world) {
    	List<EntityGroup> groups = new ArrayList<EntityGroup>();
    	
    	String remote = "";
    	int offsetCount = 10;
    	if(world.isRemote)
    	{
    		remote = "client_";
    		offsetCount += 7;
    	}
    	
    	String groupNamePrefix = new StringBuilder().append(world.provider.dimensionId).append(".").toString();
    	//TODO: Don't compare the first 10 characters, as they are always the same(Have offset)
    	for(Map.Entry<String, EntityGroup> entry : entityGroups.entrySet()) {
    		String groupName = entry.getKey();
    		if(!groupName.startsWith(groupNamePrefix, offsetCount))
    			continue;
    		
    		groups.add(entry.getValue());
    	}
    	
    	return groups;
    }
    
    //Unload every Entity Group for the given world
    public void clearWorldEntityGroups(World world) {
    	if(world == null)
    		return;
    	
    	List<EntityGroup> groups = getWorldEntityGroups(world);
    	int groupCount = 0;
    	for(EntityGroup group : groups) {
    		if(group.getWorld() == null) {
    			if(debug)
    				System.out.println("Unable to unload group: " + group.getName() + ". World is null.");
    			continue;
    		}
    		String groupName = getEntityGroupName(group.getWorld(), group.getName());
    		if(!entityGroups.remove(groupName, group)) {
    			System.err.println("Failed to unload EntityGroup: " + groupName + " for world: " + world.provider.getDimensionName());
    			System.err.println("This might cause the world to remain in memory!"); 
    		}
    		else
    			groupCount++;
    		group.valid = false;
    	}
    	
    	if(debug)
    		System.out.println("Unloaded " + groupCount + " EntityGroups while unloading world: " + world.provider.getDimensionName());
    }
    
    public String getWorldPrefix(World world) {
    	return "worlds.dim" + world.provider.dimensionId;
    }
    
    public ConfigCategory getWorldConfigCategory(World world) {
    	return config.getCategory(getWorldPrefix(world));
    }
    
    //Get the group for Ungrouped Tile Entities in the given world
    //Will create the world TimeManager and Entity Group if it doesn't exist.
    public EntityGroup getWorldTileEntities(World world) {
    	EntityGroup teGroup = getWorldEntityGroup(world, "tileentity", EntityType.TileEntity, true, true);
    	return teGroup;
    }
    
    //Get the group for Ungrouped Tile Entities in the given world
    //Will create the world TimeManager and Entity Group if it doesn't exist.
    public EntityGroup getWorldEntities(World world) {
    	EntityGroup eGroup = getWorldEntityGroup(world, "entity", EntityType.Entity, true, true);
    	return eGroup;
    }
    

}
