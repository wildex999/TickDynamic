package com.wildex999.tickdynamic.listinject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Property;

import com.wildex999.tickdynamic.TickDynamicMod;
import com.wildex999.tickdynamic.timemanager.TimedEntities;
import com.wildex999.tickdynamic.timemanager.TimedGroup;
import com.wildex999.tickdynamic.timemanager.TimedGroup.GroupType;

import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class EntityGroup {
	
	public TimedEntities timedGroup; //Group timing
	public int currentGroupTime; //World time as seen by the entities in this group. Goes up when whole entities list is ticked.
	public ArrayList<EntityObject> entities;
	
	private HashSet<Class> entityEntries; //List of Entity/TileEntity classes who belong to this group
	private boolean catchAll; //If true, can load any Entity/TileEntity into this group. Used for the default 'entity' & 'tileentity' groups
	private boolean gotOwnEntries; //Set to true if our entries differ from base Group
	
	private String name;
	private String configEntry;
	public static final String config_useCorrectedTime = "useCorrectedTime";
	public static final String config_entityNames = "entityNames";
	public static final String config_classNames = "entityClassNames";
	public static final String config_modId = "modId";
	public static final String config_groupType = "groupType";
	public static final String config_enabled = "enabled";
	
	private TickDynamicMod mod;
	private World world;
	
	public ListManager list; //The ListManager that contains this group
	public EntityGroup base;
	public boolean enabled;
	
	private boolean useCorrectedTime;
	private EntityType groupType;
	
	private static Map<String, Class> tileNameToClassMap;
	
	static {
		try {
			tileNameToClassMap = ReflectionHelper.getPrivateValue(TileEntity.class, null, "nameToClassMap", "field_145855_i");
			//Field registryField = TileEntity.class.getField("nameToClassMap");
			//registryField.setAccessible(true);
			//tileNameToClassMap = (Map<String, Class>)registryField.get(null);
		} catch(Exception e) {
			System.err.println(e);
			System.err.println("Unable to load TileEntities from Mods, class variable(nameToClassMap) lookup failed. The code might be obfuscated!");
		}
	}
	
	//If base is not null, copy the values from it before reading the config
	public EntityGroup(TickDynamicMod mod, World world, TimedEntities timedGroup, String name, String configEntry, EntityType groupType, EntityGroup base) {
		if(timedGroup == null && base != null)
			System.err.println("Assertion failed: Created EntityGroup with a null TimedGroup!");
		
		if(name != null)
			this.name = name;
		else
			this.name = "-";
		this.timedGroup = timedGroup;
		if(timedGroup != null)
			timedGroup.setEntityGroup(this);
		this.configEntry = configEntry;
		this.groupType = groupType;
		
		entities = new ArrayList<EntityObject>();
		entityEntries = new HashSet<Class>();
		list = null;
		
		if(base != null)
		{
			gotOwnEntries = false;
			copy(base, true);
		}
		else
			gotOwnEntries = true;
		this.base = base;
		
		this.mod = mod;
		this.world = world;
		readConfig(true);
	}
	
	//Read the config, but does not save defaults when created from a base Group
	public void readConfig(boolean save) {
		if(configEntry == null)
			return;
		
		if(gotOwnEntries)
			entityEntries.clear();
		gotOwnEntries = false;
		
		enabled = true;
		String comment = "Whether this group is enabled or not. If not, no Entity/TileEntity will be added to it.";
		if(base != null && !mod.config.hasKey(configEntry, config_enabled))
			enabled = mod.config.get(base.configEntry, config_enabled, enabled, comment).getBoolean();
		else
			enabled = mod.config.get(configEntry, config_enabled, enabled, comment).getBoolean();
			
		comment = "Entity or TileEntity group";
		if(base != null && !mod.config.hasKey(configEntry, config_groupType))
			groupType = EntityType.valueOf(mod.config.get(base.configEntry, config_groupType, groupType.toString(), comment).getString());
		else
			groupType = EntityType.valueOf(mod.config.get(configEntry, config_groupType, groupType.toString(), comment).getString());
			
		useCorrectedTime = true;
		comment = "Set the World time to the correct time for the TPS of this group.";
		if(base != null && !mod.config.hasKey(configEntry, config_useCorrectedTime))
			useCorrectedTime = mod.config.get(base.configEntry, config_useCorrectedTime, useCorrectedTime, comment).getBoolean();
		else
			useCorrectedTime = mod.config.get(configEntry, config_useCorrectedTime, useCorrectedTime, comment).getBoolean();
		
		String[] entities = {""};
		comment = "List of Entity/Block names(Ex: Sheep / minecraft:furnace) who are to be included in this group.";
		if(base != null && !mod.config.hasKey(configEntry, config_entityNames))
			entities = mod.config.get(base.configEntry, config_entityNames, entities, comment).getStringList();
		else
		{
			gotOwnEntries = true;
			entities = mod.config.get(configEntry, config_entityNames, entities, comment).getStringList();
		}
		
		String[] entityClasses = {""};
		comment = "List of Entity/TileEntity class names(Ex: net.minecraft.tileentity.TileEntityDropper), for Entities that are to be included in this group.";
		if(base != null && !mod.config.hasKey(configEntry, config_classNames))
			entityClasses = mod.config.get(base.configEntry, config_classNames, entityClasses, comment).getStringList();
		else
		{
			gotOwnEntries = true;
			entityClasses = mod.config.get(configEntry, config_classNames, entityClasses, comment).getStringList();
		}

		String[] mods = {""};
		comment = "List of mods to include. Will include every Entity or TileEntity from the specific mod, independent of 'entityClassNames' and 'entityNames'\n"
				+ "Note: Might not work if mod does not properly register the TileEntity/Entity!";
		if(base != null && !mod.config.hasKey(configEntry, config_modId))
			mods = mod.config.get(base.configEntry, config_modId, mods, comment).getStringList();
		else
		{
			gotOwnEntries = true;
			mods = mod.config.get(configEntry, config_modId, mods, comment).getStringList();
		}
		
		
		if(gotOwnEntries)
		{
			if(groupType == EntityType.Entity)
			{
				loadEntitiesByName(entities);
				loadEntitiesByClassName(entityClasses);
				loadEntitiesByModNames(mods);
			}
			else
			{
				loadTilesByName(entities);
				loadTilesByClassName(entityClasses);
				loadTilesByModNames(mods);
			}
		}
		else if(base != null)
			shareEntries(base); //Since we have nothing different from base, we just share the list of Entries
		
		if(save)
			mod.queueSaveConfig();
	}
	
	public void writeConfig(boolean saveFile) {
		//TODO
		//TODO: Don't write value if set by base(And not overwritten)
		
		if(saveFile)
			mod.queueSaveConfig();
	}
	
	public String getConfigEntry() {
		return configEntry;
	}
	
	public String getName() {
		return name;
	}
	
	public TimedEntities getTimedGroup() {
		return timedGroup;
	}
	
	public EntityType getGroupType() {
		return groupType;
	}
	
	//Copy variables from another EntityGroup to this one
	//copyEntries: Whether to copy the entityEntries list
	public void copy(EntityGroup other, boolean copyEntries) {
		if(copyEntries)
			entityEntries.addAll(other.entityEntries);
		//TODO: More? Assign if false?
	}
	
	public void addEntity(EntityObject entity) {
		entity.TD_Init(this);
		entities.add(entity);
	}
	
	public boolean removeEntity(EntityObject entity) {
		int index = entities.indexOf(entity);
		if(index != -1)
		{
			int currentEntityIndex = timedGroup.getCurrentObjectIndex();
			if(currentEntityIndex > index)
				timedGroup.setCurrentObjectIndex(currentEntityIndex-1);
			entities.remove(index);
			entity.TD_Deinit();
			return true;
		}
		return false;
	}
	
	public void clearEntities() {
		for(EntityObject entity : entities)
			entity.TD_Deinit();
		entities.clear();
	}
	
	public int getEntityCount() {
		return entities.size();
	}
	
	//Share the same Entity entries as the given Group.
	//This will delete our current entries list, and make a reference to the other one.
	//Any changes made to the list in either of these groups will be reflected on both.
	public void shareEntries(EntityGroup other) {
		entityEntries = other.entityEntries;
	}
	
	//Get the list of Entity classes that are accepted into this group
	public Set<Class> getEntityEntries() {
		return entityEntries;
	}
	
	
	//--Loading Entity classes from config list--
	//Load by name
	
	private void loadTilesByName(String[] names) {
		if(names.length == 0)
			return;
		if(names.length == 1 && names[0].length() == 0)
			return;
		
		for(String name : names)
		{
			List<Class> tileClassList = loadTilesByName(name);
			if(tileClassList == null)
			{
				System.out.println("Failed to find Block with the name: " + name);
				continue;
			}
			if(tileClassList.size() == 0)
				continue;
			
			for(Class tileClass : tileClassList)
			{
				if(entityEntries.contains(tileClass))
					continue;
				if(mod.debug)
					System.out.println("Found TileEntity: " + tileClass);
				entityEntries.add(tileClass);
			}
		}
	}
	
	private void loadEntitiesByName(String[] names) {
		if(names.length == 0)
			return;
		if(names.length == 1 && names[0].length() == 0)
			return;

		for(String name : names)
		{
			Class entityClass = loadEntityByName(name);
			if(entityClass == null)
			{
				System.out.println("Failed to find an Entity by the name: " + name);
				continue;
			}
			if(entityEntries.contains(entityClass))
				continue;
			if(mod.debug)
				System.out.println("Found Entity: " + entityClass);
			entityEntries.add(entityClass);
		}
	}
	
	//A single name block might have multiple TileEntities(For the different metadata)
	private List<Class> loadTilesByName(String name) {
		FMLControlledNamespacedRegistry<Block> blockRegistry = GameData.getBlockRegistry();
		Block block = blockRegistry.getRaw(name);
		if(block == null)
			return null;

		//Get TileEntities for every metadata
		TileEntity currentTile;
		Class prevTile = null;
		List<Class> tileClassList = new ArrayList<Class>(16);
		for(byte b = 0; b < 16; b++)
		{
			if(block.hasTileEntity(b))
			{
				//Might throw an exception while creating TileEntity, especially at initial load of global groups
				try {
					currentTile = block.createTileEntity(world, b);
				} catch(Exception e)
				{
					if(mod.debug)
						System.out.println("Exception while loading Tile for " + name + ":\n" + e.getMessage());
					currentTile = null;
				}
				
				Class cls = currentTile.getClass();
				if(currentTile != null && cls != prevTile)
				{
					
					if(!tileClassList.contains(cls))
						tileClassList.add(currentTile.getClass());
				}
				prevTile = cls;
			}
		}
		
		return tileClassList;
	}
	
	private Class loadEntityByName(String name) {
		return (Class)EntityList.stringToClassMapping.get(name);
	}
	
	//Load by class name
	
	private void loadTilesByClassName(String[] names) {
		if(names.length == 0)
			return;
		if(names.length == 1 && names[0].length() == 0)
			return;
		
		for(String name : names)
		{
			Class entityClass = loadByClassName(name);
			if(entityClass == null || !TileEntity.class.isAssignableFrom(entityClass))
			{
				System.out.println("Could not find TileEntity class with the name: " + name);
				continue;
			}
			if(entityEntries.contains(entityClass))
				continue;
			
			if(mod.debug)
				System.out.println("Found TileEntity class: " + entityClass);
			entityEntries.add(entityClass);
		}
	}
	
	private void loadEntitiesByClassName(String[] names) {
		if(names.length == 0)
			return;
		if(names.length == 1 && names[0].length() == 0)
			return;
		
		for(String name : names)
		{
			Class entityClass = loadByClassName(name);
			if(entityClass == null || !Entity.class.isAssignableFrom(entityClass))
			{
				System.out.println("Could not find Entity class with the name: " + name + " Class: " + entityClass);
				continue;
			}
			if(entityEntries.contains(entityClass))
				continue;
			
			if(mod.debug)
				System.out.println("Found Entity class: " + entityClass);
			entityEntries.add(entityClass);
		}
	}
	
	private Class loadByClassName(String name) {
		try {
			return Class.forName(name);
		} catch(Exception e) {
			return null;
		}
	}
	
	//Load by mod
	
	private void loadTilesByModNames(String[] names) {
		if(names.length == 0)
			return;
		if(names.length == 1 && names[0].length() == 0)
			return;
		
		for(String name : names) {
			loadTilesByModName(name);
		}
	}
	
	private void loadEntitiesByModNames(String[] names) {
		if(names.length == 0)
			return;
		if(names.length == 1 && names[0].length() == 0)
			return;
		
		for(String name : names)
		{
			List<Class> classList = loadEntitiesByModName(name);
			entityEntries.addAll(classList);
		}
	}
	
	private List<Class> loadTilesByModName(String name) {
		if(tileNameToClassMap == null)
			return null;
		return loadClassesFromNamePrefix(tileNameToClassMap, name);
	}
	
	private List<Class> loadEntitiesByModName(String name) {
		return loadClassesFromNamePrefix(EntityList.stringToClassMapping, name);
	}
	
	private List<Class> loadClassesFromNamePrefix(Map<String, Class> nameToClassMap, String name) {
		List<Class> classList = new ArrayList<Class>();
		
		Set<Entry<String, Class>> entries = nameToClassMap.entrySet();
		Iterator<Entry<String, Class>> it = entries.iterator();
		while(it.hasNext())
		{
			Entry<String, Class> entry = it.next();
			if(entry.getKey().startsWith(name)) //TODO: Could get false positives. Use Regex instead?
			{
				Class value = entry.getValue();
				classList.add(value);
			}
		}
		
		return classList;
	}
}
