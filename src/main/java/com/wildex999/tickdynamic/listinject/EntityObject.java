package com.wildex999.tickdynamic.listinject;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

//Injected as a base class for both Entity and TileEntity to avoid having to cast inside ListIterator and ListManager.
//Will also be used to add some methods and fields without having to inject each.

public class EntityObject {
	public EntityGroup TD_entityGroup;
	public Entity TD_selfEntity; //Avoid casting
	public TileEntity TD_selfTileEntity; //Avoid casting
	public boolean TD_selfInit = false;

	//Initialize the EntityObject, usually called when added to an Entity List.
	public void TD_Init(EntityGroup group) {
		if(!TD_selfInit)
		{
			Object self = this;
			if(self instanceof Entity)
				TD_selfEntity = (Entity) self;
			else if(self instanceof TileEntity)
				TD_selfTileEntity = (TileEntity) self;
			TD_selfInit = true;
		}
		TD_entityGroup = group;
	}
	
	//Called when EntityObject is removed from an Entity list.
	public void TD_Deinit() {
		TD_entityGroup = null;
	}
}
