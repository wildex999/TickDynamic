package com.wildex999.tickdynamic.timemanager;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.wildex999.tickdynamic.TickDynamicMod;

import net.minecraft.world.World;

public class TimedTileEntities extends TimedGroup {
	protected int currentObjectIndex; //Current index into updating Objects
	protected int remainingObjects; //Remaining objects for current update cycle
	protected World world; //World for this group
	
	//TPS calculation
	protected double currentTPS;
	protected LinkedList<Double> listTPS;
	public double averageTPS;

	
	public TimedTileEntities(TickDynamicMod mod, World world, String name, String configEntry) {
		super(mod, world, name, configEntry);
		
	}
	
    //Initialize a timed Tile Entity group, reading in the configuration if it exists.
    //If no configuration exits, create a new default.
	@Override
    public void init() {
		timeUsed = 0;
		objectsRun = 0;
		currentTPS = 0;
		listTPS = new LinkedList<Double>();
		setTimeMax(0);
		
		if(configEntry != null)
		{
			loadConfig(true);
		}
		else
		{
			setSliceMax(mod.defaultTileEntitySlicesMax);
			setMinimumObjects(mod.defaultTileEntityMinimumObjects);
		}

    }
	
	@Override
	public void loadConfig(boolean saveDefaults) {
		if(configEntry == null)
			return;
		
		setSliceMax(mod.config.get(configEntry, configKeySlicesMax, mod.defaultTileEntitySlicesMax).getInt());
		setMinimumObjects(mod.config.get(configEntry, configKeyMinimumObjects, mod.defaultTileEntityMinimumObjects).getInt());
		
		//Save any default values
		if(saveDefaults)
			mod.config.save();
	}
	
	@Override
	public void writeConfig(boolean saveFile) {
		if(configEntry == null)
			return;
		
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
		
		//Calculate TPS
		if(loadedList.size() > 0)
			currentTPS += (remainingObjects/(double)loadedList.size())*20.0;
		else
			currentTPS += 20;
		
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
	
	@Override
	public void newTick(boolean clearChildren) {
		super.newTick(clearChildren);
		
		//Calculate average TPS
		if(listTPS.size() >= mod.defaultAverageTicks)
			listTPS.removeFirst();
		listTPS.add(currentTPS);
		
		currentTPS = 0;
		averageTPS = 0;
		for(double tps : listTPS)
			averageTPS += tps;
		averageTPS = averageTPS / listTPS.size();
	}

	//Get the loaded list of Entities/TileEntities
	public List getWorldLoadedList(World world) {
		return world.loadedTileEntityList;
	}
}
