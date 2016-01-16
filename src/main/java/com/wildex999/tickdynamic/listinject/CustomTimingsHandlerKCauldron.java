package com.wildex999.tickdynamic.listinject;

import org.spigotmc.CustomTimingsHandler;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/*
 * Our own CustomTimingsHandler which we inject into the KCauldron world,
 * allowing us to know when we are looping TileEntity list, and ticking an entity.
 */

public class CustomTimingsHandlerKCauldron extends CustomTimingsHandler {

	public static CustomTimingsHandler currentTimer;
	
	private boolean loopHandler;
	private World world;
	
	public static boolean inLoop;
	public static boolean inTick;
	
	public CustomTimingsHandlerKCauldron(World world, boolean loopHandler) {
		super("TD_" + world.provider.dimensionId + (loopHandler == true ? "_loop" : "_tick"));
		this.loopHandler = loopHandler;
		this.world = world;
	}
	
	@Override
	public void startTiming() {
		if(!loopHandler)
		{
			inTick = true;
			currentTimer.startTiming();
		}
		else
		{
			inLoop = true;
		}
		
		
	}
	
	@Override
	public void stopTiming() {
		if(inTick)
		{
			inTick = false;
			currentTimer.stopTiming();
		}
		else
		{
			inLoop = false;
			((ListManagerTileEntities)world.loadedTileEntityList).stopUpdate();
		}
		
	}

}
