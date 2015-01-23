package com.wildex999.tickdynamic.timemanager;

import java.util.Iterator;

public interface ITimed {
	//Set the current time allotment for this object(In nanoseconds)
	public void setTimeMax(long newTimeMax);
	public long getTimeMax();
	
	//Set the number of time slices for this object
	public void setSliceMax(int newSliceMax);
	public int getSliceMax();

	//Get the last measured time used for the objects in this object(Including any children)(In nanoseconds)
	//Note: This will recursively call down the whole child tree, cache this value when possible.
	public long getTimeUsed();
		
	//Clear the current timeUsed. Usually called at the beginning of a new tick.
	//clearChildren: Whether to also clear for children(Who will clear for their children)(Recursion)
	public void clearTimeUsed(boolean clearChildren);
	
	//Simple check to differentiate the objects and managers
	public boolean isManager();
}
