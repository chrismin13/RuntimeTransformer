package me.yamakaja.runtimetransformer.agent;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Yamakaja on 3/5/18.
 */
public class ClassFactory {

    private static final String classPrefix = "me/yamakaja/runtimetransformer/generated/Anonymous$";
    private static volatile AtomicLong classCounter = new AtomicLong(0);
    private static Method classLoaderDefineClass;

    static {
        try {
            classLoaderDefineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
            classLoaderDefineClass.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public static Class<?> generateAnonymousClassSubstitute(String newOuterClass, ClassNode paramNode, ClassLoader targetClassLoader) {
        ClassNode node = new ClassNode(Opcodes.ASM5);
        paramNode.accept(node);

        String originalClassName = node.name;
        String originalContainingClass = node.name.substring(0, node.name.lastIndexOf('$'));

        node.name = classPrefix + classCounter.getAndIncrement();
        node.access = Modifier.PUBLIC;

        List<MethodNode> methods = (List<MethodNode>) node.methods;

        for (MethodNode method : methods) {
            method.access = (method.access | Modifier.PUBLIC) & ~(Modifier.PRIVATE | Modifier.PROTECTED);
            for (ListIterator<AbstractInsnNode> iter = method.instructions.iterator(); iter.hasNext(); ) {
                AbstractInsnNode instruction = iter.next();

                if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode fieldInsn = (FieldInsnNode) instruction;

                    if ("<init>".equals(method.name)) {
                        fieldInsn.owner = node.name;
                        if (fieldInsn.desc.equals("L" + originalContainingClass + ";"))
                            fieldInsn.desc = "L" + newOuterClass + ";";
                        continue;
                    }

                    if (fieldInsn.owner.equals(originalClassName))
                        fieldInsn.owner = node.name;
                }

                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode methodInsn = (MethodInsnNode) instruction;

                    if (methodInsn.owner.equals(originalClassName))
                        methodInsn.owner = node.name;
                    else if (methodInsn.owner.equals(originalContainingClass))
                        methodInsn.owner = newOuterClass;
                }
            }


            if (method.name.equals("<init>"))
                method.desc = method.desc.replace(originalContainingClass, newOuterClass);
        }

        List<FieldNode> fields = (List<FieldNode>) node.fields;

        for (FieldNode field : fields) {
            field.access = (field.access | Modifier.PUBLIC) & ~(Modifier.PRIVATE | Modifier.PROTECTED);
            if (!field.desc.equals("L" + originalContainingClass + ";"))
                continue;

            field.desc = "L" + newOuterClass + ";";
        }

        node.outerClass = null;

        ClassWriter writer = new ClassWriter(Opcodes.ASM5);
        node.accept(writer);

        byte[] data = writer.toByteArray();

        try {
            return (Class<?>) classLoaderDefineClass.invoke(targetClassLoader, node.name.replace('/', '.'), data, 0, data.length);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

}
