package me.yamakaja.runtimetransformer.transform;

import org.objectweb.asm.ClassWriter;

/**
 * Created by Yamakaja on 3/5/18.
 */
public class FixedClassWriter extends ClassWriter {

    private ClassLoader loader;

    public FixedClassWriter(int flags, ClassLoader loader) {
        super(flags);
        this.loader = loader;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        Class<?> c, d;
        try {
            c = Class.forName(type1.replace('/', '.'), false, loader);
            d = Class.forName(type2.replace('/', '.'), false, loader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (c.isAssignableFrom(d))
            return type1;

        if (d.isAssignableFrom(c))
            return type2;

        if (c.isInterface() || d.isInterface())
            return "java/lang/Object";

        else {
            do
                c = c.getSuperclass();
            while (!c.isAssignableFrom(d));

            return c.getName().replace('.', '/');
        }
    }

}
