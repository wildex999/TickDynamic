package com.wildex999.patcher;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.wildex999.tickdynamic.listinject.EntityObject;

import net.minecraft.launchwrapper.IClassTransformer;

//Inject EntityObject as a base for both Entity and TileEntity for further use

public class EntityInjector implements IClassTransformer {

	@Override
	public byte[] transform(String name, String transformedName,
			byte[] basicClass) {
		
		if(!transformedName.equals("net.minecraft.entity.Entity") && !transformedName.equals("net.minecraft.tileentity.TileEntity"))
			return basicClass;
		System.out.println("Entity Inject: " + transformedName);
		
		ClassReader cr = new ClassReader(basicClass);
		ClassWriter cw = new ClassWriter(0);
		ClassInjectorVisitor iv = new ClassInjectorVisitor(Opcodes.ASM4, cw);
		cr.accept(iv, ClassReader.EXPAND_FRAMES);
		
		
		return cw.toByteArray();
	}
	
	//Inject super class
	private class ClassInjectorVisitor extends ClassVisitor {

		boolean done = false;
		
		public ClassInjectorVisitor(int api, ClassVisitor cv) {
			super(api, cv);
		}

		@Override
		public void visit(int version, int access, String name,
				String signature, String superName, String[] interfaces) {
			if(!superName.equals("java/lang/Object"))
			{
				System.err.println("WARNING: " + name + " already has a super class which will be overwritten: " + superName 
						+ "\nThis means that some other mod might no longer work properly!");
			}
			super.visit(version, access+Opcodes.ACC_SUPER, name, signature, EntityObject.class.getName().replace('.', '/'), interfaces);
		}
		
		@Override
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			if(!done && name.equals("<init>"))
			{
				done = true;
				return new MedthodInjectorVisitor(super.api, super.visitMethod(access, name, desc, signature, exceptions));
			}
			else
				return super.visitMethod(access, name, desc, signature, exceptions);
		}
		
	}
	
	//Replace constructor init to use super
	private class MedthodInjectorVisitor extends MethodVisitor {

		boolean gotInit = false;
		
		public MedthodInjectorVisitor(int api, MethodVisitor mv) {
			super(api, mv);
		}
		
		@Override
		public void visitMethodInsn(int opcode, String owner, String name,
				String desc, boolean itf) {
			if(!gotInit && opcode == Opcodes.INVOKESPECIAL)
			{
				super.visitMethodInsn(opcode, EntityObject.class.getName().replace('.', '/'), name, desc, itf);
				gotInit = true;
			}
			else
				super.visitMethodInsn(opcode, owner, name, desc, itf);
		}
		
	}
}
