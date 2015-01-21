package com.wildex999.tickdynamic;

import java.util.Map;

import patcher.TransformerPatcher;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.Name;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.DependsOn;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.SortingIndex;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions;

@SortingIndex(1001) //Run after deobfuscation
@MCVersion("1.7.10")
@DependsOn("forge")
//@TransformerExclusions({ "com.wildex999.transformers.", "com.wildex999.transformers.transformer1" })
public class LoadingPlugin implements IFMLLoadingPlugin {

	@Override
	public String[] getASMTransformerClass() {
		return new String[] { TransformerPatcher.class.getName() };
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
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}

}
