package com.wildex999.tickdynamic.timemanager;

import java.util.Iterator;
import java.util.List;

import net.minecraft.world.World;

import com.wildex999.tickdynamic.TickDynamicMod;

public class TimedEntities extends TimedTileEntities {

	public TimedEntities(TickDynamicMod mod, String name) {
		super(mod, name);
	}
	
	@Override
	public List getWorldLoadedList(World world) {
		return world.loadedEntityList;
	}
	
	//Initialize a timed Entity group, reading in the configuration if it exists.
    //If no configuration exits, create a new default.
	@Override
    public void init(String configEntry) {
		timeUsed = 0;
		objectsRun = 0;
		setTimeMax(0);
		
		if(configEntry != null)
		{
			loadConfig(configEntry);
		}
		else
		{
			setSliceMax(mod.defaultEntitySlicesMax);
			setMinimumObjects(mod.defaultEntityMinimumObjects);
		}
		
		
    }
	
	@Override
	public void loadConfig(String configEntry) {
		setSliceMax(mod.config.get(configEntry, configKeySlicesMax, mod.defaultEntitySlicesMax).getInt());
		setMinimumObjects(mod.config.get(configEntry, configKeyMinimumObjects, mod.defaultEntityMinimumObjects).getInt());
		
		//Save any changes from defaults
		mod.config.save();
	}
	
	@Override
	public void writeConfig(String configEntry, boolean saveFile) {
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
