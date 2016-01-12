package com.wildex999.tickdynamic.listinject;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.wildex999.tickdynamic.TickDynamicMod;

import net.minecraft.world.World;

//The World Entities loop does not use iterators, so we have to handle it specially

public class ListManagerEntities extends ListManager {

	//Flags to check when Entity update loops start
	public boolean tickStarted; //Flag 1
	public boolean flagRemoveAll; //Flag 2
	private boolean updateStarted;
	private boolean doIterate; //Only the first get after size() should continue the iteration
	private List unloadList; //Used to check when we should start updating
	
	private int loopCount;
	private Iterator<EntityObject> entityIterator;
	private EntityObject lastObj;
	
	public ListManagerEntities(World world, TickDynamicMod mod) {
		super(world, mod, EntityType.Entity);
		try {
			Field unloadedField = net.minecraft.world.World.class.getDeclaredField("unloadedEntityList");
			unloadedField.setAccessible(true);
			unloadList = (List) unloadedField.get(world);
		} catch(Exception e) {
			System.err.println(e);
			throw new RuntimeException("TickDynamic failed to get field 'unloadedEntityList'. There might be some obfuscation problem.");
		}
	}
	
	@Override
	public boolean removeAll(Collection<?> c) {
		if(tickStarted && c == unloadList)
			flagRemoveAll = true;
		else
			flagRemoveAll = false;
		
		return super.removeAll(c);
	}
	
	@Override
	public int size() {
		if(!tickStarted || !flagRemoveAll)
			return super.size();
		
		if(!updateStarted) {
			updateStarted = true;
			
			entityIterator = new EntityIteratorTimed(this, this.getAge());
			loopCount = 0; //Start the counter
		}
		
		//Verify we have a next element to move on to
		if(!entityIterator.hasNext())
		{
			updateStarted = false;
			flagRemoveAll = false;
			tickStarted = false; //Make sure we don't accidentally start it somewhere else during TileEntity ticking
			
			return 0; //Should end
		}
		
		doIterate = true;
		//Return one larger number than previously until our iterator has no more elements
		return ++loopCount;
	}
	
	@Override
	public EntityObject get(int index) {
		if(!updateStarted) {
			return super.get(index);
		}
		
		if(!doIterate)
			return super.get(index);
		
		lastObj = entityIterator.next();
		doIterate = false; //Any get's before the next size() is not part of the timed
		//System.out.println("Get: " + loopCount);
		
		return lastObj;
	}
	
	@Override
	public EntityObject remove(int index) {
		if(!updateStarted)
			return super.remove(index);
		
		//Fast remove the current Entity
		entityIterator.remove();
		return lastObj;
	}
	
	//Iterators: Return non-timed(For now. Later we might to return timed if they start using iterators)
	@Override
	public ListIterator<EntityObject> listIterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ListIterator<EntityObject> listIterator(int index) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public Iterator<EntityObject> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

}
