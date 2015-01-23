package patcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.StringWriter;
import java.io.Writer;
import java.io.PrintWriter;
import java.lang.reflect.Array;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.ClassReader;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Scanner;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.LinkedList;
import java.util.Scanner;


/*
 * PATCH FILE FORMAT
 * A patch file contains multiple patches.
 * 
 * int patchFileVersion; //Version, allowing for future changes with warning
 * int patchCount; //Number of patches in this file
 * 
 * for(each patch) {
 *     int start1;
 *     int start2;
 *     int length1;
 *     int length2;
 *     int diffCount; //Number of diff operations in patch
 *     
 *     for(each diff) {
 *         byte diffOperation; //0 = DELETE, 1 = INSERT, 2 = EQUAL
 *         UTF8 text; //Prefixed with the length(Short, 2 bytes)
 *     }
 * }
 * 
 */

public class Patcher {
	public static final int patcherVersion = 1;
	
	private String baseJarPath;
	private String sourcePath;
	private String patchOutputPath;
	
	public static void main (String[] args) {
		Patcher patcher = new Patcher(args);
    }
	
	public Patcher(String[] args) {
		if(args.length != 3)
			throw new RuntimeException("Patcher requires 3 arguments: The path to the base jar, the path to the new source code, the path of the output.");
		
		baseJarPath = args[0];
		sourcePath = args[1];
		patchOutputPath = args[2];
		System.out.println("--Patcher--");
		System.out.println("Base Jar: " + baseJarPath);
		System.out.println("Source Jar: " + sourcePath);
		System.out.println("Output Path: " + patchOutputPath);
		
		//Go through all source files and see if the same file exists within the base jar.
		//If it does, generate a patch.
		File sourceFile = new File(sourcePath);
		if(!sourceFile.exists())
			throw new RuntimeException("Source jar does not exist: " + sourcePath);
		
		try
		{
			//Clear any existing patches by deleting the directory
			File delDir = new File(patchOutputPath);
			FileUtils.deleteDirectory(delDir);
			
			ZipFile baseJar = new ZipFile(baseJarPath);
			ZipFile sourceJar = new ZipFile(sourceFile);
			//Collection files = FileUtils.listFiles(sourceFile, new SuffixFileFilter(".class"), DirectoryFileFilter.DIRECTORY);
			Enumeration<? extends ZipEntry> entries = sourceJar.entries();
			while(entries.hasMoreElements()) {
				ZipEntry sourceEntry = entries.nextElement();
				if(!sourceEntry.getName().endsWith(".class"))
					continue;
				
				ZipEntry baseEntry = baseJar.getEntry(sourceEntry.getName());
				if(baseEntry == null)
					continue;
				
				System.out.println("Creating Patch: " + sourceEntry.getName());
				
				//Read in byte data and convert into strings for diffing, using a 1-to-1 encoding 
				//since the patching method uses text instead of binary.
				//TODO: Find a better method?
				Writer stringWriter;
				String sourceData;
				String baseData;
				
				//Read source class
				InputStream input = sourceJar.getInputStream(sourceEntry);
				ClassNode cn = new ClassNode();
				stringWriter = new StringWriter();
				TraceClassVisitor printer = new TraceClassVisitor(new PrintWriter(stringWriter));
				ClassReader cr = new ClassReader(input);
				cr.accept(printer, ClassReader.EXPAND_FRAMES);
				
				sourceData = stringWriter.toString();
				
				//Read base file
				InputStream input2 = baseJar.getInputStream(baseEntry);
				cn = new ClassNode();
				stringWriter = new StringWriter();
				printer = new TraceClassVisitor(new PrintWriter(stringWriter));
				cr = new ClassReader(input2);
				cr.accept(printer, ClassReader.EXPAND_FRAMES);
				
				baseData = stringWriter.toString();
				
				
				File patchFileOutTest = new File(patchOutputPath + sourceEntry.getName() + ".patch.base");
				if(!patchFileOutTest.getParentFile().exists() && !patchFileOutTest.getParentFile().mkdirs())
					throw new RuntimeException("Failed to create new file for patch: " + patchFileOutTest.getAbsolutePath());
				FileOutputStream output1 = new FileOutputStream(patchFileOutTest);
				DataOutputStream dos1 = new DataOutputStream(output1);
				dos1.write(baseData.getBytes());
				dos1.close();
				
				DiffPatch diffPatch = new DiffPatch();
				LinkedList<DiffPatch.Patch> patchSet = diffPatch.patch_make(baseData, sourceData);
				
				//Strip any line numbers to reduce patching surface area and retain correct debug lines(Mostly)
				stripLineNumberDiffs(baseData, patchSet, false);
				
				
				if(patchSet.size() == 0)
				{
					System.out.println("Warning: Nothing to patch!");
					continue;
				}
				
				patchFileOutTest = new File(patchOutputPath + sourceEntry.getName() + ".patch.source");
				if(!patchFileOutTest.getParentFile().exists() && !patchFileOutTest.getParentFile().mkdirs())
					throw new RuntimeException("Failed to create new file for patch: " + patchFileOutTest.getAbsolutePath());
				output1 = new FileOutputStream(patchFileOutTest);
				dos1 = new DataOutputStream(output1);
				dos1.write(sourceData.getBytes());
				dos1.close();
				
				//Write patch file
				File patchFileOut = new File(patchOutputPath + sourceEntry.getName() + ".patch");
				if(!patchFileOut.getParentFile().exists() && !patchFileOut.getParentFile().mkdirs())
					throw new RuntimeException("Failed to create new file for patch: " + patchFileOut.getAbsolutePath());
				FileOutputStream output = new FileOutputStream(patchFileOut);
				DataOutputStream dos = new DataOutputStream(output);
				//dos.write(patchSet.toString().getBytes());
				writePatchSet(dos, patchSet);
				//System.out.println("Wrote to: " + patchFileOut.getAbsolutePath());
				dos.close();
				
				//DEBUG: Raw patch
				patchFileOut = new File(patchOutputPath + sourceEntry.getName() + ".patch.raw");
				if(!patchFileOut.getParentFile().exists() && !patchFileOut.getParentFile().mkdirs())
					throw new RuntimeException("Failed to create new file for patch: " + patchFileOut.getAbsolutePath());
				output = new FileOutputStream(patchFileOut);
				dos = new DataOutputStream(output);
				dos.write(patchSet.toString().getBytes());
				dos.close();
				
			}
			
			baseJar.close();
		}
		catch(Exception e)
		{
			throw new RuntimeException(e);
		}
		

	}
	
	//Given the baseData and Patch-set, remove patches that only modify LINENUMBER(I.e, LINENUMBER shifts due to addition of new code) numbers
	public static void stripLineNumberDiffs(String baseData, LinkedList<DiffPatch.Patch> patchSet, boolean debug) {
		
		int curPos = 0;
		int patchOffset; //Offset added to curPos by patches adding/removing data
		curPos = baseData.indexOf("LINENUMBER", curPos);
		while(curPos != -1)
		{
			int lineEnd = baseData.indexOf("\n", curPos);
			if(lineEnd == -1)
				lineEnd = baseData.length();
			patchOffset = 0;
			
			DiffPatch.Patch curPatch;
			for(Iterator<DiffPatch.Patch> p = patchSet.iterator(); p.hasNext(); patchOffset += curPatch.length2 - curPatch.length1) {
				curPatch = p.next();
				
				//Need at least two diffs(Prefix & minus)
				if(curPatch.diffs.size() <2)
					continue;
				
				int minusOffset = curPatch.diffs.get(0).text.length(); //Offset to end of prefix(I.e, untill the actual place of change)
				
				//Check for newline in patch, as we only care about patches changing a single line
				if(curPatch.diffs.get(2).text.indexOf("\n") != -1 || curPatch.diffs.get(1).text.indexOf("\n") != -1)
					continue;
				
				//Check if starting to modify after NEWLINE, but before end of line
				//System.out.println("curPos: " + curPos + " start1: " + ((curPatch.start1+minusOffset)-patchOffset));
				if((curPatch.start1+minusOffset)-patchOffset >= curPos && (curPatch.start1+minusOffset)-patchOffset <= lineEnd)
				{
					//System.out.println("Len1: " + curPatch.length1 + " Len2: " + curPatch.length2);
					if(curPatch.length1 == curPatch.length2) //A change in linenumber will usually be of same length(Also we don't have to correct patch offsets when removing)
					{	
						if(debug)
						{
							System.out.println("Removing suspected LINENUMBER patch:");
							System.out.println(curPatch.toString());
						}
						p.remove();
						break; //Not multiple patches per LINENUMBER
					}
					else
					{
						//Allow for removing LINENUMBER with up to 1 in length difference(For transitions: 999 -> 1002 etc.)
						int shift = curPatch.length2 - curPatch.length1;
						if(Math.abs(shift) != 1) //Should always be increasing, but just to be sure we use abs
							continue;
						
						p.remove();
						
						//Shift all following patches
						for(;p.hasNext();)
						{
							curPatch = p.next();
							curPatch.start1 -= shift;
							curPatch.start2 -= shift;
						}
						break;
					}
				}
				
			}
			curPos = baseData.indexOf("LINENUMBER", curPos+1);
		}
		
		System.out.println("Patches: " + patchSet.size());
		
	}
	
	//Write the patchSet to a file that can be later read and parsed
	public static void writePatchSet(DataOutputStream output, LinkedList<DiffPatch.Patch> patchSet) throws IOException {
		
		output.writeInt(patcherVersion);
		output.writeInt(patchSet.size());
		
		DiffPatch.Patch curPatch;
		for(Iterator<DiffPatch.Patch> p = patchSet.iterator(); p.hasNext();) {
			curPatch = p.next();
			
			output.writeInt(curPatch.start1);
			output.writeInt(curPatch.start2);
			output.writeInt(curPatch.length1);
			output.writeInt(curPatch.length2);
			
			output.writeInt(curPatch.diffs.size());
			
			for(Iterator<DiffPatch.Diff> d = curPatch.diffs.iterator(); d.hasNext();) {
				DiffPatch.Diff curDiff = d.next();
				
				switch(curDiff.operation)
				{
				case DELETE:
					output.writeByte(0);
					break;
				case INSERT:
					output.writeByte(1);
					break;
				case EQUAL:
					output.writeByte(2);
					break;
				}
				
				output.writeUTF(curDiff.text);
			}
		}
	}
	
	public static LinkedList<DiffPatch.Patch> readPatchSet(DataInputStream input) throws IOException {
		int version = input.readInt();
		if(version != patcherVersion)
		{
			System.err.println("Trying to read patch file written with different version Patcher. File version: " + version + " Patcher Version: " + patcherVersion);
			return null;
		}
		
		int patchCount = input.readInt();
		LinkedList<DiffPatch.Patch> patchSet = new LinkedList<DiffPatch.Patch>();
		
		for(int p=0; p<patchCount; p++)
		{
			DiffPatch.Patch curPatch = new DiffPatch.Patch();
			patchSet.add(curPatch);
			
			curPatch.start1 = input.readInt();
			curPatch.start2 = input.readInt();
			curPatch.length1 = input.readInt();
			curPatch.length2 = input.readInt();
			
			int diffCount = input.readInt();
			LinkedList<DiffPatch.Diff> diffSet = new LinkedList<DiffPatch.Diff>();
			curPatch.diffs = diffSet;
			
			for(int d=0; d<diffCount; d++)
			{
				byte operation = input.readByte();
				DiffPatch.Operation op = DiffPatch.Operation.EQUAL;
				switch(operation)
				{
				case 0:
					op = DiffPatch.Operation.DELETE;
					break;
				case 1:
					op = DiffPatch.Operation.INSERT;
					break;
				case 2:
					op = DiffPatch.Operation.EQUAL;
					break;
				}
				
				String text = input.readUTF();
				DiffPatch.Diff curDiff = new DiffPatch.Diff(op, text);
				diffSet.add(curDiff);
			}
		}
		
		return patchSet;
	}
	
	//Apply the given patchSet to the given base, returns null on failure to patch
	public static String applyPatchSet(String base, LinkedList<DiffPatch.Patch> patchSet, boolean debug) {
		String patched;
		
		DiffPatch patcher = new DiffPatch();
		Object[] output = patcher.patch_apply(patchSet, base);
		patched = (String)output[0];
		
		boolean[] results = (boolean[]) output[1];
		
		for(int r = 0; r<results.length; r++)
		{
			if(results[r] == false)
			{
				if(debug)
					System.err.println("Failed to patch base: " + base + "\nUsing patch: " + patchSet.get(r));
				return null;
			}
		}
		
		return patched;
	}
	
}