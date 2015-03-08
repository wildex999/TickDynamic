package com.wildex999.tickdynamic.listinject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import com.wildex999.tickdynamic.TickDynamicMod;
import com.wildex999.tickdynamic.timemanager.TimedGroup;

import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Property;

/*
 * Written by: Wildex999
 * 
 * Overrides ArrayList to act as an replacement for loadedEntityList and loadedTileEntityList.
 */

public class ListManager<E extends EntityObject> implements List<E> {
	private World world;
	private TickDynamicMod mod;
	private HashSet<EntityGroup> localGroups; //Groups local to the world this list is part of
	private Map<Class, EntityGroup> groupMap; //Map of Class to Group
	private EntityGroup ungroupedEntities;
	
	public ListManager(World world, TickDynamicMod mod, EntityType type) {
		this.world = world;
		this.mod = mod;
		localGroups = new HashSet<EntityGroup>();
		groupMap = new HashMap<Class, EntityGroup>();
		
		//Add default Entity group
		if(type == EntityType.Entity)
			ungroupedEntities = mod.getWorldEntities(world);
		else 
			ungroupedEntities = mod.getWorldTileEntities(world);
		localGroups.add(ungroupedEntities);

		//Add local groups from config
		ConfigCategory config = mod.getWorldConfigCategory(world);
		Iterator<ConfigCategory> localIt;
		for(localIt = config.getChildren().iterator(); localIt.hasNext(); )
		{
			ConfigCategory localGroupCategory = localIt.next();
			String name = localGroupCategory.getName();
			//name = name.substring(name.lastIndexOf("."));
			EntityGroup localGroup = mod.getWorldEntityGroup(world, name, type);
			if(localGroup.getGroupType() != type)
				continue;
			
			localGroups.add(localGroup);
			//GameData.getBlockRegistry()
			//EntityRegistry.registerModEntity(entityClass, entityName, id, mod, trackingRange, updateFrequency, sendsVelocityUpdates)
		}
		
		//Create map of ID to group
	}
	
	//Re-create groups from config, and move any entities in/out due to change
	public void reloadGroups() {
		//TODO: Do partial updates each tick to not stop the world, I.e 1% of groups per tick?
	}
	
	@Override
	public boolean add(E e) {
		return false;
	}

	@Override
	public void add(int index, E element) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean contains(Object o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public E get(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int indexOf(Object o) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Iterator<E> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int lastIndexOf(Object o) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ListIterator<E> listIterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean remove(Object o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public E remove(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public E set(int index, E element) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int size() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object[] toArray() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> T[] toArray(T[] a) {
		// TODO Auto-generated method stub
		return null;
	}
}
