package com.wildex999.patcher;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedList;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import net.minecraft.init.Blocks;
import net.minecraft.launchwrapper.IClassTransformer;

//Default Transformer that will apply any patches found in the "patches" directory of the jar

public class TransformerPatcher implements IClassTransformer {

	@Override
	public byte[] transform(String name, String transformedName,
			byte[] basicClass) {
		//System.out.println("Transform:");
		//System.out.println("Name: " + name);
		//System.out.println("Transformed Name: " + transformedName);
		InputStream input = getClass().getResourceAsStream("/patches/" + transformedName.replace('.', '/') + ".patch");
		
		if(input != null)
		{
			//Read, patch, parse and write the class
			Writer stringWriter;
			String baseData;
			String patchedData;
			
			try {
				System.out.println("Patching class: " + transformedName);
				
				File out2 = new File("output_original.log");
				
				//Read
				ClassNode cn = new ClassNode();
				stringWriter = new StringWriter();
				TraceClassVisitor printer = new TraceClassVisitor(null, new ExtraTextifier(), new PrintWriter(stringWriter));
				TraceClassVisitor printer2 = new TraceClassVisitor(null, new Textifier(), new PrintWriter(out2));
				ClassReader cr;
				cr = new ClassReader(basicClass);
				cr.accept(printer, ClassReader.EXPAND_FRAMES);
				cr.accept(printer2, ClassReader.EXPAND_FRAMES);
				baseData = stringWriter.toString();
				
				//Patch
				String patch = IOUtils.toString(input);
				PatchParser patchParser = new PatchParser();
				patchParser.parsePatch(patch);
				patchedData = patchParser.patch(baseData);
				if(patchedData == null)
					throw new RuntimeException("Failed to patch class: " + name + ".\nThis usually means there is either a mod conflict or patch version is wrong!");
				
				File out3 = new File("output_patched.log");
				FileOutputStream output1 = new FileOutputStream(out3);
				DataOutputStream dos1 = new DataOutputStream(output1);
				dos1.write(patchedData.getBytes());
				dos1.close();
				
				//Parse
				ASMClassParser parser = new ASMClassParser();
				ClassWriter parsedClass = parser.parseClass(patchedData);
				
				//Write
				basicClass = parsedClass.toByteArray();
				
				cr = new ClassReader(basicClass);
				File out = new File("output_source.log");
				printer = new TraceClassVisitor(null, new Textifier(), new PrintWriter(out));
				cr.accept(printer, ClassReader.EXPAND_FRAMES);
				
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			
		}
		
		return basicClass;
	}

}
