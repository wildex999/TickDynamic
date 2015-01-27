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
import java.util.List;
import java.util.Map;
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
import com.wildex999.patcher.PatchParser;
import com.wildex999.tickdynamic.commands.CommandHandler;
import com.wildex999.tickdynamic.timemanager.ITimed;
import com.wildex999.tickdynamic.timemanager.TimeManager;
import com.wildex999.tickdynamic.timemanager.TimedEntities;
import com.wildex999.tickdynamic.timemanager.TimedGroup;
import com.wildex999.tickdynamic.timemanager.TimedGroup.GroupType;
import com.wildex999.tickdynamic.timemanager.TimedTileEntities;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
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
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;


public class TickDynamicMod extends DummyModContainer
{
    public static final String MODID = "tickDynamic";
    public static final String VERSION = "0.1";
    public static boolean debug = false;
    public static TickDynamicMod tickDynamic;
    
    
    public Map<String, ITimed> timedObjects;
    public TimeManager root;
    public boolean enabled;
    
    //Config
    public Configuration config;
    
    public static final String configCategoryDefaultEntities = "general.entitydefaults";
    public static final String configCategoryDefaultTileEntities = "general.tileentitydefaults";
    public int defaultTickTime = 50;
    public int defaultEntitySlicesMax = 100;
    public int defaultEntityMinimumObjects = 100;
    public int defaultTileEntitySlicesMax = 100;
    public int defaultTileEntityMinimumObjects = 100;
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
    	
    	tickDynamic = this;
    }
    
    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
    	bus.register(this);
    	return true;
    }
    
    @Subscribe
    public void preInit(FMLPreInitializationEvent event) {
    	config = new Configuration(event.getSuggestedConfigurationFile());
    	config.load();
    	
    	//Generate default config if not set
    	config.getCategory("general");
    	config.setCategoryComment("general", "Slices are the way you control the time allottment to each world, and within each world, to Entities and TileEntities.\n"
    			+ "Each tick the time for a tick(By default 50ms) will be distributed among all the worlds, according to how many slices they have.\n"
    			+ "If you have 3 worlds, each with 100 slices, then each world will get 100/300 = ~33% of the time.\n"
    			+ "So you can thus give the Overworld a maxSlices of 300, while giving the other two 100 each. This way the Overworld will get 60% of the time.\n"
    			+ "\n"
    			+ "Of the time given to the world, this is further distributed to TileEntities and Entities according tho their slices, the same way.\n"
    			+ "If any group has unused time, then that time will be distributed to the remaining groups.\n"
    			+ "So even if you give 1000 slices to TileEntities and 100 to Entities, as long as as TileEntities aren't using it's full time,\n"
    			+ "Entities will be able to use more than 100 slices of time.\n"
    			+ "\n"
    			+ "So the formula for slices to time percentage is: (group.maxSlices/allSiblings.maxSlices)*100");
    	
    	enabled = config.get("general", "enabled", enabled, "").getBoolean();
    	
    	debug = config.get("general", "debug", debug, "Debug output. Warning: Setting this to true will cause a lot of console spam.\n"
    			+ "Only do it if developer or someone else asks for the output!").getBoolean();
    	
    	defaultTickTime = config.get("worlds", "tickTime", defaultTickTime, "The time allotted to a tick in milliseconds. 20 Ticks per second means 50ms per tick.\n"
    			+ "This is the base time allotment it will use when balancing the time usage between worlds and objects.\n"
    			+ "You can set this to less than 50ms if you want to leave a bit of buffer time for other things, or don't want to use 100% cpu.").getInt();
    	
    	defaultWorldSlicesMax = config.get("general", "defaultWorldSlicesMax", defaultWorldSlicesMax, "The default maxSlices for a new automatically added world.").getInt();
    	
    	config.setCategoryComment(configCategoryDefaultEntities, "The default values for new Entity groups when automatically created for new worlds.");
    	defaultEntitySlicesMax = config.get(configCategoryDefaultEntities, TimedGroup.configKeySlicesMax, defaultEntitySlicesMax, 
    			"The number of time slices given to the group.").getInt();
    	defaultEntityMinimumObjects = config.get(configCategoryDefaultEntities, TimedGroup.configKeyMinimumObjects, defaultEntityMinimumObjects, 
    			"The minimum number of Entities to update per tick, independent of time given.").getInt();
    	
    	config.setCategoryComment(configCategoryDefaultTileEntities, "The default values for new TileEntity groups when automatically created for new worlds.");
    	defaultTileEntitySlicesMax = config.get(configCategoryDefaultTileEntities, TimedGroup.configKeySlicesMax, defaultTileEntitySlicesMax, 
    			"The number of time slices given to the group.").getInt();
    	defaultTileEntityMinimumObjects = config.get(configCategoryDefaultTileEntities, TimedGroup.configKeyMinimumObjects, defaultTileEntityMinimumObjects, 
    			"The minimum number of TileEntities to update per tick, independent of time given.").getInt();
    	
    	defaultAverageTicks = config.get("general", "averageTicks", defaultAverageTicks, "How many ticks of data to when averaging for time balancing.\n"
    			+ "A higher number will make it take regular spikes into account, however will make it slower to addjust to changes.").getInt();
    	
    	//Save any new defaults
    	config.save();
    }
    
    @Subscribe
    public void init(FMLInitializationEvent event) {
    	FMLCommonHandler.instance().bus().register(this);
    	timedObjects = new HashMap<String, ITimed>();
    	
    	root = new TimeManager(this, "root");
    	root.init(null);
    	root.setTimeMax(defaultTickTime * TimeManager.timeMilisecond);
    	TimedGroup otherTimed = new TimedGroup(this, "other");
    	otherTimed.setSliceMax(0); //Make it get unlimited time
    	root.addChild(otherTimed);
    }
    
    
    @Subscribe
    public void serverStart(FMLServerStartingEvent event) {
    	event.registerServerCommand(new CommandHandler());
    }

    @SubscribeEvent
    public void tickEvent(ServerTickEvent event) {
    	if(event.phase == Phase.START)
    	{
	        //Clear any values from the previous tick for all worlds.
    		root.newTick(true);
    		
    		getGroup("other").startTimer();
    	} else {    		
    		getGroup("other").endTimer();
    		
    		if(debug)
    			System.out.println("Tick time used: " + (root.getTimeUsed()/root.timeMilisecond) + "ms");
    		//After every world is done ticking, re-balance the time slices according
    	    //to the data gathered during the tick.
    		root.balanceTime();
    	}
    }
    
    public TimedGroup getGroup(String name) {
    	return (TimedGroup)timedObjects.get(name);
    }
    
    public TimeManager getManager(String name) {
    	return (TimeManager)timedObjects.get(name);
    }
    
    //Get the TimeManager for a world.
    //Will create if it doesn't exist.
    public TimeManager getWorldManager(World world) {
    	String remote = "";
    	if(world.isRemote)
    		remote = "r";
    	String managerName = new StringBuilder().append(remote).append("tm_DIM").append(world.provider.dimensionId).toString();
    	TimeManager worldManager = getManager(managerName);
    	
    	if(worldManager == null)
    	{
    		worldManager = new TimeManager(this, managerName);
    		worldManager.init("worlds.dim" + world.provider.dimensionId);
    		if(world.isRemote)
    			worldManager.setSliceMax(0);
    		
    		config.setCategoryComment("worlds.dim" + world.provider.dimensionId, world.provider.getDimensionName());
    		
    		//TODO: Allow for worlds to be child of other worlds
    		root.addChild(worldManager);
    	}
    	
    	return worldManager;
    }
    
    //Get the named TimedGroup from the given world.
    //Will create if it doesn't exist.
    public TimedGroup getWorldGroup(World world, TimedGroup.GroupType type, String name) {
    	String remote = "";
    	if(world.isRemote)
    		remote = "r";
    	String groupName = new StringBuilder().append(remote).append("DIM").append(world.provider.dimensionId).append("_").append(name).toString();
    	TimedGroup group = getGroup(groupName);
    	
    	if(group == null)
    	{
    		if(type == TimedGroup.GroupType.TileEntity)
    		{
    			group = new TimedTileEntities(this, groupName);
    			group.init("worlds.dim" + world.provider.dimensionId + ".tileentity");
    		}
    		else if(type == TimedGroup.GroupType.Entity)
    		{
    			group = new TimedEntities(this, groupName);
    			group.init("worlds.dim" + world.provider.dimensionId + ".entity");
    		}
    		
    		TimeManager worldManager = getWorldManager(world);
    		worldManager.addChild(group);
    	}
    	
    	return group;
    }
    
    //Get the group for TileEntities in the given world
    //Will create the world TimeManager and TE Group if it doesn't exist.
    public TimedTileEntities getWorldTileEntities(World world) {
    	TimedGroup teGroup = getWorldGroup(world, TimedGroup.GroupType.TileEntity, "te");
    	return (TimedTileEntities)teGroup;
    }
    
    public TimedEntities getWorldEntities(World world) {
    	TimedGroup entityGroup = getWorldGroup(world, TimedGroup.GroupType.Entity, "e");
    	return (TimedEntities)entityGroup;
    }
    
    

}
