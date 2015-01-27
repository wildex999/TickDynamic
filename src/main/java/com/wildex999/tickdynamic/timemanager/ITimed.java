package com.wildex999.tickdynamic.timemanager;

import java.util.Iterator;

public interface ITimed {
	//Easy conversion to nanoseconds
	public static final long timeMilisecond = 1000000;
	public static final long timeSecond = 1000000000;
	public static final String configKeySlicesMax = "slicesMax";

	//Set the current time allotment for this object(In nanoseconds)
	public void setTimeMax(long newTimeMax);
	public long getTimeMax();
	
	//Set the number of time slices for this object
	public void setSliceMax(int newSliceMax);
	public int getSliceMax();

	//Get the last measured time used for the objects in this object(Including any children)(In nanoseconds)
	//Note: This will recursively call down the whole child tree, cache this value when possible.
	public long getTimeUsed();
	public long getTimeUsedAverage(); 
	public long getTimeUsedLast();  //Time used last tick
		
	//Called at the beginning of a new tick to prepare for new time capture etc.
	//clearChildren: Whether to also call for children(Who will call for their children)(Recursion)
	public void newTick(boolean clearChildren);
	
	//Simple check to differentiate the objects and managers
	public boolean isManager();
}
