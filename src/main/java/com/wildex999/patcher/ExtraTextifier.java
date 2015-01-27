package com.wildex999.patcher;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;

//Extends the default ASM Textifier to provide some more info we use when parsing the classes after patching
//For example: Whether a constant number is a float or double, The type of an Annotation value etc.

//Most of the methods are directo copies with some small changes from the super class

public class ExtraTextifier extends Textifier {

	public ExtraTextifier() {
		super(Opcodes.ASM5);
	}
	
    @Override
    public void visitLdcInsn(final Object cst) {
        buf.setLength(0);
        buf.append(tab2).append("LDC ");
        if (cst instanceof String) {
            Printer.appendString(buf, (String) cst);
        } else if(cst instanceof Float) {
        	buf.append(cst).append("F");
        } else if(cst instanceof Double) {
        	buf.append(cst).append("D");
        } else if(cst instanceof Long) {
        	buf.append(cst).append("L");
        } else if (cst instanceof Type) {
            buf.append(((Type) cst).getDescriptor()).append(".class");
        } else {
            buf.append(cst);
        }
        buf.append('\n');
        text.add(buf.toString());
    }
    
    @Override
    protected Textifier createTextifier() {
        return new ExtraTextifier();
    }
}
