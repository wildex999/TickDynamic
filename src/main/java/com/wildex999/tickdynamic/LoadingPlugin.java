package com.wildex999.tickdynamic;

import java.util.Map;

import com.wildex999.patcher.EntityInjector;
import com.wildex999.patcher.TransformerPatcher;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.DependsOn;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.SortingIndex;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions;

@SortingIndex(1009) //Run after deobfuscation, and try to run after most other coremods
@MCVersion("1.8.9")
@DependsOn("forge")
@TransformerExclusions({ "com.wildex999", })
public class LoadingPlugin implements IFMLLoadingPlugin {

	@Override
	public String[] getASMTransformerClass() {
		boolean developmentEnvironment = (Boolean)Launch.blackboard.get("fml.deobfuscatedEnvironment");
		
		//Only return the patcher if running in live environment, to allow for testing when running from eclipse
		if(!developmentEnvironment)
			return new String[] { TransformerPatcher.class.getName(), EntityInjector.class.getName() };
		else
			return new String[] { EntityInjector.class.getName() };
	}

	@Override
	public String getModContainerClass() {
		return TickDynamicMod.class.getName();
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {
		
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}

}
