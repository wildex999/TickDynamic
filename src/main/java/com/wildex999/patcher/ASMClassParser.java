package com.wildex999.patcher;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.google.common.primitives.Ints;

import scala.actors.threadpool.Arrays;
import scala.collection.mutable.HashTable;

//Parses the output of TraceClassVisitor to populate a ClassWriter

public class ASMClassParser {
	
	public class Token {
		public String str;
		public int line;
	}
	
	protected List<Token> tokens;
	protected int currentToken;
	public int classVersion = 0;
	
	protected ClassWriter cl;
	
	public ClassWriter parseClass(String classData) throws Exception {
		cl = new ClassWriter(0);

		//Generate a list of tokens
		tokens = new ArrayList<Token>();
		
		int tokenStart = 0;
		int curPos = 0;
		boolean inQuote = false;
		boolean escaped = false;
		boolean inToken = false;
		int currentLine = 0;
		while(curPos < classData.length())
		{
			char curChar = classData.charAt(curPos);
			curPos++;
			
			//If inside quotes, ignore whitespace(TODO: Don't ignore newline? Should be no newline inside quotes tho, as Java doesn't allow it?)
			if(inQuote)
			{
				if(curChar == '\\')
					escaped = !escaped; 
				else if(curChar == '"' && !escaped)
					inQuote = false;
					
				continue;
			}
			
			//Check for whitespace
			if(Character.isWhitespace(curChar))
			{
				if(inToken)
				{
					Token newToken = new Token();
					newToken.str = classData.substring(tokenStart, curPos-1);
					newToken.line = currentLine;
					tokens.add(newToken);
					//System.out.println("["+tokens.get(tokens.size()-1)+"]");
					inToken = false;
				}
				//We want to tokenize newline as it might be useful
				if(curChar == '\n') {
					Token newToken = new Token();
					newToken.str = "\n";
					newToken.line = currentLine;
					tokens.add(newToken);
					currentLine++;
					//System.out.println("["+tokens.get(tokens.size()-1)+"]");
				}
				
				continue;
			}
			
			//Not whitespace
			if(inToken == false)
			{
				inToken = true;
				tokenStart = curPos-1;
			}
			
			//Enter quotes
			if(curChar == '"')
				inQuote = true;
		}
		//Write any token that reached until the end
		if(inToken)
		{
			Token newToken = new Token();
			newToken.str = classData.substring(tokenStart);
			newToken.line = currentLine;
			tokens.add(newToken);
		}
		currentToken = -1; //first nextToken will get the first token
		System.out.println("Tokens: " + tokens.size());
		
		//Parse Class header
		parseClassVersion();
		skipLine(); //Bypass access flags comment line
		parseClassHeader();
		
		String value;
		String signature = null;
		while(true)
		{
			value = nextToken();
			if(value == null)
				throw new Exception(getCurrentTokenLine() + ": Reached end of class data without complete parse, Patch might be corrupt!");
			
			//Skip empty lines
			if(value.equals("\n"))
				continue;
			
			//Skip //
			if(value.startsWith("//"))
			{
				//Check for pre-method generics signature
				value = nextToken();
				if(value.equals("signature"))
					signature = nextToken();
				else if(value.equals("compiled"))
				{
					currentToken++; //Skip the 'from:'
					cl.visitSource(nextToken(), null);
				}
				else
					currentToken--;
				skipLine();
				continue;
			}
			
			//Handle Class Annotation
			if(value.startsWith("@"))
			{
				int valuesOffset = value.indexOf("(");
				String valuesToken = "";
				if(valuesOffset != -1)
				{
					valuesToken = value.substring(valuesOffset);
					valuesToken += getLine(); //There is whitespace inside the (), so we have to combine the tokens
				}
				String desc = value.substring(1, valuesOffset);
				
				AnnotationVisitor av = cl.visitAnnotation(desc, true);
				parseAnnotation(av, valuesToken);
				av.visitEnd();
			}
			
			if(value.equals("}"))
				break;
			
			currentToken--; //Parsers use nextToken, so go back from our peek
			
			//The first thing before either a Method or a Field is the access flags
			int access = parseAccessFlags();
			
			//Check if it's a method, field etc.
			value = nextToken();
			if(value == null)
				continue; //Let it reach the == null throw
			
			if(value.equals("INNERCLASS"))
				parseInnerClass(access);
			else if(value.contains("(")) //All methods have parentheses. while Fields do not
				parseMethod(access, signature);
			else
				parseField(access, signature);
			
			signature = null;
				
		}
		
		//Done writing class
		cl.visitEnd();
		
		return cl;
	}
	
	protected String nextToken() {
		if(currentToken >= tokens.size()-1)
			return null;
		return tokens.get(++currentToken).str;
	}
	
	protected String getCurrentToken() {
		if(currentToken < 0 || currentToken >= tokens.size())
			return null;
		return tokens.get(currentToken).str;
	}
	
	protected String previousToken() {
		if(currentToken <= 0 || currentToken >= tokens.size()+1)
			return null;
		return tokens.get(--currentToken).str;
	}
	
	protected int getCurrentTokenLine() {
		if(currentToken < 0 || currentToken >= tokens.size())
			return -1;
		return tokens.get(currentToken).line;
	}
	//Skip all tokes on this line(Go to next newline token)
	protected void skipLine() {
		String value;
		do {
			value = nextToken();
		} while(value != null && !value.equals("\n"));
	}
	
	//Get and combine all the remaining tokens until it finds a "\n" which it will include at the end
	protected String getLine() {
		StringBuilder builder = new StringBuilder();
		String value;
		do {
			value = nextToken();
			builder.append(value);
		} while(value != null && !value.equals("\n"));
		
		return builder.toString();
	}
	
	
	//Parse the class version, kept in a comment at the top
	//Will fail if it doesn't find the version, or if the version is unsuported  
	protected void parseClassVersion() throws Exception {
		String noMatchError = "Found no class version at beginning of class!";
		String value;
		
		if(!nextToken().equals("//"))
			throw new Exception(noMatchError);
		if(!nextToken().equals("class"))
			throw new Exception(noMatchError);
		if(!nextToken().equals("version"))
			throw new Exception(noMatchError);
		value = nextToken();
		
		int version = (int)Float.parseFloat(value);
		
		switch (version) {
		case 50:
			classVersion = Opcodes.V1_6;
			System.out.println("V1_6");
			break;
		case 51:
			classVersion = Opcodes.V1_7;
			break;
		case 52:
			classVersion = Opcodes.V1_8;
			break;
		default:
			throw new Exception("Unsuported class version: " + version);
		}
		
		//Move to next line and Verify
		nextToken();
		value = nextToken();
		if(!value.equals("\n"))
			throw new Exception("Error while parsing class version, expected \n, got: " + value);
	}
	
	//Parse the class header(name, generics, super, implements etc.)
	protected void parseClassHeader() throws Exception {
		int access = parseAccessFlags();
		if(access == 0)
			throw new Exception("Error: Got zero access modifier while parsing class header!");
		
		if(!nextToken().equals("class"))
			throw new Exception("Error: Did not find class identifier while parsing class header!");
		
		String className = nextToken();
		String superClassName = null;
		List<String> impl = new ArrayList<String>();
		String value;
		
		//Extending, implementing or done?
		boolean implement = false;
		while(true)
		{
			value = nextToken();
			
			if(implement)
			{
				if(value.equals("{")) //Done reading implements
					break;
				impl.add(value);
				continue;
			}

			if(value.equals("implements"))
				implement = true;
			else if(value.equals("extends"))
				superClassName = nextToken();
			else if(value.equals("{")) //Done reading header info
				break;
		}
		
		//Write header
		/*if(superClassName != null)
		{
			access += Opcodes.ACC_SUPER;
			System.out.println("super access: " + superClassName);
		}
		else*/
		access += Opcodes.ACC_SUPER; //Seems this is always set after Java 1.1?
		if(superClassName == null)
			superClassName = "java/lang/Object";
		
		System.out.println("Writing class header:"
				+ "\nAccess: " + Integer.toHexString(access).toUpperCase()
				+ "\nName: " + className
				+ "\nSuper class: " + superClassName
				+ "\nImplements: " + impl);
		
		String[] implNames = null;
		if(impl.size() != 0)
			implNames = impl.toArray(new String[impl.size()]);
		
		cl.visit(classVersion, access, className, null, superClassName, implNames);
	}
	
	//Scan and build up access flags(public, private, static, final etc.) for class, method, field and parameter
	protected int parseAccessFlags() throws Exception {
		String current;
		int access = 0;
		
		//They share value(Mutually exclusive, one is for method, one is for field)
		boolean gotTransient = false;
		boolean gotVarargs = false;
		while(true)
		{
			current = nextToken();
			if(current == null)
				return access;
			
			//A lot of these actually are not a keyword, but something the compiler adds, just keep it here for now
			if(current.equals("public"))
				access += Opcodes.ACC_PUBLIC;
			else if(current.equals("private"))
				access += Opcodes.ACC_PRIVATE;
			else if(current.equals("protected"))
				access += Opcodes.ACC_PROTECTED;
			else if(current.equals("static"))
				access += Opcodes.ACC_STATIC;
			else if(current.equals("final"))
				access += Opcodes.ACC_FINAL;
			/*else if(current.equals("super"))
				access += Opcodes.ACC_SUPER;*/ //This one is added on newer compilers whenever a class has a super class
			else if(current.equals("synchronized"))
				access += Opcodes.ACC_SYNCHRONIZED;
			else if(current.equals("volatile"))
				access += Opcodes.ACC_VOLATILE;
			else if(current.equals("bridge"))
				access += Opcodes.ACC_BRIDGE;
			else if(current.equals("varargs"))
			{
				if(!gotTransient)
				{
					access += Opcodes.ACC_VARARGS;
					gotVarargs = true;
				}
			}
			else if(current.equals("transient"))
			{
				if(!gotVarargs)
				{
					access += Opcodes.ACC_TRANSIENT;
					gotTransient = true;
				}
			}
			else if(current.equals("native"))
				access += Opcodes.ACC_NATIVE;
			else if(current.equals("interface"))
				access += Opcodes.ACC_INTERFACE;
			else if(current.equals("abstract"))
				access += Opcodes.ACC_ABSTRACT;
			else if(current.equals("strict"))
				access += Opcodes.ACC_STRICT;
			else if(current.equals("synthetic"))
				access += Opcodes.ACC_SYNTHETIC;
			else if(current.equals("annotation"))
				access += Opcodes.ACC_ANNOTATION;
			else if(current.equals("enum"))
				access += Opcodes.ACC_ENUM;
			else if(current.equals("mandated"))
				access += Opcodes.ACC_MANDATED;
			else
			{
				//Move back from our peek
				currentToken--;
				//System.out.println("Access: " + Integer.toHexString(access).toUpperCase());
				return access;
			}
		}
	}
	
	//Parse a inner class declaration
	protected void parseInnerClass(int access) throws Exception {
		String name = nextToken();
		String outerName = nextToken();
		String innerName = nextToken();
		
		if(outerName.equals("null"))
			outerName = null;
		if(innerName.equals("null"))
			innerName = null;
		
		cl.visitInnerClass(name, outerName, innerName, access);
	}
	
	//Parse a Field. Expects the currentToken to be the first
	protected void parseField(int access, String signature) throws Exception {
		String value = getCurrentToken();
		
		String desc = value;
		String name = nextToken();
		Object fieldValue = null;
		
		//Check for preset value
		if(nextToken().equals("="))
			fieldValue = parseValue(nextToken()).value;
		else
			currentToken--; //Go back from our peek
		
		FieldVisitor field = cl.visitField(access, name, desc, signature, fieldValue);
		//System.out.println("Field: " + Integer.toHexString(access).toUpperCase() + " | " + name + " | " + desc + " | " + fieldValue);
		
		if(!nextToken().equals("\n"))
			throw new Exception(getCurrentTokenLine() + ": Error: Expected newline while parsing field: " + name + "! Got: " + getCurrentToken());
		
		//Read any annotations for the field
		while(true)
		{
			value = nextToken();
			if(value.startsWith("@"))
			{
				int valuesOffset = value.indexOf("(");
				String valuesToken = "";
				if(valuesOffset != -1)
				{
					valuesToken = value.substring(valuesOffset);
					valuesToken += getLine(); //There is whitespace inside the (), so we have to combine the tokens
				}
				desc = value.substring(1, valuesOffset);
				
				AnnotationVisitor av = field.visitAnnotation(desc, true);
				parseAnnotation(av, valuesToken);
				av.visitEnd();
			}
			else //End of field if we reach anything else
			{
				currentToken--;
				break;
			}
		}
		
		field.visitEnd();
	}
	
	//Parse the annotation values
	//Expects the annotationvisitior to already be created with desc, and for token to be the values, including the ()
	protected void parseAnnotation(AnnotationVisitor anno, String token) throws Exception {
		if(token == null || token.length() == 0 || token.length() == 2)
			return; //Nothing to parse
		
		//System.out.println("Parse annotation: " + token);
		
		//TODO: Handle escaped quotes properly
		if(token.contains("\\\""))
			throw new Exception(getCurrentTokenLine() + ": Parser currently does not handle escaped quotes in annotations! Bug me about this -_-(Unless you are on an old version");
		
		int offset = 1; //Start after the (
		int index;
		while(true)
		{
			String valueName;
			String value;
			
			//Get value name
			index = token.indexOf("=", offset);
			valueName = token.substring(offset, index);
			
			//Get value
			offset = index+1;
			
			char tokenChar = token.charAt(offset);
			if(tokenChar == '"') //String value
			{
				index = token.indexOf('"', offset+1);
				value = token.substring(offset+1, index);
				
				anno.visit(valueName, value);
				//System.out.println("AnnotationStr: " + valueName + "=" + value);
				
				offset = index+1;
			}
			else if(tokenChar == '{') //Array value
				throw new Exception(getCurrentTokenLine() + ": Parser currently does not handle arrays in annotations!");
			else if(tokenChar == 'L') //Enum or Object Type
			{
				//Start with getting the Type name
				index = token.indexOf(";", offset);
				value = token.substring(offset, index+1);
				offset = index+1;
				
				//If we have a '.' after that, it's an Enum
				if(token.charAt(offset) == '.')
				{
					//Find length
					int index1 = token.indexOf(",", offset);
					int index2 = token.indexOf(")", offset);
					
					if(index1 < index2 && index1 != -1)
						index = index1;
					else
						index = index2;
					
					String entryName = token.substring(offset+1, index);
					anno.visitEnum(valueName, value, entryName);
					//System.out.println("AnnotationEnum: " + valueName + "=" + value + "." + entryName);
					
					offset = index;
				}
				else
				{
					anno.visit(valueName, org.objectweb.asm.Type.getType(value));
					//System.out.println("AnnotationObj: " + valueName + "=" + value);
				}
				
			} else {
				//Check for Boolean and Number values
				index = token.indexOf(",", offset);
				if(index == -1)
					value = token.substring(offset, token.length()-1);
				else
					value = token.substring(offset, index);
				
				ValueType parsedValue = parseValue(value);
				anno.visit(valueName, parsedValue.value);
				//System.out.println("AnnotationBoolNr: " + valueName + "=" + parsedValue.value);
				offset = index;
			}
			
			tokenChar = token.charAt(offset);
			if(tokenChar == ',') //Continue to next value
			{
				offset ++;
				continue;
			}
			else if(tokenChar == ')') //Done
				break;
			
			throw new Exception(getCurrentTokenLine() + ": Error while parsing Annotation: Expected ',' or ')', got: " + tokenChar);
			
		}
		//TODO: If we get "// invisible" before end of line, the annotation is invisible?
	}
	
	//Get the value parsed from a string
	protected ValueType parseValue(String token) throws Exception {
		ValueType val = new ValueType();
		
		if(token.startsWith("\""))
		{
			val.type = ValueType.Type.TString;
			val.value = token.substring(1, token.length()-1);
			return val;
		}
		
		if(token.equals("true"))
		{
			val.type = ValueType.Type.TBoolean;
			val.value = Boolean.TRUE;
			return val;
		}
		
		if(token.equals("false"))
		{
			val.type = ValueType.Type.TBoolean;
			val.value = Boolean.FALSE;
			return val;
		}
		
		if(token.startsWith("L")) //Type
		{
			String objType = token.substring(0, token.indexOf(".")); //We don't want the '.class' at the end
			val.type = ValueType.Type.TType;
			val.value = org.objectweb.asm.Type.getType(objType);
			return val;
		}
		
		//Number value
		int index = token.indexOf("F");
		if(index != -1)
		{
			val.type = ValueType.Type.TFloat;
			val.value = new Float(token.substring(0, index));
			return val;
		}
		
		index = token.indexOf("D");
		if(index != -1)
		{
			val.type = ValueType.Type.TDouble;
			val.value = new Double(token.substring(0, index));
			return val;
		}
		
		index = token.indexOf("L");
		if(index != -1)
		{
			val.type = ValueType.Type.TLong;
			val.value = new Long(token.substring(0, index));
			return val;
		}
		
		if(token.startsWith("(char)"))
		{
			val.type = ValueType.Type.TChar;
			val.value = new Character(token.charAt(6));
			return val;
		}
		
		if(token.startsWith("(short)"))
		{
			val.type = ValueType.Type.TShort;
			val.value = new Short(token.substring(7));
			return val;
		}
		
		if(token.startsWith("(byte)"))
		{
			val.type = ValueType.Type.TByte;
			val.value = new Byte(token.substring(6));
			return val;
		}					
		
		try {
			val.type = ValueType.Type.TInteger;
			val.value = new Integer(token);
			return val;
		} catch (Exception e) {}
		
		throw new Exception(getCurrentTokenLine() + ": Could not parse value: " + token);
	}
	
	//Parse a method. Expect the currentToken to be the first
	protected void parseMethod(int access, String signature) throws Exception {
		String value = getCurrentToken();
		String methodName;
		String desc;
		String[] exceptionsArray = null;
		
		Map<String, Label> labels = new HashMap<String, Label>();
		
		int index = value.indexOf("(");
		methodName = value.substring(0, index);
		desc = value.substring(index);
		
		//System.out.println("Parsing method: " + methodName + " Desc: " + desc);
		
		value = nextToken();
		
		if(value.equals("throws")) {
			List<String> exceptions = new ArrayList<String>();
			while(true)
			{
				value = nextToken();
				if(value.equals("\n"))
					break;
				exceptions.add(value);
			}
			exceptionsArray = exceptions.toArray(new String[exceptions.size()]);
		} else if(!value.equals("\n"))
			throw new Exception(getCurrentTokenLine() + ": Error while parsing method: Expected \\n on method header, got: " + value);
		
		MethodVisitor method = cl.visitMethod(access, methodName, desc, signature, exceptionsArray); //TODO: Generics and exceptions
		
		//Read any annotations for the method
		while(true)
		{
			value = nextToken();
			if(value.startsWith("@"))
			{
				int valuesOffset = value.indexOf("(");
				String valuesToken = "";
				if(valuesOffset != -1)
				{
					valuesToken = value.substring(valuesOffset);
					valuesToken += getLine(); //There is whitespace inside the (), so we have to combine the tokens
				}
				desc = value.substring(1, valuesOffset);
				
				AnnotationVisitor av = method.visitAnnotation(desc, true);
				parseAnnotation(av, valuesToken);
				av.visitEnd();
			}
			else //End of method header if we reach anything else
			{
				currentToken--;
				break;
			}
		}

		//Parse method instructions
		boolean lineData = false;
		String insnSignature = null;
		while(true) {
			value = nextToken();
			
			if(value.equals("}"))
			{
				currentToken--; //Allow main loop to detect the class end
				break;
			}
			else if(value.startsWith("//"))
			{

				//Check for pre-method generics signature
				value = nextToken();
				if(value.equals("signature"))
					insnSignature = nextToken();
				else
					currentToken--;
				skipLine();
				continue;
			}
			if(!value.equals("\n"))
			{
				lineData = true;
				currentToken--;
				parseInstruction(method, labels, insnSignature);
				insnSignature = null; //Reset signature after known instruction
			}
			else
			{
				if(lineData == false)
					break; //End of method
				else
					lineData = false;
			}
		}
		
		method.visitEnd();
	}
	
	//Parse a instruction, consisting for a single line with tokens
	protected void parseInstruction(MethodVisitor method, Map<String, Label> labels, String signature) throws Exception {
		String value = nextToken();
		//System.out.println("Parsing instruction: " + value);
		
		//Ops that have no arguments
		final String[] insn = {"NOP", "ACONST_NULL", "ICONST_M1", "ICONST_0", "ICONST_1", "ICONST_2", "ICONST_3", "ICONST_4", "ICONST_5",
				"LCONST_0", "LCONST_1", "FCONST_0", "FCONST_1", "FCONST_2", "DCONST_0", "DCONST_1", "IALOAD", "LALOAD", "FALOAD", "DALOAD", 
				"AALOAD", "BALOAD", "CALOAD", "SALOAD", "IASTORE", "LASTORE", "FASTORE", "DASTORE", "AASTORE", "BASTORE", "CASTORE", "SASTORE",
				"POP", "POP2", "DUP", "DUP_X1", "DUP_X2", "DUP2", "DUP2_X1", "DUP2_X2", "SWAP", "IADD", "LADD", "FADD", "DADD", "ISUB", "LSUB",
				"FSUB", "DSUB", "IMUL", "LMUL", "FMUL", "DMUL", "IDIV", "LDIV", "FDIV", "DDIV", "IREM", "LREM", "FREM", "DREM", "INEG", "LNEG", 
				"FNEG", "DNEG", "ISHL", "LSHL", "ISHR", "LSHR", "IUSHR", "LUSHR", "IAND", "LAND", "IOR", "LOR", "IXOR", "LXOR", "I2L", "I2F",
				"I2D", "L2I", "L2F", "L2D", "F2I", "F2L", "F2D", "D2I", "D2L", "D2F", "I2B", "I2C", "I2S", "LCMP", "FCMPL", "FCMPG", "DCMPL", "DCMPG",
				"IRETURN", "LRETURN", "FRETURN", "DRETURN", "ARETURN", "RETURN", "ARRAYLENGTH", "ATHROW", "MONITORENTER", "MONITOREXIT"};
		
		final String[] intInsn = {"BIPUSH", "SIPUSH", "NEWARRAY"};
		
		final String[] varInsn = {"ILOAD", "LLOAD", "FLOAD", "DLOAD", "ALOAD", "ISTORE", "LSTORE", "FSTORE", "DSTORE", "ASTORE", "RET"};
		
		final String[] typeInsn = {"NEW", "ANEWARRAY", "CHECKCAST", "INSTANCEOF"};
		
		final String[] fieldInsn = {"GETSTATIC", "PUTSTATIC", "GETFIELD", "PUTFIELD"};
		
		final String[] methodInsn = {"INVOKEVIRTUAL", "INVOKESPECIAL", "INVOKESTATIC", "INVOKEINTERFACE"};
		
		final String[] jumpInsn = {"IFEQ", "IFNE", "IFLT", "IFGE", "IFGT", "IFLE", "IF_ICMPEQ", "IF_ICMPNE", "IF_ICMPLT", "IF_ICMPGE", "IF_ICMPGT",
									"IF_ICMPLE", "IF_ACMPEQ", "IF_ACMPNE", "GOTO", "JSR", "IFNULL", "IFNONNULL"};
		
		
		//Instructions(No arguments)
		if(stringArrayContains(insn, value)) {
			int opcode = Opcodes.class.getField(value).getInt(null); //The ASM library already has the name to number binding, so we use it. How slow is this?
			method.visitInsn(opcode);
		}
		
		//Integer instructions
		else if(stringArrayContains(intInsn, value)) {
			int opcode = Opcodes.class.getField(value).getInt(null);
			
			if(value.equals("NEWARRAY"))
				method.visitIntInsn(opcode, Opcodes.class.getField(nextToken()).getInt(null));
			else
				method.visitIntInsn(opcode, Integer.parseInt(nextToken()));
		}
		
		//Variable instructions
		else if(stringArrayContains(varInsn, value)) {
			int opcode = Opcodes.class.getField(value).getInt(null);
			method.visitVarInsn(opcode, Integer.parseInt(nextToken()));
		}
		
		//Type Instructions
		else if(stringArrayContains(typeInsn, value)) {
			int opcode = Opcodes.class.getField(value).getInt(null);
			method.visitTypeInsn(opcode, nextToken());
		}
		
		//Field instructions
		else if(stringArrayContains(fieldInsn, value)) {
			int opcode = Opcodes.class.getField(value).getInt(null);
			String temp = nextToken();
			int index = temp.indexOf(".");
			String owner = temp.substring(0, index);
			String field = temp.substring(index+1);
			currentToken++; //Skip the ':' token
			temp = nextToken();
			
			method.visitFieldInsn(opcode, owner, field, temp);
		}
		
		//Method instructions
		else if(stringArrayContains(methodInsn, value)) {
			int opcode = Opcodes.class.getField(value).getInt(null);
			String temp = nextToken();
			int index = temp.indexOf(".");
			String owner = temp.substring(0, index);
			String field = temp.substring(index+1);
			temp = nextToken();
			
			//We don't know if the owner class is an interface, but it seems to be able to figure it out itself
			//TODO: if value == INVOKEINTERFACE then parent class is an interface? Inheritance? What's the point of having a flag for it if it's in the opcode?
			method.visitMethodInsn(opcode, owner, field, temp);
		}
		
		else if(stringArrayContains(jumpInsn, value)) {
			int opcode = Opcodes.class.getField(value).getInt(null);
			//Get the Label index
			String labelIndex = nextToken();
			method.visitJumpInsn(opcode, getLabel(labels, labelIndex));
		}
		
		else if(value.startsWith("L") && StringUtils.isNumeric(value.substring(1))) {
			method.visitLabel(getLabel(labels, value));
		}
		
		else if(value.equals("LDC")) {
			ValueType obj = parseValue(nextToken());
			method.visitLdcInsn(obj.value);
		}
		
		else if(value.equals("IINC")) {
			int index = Integer.parseInt(nextToken());
			int increase = Integer.parseInt(nextToken());
			method.visitIincInsn(index, increase);
		}
		
		else if(value.equals("TABLESWITCH")) {
			//This is a multiline instruction
			currentToken++; //Go past the newline
			
			int min = 0;
			int max = 0;
			String temp;
			Label defLabel;
			List<Label> labelList = new ArrayList<Label>();
			while(true) {
				int index;
				temp  = nextToken();
				if(temp.equals("default:"))
				{
					defLabel = getLabel(labels, nextToken());
					break;
				}
				
				index = Integer.parseInt(temp.substring(0, temp.length()-1));
				labelList.add(getLabel(labels, nextToken()));
				
				if(index > max)
					max = index;
				else if(index < min)
					min = index;
			}
			
			method.visitTableSwitchInsn(min, max, defLabel, (Label[])labelList.toArray());
		}
		
		else if(value.equals("LOOKUPSWITCH")) {
			String temp;
			Label defLabel;
			List<Integer> keyList = new ArrayList<Integer>();
			List<Label> labelList = new ArrayList<Label>();
			while(true) {
				temp = nextToken();
				if(temp.equals("default:"))
				{
					defLabel = getLabel(labels, nextToken());
					break;
				}
				
				keyList.add(Integer.parseInt(temp.substring(0, temp.length()-1)));
				labelList.add(getLabel(labels, nextToken()));
			}
			
			method.visitLookupSwitchInsn(defLabel, Ints.toArray(keyList), (Label[])labelList.toArray());
		}
		
		else if(value.equals("MULTIANEWARRAY")) {
			method.visitMultiANewArrayInsn(nextToken(), Integer.parseInt(nextToken()));
		}
		
		else if(value.equals("INVOKEDYNAMIC")) {
			throw new Exception(getCurrentTokenLine() + ": Error while parsing method instructions: Handling 'INVOKEDYNAMIC' not yet implemented!");
		}
		
		else if(value.equals("TRYCATCHBLOCK")) {
			Label start = getLabel(labels, nextToken());
			Label end = getLabel(labels, nextToken());
			Label handler = getLabel(labels, nextToken());
			
			method.visitTryCatchBlock(start, end, handler, nextToken());
		}
		
		else if(value.equals("LOCALVARIABLE")) {
			String name = nextToken();
			String desc = nextToken();
			Label start = getLabel(labels, nextToken());
			Label end = getLabel(labels, nextToken());
			int index = Integer.parseInt(nextToken());
			
			method.visitLocalVariable(name, desc, signature, start, end, index);
		}
		
		else if(value.equals("LINENUMBER")) {
			int line = Integer.parseInt(nextToken());
			Label start = getLabel(labels, nextToken());
			
			method.visitLineNumber(line, start);
		}
		
		else if(value.equals("FRAME")) {
			String typeStr = nextToken();
			int type = 0;
			
			//Frame operation type
			if(typeStr.equals("NEW"))
				type = Opcodes.F_NEW;
			else if(typeStr.equals("FULL"))
				type = Opcodes.F_FULL;
			else if(typeStr.equals("SAME"))
				type = Opcodes.F_SAME;
			else if(typeStr.equals("SAME1"))
				type = Opcodes.F_SAME1;
			else if(typeStr.equals("APPEND"))
				type = Opcodes.F_APPEND;
			else if(typeStr.equals("CHOP"))
				type = Opcodes.F_CHOP;
			else
				throw new Exception(getCurrentTokenLine() + ": Error while parsing method frame: No known FRAME type found. Got: " + typeStr);
			
			Object[] localTypes = null;
			Object[] stackTypes = null;
			int localCount = 0;
			int stackCount = 0;
			if(type == Opcodes.F_NEW || type == Opcodes.F_FULL) {
				//Parse local types
				localTypes = parseFrameElements(labels);
				localCount = localTypes.length;
				
				//Parse stack types
				stackTypes = parseFrameElements(labels);
				stackCount = stackTypes.length;
			} else if (type == Opcodes.F_SAME1) {
				stackTypes = new Object[] { nextToken() };
				stackCount = 1;
			} else if(type == Opcodes.F_APPEND) {
				localTypes = parseFrameElements(labels);
				localCount = localTypes.length;
			} else if(type == Opcodes.F_CHOP) {
				localCount = Integer.parseInt(nextToken());
			}
			
			method.visitFrame(type, localCount, localTypes, stackCount, stackTypes);
		}
		
		else if(value.equals("MAXSTACK")) {
			currentToken++; //Skip the '='
			int maxStack = Integer.parseInt(nextToken());
			currentToken++; //Skip to next line
			
			if(!nextToken().equals("MAXLOCALS"))
				throw new Exception(getCurrentTokenLine() + ": Error while parsing method: Expected MAXLOCALS, got: " + getCurrentToken());
			currentToken++;
			int maxLocals = Integer.parseInt(nextToken());
			method.visitMaxs(maxStack, maxLocals);
		} else {
			throw new RuntimeException(getCurrentTokenLine() + ": Parser got unknown method instruction: " + value);
		}
		//For now we asume(As has been observed) that MAXSTACK and MAXLOCALS always are together in the same order
		/*else if(value.equals("MAXLOCALS")) {
			
		}*/
		
	}
	
	protected Object[] parseFrameElements(Map<String, Label> labels) throws Exception {
		List<Object> objects = new ArrayList<Object>();
		
		String value = nextToken();
		if(value.equals("[]")) //Empty
			return objects.toArray();

		if(value.endsWith("]"))
		{
			objects.add(parseFrameType(labels, value.substring(1, value.length()-1))); //Remove both '[' and ']'
			return objects.toArray();
		}
		objects.add(parseFrameType(labels, value.substring(1))); //Get first value without the '['
		
		while(true)
		{
			value = nextToken();
			
			if(value.endsWith("]"))
			{
				objects.add(parseFrameType(labels, value.substring(0,value.length()-1)));
				return objects.toArray();
			}
			
			objects.add(parseFrameType(labels, value));
		}

	}
	
	//Parse the element/object type for a frame
	protected Object parseFrameType(Map<String, Label> labels, String typeStr) throws Exception {
		if(typeStr.length() == 1) //Base type
		{
			if(typeStr.equals("T"))
				return Opcodes.TOP;
			else if(typeStr.equals("I"))
				return Opcodes.INTEGER;
			else if(typeStr.equals("F"))
				return Opcodes.FLOAT;
			else if(typeStr.equals("D"))
				return Opcodes.DOUBLE;
			else if(typeStr.equals("J"))
				return Opcodes.LONG;
			else if(typeStr.equals("N"))
				return Opcodes.NULL;
			else if(typeStr.equals("U"))
				return Opcodes.UNINITIALIZED_THIS;
			else
				throw new Exception(getCurrentTokenLine() + ": Error while parsing frame type, found no type for " + typeStr);
		}
		
		//Label
		if(typeStr.startsWith("L") && StringUtils.isNumeric(typeStr.substring(1)))
			return getLabel(labels, typeStr);
		
		//Class name
		return typeStr;
	}
	
	//Return a label for the given labelIndex, creating it in the map if it doesn't exist
	protected Label getLabel(Map<String, Label> labelMap, String labelIndex) {
		Label label = labelMap.get(labelIndex);
		if(label == null)
		{
			label = new Label();
			labelMap.put(labelIndex, label);
		}
		return label;
	}
	
	protected boolean stringArrayContains(String[] arr, String value) {
		for(int i=0; i<arr.length; i++) {
			if(arr[i].equals(value))
				return true;
		}
		
		return false;
	}
	
}
