package com.enosistudio;

import org.jetbrains.annotations.Contract;

import java.net.URL;

/**
 * Simple wrapper for resource folders.
 * Provides consistent path handling for resource directories in both JAR and filesystem environments.
 */
@SuppressWarnings("unused")
public class RFolder {
    private final String path;
    private final ClassLoader loader;

    /**
     * Creates a new RFolder instance with the specified folder path.
     *
     * @param folderPath the complete path to the folder (leading slash optional)
     * @throws IllegalArgumentException if folderPath is null or empty
     */
    protected RFolder(String folderPath) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Folder path cannot be null or empty");
        }

        this.path = folderPath.startsWith("/") ? folderPath.substring(1) : folderPath;
        this.loader = getClass().getClassLoader();
    }

    // ========== Metadata ==========

    /**
     * Returns the absolute URL for this folder.
     *
     * @return the URL
     */
    @Contract(pure = true)
    public URL getURL() {
        return loader.getResource(path);
    }

    /**
     * Checks if the resource folder exists.
     *
     * @return true if it exists
     */
    @Contract(pure = true)
    public boolean exists() {
        return getURL() != null;
    }

    // ========== Name Parsing ==========

    /**
     * Returns the folder name (last part of the path).
     *
     * @return the folder name
     */
    @Contract(pure = true)
    public String getFolderName() {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    // ========== Paths ==========

    /**
     * Returns the normalized path (without leading slash).
     *
     * @return the resource path
     */
    @Contract(pure = true)
    public String getResourcePath() {
        return path;
    }

    /**
     * Returns the path with leading slash.
     *
     * @return the path prefixed with "/"
     */
    @Contract(pure = true)
    public String getResourcePathWithSlash() {
        return "/" + path;
    }

    /**
     * Returns the parent directory path.
     *
     * @return the parent path, or "" if at root
     */
    @Contract(pure = true)
    public String getParentResourcePath() {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(0, lastSlash) : "";
    }

    @Override
    public String toString() {
        return path;
    }
}