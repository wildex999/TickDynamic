package com.wildex999.tickdynamic.listinject;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

//Injected as a base class for both Entity and TileEntity to avoid having to cast inside ListIterator and ListManager.
//Will also be used to add some methods and fields without having to inject each.

public class EntityObject {
	public ListManager TD_entityList;
	public Entity TD_selfEntity; //Avoid casting
	public TileEntity TD_selfTileEntity; //Avoid casting

	//Initialize the EntityObject, usually called when added to an Entity List.
	public void TD_Init() {
		Object self = this;
		if(self instanceof Entity)
			TD_selfEntity = (Entity) self;
		else if(self instanceof TileEntity)
			TD_selfTileEntity = (TileEntity) self;
	}
}
