package com.github.atdi.gboot.loader;

import com.github.atdi.gboot.loader.jar.GBootJarFile;
import com.github.atdi.gboot.loader.jar.Handler;

import java.net.URL;
import java.net.URLClassLoader;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Copyright (C) 2015 Aurel Avramescu
 */
public class GBootClassLoader extends URLClassLoader {

    private static LockProvider LOCK_PROVIDER = setupLockProvider();

    private final ClassLoader rootClassLoader;

    public GBootClassLoader(URLClassLoader parent) {
        super(parent.getURLs(), parent);
        this.rootClassLoader = parent;
    }

    /**
     * Create a new {@link GBootClassLoader} instance.
     * @param urls the URLs from which to load classes and resources
     * @param parent the parent class loader for delegation
     */
    public GBootClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.rootClassLoader = findRootClassLoader(parent);
    }

    public void addUrl(URL url) {
        super.addURL(url);
    }


    private ClassLoader findRootClassLoader(ClassLoader classLoader) {
        while (classLoader != null) {
            if (classLoader.getParent() == null) {
                return classLoader;
            }
            classLoader = classLoader.getParent();
        }
        return null;
    }

    @Override
    public URL getResource(String name) {
        URL url = null;
        if (this.rootClassLoader != null) {
            url = this.rootClassLoader.getResource(name);
        }
        return (url == null ? findResource(name) : url);
    }

    @Override
    public URL findResource(String name) {
        try {
            if (name.equals("") && hasURLs()) {
                return getURLs()[0];
            }
            return super.findResource(name);
        }
        catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        if (name.equals("") && hasURLs()) {
            return Collections.enumeration(Arrays.asList(getURLs()));
        }
        return super.findResources(name);
    }

    private boolean hasURLs() {
        return getURLs().length > 0;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (this.rootClassLoader == null) {
            return findResources(name);
        }

        final Enumeration<URL> rootResources = this.rootClassLoader.getResources(name);
        final Enumeration<URL> localResources = findResources(name);

        return new Enumeration<URL>() {

            @Override
            public boolean hasMoreElements() {
                return rootResources.hasMoreElements()
                        || localResources.hasMoreElements();
            }

            @Override
            public URL nextElement() {
                if (rootResources.hasMoreElements()) {
                    return rootResources.nextElement();
                }
                return localResources.nextElement();
            }

        };
    }

    /**
     * Attempt to load classes from the URLs before delegating to the parent loader.
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        synchronized (GBootClassLoader.LOCK_PROVIDER.getLock(this, name)) {
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass == null) {
                Handler.setUseFastConnectionExceptions(true);
                try {
                    loadedClass = doLoadClass(name);
                }
                finally {
                    Handler.setUseFastConnectionExceptions(false);
                }
            }
            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }
    }

    private Class<?> doLoadClass(String name) throws ClassNotFoundException {

        // 1) Try the root class loader
        try {
            if (this.rootClassLoader != null) {
                return this.rootClassLoader.loadClass(name);
            }
        }
        catch (Exception ex) {
        }

        // 2) Try to find locally
        try {
            findPackage(name);
            Class<?> cls = findClass(name);
            return cls;
        }
        catch (Exception ex) {
        }

        // 3) Use standard loading
        return super.loadClass(name, false);
    }

    private void findPackage(final String name) throws ClassNotFoundException {
        int lastDot = name.lastIndexOf('.');
        if (lastDot != -1) {
            String packageName = name.substring(0, lastDot);
            if (getPackage(packageName) == null) {
                try {
                    definePackageForFindClass(name, packageName);
                }
                catch (Exception ex) {
                    // Swallow and continue
                }
            }
        }
    }

    /**
     * Define a package before a {@code findClass} call is made. This is necessary to
     * ensure that the appropriate manifest for nested JARs associated with the package.
     * @param name the class name being found
     * @param packageName the package
     */
    private void definePackageForFindClass(final String name, final String packageName) {
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                @Override
                public Object run() throws ClassNotFoundException {
                    String path = name.replace('.', '/').concat(".class");
                    for (URL url : getURLs()) {
                        try {
                            if (url.getContent() instanceof GBootJarFile) {
                                GBootJarFile jarFile = (GBootJarFile) url.getContent();
                                // Check the jar entry data before needlessly creating the
                                // manifest
                                if (jarFile.getJarEntryData(path) != null
                                        && jarFile.getManifest() != null) {
                                    definePackage(packageName, jarFile.getManifest(), url);
                                    return null;
                                }

                            }
                        }
                        catch (IOException ex) {
                            // Ignore
                        }
                    }
                    return null;
                }
            }, AccessController.getContext());
        }
        catch (java.security.PrivilegedActionException ex) {
            // Ignore
        }
    }

    private static LockProvider setupLockProvider() {
        try {
            ClassLoader.registerAsParallelCapable();
            return new Java7LockProvider();
        }
        catch (NoSuchMethodError ex) {
            return new LockProvider();
        }
    }

    /**
     * Strategy used to provide the synchronize lock object to use when loading classes.
     */
    private static class LockProvider {

        public Object getLock(GBootClassLoader classLoader, String className) {
            return classLoader;
        }

    }

    /**
     * Java 7 specific {@link GBootClassLoader.LockProvider}.
     */
    private static class Java7LockProvider extends LockProvider {

        @Override
        public Object getLock(GBootClassLoader classLoader, String className) {
            return classLoader.getClassLoadingLock(className);
        }

    }

}