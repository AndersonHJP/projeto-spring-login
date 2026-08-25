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
}
