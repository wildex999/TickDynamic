package com.wildex999.tickdynamic.timemanager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.wildex999.tickdynamic.TickDynamicMod;

//Written by Kai Roar Stjern ( wildex999@gmail.com )

//TimeManager is in charge of managing the time given to timed objects
//An object can either be a group(Like Entities in a world) or other TimeManagers(Which is in charge of other groups and TimeManagers)

//Whenever objects managed by this manager uses the time, this manager will measure it
//and then calculate how many objects should run to achieve the managers max time when balanced with
//child TimeManager's.

//Time slices are used to calculate the percentage of the parent's timeMax to give each child:
//sliceMax / allSlices = percentage.
//So, 100 vs 100 slices = 100 / 200 = 0.5, so 50%
//50 vs 125 = 50 / 175 = ~0.29, so 29% etc.

//Note: The TimeManager is reactive, not proactive! 
//This means each TimeManager will overrun and underrun it's time allotment over a period
//as it adjusts to changes in time usage per object.

public class TimeManager implements ITimed {
	private int sliceMax; //Used by parent to rebalance timeMax
	
	private long timeMax; //How much time allowed to use, set by parent TimeManager when balancing
	private long timeUsed; //Measured time usage for objects(Not including child TimeManagers)
	
	List<ITimed> children;
	
	public TimeManager(TickDynamicMod mod, String name) {
		children = new ArrayList<ITimed>();
		mod.timedObjects.put(name, this);
	}
	
	//Looks at current usage and rebalances the max time usage according to slices.
	//Will call balanceTime() on children when done balancing, to propagate the new timeMax.
	public void balanceTime() {
		//If there is no max time, then there is none for the children either
		if(timeMax == 0)
		{
			for(Iterator<ITimed> it = children.iterator(); it.hasNext(); )
				it.next().setTimeMax(0);
			return;
		}
		
		//Calculate new timeMax from slices
		long leftover = 0;
		int allSlices = 0;
		List<ITimed> childrenLeft = new ArrayList<ITimed>(children);
		for(Iterator<ITimed> it = children.iterator(); it.hasNext(); )
			allSlices += it.next().getSliceMax();
		//Round 1
		for(Iterator<ITimed> it = childrenLeft.iterator(); it.hasNext(); )
		{
			ITimed child = it.next();
			child.setTimeMax((long)(timeMax * ((double)child.getSliceMax() / (double)allSlices)) );
			long left = child.getTimeMax() - child.getTimeUsed();
			if(left > 0)
				leftover += left;
		}
		
		//Calculate and redistribute leftovers according to slices(Round 2+)
		while(leftover > 0 && childrenLeft.size() > 0)
		{
			long before = leftover; //Store the value as we make changes to leftover as we go
			for(Iterator<ITimed> it = childrenLeft.iterator(); it.hasNext(); )
			{
				ITimed child = it.next();
				long currentMax = child.getTimeMax();
				long slice = (long)(before * ((double)child.getSliceMax() / (double)allSlices));
				currentMax += slice;
				leftover -= slice;
		
				//If more than 1% to spare, put into leftover pool
				long left = currentMax - child.getTimeUsed();
				if(left > (currentMax/100.0))
				{
					//Give back what will be unused
					leftover += (left-(currentMax/100.0)); //Leave 1% for further growth
					it.remove(); //Remove for further distribution
					allSlices -= child.getSliceMax(); //Remove it's contribution to allSlices so percentages becomes correct
				}
				child.setTimeMax(currentMax); //Update the max
			}
		}
		
		
		
	}
	
	public void addChild(ITimed object) {
		children.add(object);
	}
	public void removeChild(ITimed object) {
		children.remove(object);
	}
	
	//Set the current time allotment for this TimeManager
	@Override
	public void setTimeMax(long newTimeMax) {
		timeMax = newTimeMax;
	}
	@Override
	public long getTimeMax() { 
		return timeMax; 
	}
	
	//Set the number of slices for this TimeManager
	@Override
	public void setSliceMax(int newSliceMax) {
		sliceMax = newSliceMax;
	}
	@Override
	public int getSliceMax() {
		return sliceMax;
	}

	//Get the last measured time used for the objects in this TimeManager(Including child TimeManagers)
	//Note: This will recursively call down the whole child tree, cache this value when possible.
	@Override
	public long getTimeUsed() {
		long output = timeUsed;
		
		for(Iterator<ITimed> it = children.iterator(); it.hasNext();)
		{
			ITimed child = it.next();
			output += child.getTimeUsed();
		}
		
		return output;
	}
	
	//Clear the current timeUsed. Usually called at the beginning of a new tick.
	//clearChildren: Whether to also clear for children(Who will clear for their children)(Recursion)
	@Override
	public void clearTimeUsed(boolean clearChildren) {
		//TODO: Write current timeUsed into a table for computing averages
		timeUsed = 0;
		
		if(clearChildren)
		{
			for(Iterator<ITimed> it = children.iterator(); it.hasNext();)
				it.next().clearTimeUsed(true);
		}
			
	}
	
	@Override
	public boolean isManager() {
		return true;
	}
	
}
