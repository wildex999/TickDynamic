package com.wildex999.tickdynamic.timemanager;

import com.wildex999.tickdynamic.TickDynamicMod;

//TODO: timeUsed should use average for the last second or so, to handle a smaler subset of objects
//which uses a large amount of time.

public class TimedGroup implements ITimed {
	private int sliceMax; //Used by parent to rebalance timeMax
	private long timeMax; //How much time allowed to use, set by parent TimeManager when balancing
	private long timeUsed; //Measured time usage for objects(Not including child TimeManagers)
	private int objectsRun; //How many objects ran for the current timeUsed
	private long startTime; //Start of time measurement
	
	public TimedGroup(TickDynamicMod mod, String name) {
		mod.timedObjects.put(name, this);
	}
	
	//Time actual usage for this Manager, call before and after the objects have executed their time.
	//This time will be added to the current timeUsed.
	//Note: Only one startTimer() may be running at once!
	public void startTimer() {
		startTime = System.nanoTime();
	}
	
	public void endTimer(int objectCount) {
		timeUsed += System.nanoTime() - startTime;
		objectsRun += objectCount;
		
		System.out.println("TimedGroup endTimer time: " + timeUsed + "ns " + "Objects: " + objectsRun);
	}
	
	@Override
	public void setTimeMax(long newTimeMax) {
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
		//TODO: Use average
		return timeUsed;
	}
	
	//Calculate how many objects can run within the given timeMax.
	//It calculates this using an average for the time used by the objects.(timeUsed/objectCount)
	public int getTargetObjectCount() {
		if(timeMax == 0) //No limit set
			return Integer.MAX_VALUE;
		
		return (int)Math.ceil((double)timeUsed/(double)objectsRun);
	}
	
	//Get the number of objects that have run within the currently measured timeUsed
	public int getObjectsRun() {
		return objectsRun;
	}
	
	//Will also clear objectsRun
	@Override
	public void clearTimeUsed(boolean clearChildren) {
		timeUsed = 0;
		objectsRun = 0;
	}
	
	@Override
	public boolean isManager() {
		return false;
	}
}
