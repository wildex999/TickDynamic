package com.wildex999.tickdynamic.timemanager;

import java.util.Iterator;
import java.util.List;

import com.wildex999.tickdynamic.TickDynamicMod;

import net.minecraft.world.World;

public class TimedTileEntities extends TimedGroup {
	protected int currentObjectIndex; //Current index into updating Objects
	protected int remainingObjects; //Remaining objects for current update cycle
	protected World world; //World for this group
	
	public TimedTileEntities(TickDynamicMod mod, String name) {
		super(mod, name);
	}
	
    //Initialize a timed Tile Entity group, reading in the configuration if it exists.
    //If no configuration exits, create a new default.
	@Override
    public void init(String configEntry) {
		timeUsed = 0;
		objectsRun = 0;
		setTimeMax(0);
		
		if(configEntry != null)
		{
			loadConfig(configEntry);
		}
		else
		{
			setSliceMax(mod.defaultTileEntitySlicesMax);
			setMinimumObjects(mod.defaultTileEntityMinimumObjects);
		}

    }
	
	@Override
	public void loadConfig(String configEntry) {
		setSliceMax(mod.config.get(configEntry, configKeySlicesMax, mod.defaultTileEntitySlicesMax).getInt());
		setMinimumObjects(mod.config.get(configEntry, configKeyMinimumObjects, mod.defaultTileEntityMinimumObjects).getInt());
		
		//Save any default values
		mod.config.save();
	}
	
	@Override
	public void writeConfig(String configEntry, boolean saveFile) {
		mod.config.get(configEntry, configKeySlicesMax, mod.defaultTileEntitySlicesMax).setValue(getSliceMax());
		mod.config.get(configEntry, configKeyMinimumObjects, mod.defaultTileEntityMinimumObjects).setValue(getMinimumObjects());
		
		if(saveFile)
			mod.config.save();
	}
	
	//Calculate how many objects can run within the given timeMax.
	//It calculates this using an average for the time used by the objects.(timeUsed/objectCount)
	public int getTargetObjectCount() {
		if(timeMax == 0 || !mod.enabled) //No limit set
			return Integer.MAX_VALUE;

		double timePerObject = (int)Math.ceil((double)getTimeUsedAverage()/(double)getObjectsRunAverage());
		if(TickDynamicMod.debug)
			System.out.println(name + ": getTargetObjectCount: timeUsed:" + getTimeUsedAverage() + " objectsRun: " + getObjectsRunAverage() + " timePerObject: " + timePerObject + " timeMax: " + timeMax);
		return (int) Math.ceil(timeMax / timePerObject);
	}

	//Get the current offset into the list of objects(Entities/TileEntities)
	public int getCurrentObjectIndex() {
		return currentObjectIndex;
	}

	//Called before the loop for updating objects in the group.
	//Calculates how many objects to update in this loop.
	//Return: The starting offset
	public int startUpdateObjects(World world) {
		this.world = world;
		
		if(world.isRemote || !mod.enabled)
			return 0; //remote world updates should run as normal(Client)
		
		List loadedList = getWorldLoadedList(world);
		
		remainingObjects = getTargetObjectCount();
		if(remainingObjects < minimumObjects)
			remainingObjects = minimumObjects;
		if(remainingObjects > loadedList.size())
			remainingObjects = loadedList.size();
		
		if(currentObjectIndex >= loadedList.size())
			currentObjectIndex = 0;
		if(TickDynamicMod.debug)
			System.out.println("Start ("+ name +"). CurrentObjectIndex: " + currentObjectIndex + " | "
				+ "RemainingObjects: " + remainingObjects + " of " + loadedList.size());
		
		return currentObjectIndex;
	}
	
	//Called at the end of the update loop for TileEntities.
	//Keeps track of position, repositions the iterator when needed, and ends the loop when limit has been hit
	public Iterator updateObjects(Iterator iterator) {
		if(world.isRemote || !mod.enabled)
			return iterator; //Run remote world as normal(Client view)
		
		List loadedList = getWorldLoadedList(world);
		
		//Stop if no more remaining
		if(remainingObjects-- <= 0)
			return loadedList.listIterator(loadedList.size());
		
		currentObjectIndex++;
		objectsRun++;
		
		//Loop around
		if(!iterator.hasNext())
			return loadedList.iterator();
		else
			return iterator;
	}

	//Get the loaded list of Entities/TileEntities
	public List getWorldLoadedList(World world) {
		return world.loadedTileEntityList;
	}
}
