package com.wildex999.tickdynamic.listinject;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.common.config.Property;

import com.wildex999.tickdynamic.TickDynamicMod;
import com.wildex999.tickdynamic.timemanager.TimedEntities;
import com.wildex999.tickdynamic.timemanager.TimedGroup;
import com.wildex999.tickdynamic.timemanager.TimedGroup.GroupType;

public class EntityGroup {
	
	private TimedEntities timedGroup; //Group timing
	private ArrayList<EntityObject> entities;
	
	private List<Class> entityEntries; //List of Entity/TileEntity classes who belong to this group
	
	private String configEntry;
	public static final String config_useCorrectedTime = "useCorrectedTime";
	public static final String config_entityNames = "entityNames";
	public static final String config_classNames = "entityClassNames";
	public static final String config_modId = "modId";
	public static final String config_groupType = "groupType";
	public static final String config_enabled = "enabled";
	
	private TickDynamicMod mod;
	public EntityGroup base;
	public boolean enabled;
	
	private boolean useCorrectedTime;
	private EntityType groupType;
	
	//If base is not null, copy the values from it before reading the config
	public EntityGroup(TickDynamicMod mod, TimedEntities timedGroup, String configEntry, EntityType groupType, EntityGroup base) {
		if(timedGroup == null && base != null)
			System.err.println("Assertion failed: Created EntityGroup with a null TimedGroup!");
		
		this.timedGroup = timedGroup;
		this.configEntry = configEntry;
		this.groupType = groupType;
		
		entityEntries = new ArrayList<Class>();
		
		if(base != null)
			copy(base);
		this.base = base;
		
		this.mod = mod;
		readConfig(true);
	}
	
	//Read the config, but does not save defaults when created from a base Group
	public void readConfig(boolean save) {
		entityEntries.clear();
		
		enabled = true;
		String comment = "Whether this group is enabled or not. If ignored, no Entity/TileEntity will be added to it.";
		if(base != null && mod.config.hasKey(base.configEntry, config_enabled))
			enabled = mod.config.get(base.configEntry, config_enabled, enabled, comment).getBoolean();
		else
			enabled = mod.config.get(configEntry, config_enabled, enabled).getBoolean();
			
		//groupType = EntityType.Entity;
		comment = "Entity or TileEntity group";
		if(base != null && mod.config.hasKey(base.configEntry, config_groupType))
			groupType = EntityType.valueOf(mod.config.get(base.configEntry, config_groupType, groupType.toString(), comment).getString());
		else
			groupType = EntityType.valueOf(mod.config.get(configEntry, config_groupType, groupType.toString()).getString());
			
		useCorrectedTime = true;
		comment = "Set the World time to the correct time for the TPS of this group.";
		if(base != null && mod.config.hasKey(base.configEntry, config_useCorrectedTime))
			useCorrectedTime = mod.config.get(base.configEntry, config_useCorrectedTime, useCorrectedTime, comment).getBoolean();
		else
			useCorrectedTime = mod.config.get(configEntry, config_useCorrectedTime, useCorrectedTime).getBoolean();
		
		String[] entities = {"*"};
		comment = "List of Entity/Block names(Ex: Sheep / minecraft:furnace) who are to be included in this group.";
		if(base != null && mod.config.hasKey(base.configEntry, config_entityNames))
			entities = mod.config.get(base.configEntry, config_entityNames, entities, comment).getStringList();
		else
			entities = mod.config.get(configEntry, config_entityNames, entities).getStringList();
		//Load and add Entities/TileEntities to list using name
		
		
		
		String[] entityClasses = {"*"};
		comment = "List of Entity/TileEntity class names(Ex: net.minecraft.tileentity.TileEntityDropper), for Entities that are to be included in this group.";
		if(base != null && mod.config.hasKey(base.configEntry, config_classNames))
			entityClasses = mod.config.get(base.configEntry, config_classNames, entityClasses, comment).getStringList();
		else
			entityClasses = mod.config.get(configEntry, config_classNames, entityClasses).getStringList();
		//Load and add Entities/TileEntities to list
		
		String[] mods = {""};
		comment = "List of mods to include. Will include every Entity or TileEntity from the specific mod, independent of 'entityClassNames' and 'entityNames'\n"
				+ "Note: Might not work if mod does not properly register the TileEntity/Entity!";
		if(base != null && mod.config.hasKey(base.configEntry, config_modId))
			mods = mod.config.get(base.configEntry, config_modId, mods, comment).getStringList();
		else
			mods = mod.config.get(configEntry, config_modId, mods).getStringList();
		//Load and add all Entities/TileEntities from a mod to this list.
		
		if(save)
			mod.queueSaveConfig();
	}
	
	public void writeConfig() {
		//TODO
		//TODO: Don't write value if set by base(And not overwritten)
	}
	
	public TimedEntities getTimedGroup() {
		return timedGroup;
	}
	
	public EntityType getGroupType() {
		return groupType;
	}
	
	//Copy variables from another EntityGroup to this one
	public void copy(EntityGroup other) {
		
	}
}
