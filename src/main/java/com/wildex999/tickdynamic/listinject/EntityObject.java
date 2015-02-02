package com.wildex999.tickdynamic.listinject;

//Injected as a base class for both Entity and TileEntity to avoid having to cast inside ListIterator and ListManager.
//Will also be used to add some methods and fields at a later point.

public class EntityObject {
	public ListManager TD_entityList;
}
