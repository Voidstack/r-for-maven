package com.enosistudio;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Mojo to generate a Java class with constants for resource files and folders.
 * The generated class will be named R and will contain a hierarchical structure
 * for accessing files and folders.
 */
@SuppressWarnings("unused")
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateRMojo extends AbstractMojo {
    private static final String PATH_SEPARATOR = "/";

    /**
     * If true, generated files will be placed in src/main/generated instead of target/generated-sources/java.
     */
    @Parameter(defaultValue = "true")
    private boolean keepInProjectFiles;

    /**
     * Directory containing resource files to scan.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/resources")
    private File resourcesDir;

    /**
     * Java package name for the generated R class.
     */
    @Parameter(defaultValue = "com.enosistudio.generated")
    private String packageName;

    /**
     * Base directory for generated sources (when keepInProjectFiles is false).
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources")
    private File outputTargetDirectory;

    /**
     * Base directory for generated sources (when keepInProjectFiles is true).
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/java")
    private File outputSrcDirectory;

    /**
     * Executes the Mojo to generate the R.java file.
     *
     * @throws MojoExecutionException if an error occurs during generation
     */
    public void execute() throws MojoExecutionException {
        // Choose the base directory
        File baseDir = keepInProjectFiles ? outputSrcDirectory : outputTargetDirectory;
        String packagePath = packageName.replace('.', '/');
        File outputDir = new File(baseDir, packagePath);

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new MojoExecutionException("Failed to create output directory: " + outputDir);
        }

        try {
            generateResourceClasses(outputDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate resource classes", e);
        }

        getLog().info("Generated R.java with hierarchical resource structure");
    }

    /**
     * Generates the R.java class with hierarchical structure based on the resources' directory.
     *
     * @param outputDir the directory to write the R.java file to
     * @throws IOException if an I/O error occurs
     */
    private void generateResourceClasses(File outputDir) throws IOException {
        ResourceNode rootNode = buildResourceTree();

        // Generate only the main R class
        generateMainRClass(outputDir, rootNode);
    }

    /**
     * Generates the main R.java class with hierarchical structure.
     *
     * @param outputDir the directory to write the R.java file to
     * @param rootNode  the root ResourceNode representing the resources
     * @throws IOException if an I/O error occurs
     */
    private void generateMainRClass(File outputDir, ResourceNode rootNode) throws IOException {
        File rClass = new File(outputDir, "R.java");
        try (FileWriter writer = new FileWriter(rClass)) {
            writer.write(String.format("""
                    package %s;
                    
                    import java.io.File;
                    import java.io.IOException;
                    import java.io.InputStream;
                    import java.nio.charset.StandardCharsets;
                    import java.nio.file.Path;
                    import java.nio.file.Paths;
                    import java.net.URL;
                    
                    import com.enosistudio.RFile;
                    import com.enosistudio.RFolder;
                    import org.jetbrains.annotations.Contract;
                    
                    /**
                     * Generated resource constants class.
                     * Contains hierarchical access to all resource files and folders.
                     */
                    @SuppressWarnings({"java:S101", "unused"})
                    public final class R {
                        private R() {} // Utility class
                    
                    """, packageName));

            // Generate static fields for root level items
            generateNodeFields(writer, rootNode, "    ");

            writer.write("}\n");
        }
    }

    /**
     * Recursively generates fields and nested classes for a ResourceNode.
     *
     * @param writer the FileWriter to write the class content to
     * @param node   the current ResourceNode
     * @param indent current indentation level
     * @throws IOException if an I/O error occurs
     */
    private void generateNodeFields(FileWriter writer, ResourceNode node, String indent) throws IOException {
        // Generate fields for files at current level
        for (ResourceFile file : node.files) {
            String fieldName = toValidJavaName(file.name);
            writer.write(String.format("%spublic static final RFile %s = new RFile(\"%s\");%n", indent, fieldName, file.path));
        }

        // Generate nested classes for folders
        for (Map.Entry<String, ResourceNode> entry : node.children.entrySet()) {
            String folderName = entry.getKey();
            ResourceNode childNode = entry.getValue();
            String className = toValidJavaName(folderName);

            writer.write(String.format("""
                    %s
                    %spublic static final class %s extends RFolder {
                    %s    public static final RFolder _self = new %s();
                    %s    private %s() { super("%s"); }
                    """, indent, indent, className, indent, className, indent, className, childNode.path));

            // Generate nested content (files and subfolders)
            generateNodeFields(writer, childNode, indent + "    ");

            // Close class
            writer.write(String.format("%s}%n", indent));
        }
    }

    /**
     * Reads the content of a class file from the plugin's resources.
     *
     * @param fileName the name of the class file to read
     * @return the content of the class file as a string, or null if not found
     */
    private String readClassContent(String fileName) {
        try (InputStream inputStream = getClass().getResourceAsStream("/" + fileName)) {
            if (inputStream == null) {
                getLog().warn("Could not find " + fileName + " in plugin resources, using fallback");
                return null;
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLog().warn("Error reading " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Converts a standalone class content to an inner class format.
     *
     * @param classContent the full content of the class
     * @param className    the name of the class to convert
     * @param isFinal      whether the class should be final
     * @return the converted inner class content
     */
    private String convertToInnerClass(String classContent, String className, boolean isFinal) {
        // Remove package declaration
        classContent = classContent.replaceFirst("package\\s+[^;]+;\\s*", "");

        // Remove imports (they should be in the main R class)
        classContent = classContent.replaceAll("import\\s+[^;]+;\\s*", "");

        // Convert public class to public static class with proper indentation
        String classDeclaration = isFinal ? "public static final class" : "public static class";
        classContent = classContent.replaceFirst("public\\s+(final\\s+)?class\\s+" + className, "    " + classDeclaration + " " + className);

        // Add indentation to all lines
        String[] lines = classContent.split("\n");
        StringBuilder indentedContent = new StringBuilder();

        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                indentedContent.append("    ").append(line).append("\n");
            } else {
                indentedContent.append("\n");
            }
        }

        return indentedContent.toString();
    }

    /**
     * Builds a tree representation of the resources' directory.
     *
     * @return the root ResourceNode
     */
    private ResourceNode buildResourceTree() {
        ResourceNode root = new ResourceNode("", "");
        scanResourcesRecursive(resourcesDir, "", root);
        return root;
    }

    /**
     * Recursively scans the resources directory and builds a tree of ResourceNode and ResourceFile.
     *
     * @param dir         current directory to scan
     * @param currentPath relative path from resources root
     * @param currentNode current ResourceNode to populate
     */
    private void scanResourcesRecursive(File dir, String currentPath, ResourceNode currentNode) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String filePath = currentPath.isEmpty() ? file.getName() : currentPath + PATH_SEPARATOR + file.getName();

            if (file.isFile()) {
                currentNode.files.add(new ResourceFile(file.getName(), filePath));
            } else if (file.isDirectory()) {
                ResourceNode childNode = new ResourceNode(file.getName(), filePath);
                currentNode.children.put(file.getName(), childNode);
                scanResourcesRecursive(file, filePath, childNode);
            }
        }
    }

    /**
     * Converts a string to a valid Java variable name by replacing invalid characters with underscores
     * and converting to camelCase.
     *
     * @param name the original name
     * @return a valid Java variable name
     */
    private String toValidJavaName(String name) {
        String cleaned = name.replaceAll("[^a-zA-Z0-9]", "_").replaceAll("^(\\d)", "_$1");

        // Convert to camelCase
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        for (char c : cleaned.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else {
                result.append(capitalizeNext ? Character.toUpperCase(c) : Character.toLowerCase(c));
                capitalizeNext = false;
            }
        }
        return result.toString();
    }

    /**
     * Converts a string to a valid Java class name by replacing invalid characters with underscores
     * and converting to PascalCase.
     *
     * @param name the original name
     * @return a valid Java class name
     */
    private String toValidJavaClassName(String name) {
        String cleaned = name.replaceAll("[^a-zA-Z0-9]", "_").replaceAll("^(\\d)", "_$1");

        // Convert to PascalCase
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : cleaned.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else {
                result.append(capitalizeNext ? Character.toUpperCase(c) : Character.toLowerCase(c));
                capitalizeNext = false;
            }
        }
        return result.toString();
    }

    /**
     * Represents a node in the resource tree, which can be a folder containing files and subfolders.
     *
     * @param name     the folder name
     * @param path     the relative path from resources root
     * @param children map of child folder names to their ResourceNode
     * @param files    list of ResourceFile objects in this folder
     */
    private record ResourceNode(String name, String path, Map<String, ResourceNode> children,
                                List<ResourceFile> files) {
        public ResourceNode(String name, String path) {
            this(name, path, new LinkedHashMap<>(), new ArrayList<>());
        }
    }

    /**
     * Represents a resource file with name and path.
     *
     * @param name the file name
     * @param path the relative path from resources root
     */
    private record ResourceFile(String name, String path) {
    }
}