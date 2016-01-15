package com.wildex999.tickdynamic.listinject;

import net.minecraft.profiler.Profiler;

/*
 * This profiler will note where the world is while ticking Entities
 * when using a for loop for iterating.
 * This will allow us to be 100% accurate while the world is iterating, as we would know
 * whether we are ticking, or looping the list from inside a ticking entity.
 * 
 * This does however add a bit of an overhead with the function calls and String compare.
 */


public class CustomProfiler extends Profiler {

	public enum Stage {
		None, //Just pass on to original
		BeforeLoop, //World Tick has started
		InLoop, //We have started looping entities
		InTick, //We are currently ticking a single entity
		InRemove //Removing dead Entity
	}
	
	private final Profiler original;
	public Stage stage;
	public boolean reachedTile; //Set to true when starting to tick TileEntities
	
	
	private int depthCount; //start and end can be called inside Entity tick, we have to track it
	
	public CustomProfiler(Profiler originalProfiler) {
		this.original = originalProfiler;
		this.stage = Stage.None;
		this.reachedTile = false;
	}
	
	@Override
    public void startSection(String sectionName)
    {

		switch(stage) {
		case None:
			break;
			
		case BeforeLoop:
			if(sectionName.equals("regular"))
				stage = Stage.InLoop;
			break;
			
		case InLoop:
			if(sectionName.equals("tick")) {
				stage = Stage.InTick;
				depthCount = 0;
			} else if(sectionName.equals("remove")) {
				stage = Stage.InRemove;
				depthCount = 0;
			} else if(sectionName.equals("blockEntities")) {
				stage = Stage.None; //Done ticking Entities
				reachedTile = true;
			}
			break;
			
		case InTick:
		case InRemove:
			depthCount++;
			break;
		}
		
		original.startSection(sectionName);
    }
	
	@Override
	public void endSection() {
		switch(stage) {
		case InTick:
			if(depthCount-- == 0)
				stage = Stage.InLoop;
			break;
		case InRemove:
			if(depthCount-- == 0)
				stage = Stage.InLoop;
			break;
		default:
			break;
		}
		original.endSection();
	}
	
}
