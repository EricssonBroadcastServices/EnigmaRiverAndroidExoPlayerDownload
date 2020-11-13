package com.redbeemedia.enigma.download;

import com.redbeemedia.enigma.core.testutil.ReflectionUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;

public class MockEnigmaDownloadContext {
    public static void resetInitialize(EnigmaDownloadContext.Initialization initialization) {
        Collection<Method> methods = ReflectionUtil.getPublicMethods(EnigmaDownloadContext.class, (isStatic, returnType, name, parametersTypes) -> isStatic && "resetInitialize".equals(name));
        try {
            methods.iterator().next().invoke(null, initialization);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
