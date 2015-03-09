package com.wildex999.tickdynamic;

import com.wildex999.tickdynamic.listinject.EntityType;
import com.wildex999.tickdynamic.listinject.ListManager;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.world.WorldEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class WorldEventHandler {
	public TickDynamicMod mod;
	
	public WorldEventHandler(TickDynamicMod mod) {
		this.mod = mod;
	}
	
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDimensionLoad(WorldEvent.Load event)
    {
    	//Register our own Entity List manager, copying over any existing Entities
    	System.out.println("World load: " + event.world.provider.getDimensionName());
    	ListManager entityManager = new ListManager(event.world, mod, EntityType.Entity);
    	ListManager tileEntityManager = new ListManager(event.world, mod, EntityType.TileEntity);
    }
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDimensionUnload(WorldEvent.Unload event)
    {
    	//Make sure we unload our own Entity List manager to avoid memory leak
    }
}
