package com.wildex999.tickdynamic.listinject;

import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;

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
	private ListManagerEntities entitiesList;
	private final World world;
	private final Thread thread; //Used to ignore calls outside main thread
	
	public Stage stage;
	public boolean reachedTile; //Set to true when starting to tick TileEntities
	
	
	private int depthCount; //start and end can be called inside Entity tick, we have to track it
	
	public CustomProfiler(Profiler originalProfiler, World world) {
		this.original = originalProfiler;
		setStage(Stage.None);
		this.reachedTile = false;
		this.world = world;
		this.thread = Thread.currentThread();
	}
	
	public void setStage(Stage stage) {
		//System.out.println("Stage change: " + this.stage + " -> " + stage);
		this.stage = stage;
	}
	
	@Override
    public void startSection(String sectionName)
    {
		//KCauldron/PaperSpigot runs some Lighting updates in another thread, which also calls the Profiler
		//This causes problems for us(Not that the Profiler is Thread-safe to begin with, tsk tsk PaperSpigot)
		if(thread != Thread.currentThread())
		{
			original.startSection(sectionName);
			return;
		}
		
		switch(stage) {
		case None:
			break;
			
		case BeforeLoop:
			if(sectionName.equals("regular"))
				setStage(Stage.InLoop);
			break;
			
		case InLoop:
			if(sectionName.equals("tick")) {
				setStage(Stage.InTick);
				depthCount = 0;
			} else if(sectionName.equals("remove")) {
				setStage(Stage.InRemove);
				depthCount = 0;
			} else if(sectionName.equals("blockEntities")) {
				setStage(Stage.None); //Done ticking Entities
				
				//Make sure Entity list knows we are done, in case we are forcibly cut out of the loop(Looking at you KCauldron limiter)
				if(entitiesList == null)
					entitiesList = (ListManagerEntities)world.loadedEntityList;
				entitiesList.stopUpdate();
				
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
		if(thread != Thread.currentThread())
		{
			original.endSection();
			return;
		}
		
		switch(stage) {
		case InTick:
			if(depthCount-- == 0)
				setStage(Stage.InLoop);
			break;
		case InRemove:
			if(depthCount-- == 0)
				setStage(Stage.InLoop);
			break;
		default:
			break;
		}
		original.endSection();
	}
	
}
