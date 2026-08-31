package com.familyti.product.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerUtil {

    private LoggerUtil() {
    }

    private static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    private static String methodSignature(Class<?> clazz, String methodName) {
        return clazz.getSimpleName() + "::" + methodName;
    }

    public static void logError(Class<?> clazz, String methodName, String message, Throwable t, Object... args) {
        getLogger(clazz).error("[{}] " + message, methodSignature(clazz, methodName), args, t);
    }

    public static void logInfo(Class<?> clazz, String methodName, String message, Object... args) {
        getLogger(clazz).info("[{}] " + message, prepend(methodSignature(clazz, methodName), args));
    }

    public static void logWarn(Class<?> clazz, String methodName, String message, Object... args) {
        getLogger(clazz).warn("[{}] " + message, prepend(methodSignature(clazz, methodName), args));
    }

    private static Object[] prepend(String signature, Object... args) {
        Object[] combined = new Object[args.length + 1];
        combined[0] = signature;
        System.arraycopy(args, 0, combined, 1, args.length);
        return combined;
    }
}
