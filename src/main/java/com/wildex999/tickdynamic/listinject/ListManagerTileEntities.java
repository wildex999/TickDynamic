package com.wildex999.tickdynamic.listinject;

import java.util.Iterator;

import com.wildex999.tickdynamic.TickDynamicMod;

import net.minecraft.world.World;

public class ListManagerTileEntities extends ListManager {

	public CustomTimingsHandlerKCauldron timingsHandlerLoop;
	public CustomTimingsHandlerKCauldron timingsHandlerTick;
	
	private boolean updateStarted;
	private EntityIteratorTimed entityIterator;
	private EntityObject lastObj;
	
	public ListManagerTileEntities(World world, TickDynamicMod mod) {
		super(world, mod, EntityType.TileEntity);
		
		timingsHandlerLoop = new CustomTimingsHandlerKCauldron(world, true);
		timingsHandlerTick = new CustomTimingsHandlerKCauldron(world, false);
	}
	
	public void stopUpdate() {
		updateStarted = false;
		if(entityIterator != null)
			entityIterator.endUpdate();
	}
	
	@Override
	public int size() {
		if(!CustomTimingsHandlerKCauldron.inLoop || CustomTimingsHandlerKCauldron.inTick)
			return super.size();

		if(!updateStarted) {
			updateStarted = true;
			entityIterator = new EntityIteratorTimed(this, this.getAge());
		}
		
		//Verify we have a next element to move on to
		if(!entityIterator.hasNext())
		{
			updateStarted = false;
			return 0; //Should end
		}
		CustomTimingsHandlerKCauldron.currentTimer = timingsHandlerLoop;

		return super.size();
	}
	
	@Override
	public EntityObject get(int index) {
		if(!updateStarted || CustomTimingsHandlerKCauldron.inTick)
			lastObj = super.get(index);
		else
			lastObj = entityIterator.next();
		
		CustomTimingsHandlerKCauldron.currentTimer = lastObj.TD_originalTimer;
		return lastObj;
	}
	
	@Override
	public boolean add(EntityObject element) {
		if(super.add(element))
		{
			//Assert: All items on this list are TileEntities
			element.TD_selfTileEntity.tickTimer = timingsHandlerTick;
			
			return true;
		}
		
		return false;
	}
	
	@Override
	public EntityObject remove(int index) {
		if(!updateStarted || CustomTimingsHandlerKCauldron.inTick)
			return super.remove(index);
		
		//Fast remove the current Entity
		entityIterator.remove();
		return lastObj;
	}

}
