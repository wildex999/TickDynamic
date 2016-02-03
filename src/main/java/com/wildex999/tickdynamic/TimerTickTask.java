package com.wildex999.tickdynamic;

import java.util.TimerTask;

//Every second, take

public class TimerTickTask extends TimerTask {

	private TickDynamicMod mod;
	
	public TimerTickTask(TickDynamicMod mod) {
		this.mod = mod;
	}
	
	@Override
	public void run() {
		try {
			mod.tpsMutex.acquire();
			
			if(mod.tpsList.size() >= mod.tpsAverageSeconds)
				mod.tpsList.removeFirst();
			mod.tpsList.add(mod.tickCounter);
			mod.tickCounter = 0;
			
		} catch (InterruptedException e) {
			System.err.println("Exception during TPS Calculation:");
			e.printStackTrace();
		} finally {
			if(mod != null && mod.tpsMutex != null)
				mod.tpsMutex.release();
		}
		

	}

}
