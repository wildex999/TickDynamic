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
	private List<ITimed> children;
	
	public final String name;
	public final TickDynamicMod mod;
	
	public TimeManager(TickDynamicMod mod, String name) {
		children = new ArrayList<ITimed>();
		mod.timedObjects.put(name, this);
		this.name = name;
		this.mod = mod;
	}
	
	//Looks at current usage and rebalances the max time usage according to slices.
	//Will call balanceTime() on children when done balancing, to propagate the new timeMax.
	public void balanceTime() {
		if(TickDynamicMod.debug)
			System.out.println(name + ": balanceTime for " + children.size() + " children, with " + timeMax + " to give.");
		
		long leftover = 0;
		int allSlices = 0;
		int allSlicesPrev;
		List<ITimed> childrenLeft = new ArrayList<ITimed>(children);
		leftover = timeMax;
		
		//Set the initial distribution
		for(Iterator<ITimed> it = childrenLeft.iterator(); it.hasNext(); )
		{
			ITimed timed = it.next();
			timed.setTimeMax(0);
			allSlices += timed.getSliceMax();
			
			//Special cases: sliceMax == 0. These will not be limited by the timeMax, but will still take time from the others
			if(timed.getSliceMax() == 0)
			{
				leftover -= timed.getTimeUsedAverage();
				it.remove();
				if(leftover <= 0)
					leftover = 1;
			}
		}
		allSlicesPrev = allSlices; //Used for last redistribution of leftover
		
		//Calculate and redistribute according to slices
		while(leftover > 0 && childrenLeft.size() > 0)
		{
			long before = leftover; //Store the value as we make changes to leftover as we go
			if(TickDynamicMod.debug)
				System.out.println("Leftover: " + leftover);
			for(Iterator<ITimed> it = childrenLeft.iterator(); it.hasNext(); )
			{
				ITimed child = it.next();
				long slice = 1 + (long)(before * ((double)child.getSliceMax() / (double)allSlices)); //A slice can't be 0
				long currentMax = child.getTimeMax() + slice;
				if(currentMax > 0)
					leftover -= slice;
		
				//If more than 1% to spare, put into leftover pool
				long left = currentMax - child.getTimeUsedAverage();
				if(left > (currentMax/100.0))
				{
					//Give back what will be unused
					long giveBack = (long) (left-(currentMax/100.0)); //Leave 1% for further growth
					leftover += giveBack; 
					currentMax -= giveBack;
					it.remove(); //Remove for further distribution
					allSlices -= child.getSliceMax(); //Remove it's contribution to allSlices so percentages becomes correct
				}
				child.setTimeMax(currentMax+1); //Update the max
			}
		}
		
		//Give back any remaining leftover for growth
		if(leftover > 0)
		{
			for(ITimed child : children ) {
				long slice = (long)(leftover * ((double)child.getSliceMax() / (double)allSlicesPrev)); //A slice can't be 0
				child.setTimeMax(child.getTimeMax() + slice);
			}
		}
			
		
		//Initiate rebalance on children
		for(ITimed child : children) {
			if(child.isManager())
				((TimeManager)child).balanceTime();
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
		if(TickDynamicMod.debug)
			System.out.println(name + ": setTimeMax: " + newTimeMax);
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
		long output = 0;
		
		for(Iterator<ITimed> it = children.iterator(); it.hasNext();)
		{
			ITimed child = it.next();
			output += child.getTimeUsed();
		}
		
		return output;
	}
	public long getTimeUsedAverage() {
		long output = 0;
		
		for(Iterator<ITimed> it = children.iterator(); it.hasNext();)
		{
			ITimed child = it.next();
			output += child.getTimeUsedAverage();
		}
		
		return output;
	}
	public long getTimeUsedLast() {
		long output = 0;
		
		for(Iterator<ITimed> it = children.iterator(); it.hasNext();)
		{
			ITimed child = it.next();
			output += child.getTimeUsedLast();
		}
		
		return output;
	}
	
	//Called at the beginning of a new tick to prepare for new time capture etc.
	//clearChildren: Whether to also call for children(Who will call for their children)(Recursion)
	@Override
	public void newTick(boolean clearChildren) {
		
		if(clearChildren)
		{
			for(Iterator<ITimed> it = children.iterator(); it.hasNext();)
				it.next().newTick(true);
		}
			
	}
	
	@Override
	public boolean isManager() {
		return true;
	}
	
}
