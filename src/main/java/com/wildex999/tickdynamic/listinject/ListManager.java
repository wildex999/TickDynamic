package com.wildex999.tickdynamic.listinject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.commons.lang3.NotImplementedException;

import com.wildex999.tickdynamic.TickDynamicMod;
import com.wildex999.tickdynamic.timemanager.TimeManager;
import com.wildex999.tickdynamic.timemanager.TimedEntities;
import com.wildex999.tickdynamic.timemanager.TimedGroup;

import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Property;

/*
 * Written by: Wildex999
 * 
 * Overrides ArrayList to act as an replacement for loadedEntityList and loadedTileEntityList.
 */

public class ListManager implements List<EntityObject> {
	private World world;
	private TickDynamicMod mod;
	private EntityType entityType;
	
	private HashSet<EntityGroup> localGroups; //Groups local to the world this list is part of
	private Map<Class, EntityGroup> groupMap; //Map of Class to Group
	private EntityGroup ungroupedEntities;
	private Queue<EntityObject> queuedEntities; //Entities awaiting grouping, ticked as part of ungroupedEntities
	private List<EntityPlayer> playerEntities; //List of players that should tick every server tick
	
	private int entityCount; //Real count of entities combined in all groups
	
	public ListManager(World world, TickDynamicMod mod, EntityType type) {
		this.world = world;
		this.mod = mod;
		entityType = type;
		localGroups = new HashSet<EntityGroup>();
		groupMap = new HashMap<Class, EntityGroup>();
		playerEntities = new ArrayList<EntityPlayer>();
		queuedEntities = new ArrayDeque<EntityObject>();
		
		entityCount = 0;
		
		if(mod.debug)
			System.out.println("Initializing " + type + " list for world: " + world.provider.getDimensionName() + "(DIM" + world.provider.dimensionId + ")");
		
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
			EntityGroup localGroup = mod.getWorldEntityGroup(world, name, type, true, true);
			if(localGroup.getGroupType() != type)
				continue;
			
			if(mod.debug)
				System.out.println("Load local group: " + name);
			localGroups.add(localGroup);
			localGroup.list = this;
			//GameData.getBlockRegistry()
			//EntityRegistry.registerModEntity(entityClass, entityName, id, mod, trackingRange, updateFrequency, sendsVelocityUpdates)
		}
		
		//Add a copy of any remaining global groups
		config = mod.config.getCategory("groups");
		Iterator<ConfigCategory> globalIt;
		for(globalIt = config.getChildren().iterator(); globalIt.hasNext(); )
		{
			ConfigCategory groupCategory = globalIt.next();
			String name = groupCategory.getName();
			EntityGroup globalGroup = mod.getEntityGroup("groups." + name);
			
			if(globalGroup == null || globalGroup.getGroupType() != type)
				continue;
			
			//Get or create the local group as a copy of the global, but without a world config entry.
			//Will inherit config from the global group.
			EntityGroup localGroup = mod.getWorldEntityGroup(world, name, type, true, false);
			if(localGroups.contains(localGroup))
				continue; //Local group already defined
			
			if(mod.debug)
				System.out.println("Load global group: " + name);
			localGroups.add(localGroup);
			localGroup.list = this;
		}
		
		if(mod.debug)
			System.out.println("Creating ID map");
		//Create map of ID to group
		for(EntityGroup group : localGroups)
		{
			Set<Class> entries = group.getEntityEntries();
			for(Class entityClass : entries)
				groupMap.put(entityClass, group);
		}
		
		if(mod.debug)
			System.out.println("Done!");
		
	}
	
	//Re-create groups from config, and move any entities in/out due to change
	public void reloadGroups() {
		//TODO: Do partial updates each tick to not stop the world, I.e 1% of groups per tick?
	}
	
	//Assign the given EntityObject to an appropriate group
	public void assignToGroup(Object object) {
		if(entityType == EntityType.Entity)
		{
			if(object instanceof EntityPlayer) //EntityPlayer always tick independent of Groups and Limits
			{
				playerEntities.add((EntityPlayer)object);
				return;
			}
		}
		
		EntityObject entityObject = (EntityObject)object; //Assert: Should Always be an EntityObject
		
		//Use groupMap
	}
	
	@Override
	public boolean add(EntityObject element) {
		if(element.TD_entityGroup != null)
			return false;
		
		element.TD_Init(ungroupedEntities);
		queuedEntities.add(element); //Add to queue for later insertion into a group
		
		entityCount++;
		
		return true;
	}

	@Override
	public void add(int index, EntityObject element) {
		add(element);
	}

	@Override
	public boolean addAll(Collection<? extends EntityObject> c) {
		//TODO: Actually verify that the list did change before returning true
		for(EntityObject element : c)
			add(element);
		return true;
	}

	@Override
	public boolean addAll(int index, Collection<? extends EntityObject> c) {
		return addAll(c);
	}

	@Override
	public void clear() {
		for(EntityGroup group : localGroups) {
			group.clearEntities();
		}
		entityCount = 0;
	}

	@Override
	public boolean contains(Object o) {
		if(!(o instanceof EntityObject))
			return false;
		EntityObject entityObject = (EntityObject)o;
		if(entityObject.TD_entityGroup == null || entityObject.TD_entityGroup.list != this)
			return false;
			
		return true;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		for(Object obj : c)
		{
			if(!contains(obj))
				return false;
		}
		return true;
	}

	@Override
	public EntityObject get(int index) {
		if(index >= entityCount || index < 0)
			throw new ArrayIndexOutOfBoundsException("Tried to get index: " + index + ", but size is: " + entityCount);
		//Walk through groups, adding their size to index, until we reach the group with the index
		//Note: localGroups's order is not guaranteed to remain the same after a change.
		int offset = 0;
		for(EntityGroup group : localGroups) {
			if(offset + group.getEntityCount() > index)
				return group.entities.get(index - offset);
			offset += group.getEntityCount();
		}
		throw new ArrayIndexOutOfBoundsException("Reached end of groups before finding index: " + index);
	}

	@Override
	public int indexOf(Object o) {
		//TODO: Actually implement this, even if it would be a slow method
		throw new NotImplementedException("indexOf is not implemented in TickDynamic's List implementation!");
	}

	@Override
	public boolean isEmpty() {
		return entityCount == 0 ? true : false;
	}

	@Override
	public Iterator<EntityObject> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int lastIndexOf(Object o) {
		//TODO: Actually implement this, even if it would be a slow method
		throw new NotImplementedException("lastIndexOf is not implemented in TickDynamic's List implementation!");
	}

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
	public boolean remove(Object o) {
		if(!(o instanceof EntityObject))
			return false;
		EntityObject entityObject = (EntityObject)o;
		if(entityObject.TD_entityGroup == null || entityObject.TD_entityGroup.list != this)
			return false;
		
		if(entityObject.TD_entityGroup.removeEntity(entityObject))
		{
			entityCount--;
			return true;
		}
		return false;
	}

	@Override
	public EntityObject remove(int index) {
		if(mod.debug)
			System.out.println("Debug Warning: Using slow remove of objects!");
		EntityObject entityObject = get(index);
		if(remove(entityObject))
			return entityObject;
		return null;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		//TODO: Actually make sure any was removed before returning true
		for(Object obj : c)
			remove(obj);
		return true;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		//TODO: Actually implement this at some time(No one uses it as far as I can see)
		throw new NotImplementedException("retainAll is not implemented in TickDynamic's List implementation!");
	}

	@Override
	public EntityObject set(int index, EntityObject element) {
		//TODO: I can see absolutely no use for this in Minecraft, and any implementation would be slow(-ish) and inaccurate
		throw new NotImplementedException("set is not implemented in TickDynamic's List implementation!");
	}

	@Override
	public int size() {
		return entityCount;
	}

	@Override
	public List<EntityObject> subList(int fromIndex, int toIndex) {
		throw new NotImplementedException("subList is not implemented in TickDynamic's List implementation!");
	}

	@Override
	public Object[] toArray() {
		//Construct an array from all the groups
		if(mod.debug)
			System.out.println("SLOW toArray call on Entity/TileEntity list!");
		Object[] objects = new Object[entityCount];
		int offset = 0;
		for(EntityGroup group : localGroups)
		{
			System.arraycopy(group.entities, 0, objects, offset, group.entities.size());
			offset += group.entities.size();
		}
		return objects;
	}

	@Override
	public <T> T[] toArray(T[] a) {
		throw new NotImplementedException("toArray(T[]) is not implemented in TickDynamic's List implementation!");
	}
}
