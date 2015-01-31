package com.wildex999.patcher;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceSignatureVisitor;

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
    
    //We want the signature in FRONT of the variable
    @Override
    public void visitLocalVariable(final String name, final String desc,
            final String signature, final Label start, final Label end,
            final int index) {
        buf.setLength(0);
        
        if (signature != null) {
            buf.append(tab2);
            appendDescriptor(FIELD_SIGNATURE, signature);

            TraceSignatureVisitor sv = new TraceSignatureVisitor(0);
            SignatureReader r = new SignatureReader(signature);
            r.acceptType(sv);
            buf.append(tab2).append("// declaration: ")
                    .append(sv.getDeclaration()).append('\n');
        }
        
        buf.append(tab2).append("LOCALVARIABLE ").append(name).append(' ');
        appendDescriptor(FIELD_DESCRIPTOR, desc);
        buf.append(' ');
        appendLabel(start);
        buf.append(' ');
        appendLabel(end);
        buf.append(' ').append(index).append('\n');

        text.add(buf.toString());
    }
    
    @Override
    protected Textifier createTextifier() {
        return new ExtraTextifier();
    }
}
