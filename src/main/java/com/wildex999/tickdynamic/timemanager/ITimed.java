package com.wildex999.tickdynamic.timemanager;

import java.util.Iterator;

public interface ITimed {
	
	//Easy conversion to nanoseconds
	public static final long timeMilisecond = 1000000;
	public static final long timeSecond = 1000000000;
	public static final String configKeySlicesMax = "slicesMax";

	//Initialize, reading in the configuration if it exists.
    //If no configuration exits, create a new default.
    public void init();
    
    //Load config
    //saveDefaults: Whether to save to file when done, to save any potential new defaults
    public void loadConfig(boolean saveDefaults);

    //Write changes to config, and save to file it set
    public void writeConfig(boolean saveFile);
    
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
	
	//Return the time reserved(Itself + Any children with sliceMax == 0 or any other limit which requires a reservation)
	public long getReservedTime();
		
	//Called at the beginning of a new tick to prepare for new time capture etc.
	//recursive: Whether to also call for children(Who will call for their children)(Recursion)
	public void newTick(boolean recursive);
	
	//Called at the end of a tick, before new time balance
	public void endTick(boolean recursive);
	
	//Simple check to differentiate the objects and managers
	public boolean isManager();
	
	public String getName();
}
