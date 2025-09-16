package com.EcoChartPro.core.service;

import com.EcoChartPro.core.classloading.MemoryClassLoader;
import com.EcoChartPro.utils.AppDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * A singleton service that provides functionality to compile Java source code
 * from a string into .class files on disk or into byte arrays in memory.
 */
public final class CompilationService {

    private static final Logger logger = LoggerFactory.getLogger(CompilationService.class);
    private static volatile CompilationService instance;
    private final JavaCompiler compiler;
    private final StandardJavaFileManager fileManager;

    /**
     * Represents the outcome of a compilation task.
     * @param success     True if compilation succeeded without errors.
     * @param diagnostics A list of warnings, errors, and other messages from the compiler.
     * @param bytecode    A map of class names to their bytecode, for in-memory compilation.
     */
    public record CompilationResult(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics, Map<String, byte[]> bytecode) {}

    private CompilationService() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            logger.error("CRITICAL: Java Compiler not found. The application must be run with a JDK, not a JRE.");
            throw new IllegalStateException("Cannot find system Java compiler. Please run with a JDK.");
        }
        this.fileManager = compiler.getStandardFileManager(null, null, null);
    }

    public static CompilationService getInstance() {
        if (instance == null) {
            synchronized (CompilationService.class) {
                if (instance == null) {
                    instance = new CompilationService();
                }
            }
        }
        return instance;
    }

    public CompilationResult compileAndWriteToDisk(String className, String sourceCode) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> compilationUnits = Collections.singletonList(new StringJavaFileObject(className, sourceCode));
        List<String> options = new ArrayList<>(Arrays.asList("-classpath", buildClasspath()));

        try {
            Path classesDir = AppDataManager.getClassesDirectory().orElseThrow(() -> new IOException("Classes directory not available."));
            Files.createDirectories(classesDir);
            // Use a new file manager instance for this specific task to set the output location
            try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null)) {
                fm.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(classesDir.toFile()));

                JavaCompiler.CompilationTask task = compiler.getTask(null, fm, diagnostics, options, null, compilationUnits);
                boolean success = task.call();
                logger.info("Disk compilation for {} completed with success status: {}", className, success);
                return new CompilationResult(success, diagnostics.getDiagnostics(), null);
            }

        } catch (Exception e) {
            logger.error("Exception during disk compilation for {}", className, e);
            Diagnostic<JavaFileObject> error = new SimpleDiagnostic("Exception during compilation: " + e.getMessage());
            return new CompilationResult(false, Collections.singletonList(error), null);
        }
    }

    /**
     * Compiles Java source code entirely in memory without writing to disk.
     * @param className The fully qualified name of the class to compile.
     * @param sourceCode The string content of the Java source file.
     * @return A CompilationResult containing the success status, diagnostics, and resulting bytecode.
     */
    public CompilationResult compileInMemory(String className, String sourceCode) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> compilationUnits = Collections.singletonList(new StringJavaFileObject(className, sourceCode));
        List<String> options = Arrays.asList("-classpath", buildClasspath());

        MemoryJavaFileManager memoryFileManager = new MemoryJavaFileManager(this.fileManager);

        JavaCompiler.CompilationTask task = compiler.getTask(null, memoryFileManager, diagnostics, options, null, compilationUnits);
        boolean success = task.call();
        logger.info("In-memory compilation for {} completed with success status: {}", className, success);

        return new CompilationResult(success, diagnostics.getDiagnostics(), memoryFileManager.getAllBytecode());
    }

    private String buildClasspath() {
        StringBuilder sb = new StringBuilder();
        try {
            // Add the application's own classpath
            sb.append(System.getProperty("java.class.path"));

            // Add plugin JARs to the classpath
            Optional<Path> indicatorsDirOpt = AppDataManager.getIndicatorsDirectory();
            if (indicatorsDirOpt.isPresent() && Files.exists(indicatorsDirOpt.get())) {
                Files.walk(indicatorsDirOpt.get(), 1)
                    .filter(path -> path.toString().toLowerCase().endsWith(".jar"))
                    .forEach(jarPath -> sb.append(System.getProperty("path.separator")).append(jarPath.toAbsolutePath()));
            }
        } catch (IOException e) {
            logger.error("Failed to build classpath for compilation.", e);
        }
        return sb.toString();
    }
    
    // --- Helper classes for in-memory compilation ---

    private static class StringJavaFileObject extends SimpleJavaFileObject {
        private final String sourceCode;
        protected StringJavaFileObject(String name, String source) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = source;
        }
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return sourceCode;
        }
    }

    private static class MemoryJavaFileObject extends SimpleJavaFileObject {
        private final ByteArrayOutputStream outputStream;
        private final String className;

        protected MemoryJavaFileObject(String className, Kind kind) {
            super(URI.create("memory:///" + className.replace('.', '/') + kind.extension), kind);
            this.className = className;
            this.outputStream = new ByteArrayOutputStream();
        }

        public byte[] getBytes() {
            return outputStream.toByteArray();
        }
        
        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }
    }

    private static class MemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        private final Map<String, MemoryJavaFileObject> classFiles = new HashMap<>();

        protected MemoryJavaFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
            if (kind == JavaFileObject.Kind.CLASS) {
                MemoryJavaFileObject fileObject = new MemoryJavaFileObject(className, kind);
                classFiles.put(className, fileObject);
                return fileObject;
            }
            return super.getJavaFileForOutput(location, className, kind, sibling);
        }

        public Map<String, byte[]> getAllBytecode() {
            Map<String, byte[]> results = new HashMap<>();
            for (Map.Entry<String, MemoryJavaFileObject> entry : classFiles.entrySet()) {
                results.put(entry.getKey(), entry.getValue().getBytes());
            }
            return results;
        }
    }

    private static class SimpleDiagnostic implements Diagnostic<JavaFileObject> {
        private final String message;
        SimpleDiagnostic(String message) { this.message = message; }
        @Override public Kind getKind() { return Kind.ERROR; }
        @Override public JavaFileObject getSource() { return null; }
        @Override public long getPosition() { return -1; }
        @Override public long getStartPosition() { return -1; }
        @Override public long getEndPosition() { return -1; }
        @Override public long getLineNumber() { return -1; }
        @Override public long getColumnNumber() { return -1; }
        @Override public String getCode() { return null; }
        @Override public String getMessage(Locale locale) { return message; }
    }
}