package com.enosistudio;

import org.jetbrains.annotations.Contract;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Simple wrapper for classpath resources.
 * Handles both JAR and filesystem resources automatically.
 */
@SuppressWarnings("unused")
public final class RFile {
    private final String path;
    private final ClassLoader loader;

    /**
     * Creates a new instance for the specified resource.
     *
     * @param resourcePath the resource path (leading slash optional)
     * @throws IllegalArgumentException if path is null or empty
     */
    public RFile(String resourcePath) {
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource path cannot be null or empty");
        }

        this.path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        this.loader = getClass().getClassLoader();
    }

    // ========== Content Reading ==========

    /**
     * Opens an InputStream to the resource.
     *
     * @return a new stream
     * @throws IOException if the resource does not exist
     */
    @Contract(pure = true)
    public InputStream openStream() throws IOException {
        InputStream stream = loader.getResourceAsStream(path);
        if (stream == null) {
            throw new IOException("Resource not found: " + path);
        }
        return stream;
    }

    /**
     * Reads the entire content as UTF-8.
     *
     * @return the complete content
     * @throws IOException if read error occurs
     */
    @Contract(pure = true)
    public String readContent() throws IOException {
        return readContent(StandardCharsets.UTF_8);
    }

    /**
     * Reads the entire content with specified encoding.
     *
     * @param charset the encoding to use
     * @return the complete content
     * @throws IOException if read error occurs
     */
    @Contract(pure = true)
    public String readContent(Charset charset) throws IOException {
        if (charset == null) {
            throw new NullPointerException("Charset cannot be null");
        }
        try (InputStream in = openStream()) {
            return new String(in.readAllBytes(), charset);
        }
    }

    /**
     * Opens a BufferedReader using UTF-8.
     *
     * @return a new reader
     * @throws IOException if open error occurs
     */
    @Contract(pure = true)
    public BufferedReader openBufferedReader() throws IOException {
        return openBufferedReader(StandardCharsets.UTF_8);
    }

    /**
     * Opens a BufferedReader with specified encoding.
     *
     * @param charset the encoding to use
     * @return a new reader
     * @throws IOException if open error occurs
     */
    @Contract(pure = true)
    public BufferedReader openBufferedReader(Charset charset) throws IOException {
        if (charset == null) {
            throw new NullPointerException("Charset cannot be null");
        }
        return new BufferedReader(new InputStreamReader(openStream(), charset));
    }

    // ========== Metadata ==========

    /**
     * Returns the absolute URL of the resource.
     *
     * @return the URL, or null if the resource does not exist
     */
    @Contract(pure = true)
    public URL getURL() {
        return loader.getResource(path);
    }

    /**
     * Returns the external form of the URL.
     *
     * @return the URL as string
     */
    @Contract(pure = true)
    public String toExternalForm() {
        return getURL().toExternalForm();
    }

    /**
     * Checks if the resource exists.
     *
     * @return true if it exists
     */
    @Contract(pure = true)
    public boolean exists() {
        return getURL() != null;
    }

    /**
     * Returns the size in bytes.
     *
     * @return the size, or -1 if the resource does not exist
     * @throws IOException if calculation error occurs
     */
    @Contract(pure = true)
    public long size() throws IOException {
        if (!exists()) return -1;

        try (InputStream in = openStream()) {
            long size = 0;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                size += read;
            }
            return size;
        }
    }

    /**
     * Guesses the MIME type from content.
     *
     * @return the MIME type, or null if indeterminable
     * @throws IOException if read error occurs
     */
    @Contract(pure = true)
    public String getMimeType() throws IOException {
        try (InputStream in = openStream()) {
            return URLConnection.guessContentTypeFromStream(in);
        }
    }

    // ========== Name Parsing ==========

    /**
     * Returns the filename (last part of the path).
     *
     * @return the filename
     */
    @Contract(pure = true)
    public String getFileName() {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Returns the filename without extension.
     *
     * @return the base name
     */
    @Contract(pure = true)
    public String getBaseName() {
        String fileName = getFileName();
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }

    /**
     * Returns the file extension (without the dot).
     *
     * @return the extension, or "" if none
     */
    @Contract(pure = true)
    public String getExtension() {
        String fileName = getFileName();
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
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
        return toExternalForm();
    }
}