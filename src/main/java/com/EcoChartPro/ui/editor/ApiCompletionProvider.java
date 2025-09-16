package com.EcoChartPro.ui.editor;

import com.EcoChartPro.api.indicator.CustomIndicator;
import com.EcoChartPro.api.indicator.IndicatorType;
import com.EcoChartPro.api.indicator.IndicatorUtils;
import com.EcoChartPro.api.indicator.Parameter;
import com.EcoChartPro.api.indicator.ParameterType;
import com.EcoChartPro.core.indicator.IndicatorContext;
import com.EcoChartPro.model.KLine;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.FunctionCompletion;

import java.awt.Color;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides code completions for the EcoChartPro scripting API, including API classes,
 * common Java utilities, and language keywords.
 */
public class ApiCompletionProvider extends DefaultCompletionProvider {

    public ApiCompletionProvider() {
        super();
        loadCompletions();
    }

    private void loadCompletions() {
        // Add Java Keywords for basic syntax
        addJavaKeywords();

        // Add API Classes and their methods
        Class<?>[] apiClasses = {
                CustomIndicator.class, IndicatorType.class, Parameter.class, ParameterType.class,
                IndicatorUtils.class, IndicatorContext.class, KLine.class,
                // Common Java classes frequently used in indicators
                Color.class, BigDecimal.class, List.class, Map.class, Collections.class
        };

        for (Class<?> clazz : apiClasses) {
            addCompletionsForClass(clazz);
        }
    }

    /**
     * Adds basic Java language keywords to the completion list.
     */
    private void addJavaKeywords() {
        String[] keywords = {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null"
        };
        for (String keyword : keywords) {
            addCompletion(new BasicCompletion(this, keyword));
        }
    }

    /**
     * Uses reflection to find all public methods in a given class and adds them
     * to the completion list. It also adds the class name itself.
     * @param clazz The class to scan for completions.
     */
    private void addCompletionsForClass(Class<?> clazz) {
        // Add the class name itself for import statements and static calls.
        addCompletion(new BasicCompletion(this, clazz.getSimpleName(), "class"));

        // Add all public methods for the class.
        for (Method method : clazz.getMethods()) {
            // We only want public methods declared in this specific class, not inherited ones (e.g., from Object).
            if (Modifier.isPublic(method.getModifiers()) && method.getDeclaringClass().equals(clazz)) {
                FunctionCompletion fc = new FunctionCompletion(this, method.getName(), method.getReturnType().getSimpleName());
                
                // Build a list of parameters for the method signature popup.
                List<FunctionCompletion.Parameter> fcParams = Arrays.stream(method.getParameters())
                    .map(p -> new FunctionCompletion.Parameter(p.getType().getCanonicalName(), p.getName()))
                    .collect(Collectors.toList());
                fc.setParams(fcParams);

                // Create a user-friendly description of the method signature.
                String paramsString = fcParams.stream()
                    .map(p -> p.getType().substring(p.getType().lastIndexOf('.') + 1) + " " + p.getName())
                    .collect(Collectors.joining(", "));
                fc.setShortDescription(method.getName() + "(" + paramsString + ")");
                
                // A more detailed summary could be loaded from Javadoc if available.
                // For now, the signature is sufficient.
                fc.setSummary(clazz.getSimpleName() + "." + method.getName());

                addCompletion(fc);
            }
        }
    }
}