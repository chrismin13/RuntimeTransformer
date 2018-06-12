package me.yamakaja.runtimetransformer.agent;

import me.yamakaja.runtimetransformer.annotation.CallParameters;

import java.lang.reflect.Method;

/**
 * Created by Yamakaja on 3/5/18.
 */
public class SpecialInvocation {

    private Method method;

    private CallParameters callParameters;

    public SpecialInvocation(Method method) {
        this.method = method;
        this.callParameters = method.getAnnotation(CallParameters.class);
    }

    public Method getMethod() {
        return method;
    }

    public CallParameters getCallParameters() {
        return callParameters;
    }

}
