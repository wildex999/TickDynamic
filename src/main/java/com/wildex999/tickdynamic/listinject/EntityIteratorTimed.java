package com.wildex999.tickdynamic.listinject;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.wildex999.tickdynamic.TickDynamicMod;

/*
 * Iterator will continue from each group at the given offset and only iterate for the given number
 * of Entities as dictated by the time manager.
 * It will also take care of timing each group.
 */

public class EntityIteratorTimed implements Iterator<EntityObject> {
	
	private ListManager list;
	private int currentAge; //Used to verify if iterator is still valid(Concurrent modification)
	
	private int remainingCount; //Number of entities left to update for this group
	private int currentOffset; //Offset in current entity list
	private int updateCount;
	private boolean startedTimer;
	
	private EntityGroup currentGroup;
	private EntityObject currentObject;
	private Iterator<EntityGroup> groupIterator;
	private List<EntityObject> entityList;
	
	public EntityIteratorTimed(ListManager list, int currentAge) {
		this.list = list;
		this.currentAge = currentAge;
		this.groupIterator = list.getGroupIterator();
		this.remainingCount = 0;
		this.startedTimer = false;
	}
	
	@Override
	public boolean hasNext() {
		if(currentAge != list.age)
			throw new ConcurrentModificationException("List modified before going to next entry.");
		if(remainingCount > 0 && entityList.size() > 0)
			return true;
		
		//Find next group and end timer on current group
		if(startedTimer && currentGroup != null)
		{
			currentGroup.timedGroup.endUpdateObjects(updateCount);
			currentGroup.timedGroup.endTimer();
		}
		
		updateCount = 0;
		currentGroup = null;
		startedTimer = false;
		entityList = null;
		while(entityList == null) {
			if(!groupIterator.hasNext())
				return false;
			currentGroup = groupIterator.next();
			
			entityList = currentGroup.entities;
			if(entityList.size() <= 0)
			{
				entityList = null;
				continue;
			}
			
			//currentOffset = 0;
			//remainingCount = entityList.size();
			currentOffset = currentGroup.timedGroup.startUpdateObjects();
			remainingCount = currentGroup.timedGroup.getUpdateCount();
			updateCount = 0;
		}
		
		return true;
	}

	@Override
	public EntityObject next() {
		if(currentAge != list.age)
			throw new ConcurrentModificationException("List modified before going to next entry");
		if(!hasNext()) //hasNext will also setup next group if necessary(Usually called before next anyway)
			throw new NoSuchElementException();
		
		if(!startedTimer) {
			startedTimer = true;
			currentGroup.timedGroup.startTimer();
		}
		
		if(currentOffset >= entityList.size()) { //Loop around
			currentOffset = 0;
		}
		
		currentObject = entityList.get(currentOffset);
		remainingCount--;
		currentOffset++;
		updateCount++;
		
		return currentObject;
	}
	
	@Override
	public void remove() {
		if(currentAge != list.age)
			throw new ConcurrentModificationException("List modified before going to next entry");
		if(currentObject == null)
			return;
		
		//Remove while maintaining the Iterator integrity and position
		if(list.remove(currentObject))
		{
			currentAge++;
			currentOffset--;
		}
		else
			System.err.println("Failed to remove: " + currentObject + " from loaded entity list!");
		
		if(currentAge != list.age)
			throw new RuntimeException("ASSERT FAILED: " + currentAge + " : " + list.age);
		
		if(currentOffset < 0) //If we removed the first element
			currentOffset = 0;
	}

}
