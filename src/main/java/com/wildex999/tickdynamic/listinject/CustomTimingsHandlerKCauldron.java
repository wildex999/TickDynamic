package com.wildex999.tickdynamic.listinject;

import org.spigotmc.CustomTimingsHandler;

import net.minecraft.world.World;

/*
 * Our own CustomTimingsHandler which we inject into the KCauldron world,
 * allowing us to know when we are looping TileEntity list, and ticking an entity.
 */

public class CustomTimingsHandlerKCauldron extends CustomTimingsHandler {

	private static int indexCount = 0;
	
	private CustomTimingsHandler original;
	
	//loopHandler: If true, will handle the check for starting and ending TileEntity loop.
	//			   If false, checks for entering and exiting TileEntity tick.
	public CustomTimingsHandlerKCauldron(World world, boolean loopHandler) {
		super("TDP_"+(indexCount++));
		this.original = original;
	}
	
	@Override
	public void startTiming() {
		
	}
	
	@Override
	public void stopTiming() {
		
	}

}
