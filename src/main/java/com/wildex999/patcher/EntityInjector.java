package com.wildex999.patcher;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import net.minecraft.launchwrapper.IClassTransformer;

//Inject EntityObject as a base for both Entity and TileEntity for further use

public class EntityInjector implements IClassTransformer {

	@Override
	public byte[] transform(String name, String transformedName,
			byte[] basicClass) {
		
		ClassReader cr = new ClassReader(basicClass);
		ClassWriter cw = new ClassWriter(0);
		cr.accept(cw, ClassReader.EXPAND_FRAMES);
		
		
		return basicClass;
	}
}
