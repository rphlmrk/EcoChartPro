package com.EcoChartPro.core.plugin;

import com.EcoChartPro.utils.AppDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Handles the compilation of custom indicator .java files into .class files.
 */
public class JavaIndicatorCompiler {

    private static final Logger logger = LoggerFactory.getLogger(JavaIndicatorCompiler.class);

    /**
     * Scans the indicators directory for .java files and compiles them to the classes directory.
     */
    public void compileIndicators() {
        Optional<Path> indicatorsDirOpt = AppDataManager.getIndicatorsDirectory();
        Optional<Path> classesDirOpt = AppDataManager.getClassesDirectory();

        if (indicatorsDirOpt.isEmpty() || classesDirOpt.isEmpty()) {
            logger.warn("Cannot compile indicators: source or destination directory is missing.");
            return;
        }

        Path indicatorsDir = indicatorsDirOpt.get();
        Path classesDir = classesDirOpt.get();

        List<Path> javaFiles;
        try (Stream<Path> stream = Files.walk(indicatorsDir)) {
            javaFiles = stream
                .filter(p -> p.toString().toLowerCase().endsWith(".java"))
                .toList();
        } catch (IOException e) {
            logger.error("Failed to scan for .java files in {}", indicatorsDir, e);
            return;
        }

        if (javaFiles.isEmpty()) {
            logger.debug("No .java files found to compile.");
            return;
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            logger.error("CRITICAL: No Java compiler found. Please run EcoChartPro with a JDK, not a JRE.");
            return;
        }

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            StringWriter writer = new StringWriter();
            PrintWriter out = new PrintWriter(writer);

            List<String> optionList = new ArrayList<>();
            optionList.add("-d");
            optionList.add(classesDir.toAbsolutePath().toString());
            optionList.add("-classpath");
            optionList.add(System.getProperty("java.class.path"));

            Iterable<String> filePaths = javaFiles.stream()
                .map(Path::toAbsolutePath)
                .map(Path::toString)
                .toList();

            JavaCompiler.CompilationTask task = compiler.getTask(out, fileManager, null, optionList, null,
                fileManager.getJavaFileObjectsFromStrings(filePaths));

            if (task.call()) {
                logger.info("Successfully compiled {} indicator source file(s).", javaFiles.size());
            } else {
                logger.error("Failed to compile one or more indicators. See output below:");
                logger.error("Compiler Output:\n{}", writer.toString());
            }
        } catch (IOException e) {
            logger.error("An I/O error occurred during compilation.", e);
        }
    }
}