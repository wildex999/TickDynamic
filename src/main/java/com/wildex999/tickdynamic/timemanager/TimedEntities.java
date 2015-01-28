package com.wildex999.tickdynamic.timemanager;

import java.util.Iterator;
import java.util.List;

import com.wildex999.tickdynamic.TickDynamicMod;

import net.minecraft.world.World;

public class TimedEntities extends TimedTileEntities {

	public TimedEntities(TickDynamicMod mod, World world, String name, String configEntry) {
		super(mod, world, name, configEntry);
	}
	
	@Override
	public List getWorldLoadedList(World world) {
		return world.loadedEntityList;
	}
	
	@Override
	public void loadConfig(boolean saveDefaults) {
		if(configEntry == null)
			return;
		
		setSliceMax(mod.config.get(configEntry, configKeySlicesMax, mod.defaultEntitySlicesMax).getInt());
		setMinimumObjects(mod.config.get(configEntry, configKeyMinimumObjects, mod.defaultEntityMinimumObjects).getInt());
		
		//Save any changes from defaults
		if(saveDefaults)
			mod.config.save();
	}
	
	@Override
	public void writeConfig(boolean saveFile) {
		if(configEntry == null)
			return;
		
		mod.config.get(configEntry, configKeySlicesMax, mod.defaultEntitySlicesMax).setValue(getSliceMax());
		mod.config.get(configEntry, configKeyMinimumObjects, mod.defaultEntityMinimumObjects).setValue(getMinimumObjects());
		
		if(saveFile)
			mod.config.save();
	}
	
	//Called at the end of the update loop for Entities.
	//Keeps track of position, repositions the index when needed, and ends the loop when limit has been hit
	public int updateObjects(int index) {
		if(world.isRemote || !mod.enabled)
			return index; //Run remote world as normal(Client view)
		
		List loadedList = getWorldLoadedList(world);
		
		//Stop if no more remaining
		if(remainingObjects-- <= 0)
			return loadedList.size();
		
		currentObjectIndex++;
		objectsRun++;
		
		//Loop around
		if(index >= loadedList.size()-1)
			return 0;
		else
			return index;
	}

}
