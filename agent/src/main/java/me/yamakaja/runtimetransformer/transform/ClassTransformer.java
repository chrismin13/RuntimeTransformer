package me.yamakaja.runtimetransformer.transform;

import me.yamakaja.runtimetransformer.agent.AgentJob;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

/**
 * Created by Yamakaja on 18.05.17.
 */
public class ClassTransformer implements ClassFileTransformer {

    private List<AgentJob> agentJobs;
    private List<Class<?>> classesToRedefine;

    public ClassTransformer(List<AgentJob> agentJobs) {
        this.agentJobs = agentJobs;
        classesToRedefine = agentJobs.stream().map(AgentJob::getToTransform).collect(Collectors.toList());
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!classesToRedefine.contains(classBeingRedefined))
            return classfileBuffer;

        ClassWriter writer;
        try {
            ClassReader reader = new ClassReader(classfileBuffer);

            ClassNode node = new ClassNode(Opcodes.ASM5);
            reader.accept(node, 0);

            this.agentJobs.stream()
                    .filter(job -> job.getToTransform().getName().replace('.', '/').equals(className))
                    .forEach(job -> job.apply(node));

            writer = new FixedClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, loader);

            node.accept(writer);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return writer.toByteArray();
    }

    public Class<?>[] getClassesToTransform() {
        return agentJobs.stream().map(AgentJob::getToTransform).toArray((IntFunction<Class<?>[]>) Class[]::new);
    }

}
