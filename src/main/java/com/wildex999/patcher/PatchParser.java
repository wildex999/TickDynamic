package com.wildex999.patcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Written by: Wildex999 ( wildex999@gmail.com )
 * 
 * Parser for custom patching for Minecraft coremods
 * The goal of this patching is to be able to insert, replace or remove bytecode between two known points.
 * These points are defined as a set of instructions.
 * Points should allow for unknown/dynamic Label and LocalVariable numbers, and even to store and re-use these numbers in the patched code.
 * 
 * Instruction:
 * @ = Command
 * * = Anything. For example: 'LDC 1*2'  can be 'LDC 132', 'LDC 1432', 'LDC 1test2' (Note: Does not match length 0)
 * + = Anything until newline(Newline is not included)
 * <x] = Anything less than 'x' characters in length. Example: 'Test<3]g' can be 'Testing'
 * >x] = Anything more than 'x' characters in length. Example: ''
 * #x] = Anything exactly 'x' characters in length
 * ?varname] = Anything, then stored to varname. For example: 'L?label1]' can be 'L143', which will then put '143' into label1
 * Note: A variable can only be set once during a Start/End!
 * =varname] = Whatever is stored in the varname. For example: 'L=label1]' will become to 'L143', but note that label1 must be set earlier in the patch.
 * {varname]RegEx} = An regular expression, with the result saved to varname. 
 * Note: You have to escape any '}' in the regular expression using '%'.
 * Note2: Each saved variable from @S to @E will be stored in a named group(varname) and can be accessed using the RegEx.
 * Note3: A variable can only be set once during a Start/End!
 * ^varname] = Mark the variable varname as set through RegEx. This will save the variable for later use.
 * Note: A variable is set by using a named group, like: (?<varname>expr)
 * " = Ignore everything until next newline, essentially a comment. Note: Comments inside a command are ignored
 * ! = Ignore everything until next occurrence of !, multi-line comment Note: Comments inside a command are ignored
 * 
 * % = Ignore next instruction/command (%@ = @, %% = % etc.)
 * 
 * varname can only be the characters[a-z][A-z][0-9], and can not begin with a number!
 * 
 * Commands:
 * Note: All commands end once a new one start, for example: '@SThis is @-the@+a@E test.'
 * 
 * @| = End of previous command. Use this when you can't end the command by starting another, for example for formating:
 * '@SThis is @|
 * Note: MUST be used at the end to indicate end of stream, or else the last command is ignored!
 * 
 * @-the@|
 * @+a@|
 * 
 * @E test.'
 * 
 * @O = Origin point, everything after this, will look behind the point defined here.
 * For example, '@Opublic getBiomeGenForCoordsBody(II)Lnet/minecraft/world/biome/BiomeGenBase;@|' will make every subsequent
 * operation start checking after the first occurrence of this origin. This will allow the patching to speed up a lot
 * when the origin point is well chosen.
 * 
 * @L = Limit point, set the point which can not be searched past. Use with @O to set an area for @S and @E to work within.
 * The limit applies to every command after this command. The limit can be overwritten.
 * Example: 
 * '@Opublic getBiomeGenForCoordsBody(II)Lnet/minecraft/world/biome/BiomeGenBase;@|
 * @LLOCALVARIABLE this Lnet/minecraft/world/World; L0 L1 0@|'
 * Will make every subsequent command work within those two known points.
 * Note: will use the first occurrence found after the set origin point!
 * 
 * @R = Reset origin
 * @U = Remove limit
 * Note: These are both empty commands, and doesn't have any data
 * 
 * TODO: @N = Next point, set the origin equal to the end of the previous @E. This allows for a 'walking' patch without having to worry about absolutely unique @S and @E.
 * 
 * @S = Start point, defines the start point of every addition and/or subtraction
 * 
 * @E = End point, defines the end point of every addition and/or subtraction
 * 
 * @- = Subtraction, everything defined here will be removed from between the start point and end point.
 * This must define exactly what is between @S and @E, or else it will not be detected as the correct chunk.
 * For example: 'One Two Three'   '@SOne@- Two @E Three' will become 'One Three'.
 * 
 * @+ = Addition, everything defined here will be added between the start point and end point
 * For example: 'One Three'  '@SOne@+ Two@E Three' will become 'One Two Three'.
 * 
 * Note: You can use @+ or @- alone, however when used together the subtraction will always run first.
 * However, if you have multiple additions of subtractions, they will run in order
 * For Example: 'This is an test for you!'
 * '@Sis an@|
 * @+ examp@|
 * @+le @|
 * @- test @|
 * @Efor you!@|'
 * Will give: 'This is an example for you!'
 * Whereas 'This is an product for you!' would fail, as it can't find the combination to replace.
 */

public class PatchParser {
	
	public enum TokenType {
		Anything,
		AnythingLine,
		AnythingLess,
		AnythingMore,
		AnythingExact,
		AnythingSet,
		AnythingGet,
		RegEx,
		TouchVar,
		Text,
		CommandOrigin,
		CommandLimit,
		CommandReset,
		CommandUnlimit,
		CommandStart,
		CommandEnd,
		CommandAdd,
		CommandSubtract
	}
	
	public static class CommandToken {
		public TokenType type; //The type of token
		public String text; //Any text bound to this command
		public List<ReplacementToken> replacementTokens = new ArrayList<ReplacementToken>(); //Tokens where text will be replaced, and also tokens for raw text
		public static int tokenCounter = 0;
		public int tokenId;
		
		public CommandToken(TokenType type) { this.type = type; tokenId = tokenCounter++;}
		public CommandToken(TokenType type, String text) { this.type = type; this.text = text; tokenId = tokenCounter++;}
	}
	
	public static class ReplacementToken {
		public TokenType type;
		public Object var; //Variable(A String or an int depending on type)
		
		public ReplacementToken(TokenType type) { this.type = type; }
		public ReplacementToken(TokenType type, Object var) { this.type = type; this.var = var; }
	}
	
	List<CommandToken> tokens; //List of command tokens in execution order
	Map<String, String> variableMap;
	
	//Parse the patch, throws an exception on failure
	public void parsePatch(String patch) throws Exception {
		tokens = new ArrayList<CommandToken>();
		
		patch = patch.replace("\r", ""); //normalize newline
		
		//First we parse the commands, as we need both the start and the end
		boolean inMultiLineComment = false;
		TokenType currentCommand = null;
		int commandContentIndex = 0;
		System.out.println("Patch length: " + patch.length());
		for(int i=0; i<patch.length(); i++) {			
			if(inMultiLineComment && patch.charAt(i) != '!')
				continue;
			
			switch(patch.charAt(i))
			{
			case '@': //Command
				TokenType command;
				char currentChar = patch.charAt(++i);
				
				switch(currentChar)
				{
				case 'O':
					command = TokenType.CommandOrigin;
					break;
				case 'L':
					command = TokenType.CommandLimit;
					break;
				case 'R':
					command = TokenType.CommandReset;
					break;
				case 'U':
					command = TokenType.CommandUnlimit;
					break;
				case 'S':
					command = TokenType.CommandStart;
					break;
				case 'E':
					command = TokenType.CommandEnd;
					break;
				case '+':
					command = TokenType.CommandAdd;
					break;
				case '-':
					command = TokenType.CommandSubtract;
					break;
				case '|':
					command = null;
					break;
				default:
					throw new Exception("("+i+")Unknown command: " + currentChar);
				}
				
				if(currentCommand != null) {
					tokens.add(new CommandToken(currentCommand, patch.substring(commandContentIndex, i-1)));
					System.out.println("Wrote command: " + currentCommand + " with Text: " + tokens.get(tokens.size()-1).text);
				}
				
				if(command != null) {
					currentCommand = command;
					commandContentIndex = i+1;
				}
				else
					currentCommand = null;
				break;
			case '%':
				i++; //Bypass next char
				break;
			case '"':
				if(currentCommand != null)
					break;
				i = patch.indexOf('\n', i);
				if(i == -1)
					i=patch.length(); //Reached the end
				break;
			case '!':
				if(currentCommand != null)
					break;
				inMultiLineComment = !inMultiLineComment;
				break;
			default:
					
				break;
			}
		}
		
		System.out.println("Done parsing step 1");
		
		//Parse the replacement tokens inside each command
		for(Iterator<CommandToken> it = tokens.iterator(); it.hasNext(); )
		{
			CommandToken token = it.next();
			StringBuilder str = new StringBuilder(token.text);
			
			int startIndex = 0; //Used for creating text tokens
			int index;
			for(int i=0; i<str.length(); i++) {
				char currentChar = str.charAt(i);
				TokenType type;
				
				switch(currentChar) 
				{
				case '*':
					token.replacementTokens.add( new ReplacementToken(TokenType.Text, str.substring(startIndex, i)) );
					token.replacementTokens.add(new ReplacementToken(TokenType.Anything));
					startIndex = i + 1;
					break;
				case '+':
					token.replacementTokens.add( new ReplacementToken(TokenType.Text, str.substring(startIndex, i)) );
					token.replacementTokens.add(new ReplacementToken(TokenType.AnythingLine));
					startIndex = i + 1;
					break;
				case '<':
				case '>':
				case '#':
				case '^':
					//Add a token with the text before our replacement
					token.replacementTokens.add( new ReplacementToken(TokenType.Text, str.substring(startIndex, i)) );
					index = str.indexOf("]", i);
					//Add new token with the contained value for length
					if(currentChar == '<')
						type = TokenType.AnythingLess;
					else if(currentChar == '>')
						type = TokenType.AnythingMore;
					else if(currentChar == '#')
						type = TokenType.AnythingExact;
					else
						type = TokenType.TouchVar;
					token.replacementTokens.add( new ReplacementToken(type, Integer.parseInt(str.substring(i+1, index))) );
					startIndex = index + 1;
					i = index;
					
					break;
				case '?':
				case '=':
					token.replacementTokens.add( new ReplacementToken(TokenType.Text, str.substring(startIndex, i)) );
					index = str.indexOf("]", i);
					if(currentChar == '?')
						type = TokenType.AnythingSet;
					else
						type = TokenType.AnythingGet;
					token.replacementTokens.add( new ReplacementToken(type, str.substring(i+1, index)) );
					startIndex = index + 1;
					i = index;
					break;
				case '{':
					token.replacementTokens.add( new ReplacementToken(TokenType.Text, str.substring(startIndex, i)) );
					index = str.indexOf("]", i);
					String[] var = new String[2];
					var[0] = str.substring(i+1, index); //The varname
					//Find the end of the RegEx
					int regStart = index;
					while(true) {
						index = str.indexOf("}", index);
						if(str.charAt(index-1) != '%')
							break;
						str.deleteCharAt(index-1);
					}
					var[1] = str.substring(regStart, index); //The RegEx
					token.replacementTokens.add( new ReplacementToken(TokenType.RegEx, var) );
					
					startIndex = index + 1;
					i = index;
					break;
				case '%':
					str.deleteCharAt(i);
					break;
				}
				
			}
			
			//Add a text token for any remaining text after last replacement
			if(startIndex < str.length())
				token.replacementTokens.add(new ReplacementToken(TokenType.Text, str.substring(startIndex)) );

			System.out.println("Replacements for " + token.type + ": ");
			for(Iterator<ReplacementToken> ita = token.replacementTokens.iterator(); ita.hasNext(); )
			{
				ReplacementToken t = ita.next();
				System.out.println("- " + t.type + " :: " + t.var);
			}
			
		}
	}
	
	//Patch the given base using the parsed patch
	public String patch(String basee) throws Exception {
		basee = basee.replace("\r", ""); //normalize newline
		StringBuilder output = new StringBuilder(basee); //Our patched base
		variableMap = new HashMap<String, String>();
		Pattern pattern;
		Matcher matcher;
		int startIndex;
		
		int baseOrigin = 0;
		int baseLimit = basee.length();
		
		if(tokens == null)
			throw new Exception("parsePatch must be run before patch, can not patch without tokens!");
			
		for(int i = 0; i < tokens.size(); i++)
		{
			CommandToken token = tokens.get(i);
			
			if(token.type == TokenType.CommandOrigin)
			{
				StringBuilder regEx = new StringBuilder();
				Map<String, Boolean> varSet = new HashMap<String, Boolean>();
				generateReplacementRegEx(token, regEx, varSet);
				pattern = Pattern.compile(regEx.toString());
				matcher = pattern.matcher(output);
				if(!matcher.find())
					throw new Exception("(Token:"+i+")Unable to match Origin: " + regEx.toString());
				baseOrigin = matcher.start();
				continue;
			}
			else if(token.type == TokenType.CommandLimit)
			{
				StringBuilder regEx = new StringBuilder();
				Map<String, Boolean> varSet = new HashMap<String, Boolean>();
				generateReplacementRegEx(token, regEx, varSet);
				pattern = Pattern.compile(regEx.toString());
				matcher = pattern.matcher(output);
				if(!matcher.find())
					throw new Exception("(Token:"+i+")Unable to match Limit: " + regEx.toString());
				baseLimit = matcher.start();
				continue;
			}
			else if(token.type == TokenType.CommandReset)
			{
				baseOrigin = 0;
				continue;
			}
			else if(token.type == TokenType.CommandUnlimit)
			{
				baseLimit = output.length();
				continue;
			}
			else if(token.type != TokenType.CommandStart)
				throw new Exception("(Token:"+i+")Expected CommandStart, but got: " + token.type + " with content: " + token.text);
			
			startIndex = i;
			
			//Generate the detection RegEx
			StringBuilder detectionRegEx = new StringBuilder(); 
			Map<String, Boolean> varSet = new HashMap<String, Boolean>();
			boolean ended = false;
			for(; i<tokens.size(); i++)
			{
				token = tokens.get(i);
				
				if(token.type == TokenType.CommandAdd)
					continue;
				else if(token.type == TokenType.CommandOrigin)
					new Exception("(Token:"+i+")Can not define an Origin between Start and End.");
				else if(token.type == TokenType.CommandLimit)
					new Exception("(Token:"+i+")Can not define an Limit between Start and End.");
				else if(token.type == TokenType.CommandEnd && token.text.length() == 0)
				{
					ended = true;
					break; //Ignore empty end
				}
				
				//Generate RegEx for replacement tokens
				if(token.type == TokenType.CommandStart)
					detectionRegEx.append("("); //Start number group for later position reference
				else
					detectionRegEx.append("(?<t" + token.tokenId + ">");
				generateReplacementRegEx(token, detectionRegEx, varSet);
				detectionRegEx.append(")");
				
				if(token.type == TokenType.CommandEnd)
				{
					ended = true;
					break;
				}
			}
			
			if(ended != true)
				throw new Exception("Reached end of stream without End command!");
			
			System.out.println("(" + token.type + ") RegEx: " + detectionRegEx.toString());
			
			//Find occurrence in base using generated RegEx
			String subBase = output.substring(baseOrigin, baseLimit);
			pattern = Pattern.compile(detectionRegEx.toString(), Pattern.DOTALL);
			matcher = pattern.matcher(subBase);
			if(!matcher.find())
				throw new Exception("Failed to match: '" + detectionRegEx.toString() + "'\nOn base: '" + subBase +"'");
			
			//Read back any set variables
			for(Iterator<String> it = varSet.keySet().iterator(); it.hasNext(); )
			{
				String varName = it.next();
				String varValue = matcher.group(varName);
				if(varValue == null)
					throw new Exception("Variable '" + varName + "' was marked as set, but was null!");
				variableMap.put(varName, varValue);
			}
			
			//Start applying the actions
			int offset = baseOrigin + matcher.end(1); //The first group is always the CommandStart group
			
			//First we do removal
			for(i = startIndex; i<tokens.size(); i++)
			{
				token = tokens.get(i);
				if(token.type == TokenType.CommandEnd)
					break;
				if(token.type != TokenType.CommandSubtract)
					continue;
				
				int length = matcher.group("t"+token.tokenId).length();
				output.delete(offset, offset+length);
				
				baseLimit -= length;
			}
			
			//Then do additions
			for(i = startIndex; i<tokens.size(); i++)
			{
				token = tokens.get(i);
				
				if(token.type == TokenType.CommandEnd)
					break;
				if(token.type != TokenType.CommandAdd)
					continue;
				
				for(Iterator<ReplacementToken> it = token.replacementTokens.iterator(); it.hasNext();)
				{
					ReplacementToken repToken = it.next();
					String in;
					if(repToken.type == TokenType.Text)
						in = (String)repToken.var;
					else if(repToken.type == TokenType.AnythingGet)
					{
						in = variableMap.get((String)repToken.var);
						if(in == null)
							throw new Exception("Tried to use nonexistent variable '" + (String)repToken.var + "' during Add command.");
					}
					else
						throw new Exception("Unsuported action during CommandAdd: " + repToken.type);
					
					output.insert(offset, in);
					baseLimit += in.length();
					offset += in.length();
				}
			}

		}
		
		return output.toString();
	}
	
	protected void generateReplacementRegEx(CommandToken token, StringBuilder detectionRegEx, Map<String, Boolean> varSet) throws Exception {
		for(int repToken = 0; repToken < token.replacementTokens.size(); repToken++)
		{
			ReplacementToken action = token.replacementTokens.get(repToken);
			
			if(action.type == TokenType.Text)
				detectionRegEx.append(Pattern.quote((String)action.var));
			else if(action.type == TokenType.Anything)
				detectionRegEx.append(".+"); //We use DOTALL so it includes newline
			else if(action.type == TokenType.AnythingLine)
				detectionRegEx.append("[^<>\\r\\n]+"); //Pretty much the .+ without DOTALL
			else if(action.type == TokenType.AnythingExact)
				detectionRegEx.append(".{").append(action.var).append("}");
			else if(action.type == TokenType.AnythingLess)
				detectionRegEx.append(".{0,").append(((Integer)action.var)+1).append("}");
			else if(action.type == TokenType.AnythingMore)
				detectionRegEx.append(".{").append(action.var).append(",}");
			else if(action.type == TokenType.AnythingSet)
			{
				if(varSet.get((String)action.var) != null)
					throw new Exception("(Token:"+token.type+") Trying to set a variable("+ action.var +") that has already been set!");
				detectionRegEx.append("(?<").append(action.var).append(">[^<>\\r\\n]+)");
				varSet.put((String)action.var, true);
			}
			else if(action.type == TokenType.AnythingGet)
			{
				if(varSet.get((String)action.var) == null)
				{
					if(variableMap.get((String)action.var) == null)
						throw new Exception("(Token:"+token.type+") Trying to get a variable(" + action.var + ") which has not yet been set!");
					//Been set during a previous Start/End, so we set it now
					detectionRegEx.append("(?<").append(action.var).append(">").append(variableMap.get((String)action.var)).append(")");
					varSet.put((String)action.var, true);
				}
				else //Been set during this Start/End, so we just reference it
					detectionRegEx.append("\\k<").append(action.var).append(">"); 
			}
			else if(action.type == TokenType.RegEx)
			{
				detectionRegEx.append("(?<").append( ((String[])action.var)[0] ).append(">").append( ((String[])action.var)[1] ).append(")");
				varSet.put(((String[])action.var)[0], true);
			}
			else if(action.type == TokenType.TouchVar)
				varSet.put((String) action.var, true);
			
		}
	}
}
