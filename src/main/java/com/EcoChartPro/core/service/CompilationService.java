package com.EcoChartPro.core.service;

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
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class CompilationService {

    private static final Logger logger = LoggerFactory.getLogger(CompilationService.class);
    private static volatile CompilationService instance;
    private final JavaCompiler compiler;
    private final StandardJavaFileManager fileManager;

    public record CompilationResult(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics, Map<String, byte[]> bytecode) {}

    private CompilationService() {
        // [MODIFIED] This is the more robust way to find the compiler in a modular (jpackage) environment.
        // It uses the ServiceLoader API instead of the older ToolProvider.
        this.compiler = ServiceLoader.load(JavaCompiler.class).findFirst()
            .orElseGet(() -> {
                // Fallback to the old method just in case, but ServiceLoader should work.
                logger.warn("Could not find JavaCompiler via ServiceLoader, falling back to ToolProvider.");
                return ToolProvider.getSystemJavaCompiler();
            });

        if (this.compiler == null) {
            // This error will now only trigger if both methods fail.
            logger.error("CRITICAL: Java Compiler not found. The application must be run with a JDK, not a JRE.");
            throw new IllegalStateException("Cannot find system Java compiler. Please run with a JDK.");
        }
        
        logger.info("Java compiler successfully initialized: {}", compiler.getClass().getName());
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
        logger.info("Starting disk compilation for {}...", className);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> compilationUnits = Collections.singletonList(new StringJavaFileObject(className, sourceCode));
        List<String> options = new ArrayList<>(Arrays.asList("-classpath", buildClasspath()));

        try {
            Path classesDir = AppDataManager.getClassesDirectory().orElseThrow(() -> new IOException("Classes directory not available."));
            Files.createDirectories(classesDir);
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

    public CompilationResult compileInMemory(String className, String sourceCode) {
        logger.info("Starting in-memory compilation for {}...", className);
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
        Set<String> classpathElements = new LinkedHashSet<>();
        try {
            File selfLocation = new File(CompilationService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            classpathElements.add(selfLocation.getAbsolutePath());
            logger.debug("Added self to compiler classpath: {}", selfLocation.getAbsolutePath());
        } catch (Exception e) {
             logger.warn("Could not determine application's own location to add to classpath.", e);
        }

        try {
            Optional<Path> indicatorsDirOpt = AppDataManager.getIndicatorsDirectory();
            if (indicatorsDirOpt.isPresent() && Files.exists(indicatorsDirOpt.get())) {
                try (Stream<Path> stream = Files.walk(indicatorsDirOpt.get(), 1)) {
                    stream.filter(path -> path.toString().toLowerCase().endsWith(".jar"))
                        .forEach(jarPath -> classpathElements.add(jarPath.toAbsolutePath().toString()));
                }
            }
        } catch (IOException e) {
            logger.error("Failed to build classpath from indicators directory.", e);
        }

        String finalClasspath = String.join(File.pathSeparator, classpathElements);
        logger.debug("Final compiler classpath: {}", finalClasspath);
        return finalClasspath;
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
        protected MemoryJavaFileObject(String className, Kind kind) {
            super(URI.create("memory:///" + className.replace('.', '/') + kind.extension), kind);
            this.outputStream = new ByteArrayOutputStream();
        }
        public byte[] getBytes() { return outputStream.toByteArray(); }
        @Override
        public OutputStream openOutputStream() { return outputStream; }
    }

    private static class MemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        private final Map<String, MemoryJavaFileObject> classFiles = new HashMap<>();
        protected MemoryJavaFileManager(JavaFileManager fileManager) { super(fileManager); }
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
            classFiles.forEach((key, value) -> results.put(key, value.getBytes()));
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