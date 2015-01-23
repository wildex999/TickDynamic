package com.wildex999.tickdynamic;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.*;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.wildex999.tickdynamic.timemanager.ITimed;
import com.wildex999.tickdynamic.timemanager.TimeManager;
import com.wildex999.tickdynamic.timemanager.TimedGroup;

import net.minecraft.init.Blocks;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;


public class TickDynamicMod extends DummyModContainer
{
    public static final String MODID = "tickdynamic";
    public static final String VERSION = "0.1";
    
    public static TickDynamicMod tickDynamic;
    public Map<String, ITimed> timedObjects;
    
    public TickDynamicMod() {
    	super(new ModMetadata());
    	ModMetadata meta = getMetadata();
    	meta.version = VERSION;
    	meta.modId = MODID;
    	meta.name = "Tick Dynamic";
    	meta.description = "Dynamic control of the world tickrate to reduce lag.";
    	meta.authorList.add("Wildex999 ( wildex999@gmail.com )");
    	
    	tickDynamic = this;
    }
    
    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
    	bus.register(this);
    	return true;
    }
    
    @Subscribe
    public void init(FMLInitializationEvent event) {
    	FMLCommonHandler.instance().bus().register(this);
    	timedObjects = new HashMap<String, ITimed>();
    	
    	TimeManager tm_world = new TimeManager(this, "tm_world");
    	TimedGroup world_te = new TimedGroup(this, "world_te");
    	tm_world.addChild(world_te);
    }
    

    @SubscribeEvent
    public void tickEvent(ServerTickEvent event) {
    	if(event.phase == Phase.START)
    	{
	        //Clear any values from the previous tick for all worlds.
	    	for(Iterator<ITimed> it = timedObjects.values().iterator(); it.hasNext(); )
	    		it.next().clearTimeUsed(false);
    	} else {
    		//After every world is done ticking, re-balance the time slices according
    	    //to the data gathered during the tick.
        	for(Iterator<ITimed> it = timedObjects.values().iterator(); it.hasNext(); ) {
        		ITimed object = it.next();
        		if(object.isManager())
        			((TimeManager)object).balanceTime();
        	}
    	}
    }
    
    public TimedGroup getGroup(String name) {
    	return (TimedGroup)timedObjects.get(name);
    }
    
    public TimeManager getManager(String name) {
    	return (TimeManager)timedObjects.get(name);
    }
    
    

}
