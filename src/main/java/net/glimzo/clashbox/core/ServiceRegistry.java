package net.glimzo.clashbox.core;

import java.util.HashMap;
import java.util.Map;

public final class ServiceRegistry {

    private static final Map<Class<?>, Object> services = new HashMap<>();

    private ServiceRegistry() {}

    public static <T> void register(Class<T> clazz, T instance) {
        services.put(clazz, instance);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> clazz) {
        Object service = services.get(clazz);
        if (service == null) {
            throw new IllegalStateException("[ClashBox] Service not registered: " + clazz.getSimpleName());
        }
        return (T) service;
    }

    public static void clear() {
        services.clear();
    }
}
