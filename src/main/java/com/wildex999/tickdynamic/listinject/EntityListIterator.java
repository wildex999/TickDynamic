package com.wildex999.tickdynamic.listinject;

import java.util.ListIterator;

public class EntityListIterator implements ListIterator<EntityObject> {

	private ListManager list;
	
	
	public EntityListIterator(ListManager list) {
		this.list = list;
	}
	
	@Override
	public void add(EntityObject entityObject) {
		list.add(entityObject);
	}

	@Override
	public boolean hasNext() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean hasPrevious() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public EntityObject next() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int nextIndex() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public EntityObject previous() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int previousIndex() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void remove() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void set(EntityObject arg0) {
		// TODO Auto-generated method stub
		
	}

}
