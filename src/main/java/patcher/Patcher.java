package patcher;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import java.util.Iterator;
import java.util.Scanner;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.LinkedList;
import java.util.Scanner;

public class Patcher {
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
		System.out.println("Source Path: " + sourcePath);
		System.out.println("Output Path: " + patchOutputPath);
		
		//Go through all source files and see if the same file exists within the base jar.
		//If it does, generate a patch.
		File sourceDir = new File(sourcePath);
		if(!sourceDir.exists())
			throw new RuntimeException("Source path does not exist: " + sourcePath);
		
		try
		{
			//Clear any existing patches by deleting the directory
			File delDir = new File(patchOutputPath);
			FileUtils.deleteDirectory(delDir);
			
			ZipFile baseJar = new ZipFile(baseJarPath);
			Collection files = FileUtils.listFiles(sourceDir, new SuffixFileFilter(".class"), DirectoryFileFilter.DIRECTORY);
			for(Iterator<File> i = files.iterator(); i.hasNext(); ) {
				File sourceFile = i.next();
				String internalPath = sourceFile.getAbsolutePath().substring(sourcePath.length());
				ZipEntry baseEntry = baseJar.getEntry(internalPath.replace(File.separatorChar, '/'));
				if(baseEntry == null)
					continue;
				
				System.out.println("Creating Patch: " + internalPath);
				
				//Read in byte data and convert into strings for diffing, using a 1-to-1 encoding 
				//since the patching method uses text instead of binary.
				//TODO: Find a better method?
				Writer stringWriter;
				String sourceData;
				String baseData;
				
				//Read source class
				FileInputStream input = new FileInputStream(sourceFile);
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
				
				DiffPatch diffPatch = new DiffPatch();
				LinkedList<DiffPatch.Patch> patch = diffPatch.patch_make(baseData, sourceData);
				
				stripLineNumberDiffs(baseData, patch, true);
				
				/*Object[] patched = diffPatch.patch_apply(patch, baseData);
				String patchedBase = (String)patched[0];
				boolean[] arr = (boolean[])patched[1];
				
				for(int b = 0; b < arr.length; b++) {
					System.out.println("Res: " + arr[b]);
				}
				
				File out = new File("C:/Users/Wildex999/Downloads/development/forge-1.7.10-10.13.2.1230-src/output.log");
				FileOutputStream output = new FileOutputStream(out);
				DataOutputStream dos = new DataOutputStream(output);
				//dos.write(patch.toString().getBytes());
				dos.write(patchedBase.getBytes());
				dos.close();*/
				
				if(patch.size() == 0)
					continue;
				
				//Write patch file
				File patchFileOut = new File(patchOutputPath + internalPath + ".patch");
				if(!patchFileOut.getParentFile().mkdirs())
					throw new RuntimeException("Failed to create new file for patch: " + patchFileOut.getAbsolutePath());
				FileOutputStream output = new FileOutputStream(patchFileOut);
				DataOutputStream dos = new DataOutputStream(output);
				dos.write(patch.toString().getBytes());
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
		
		if(debug)
			System.out.println("Patch Parts: " + patchSet.size());
		
	}
	
	//Write the patchSet to a file that can be later read and parsed
	public static void writePatchSet(File target, LinkedList<DiffPatch.Patch> patchSet) {
		
	}
	
}