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

import net.minecraft.init.Blocks;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLInitializationEvent;


public class ExampleMod extends DummyModContainer
{
    public static final String MODID = "tickdynamic";
    public static final String VERSION = "0.1";
    
    public ExampleMod() {
    	super(new ModMetadata());
    	ModMetadata meta = getMetadata();
    	meta.version = VERSION;
    	meta.modId = MODID;
    	meta.name = "Tick Dynamic";
    }
    
    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
    	bus.register(this);
    	return true;
    }
    
    @Subscribe
    public void init(FMLInitializationEvent event)
    {
    	/*try {
			ZipFile jarFile = new ZipFile(new File("C:/Users/Wildex999/Downloads/development/forge-1.7.10-10.13.2.1230-src/forgeSrc-1.7.10-10.13.2.1230.jar"));
			ZipEntry entry = jarFile.getEntry("net/minecraft/block/BlockGrass.class");
			System.out.println("ENTRY: " + entry);
			jarFile.close();
		} catch (ZipException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		// some example code
        System.out.println("DIRT BLOCK >> "+Blocks.dirt.getUnlocalizedName());
        File file = new File("C:/Users/Wildex999/Downloads/development/GITProjects/TickDynamic/BlockGrass.class");
        File out = new File("C:/Users/Wildex999/Downloads/development/GITProjects/TickDynamic/output.log");
        try {
			FileInputStream input = new FileInputStream(file);
			ClassNode cn = new ClassNode();
			/*String[] args = new String[] { "C:/Users/Wildex999/Downloads/development/GITProjects/TickDynamic/BlockGrass.class" };
			try {
				ASMifier.main(args);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}*/
			/*TraceClassVisitor printer = new TraceClassVisitor(new PrintWriter(out));
			ClassReader cr = new ClassReader(input);
			cr.accept(printer, ClassReader.EXPAND_FRAMES);*/
			
			/*FileInputStream input2 = new FileInputStream(file);
			byte[] fileData = new byte[(int) file.length()];
			DataInputStream dis = new DataInputStream(input2);
			dis.readFully(fileData);
			dis.close();*/
			
			/*String decoded = new String(fileData, "ISO-8859-1");
			
			String t1 = decoded.substring(0, decoded.length() - 50);
			String t2 = decoded.substring(decoded.length() - 50);
			
			decoded = t1 + t2;*/
			
			/*File out2 = new File("C:/Users/Wildex999/Downloads/development/GITProjects/TickDynamic/output2.log");
			FileOutputStream output = new FileOutputStream(out2);
			DataOutputStream dos = new DataOutputStream(output);
			dos.write(decoded.getBytes("ISO-8859-1"));
			dos.close();*/
			//cn.accept(printer);
			
			//org.objectweb.asm.util.TraceClassVisitor
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    }
}
