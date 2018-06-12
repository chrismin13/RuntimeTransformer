package me.yamakaja.runtimetransformer.agent;

import me.yamakaja.runtimetransformer.annotation.*;
import me.yamakaja.runtimetransformer.util.MethodUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by Yamakaja on 19.05.17.
 */
public class AgentJob {

    private final List<MethodJob> methodJobs;
    private Class<?> transformer;
    private Class<?> toTransform;
    private Class<?>[] interfaces;

    private Map<String, SpecialInvocation> specialInvocations = new HashMap<>();

    public AgentJob(Class<?> transformer) {
        this.transformer = transformer;
        this.interfaces = transformer.getInterfaces();

        this.readTransformationTarget(transformer);

        ClassNode transformerNode = new ClassNode(Opcodes.ASM5);
        ClassReader transformerReader;

        try {
            transformerReader = new ClassReader(transformer.getResource(transformer.getSimpleName() + ".class").openStream());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load class file of " + this.toTransform.getSimpleName(), e);
        }

        transformerReader.accept(transformerNode, 0);
        Map<String, ClassNode> innerClasses = readInnerClasses(transformerNode);

        Method[] methods = transformer.getDeclaredMethods();

        this.findSpecialInvocations(methods);

        this.methodJobs = new ArrayList<>(methods.length);

        Arrays.stream(methods).filter(method -> method.isAnnotationPresent(Inject.class))
                .forEach(method -> {

                    InjectionType type = method.getAnnotation(Inject.class).value();

                    Optional<MethodNode> transformerMethodNode = ((List<MethodNode>) transformerNode.methods).stream()
                            .filter(node -> node != null && method.getName().equals(node.name) && MethodUtils.getSignature(method).equals(node.desc))
                            .findAny();

                    if (!transformerMethodNode.isPresent())
                        throw new RuntimeException("Transformer method node not found! (WTF?)");

                    this.methodJobs.add(new MethodJob(type, this.toTransform.getName().replace('.', '/'),
                            this.toTransform,
                            transformer.getName().replace('.', '/'),
                            this.toTransform.getSuperclass().getName().replace('.', '/'),
                            transformerMethodNode.get(), this.specialInvocations, innerClasses));

                });
    }

    private Map<String, ClassNode> readInnerClasses(ClassNode classNode) {
        Map<String, ClassNode> ret = new HashMap<>();
        ((List<InnerClassNode>) classNode.innerClasses).stream()
                .filter(node -> node.name.matches(".*\\$[0-9]+"))
                .filter(node -> node.innerName == null && node.outerName == null)
                .map(node -> {
                    ClassNode innerClassNode = new ClassNode(Opcodes.ASM5);

                    try (InputStream inputStream = this.transformer.getResourceAsStream(node.name.substring(node.name.lastIndexOf('/') + 1) + ".class")) {
                        ClassReader reader = new ClassReader(inputStream);

                        reader.accept(innerClassNode, 0);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    return innerClassNode;
                }).forEach(node -> ret.put(node.name, node));
        return ret;
    }

    private void findSpecialInvocations(Method[] methods) {
        Arrays.stream(methods)
                .filter(method -> method.isAnnotationPresent(CallParameters.class))
                .forEach(method -> this.specialInvocations.put(method.getName().replace('.', '/'),
                        new SpecialInvocation(method)));
    }

    private void readTransformationTarget(Class<?> transformer) {
        if (transformer.isAnnotationPresent(Transform.class)) {
            this.toTransform = transformer.getAnnotation(Transform.class).value();
            return;
        }

        if (transformer.isAnnotationPresent(TransformByName.class))
            try {
                this.toTransform = Class.forName(transformer.getAnnotation(TransformByName.class).value(), true, transformer.getClassLoader());
                return;
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Failed to transform class: ", e);
            }

        throw new RuntimeException("No transformation annotation present on transformer: " + transformer.getName());
    }

    public List<MethodJob> getMethodJobs() {
        return methodJobs;
    }

    public Class<?> getToTransform() {
        return toTransform;
    }

    public Class<?>[] getInterfaces() {
        return interfaces;
    }

    public Class<?> getTransformer() {
        return transformer;
    }

    public void apply(ClassNode node) {
        for (MethodJob methodJob : methodJobs)
            methodJob.apply(node);
    }
}
