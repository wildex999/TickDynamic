package com.wildex999.tickdynamic.listinject;

import net.minecraft.profiler.Profiler;

/*
 * This profiler will note where the world is while ticking Entities
 * when using a for loop for iterating.
 * This will allow us to be 100% accurate while the world is iterating, as we would know
 * whether we are ticking, or looping the list from inside a ticking entity.
 * 
 * This does however add a bit of an overhead with the function calls and String compare.
 */

public class CustomProfiler extends Profiler {

	private final Profiler original;
	
	
	private int depthCount;
	
	public CustomProfiler(Profiler originalProfiler) {
		this.original = originalProfiler;
	}
	
	@Override
    public void startSection(String sectionName)
    {
		//System.out.println("StartSection: " + sectionName);
		original.startSection(sectionName);
    }
	
	@Override
	public void endSection() {
		//System.out.println("EndSection");
		original.endSection();
	}
}
