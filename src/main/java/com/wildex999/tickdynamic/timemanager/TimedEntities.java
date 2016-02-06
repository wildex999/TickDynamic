package com.wildex999.tickdynamic.timemanager;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.wildex999.tickdynamic.TickDynamicMod;
import com.wildex999.tickdynamic.listinject.EntityGroup;

import net.minecraft.world.World;

public class TimedEntities extends TimedGroup {
	protected int currentObjectIndex; //Current index into updating Objects
	protected int updateCount; //Number of entities to update this tick
	protected TimedGroup base;
	protected EntityGroup entityGroup;
	
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
		currentTPS = 0;
		objectsRun = 0;
		listTPS = new LinkedList<Double>();
		setTimeMax(0);
		
		if(this.base == null) {
			setSliceMax(mod.defaultEntitySlicesMax);
			setMinimumObjects(mod.defaultEntityMinimumObjects);
			setMinimumTPS(mod.defaultEntityMinimumTPS);
			setMinimumTime(mod.defaultEntityMinimumTime);
		} else {
			setSliceMax(base.getSliceMax());
			if(base instanceof TimedEntities) {
				TimedEntities baseEntities = (TimedEntities)base;
				setMinimumObjects(baseEntities.getMinimumObjects());
				setMinimumTPS(baseEntities.getMinimumTPS());
				setMinimumTime(baseEntities.getMinimumTime());
			}
		}
		
		loadConfig(true);
    }
	
	@Override
	public void loadConfig(boolean saveDefaults) {
		if(configEntry == null)
			return;
		
		String comment = "The number of slices given to this Group";
		if(base != null && !mod.config.hasKey(configEntry, configKeySlicesMax))
			sliceMax = mod.config.get(base.configEntry, configKeySlicesMax, sliceMax, comment).getInt();
		else
			sliceMax = mod.config.get(configEntry, configKeySlicesMax, sliceMax, comment).getInt();
		setSliceMax(sliceMax);
		
		comment = "Minimum number of objects to tick, independent of slices. Set to 0 to disable.";
		if(base != null && !mod.config.hasKey(configEntry, configKeyMinimumObjects))
			minimumObjects = mod.config.get(base.configEntry, configKeyMinimumObjects, minimumObjects, comment).getInt();
		else
			minimumObjects = mod.config.get(configEntry, configKeyMinimumObjects, minimumObjects, comment).getInt();
		setMinimumObjects(minimumObjects);

		comment = "Minimum TPS to keep, independent of slices. Set to 0 to disable.";
		if(base != null && !mod.config.hasKey(configEntry, configKeyMinimumTPS))
			minimumTPS = (float)mod.config.get(base.configEntry, configKeyMinimumTPS, minimumTPS, comment).getDouble();
		else
			minimumTPS = (float)mod.config.get(configEntry, configKeyMinimumTPS, minimumTPS, comment).getDouble();
		setMinimumTPS(minimumTPS);
		
		comment = "Minimum Time to keep(In milliseconds), independent of slices. Set to 0 to disable.";
		if(base != null && !mod.config.hasKey(configEntry, configKeyMinimumTime))
			minimumTime = (float)mod.config.get(base.configEntry, configKeyMinimumTime, minimumTime, comment).getDouble();
		else
			minimumTime = (float)mod.config.get(configEntry, configKeyMinimumTime, minimumTime, comment).getDouble();
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
		if(TickDynamicMod.debugTimer)
			System.out.println(name + ": getTargetObjectCount: timeUsed:" + getTimeUsedAverage() + " objectsRun: " + getObjectsRunAverage() + " timePerObject: " + timePerObject + " timeMax: " + timeMax);
		return (int) Math.ceil(timeMax / timePerObject);
	}

	public void setEntityGroup(EntityGroup entityGroup) {
		this.entityGroup = entityGroup;
	}
	
	public EntityGroup getEntityGroup() {
		return entityGroup;
	}
	
	//Get the current offset into the list of objects(Entities/TileEntities)
	public int getCurrentObjectIndex() {
		return currentObjectIndex;
	}
	
	//Update Current index
	public void setCurrentObjectIndex(int index) {
		if(index < 0)
			currentObjectIndex = 0;
		else
			currentObjectIndex = index;
	}

	//Called before the loop for updating objects in the group.
	//Calculates how many objects to update in this loop.
	//Return: The starting offset
	public int startUpdateObjects() {
		if(entityGroup == null)
		{
			if(mod.debug)
				System.err.println("No EntityGroup for group:" + this.getName());
			return 0; //No entities to time
		}
		
		int listSize = entityGroup.getEntityCount();
		
		if(!mod.enabled)
			return listSize; //If disabled, update everything
		
		updateCount = getTargetObjectCount();
		if(updateCount < minimumObjects)
			updateCount = minimumObjects;
		if(updateCount > listSize)
			updateCount = listSize;
		
		//Calculate TPS
		if(listSize > 0)
			currentTPS = (updateCount/(double)listSize)*20.0;
		else
			currentTPS = 20;
		
		if(currentObjectIndex >= listSize)
			currentObjectIndex = 0;
		if(TickDynamicMod.debugTimer)
			System.out.println("Start ("+ name +"). Update Offset: " + currentObjectIndex + " | "
				+ "Updating: " + updateCount + " of " + listSize);
		
		return currentObjectIndex;
	}
	
	//Get number of entities to update this tick.
	//Count updates when calling startUpdateObjects()
	public int getUpdateCount() {
		return updateCount;
	}
	
	//Called at the end of the update for entities
	//Keeps track of position for next run.
	//Takes the number of entities that was updated.
	public void endUpdateObjects(int entitiesUpdated) {
		if(!mod.enabled || entityGroup == null)
			return;
		
		int listSize = entityGroup.getEntityCount();
		objectsRun += entitiesUpdated;
		currentObjectIndex += entitiesUpdated;
		
		while(currentObjectIndex >= listSize)
			currentObjectIndex -= listSize;
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
			reservedTPS = (long) ((getEntitiesCount() / 20.0) * getMinimumTPS());
		
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

	public int getEntitiesCount() {
		if(entityGroup == null)
			return 0;
		return entityGroup.entities.size();
	}
}
