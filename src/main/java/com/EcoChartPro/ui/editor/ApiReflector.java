package com.EcoChartPro.ui.editor;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Comparator;

/**
 * A utility class that uses reflection to scan the application's public API
 * and generate a JTree model for display in the editor's API reference panel.
 */
public final class ApiReflector {

    private static final String API_PACKAGE = "com.EcoChartPro.api";

    private ApiReflector() {}

    public static DefaultTreeModel createApiTreeModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("EcoChartPro API");

        try (ScanResult scanResult = new ClassGraph()
                .enableAllInfo()
                .acceptPackages(API_PACKAGE)
                .scan()) {
            
            ClassInfoList apiClasses = scanResult.getAllClasses().filter(ci -> 
                !ci.isAnonymousInnerClass() && ci.isPublic()
            );

            apiClasses.sort(Comparator.comparing(ClassInfo::getSimpleName));

            for (ClassInfo classInfo : apiClasses) {
                DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(new ApiNode(classInfo));
                
                classInfo.getDeclaredFieldInfo().stream()
                    .filter(field -> field.isPublic())
                    .sorted(Comparator.comparing(field -> field.getName()))
                    .forEach(fieldInfo -> {
                        classNode.add(new DefaultMutableTreeNode(new ApiNode(fieldInfo)));
                    });

                classInfo.getDeclaredMethodInfo().stream()
                    .filter(method -> method.isPublic())
                    .sorted(Comparator.comparing(method -> method.getName()))
                    .forEach(methodInfo -> {
                         classNode.add(new DefaultMutableTreeNode(new ApiNode(methodInfo)));
                    });

                root.add(classNode);
            }
        }
        
        return new DefaultTreeModel(root);
    }
}