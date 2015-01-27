package com.wildex999.tickdynamic.timemanager;

import java.util.Iterator;
import java.util.LinkedList;

import net.minecraft.world.World;

import com.wildex999.tickdynamic.TickDynamicMod;

//TODO: timeUsed should use average for the last second or so, to handle a smaler subset of objects
//which uses a large amount of time.

public class TimedGroup implements ITimed {
	protected int sliceMax; //Used by parent to rebalance timeMax
	protected long timeMax; //How much time allowed to use, set by parent TimeManager when balancing
	protected long timeUsed; //Measured time usage for objects
	protected long timeUsedAverage;
	protected long prevTimeUsed; //timeUsed from previous tick
	protected int objectsRun; //How many objects ran for the current timeUsed
	protected int objectsRunAverage;
	protected int prevObjectsRun; //objectsRun from previous tick
	protected int minimumObjects; //Minimum number of objects to update each tick
	protected long startTime; //Start of time measurement
	
	protected int averageTicks = 20; //How many ticks back to average over
	protected LinkedList<Long> listTimeUsed; //List used for calculating average timeUsed
	protected LinkedList<Integer> listObjectsRun;
	
	public final String name;
	public final TickDynamicMod mod;
	
	public static final String configKeyMinimumObjects = "minimumObjects";
	
	public enum GroupType {
		TileEntity,
		Entity,
		Other
	}
	
	public TimedGroup(TickDynamicMod mod, String name) {
		mod.timedObjects.put(name, this);
		this.name = name;
		this.mod = mod;
		
		listTimeUsed = new LinkedList<Long>();
		listObjectsRun = new LinkedList<Integer>();
	}
	
    //Initialize a timed group, reading in the configuration if it exists.
    //If no configuration exits, create a new default.
	@Override
    public void init(String configEntry) {
		timeUsed = 0;
		objectsRun = 0;
		setTimeMax(0);
		
		int configSlices = 100;
		int configMinimumObjects = 1;
		if(configEntry != null)
		{
			configSlices = mod.config.get(configEntry, configKeySlicesMax, configSlices).getInt();
			configMinimumObjects = mod.config.get(configEntry, configKeyMinimumObjects, configMinimumObjects).getInt();
		}
		setSliceMax(configSlices);
		setMinimumObjects(configMinimumObjects);
    }
	
	@Override 
	public void loadConfig(String configEntry) {
		
	}
	
	@Override 
	public void writeConfig(String configEntry, boolean saveFile) {
		
	}
	
	//Time actual usage for this Group, call before and after the objects have executed their time.
	//This time will be added to the current timeUsed.
	//Note: Only one startTimer() may be running at once!
	public void startTimer() {
		startTime = System.nanoTime();
	}
	
	public void endTimer() {
		timeUsed += System.nanoTime() - startTime;
	}
	
	@Override
	public void setTimeMax(long newTimeMax) {
		if(TickDynamicMod.debug)
			System.out.println(name + ": setTimeMax: " + newTimeMax + " timeUsed: " + timeUsed);
		timeMax = newTimeMax;
	}
	@Override
	public long getTimeMax() {
		return timeMax;
	}
	@Override
	public void setSliceMax(int newSliceMax) {
		sliceMax = newSliceMax;
	}
	@Override
	public int getSliceMax() {
		return sliceMax;
	}
	@Override
	public long getTimeUsed() {
		return timeUsed;
	}
	@Override
	public long getTimeUsedAverage() {
		return timeUsedAverage;
	}
	@Override
	public long getTimeUsedLast() {
		return prevTimeUsed;
	}
	
	public void setMinimumObjects(int minimum) {
		minimumObjects = minimum;
	}
	public int getMinimumObjects() {
		return minimumObjects;
	}
	
	//Get the number of objects that have run within the currently measured timeUsed
	public int getObjectsRun() {
		return objectsRun;
	}
	public int getObjectsRunAverage() {
		return objectsRunAverage;
	}
	public int getObjectsRunLast() {
		return prevObjectsRun;
	}
	
	//Will also clear objectsRun
	@Override
	public void newTick(boolean clearChildren) {
		prevTimeUsed = timeUsed;
		prevObjectsRun = objectsRun;
		timeUsed = 0;
		objectsRun = 0;
		
		//Average timeUsed
		if(listTimeUsed.size() >= averageTicks)
			listTimeUsed.removeFirst();
		listTimeUsed.add(prevTimeUsed);
		timeUsedAverage = 0;
		for(Long time : listTimeUsed)
			timeUsedAverage += time;
		timeUsedAverage = timeUsedAverage/listTimeUsed.size();
		
		//Average objectsRun
		if(listObjectsRun.size() >= averageTicks)
			listObjectsRun.removeFirst();
		listObjectsRun.add(prevObjectsRun);
		objectsRunAverage = 0;
		for(Integer count : listObjectsRun)
			objectsRunAverage += count;
		objectsRunAverage = objectsRunAverage/listObjectsRun.size();
	}
	
	@Override
	public boolean isManager() {
		return false;
	}
}
