package com.wildex999.tickdynamic.listinject;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class EntityIterator implements Iterator<EntityObject> {
	
	private ListManager list;
	private int currentAge; //Used to verify if iterator is still valid(Concurrent modification)
	
	private EntityGroup currentGroup;
	private EntityObject currentObject;
	private Iterator<EntityGroup> groupIterator;
	private List<EntityObject> entityList;
	private int currentIndex;
	
	public EntityIterator(ListManager list, int age) {
		this.list = list;
		this.currentAge = age;
		this.groupIterator = list.getGroupIterator();
		this.currentIndex = 0;
	}
	
	@Override
	public boolean hasNext() {
		if(currentAge != list.age)
			throw new ConcurrentModificationException("List modified before going to next entry");
		
		while(entityList == null || currentIndex >= entityList.size()) {
			currentIndex = 0;
			entityList = null;
			
			if(!groupIterator.hasNext())
				return false;
			
			currentGroup = groupIterator.next();
			if(currentGroup == null)
				return false;
			
			entityList = currentGroup.entities;
		}
		
		return true;
	}
	
	@Override
	public EntityObject next() {
		if(currentAge != list.age)
			throw new ConcurrentModificationException("List modified before going to next entry");
		if(!hasNext())
			throw new NoSuchElementException();
		
		currentObject = entityList.get(currentIndex++);
		
		return currentObject;
	}
	
	@Override
	public void remove() {
		if(currentAge != list.age)
			throw new ConcurrentModificationException("List modified before going to next entry");
		if(currentObject == null)
			return;
		
		//Remove while maintaining the Iterator integrity and position
		list.remove(currentObject);
		currentAge++;
		currentIndex--;
		
		if(currentIndex < 0) //If we removed the first element
			currentIndex = 0;
	}
}
