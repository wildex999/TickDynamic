package com.wildex999.tickdynamic;

import java.util.HashMap;
import java.util.List;

import org.spigotmc.CustomTimingsHandler;

import com.wildex999.tickdynamic.listinject.CustomProfiler;
import com.wildex999.tickdynamic.listinject.CustomTimingsHandlerKCauldron;
import com.wildex999.tickdynamic.listinject.EntityObject;
import com.wildex999.tickdynamic.listinject.EntityType;
import com.wildex999.tickdynamic.listinject.ListManager;
import com.wildex999.tickdynamic.listinject.ListManagerEntities;
import com.wildex999.tickdynamic.listinject.ListManagerTileEntities;
import com.wildex999.tickdynamic.timemanager.ITimed;
import com.wildex999.tickdynamic.timemanager.TimeManager;
import com.wildex999.tickdynamic.timemanager.TimedEntities;

import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.world.WorldEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class WorldEventHandler {
	public TickDynamicMod mod;
	
	private HashMap<World, ListManagerEntities> entityListManager;
	private HashMap<World, ListManager> tileListManager;
	
	public WorldEventHandler(TickDynamicMod mod) {
		this.mod = mod;
		entityListManager = new HashMap<World, ListManagerEntities>();
		tileListManager = new HashMap<World, ListManager>();
	}
	
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void worldTickEvent(WorldTickEvent event) {
		Profiler profiler = event.world.theProfiler;
		if(!(profiler instanceof CustomProfiler))
			return;
		CustomProfiler customProfiler = (CustomProfiler)profiler;
    	
    	if(event.phase == Phase.START) {
    		customProfiler.setStage(CustomProfiler.Stage.BeforeLoop);
    	}
    	else {
			//Ensure Entity ticking stops
			List entityList = event.world.loadedEntityList;
			if(entityList != null && entityList instanceof ListManagerEntities) {
				((ListManagerEntities)event.world.loadedEntityList).stopUpdate();
			}
			//Ensure TileEntity ticking stops
			List tileList = event.world.loadedEntityList;
			if(tileList != null && tileList instanceof ListManagerTileEntities) {
				((ListManagerTileEntities)event.world.loadedTileEntityList).stopUpdate();
			}
    		customProfiler.setStage(CustomProfiler.Stage.None);
    	}
    }
	
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDimensionLoad(WorldEvent.Load event)
    {
    	if(event.world.isRemote)
    		return;
    	
    	//Register our own Entity List manager, copying over any existing Entities
    	if(mod.debug)
    		System.out.println("World load: " + event.world.provider.getDimensionName());
    	
    	//Inject Custom Profiler for watching Entity ticking
    	try {
    		setCustomProfiler(event.world, new CustomProfiler(event.world.theProfiler, event.world));
    	} catch(Exception e) {
    		System.err.println("Unable to set TickDynamic World profiler! World will not be using TickDynamic: " + event.world);
    		System.err.println(e);
    		return; //Do not add TickDynamic to world
    	}
    	
    	//Get WorldTimings class and instance
    	Class worldTimingsHandlerClass;
    	Object worldTimingsHandlerInstance;
    	try {
    		worldTimingsHandlerClass = Class.forName("org.bukkit.craftbukkit.v1_7_R4.SpigotTimings$WorldTimingsHandler");
    		worldTimingsHandlerInstance = World.class.getField("timings").get(event.world);
		} catch (Exception e) {
			System.err.println("Unable to get class for TileEntity list management.");
			System.err.println(e);
			return; 
		}
    	
    	ListManagerEntities entityManager = new ListManagerEntities(event.world, mod);
    	entityListManager.put(event.world, entityManager);
    	ListManagerTileEntities tileEntityManager = new ListManagerTileEntities(event.world, mod);
    	tileListManager.put(event.world, tileEntityManager);
    	
    	//Replace TileTick Timings Object for TileEntity
    	ReflectionHelper.setPrivateValue(worldTimingsHandlerClass, worldTimingsHandlerInstance, tileEntityManager.timingsHandlerLoop, "tileEntityTick");
    	
    	//Overwrite existing lists, copying any loaded Entities
    	if(mod.debug)
    		System.out.println("Adding " + event.world.loadedEntityList.size() + " existing Entities.");
    	List<EntityObject> oldList = event.world.loadedEntityList;
    	event.world.loadedEntityList = entityManager;
    	for(EntityObject obj : oldList) {
    		entityManager.add(obj);
    	}
    	
    	//Tiles
    	if(mod.debug)
    		System.out.println("Adding " + event.world.loadedTileEntityList.size() + " existing TileEntities.");
    	oldList = event.world.loadedTileEntityList;
    	event.world.loadedTileEntityList = tileEntityManager;
    	for(EntityObject obj : oldList) {
    		tileEntityManager.add(obj);
    	}
    	
    }
    
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDimensionUnload(WorldEvent.Unload event)
    {
    	if(event.world == null || event.world.isRemote)
    		return;
    	
    	if(mod.debug)
    		System.out.println("TickDynamic unloading injected lists for world: " + event.world.provider.getDimensionName());
    	
    	try {
        	CustomProfiler customProfiler = (CustomProfiler)event.world.theProfiler;
			setCustomProfiler(event.world, customProfiler.original);
		} catch (Exception e) {
			System.err.println("Failed to revert World Profiler to original");
			e.printStackTrace();
		}
    	
    	//Remove all references to the lists and EntityObjects contained(Groups will remain loaded in TickDynamic)
    	ListManager list = entityListManager.remove(event.world);
    	if(list != null)
    		list.clear();
    	
    	list = tileListManager.remove(event.world);
    	if(list != null)
    		list.clear();
    	
    	//Clear loaded groups for world
    	mod.clearWorldEntityGroups(event.world);
    	
    	//Clear timed groups
    	ITimed manager = mod.getWorldTimeManager(event.world);
    	if(manager != null)
    		mod.timedObjects.remove(manager);
    	
    	for(ITimed timed : mod.timedObjects.values())
		{
    		if(timed instanceof TimedEntities)
    		{
    			TimedEntities timedGroup = (TimedEntities)timed;
    			if(!timedGroup.getEntityGroup().valid)
    				mod.timedObjects.remove(timedGroup);
    		}
		}
    	
    }
    
    private void setCustomProfiler(World world, Profiler profiler) throws Exception {
    	ReflectionHelper.setPrivateValue(World.class, world, profiler, "theProfiler", "field_72984_F");
    }
}
