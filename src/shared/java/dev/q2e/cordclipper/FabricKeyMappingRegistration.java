package dev.q2e.cordclipper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Registers a key mapping across the Fabric API's keybinding-to-keymapping
 * package transition without tying the 26.1 adapter to one API release.
 */
public final class FabricKeyMappingRegistration {
    private static final System.Logger LOGGER = System.getLogger("cord_clipper");

    private FabricKeyMappingRegistration() {
    }

    public static <T> T register(T mapping) {
        if (mapping == null) {
            throw new IllegalArgumentException("mapping cannot be null");
        }

        String[][] candidates = {
                {"net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper", "registerKeyBinding"},
                {"net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper", "registerKeyMapping"}
        };

        for (String[] candidate : candidates) {
            try {
                Class<?> helper = Class.forName(candidate[0], true, mapping.getClass().getClassLoader());
                for (Method method : helper.getMethods()) {
                    if (method.getName().equals(candidate[1])
                            && Modifier.isStatic(method.getModifiers())
                            && method.getParameterCount() == 1
                            && method.getParameterTypes()[0].isAssignableFrom(mapping.getClass())) {
                        method.invoke(null, mapping);
                        return mapping;
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // Older and newer Fabric API releases use different helper packages.
            } catch (IllegalAccessException | InvocationTargetException | LinkageError | SecurityException exception) {
                LOGGER.log(System.Logger.Level.ERROR, "Cord Clipper could not register its settings keybind.", exception);
                return mapping;
            }
        }

        LOGGER.log(System.Logger.Level.ERROR, "Cord Clipper found no compatible Fabric keybind registration helper.");
        return mapping;
    }
}
