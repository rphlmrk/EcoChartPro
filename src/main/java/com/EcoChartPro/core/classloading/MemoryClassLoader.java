package com.EcoChartPro.core.classloading;

import java.util.Map;

/**
 * A custom ClassLoader that can load one or more classes from bytecode
 * held in memory.
 * <p>
 * This is the crucial link between the in-memory compiler and the running application.
 * It takes the byte[] output from the {@link com.EcoChartPro.core.service.CompilationService}
 * and defines it as a usable Class within the JVM.
 */
public class MemoryClassLoader extends ClassLoader {

    // A map of fully qualified class names to their corresponding bytecode.
    private final Map<String, byte[]> classBytecode;

    /**
     * Constructs a new MemoryClassLoader.
     *
     * @param classBytecode The map of class names to their bytecode.
     * @param parent        The parent class loader, which is used to load dependencies
     *                      (e.g., the application's API classes, standard Java libraries).
     */
    public MemoryClassLoader(Map<String, byte[]> classBytecode, ClassLoader parent) {
        super(parent);
        this.classBytecode = classBytecode;
    }

    /**
     * Finds a class by its binary name. This method is called by the
     * {@link ClassLoader#loadClass(String)} method after checking the parent
     * class loader.
     *
     * @param name The binary name of the class (e.g., "com.EcoChartPro.plugins.inapp.MyIndicator").
     * @return The resulting {@code Class} object.
     * @throws ClassNotFoundException if the class could not be found in the provided bytecode map.
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // Look for the bytecode in our in-memory map.
        byte[] bytecode = classBytecode.get(name);

        if (bytecode == null) {
            // If we don't have the bytecode for this class, we can't load it.
            // We delegate up to the parent by throwing this exception.
            // The super.loadClass() will have already checked the parent, so this
            // effectively signals "not found".
            return super.findClass(name);
        }

        // Use the defineClass method to turn the raw bytecode into a real Class object.
        // This is the core magic of a ClassLoader.
        // The first parameter is the class name.
        // The second is the byte array of the class data.
        // The third and fourth are the offset and length of the data in the array.
        return defineClass(name, bytecode, 0, bytecode.length);
    }
}