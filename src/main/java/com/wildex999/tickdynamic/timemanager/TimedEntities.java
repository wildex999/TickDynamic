package com.wildex999.tickdynamic.timemanager;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.wildex999.tickdynamic.TickDynamicMod;

import net.minecraft.world.World;

public class TimedEntities extends TimedGroup {
	protected int currentObjectIndex; //Current index into updating Objects
	protected int remainingObjects; //Remaining objects for current update cycle
	protected World world; //World for this group
	protected TimedGroup base;
	
	public static final String configKeyMinimumObjects = "minimumObjects";
	public static final String configKeyMinimumTPS = "minimumTPS";
	public static final String configKeyMinimumTime = "minimumTime";
	
	//Time reservation
	protected int minimumObjects; //Minimum number of objects to update each tick
	protected float minimumTPS; //Minimum TPS to maintain
	protected float minimumTime; //Time in Milliseconds
	
	//TPS calculation
	protected double currentTPS;
	protected LinkedList<Double> listTPS;
	public double averageTPS;

	
	public TimedEntities(TickDynamicMod mod, World world, String name, String configEntry, TimedGroup base) {
		super(mod, world, name, configEntry);
		this.base = base;
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
		
		loadConfig(true);
    }
	
	@Override
	public void loadConfig(boolean saveDefaults) {
		if(configEntry == null)
			return;
		
		int sliceMax = mod.defaultEntitySlicesMax;
		String comment = "The number of slices given to this Group";
		if(base != null && mod.config.hasKey(base.configEntry, configKeySlicesMax))
			sliceMax = mod.config.get(base.configEntry, configKeySlicesMax, sliceMax, comment).getInt();
		else
			sliceMax = mod.config.get(configEntry, configKeySlicesMax, sliceMax).getInt();
		setSliceMax(sliceMax);
		
		int minimumObjects = mod.defaultEntityMinimumObjects;
		comment = "Minimum number of objects to tick, independent of slices. Set to 0 to disable.";
		if(base != null && mod.config.hasKey(base.configEntry, configKeyMinimumObjects))
			minimumObjects = mod.config.get(base.configEntry, configKeyMinimumObjects, minimumObjects, comment).getInt();
		else
			minimumObjects = mod.config.get(configEntry, configKeyMinimumObjects, minimumObjects).getInt();
		setMinimumObjects(minimumObjects);

		float minimumTPS = mod.defaultEntityMinimumTPS;
		comment = "Minimum TPS to keep, independent of slices. Set to 0 to disable.";
		if(base != null && mod.config.hasKey(base.configEntry, configKeyMinimumTPS))
			minimumTPS = (float)mod.config.get(base.configEntry, configKeyMinimumTPS, minimumTPS, comment).getDouble();
		else
			minimumTPS = (float)mod.config.get(configEntry, configKeyMinimumTPS, minimumTPS).getDouble();
		setMinimumTPS(minimumTPS);
		
		float minimumTime = mod.defaultEntityMinimumTime;
		comment = "Minimum Time to keep(In milliseconds), independent of slices. Set to 0 to disable.";
		if(base != null && mod.config.hasKey(base.configEntry, configKeyMinimumTime))
			minimumTime = (float)mod.config.get(base.configEntry, configKeyMinimumTime, minimumTime, comment).getDouble();
		else
			minimumTime = (float)mod.config.get(configEntry, configKeyMinimumTime, minimumTime).getDouble();
		setMinimumTime(minimumTime);
		
		//Save any default values
		if(saveDefaults)
			mod.queueSaveConfig();
	}
	
	@Override
	public void writeConfig(boolean saveFile) {
		if(configEntry == null)
			return;
		
		//TODO
		//TODO: Don't write if same as base value
		/*mod.config.get(configEntry, configKeySlicesMax, mod.defaultEntitySlicesMax).setValue(getSliceMax());
		mod.config.get(configEntry, configKeyMinimumObjects, mod.defaultEntityMinimumObjects).setValue(getMinimumObjects());
		mod.config.get(configEntry, configKeyMinimumTPS, mod.defaultEntityMinimumTPS).setValue(getMinimumTPS());
		mod.config.get(configEntry, configKeyMinimumTime, mod.defaultEntityMinimumTime).setValue(getMinimumTime());*/
		
		if(saveFile)
			mod.queueSaveConfig();
	}
	
	public void setMinimumObjects(int minimum) {
		minimumObjects = minimum;
	}
	public int getMinimumObjects() {
		return minimumObjects;
	}
	
	public void setMinimumTPS(float minimum) {
		minimumTPS = minimum;
	}
	public float getMinimumTPS() {
		return minimumTPS;
	}
	
	public void setMinimumTime(float minimum) {
		minimumTime = minimum;
	}
	public float getMinimumTime() {
		return minimumTime;
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
	
	@Override
	public long getReservedTime() {
		if(sliceMax == 0)
			return getTimeUsedAverage();
		
		//Return the minimum which requires most time
		long reservedObjects = 0;
		double timePerObject = (int)Math.ceil((double)getTimeUsedAverage()/(double)getObjectsRunAverage());
		if(getMinimumObjects() > 0)
			reservedObjects = (long) (timePerObject * getMinimumObjects());
		
		long reservedTPS = 0;
		if(getMinimumTPS() > 0 && world != null)
			reservedTPS = (long) ((getWorldLoadedList(world).size() / 20.0) * getMinimumTPS());
		
		long reservedTime = 0;
		if(getMinimumTime() > 0)
			reservedTime = (long) (getMinimumTime() * timeMilisecond);
		
		long reserved = reservedObjects;
		if(reservedTPS > reserved)
			reserved = reservedTPS;
		if(reservedTime > reserved)
			reserved =reservedTime;
		
		return reserved;
	}

	//Get the loaded list of Entities/TileEntities
	public List getWorldLoadedList(World world) {
		return world.loadedTileEntityList;
	}
}
