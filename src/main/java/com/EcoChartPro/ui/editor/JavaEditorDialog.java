package com.EcoChartPro.ui.editor;

import com.EcoChartPro.api.indicator.CustomIndicator;
import com.EcoChartPro.core.plugin.PluginManager;
import com.EcoChartPro.core.service.CompilationService;
import com.EcoChartPro.ui.ChartWorkspacePanel;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.utils.AppDataManager;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodParameterInfo;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ExtendedHyperlinkListener;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.Parser;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaEditorDialog extends JDialog {

    private RSyntaxTextArea textArea;
    private JList<Path> fileList;
    private DefaultListModel<Path> fileListModel;
    private JTextArea consoleArea;
    private RTextScrollPane scrollPane;
    private JButton compileButton;
    private Icon errorIcon;
    private final DiagnosticParser compilerNoticeParser;

    private Path currentlyOpenFile;
    private boolean isDirty = false;

    private final ChartWorkspacePanel workspacePanelOwner;
    private static final String IN_APP_PACKAGE = "com.EcoChartPro.plugins.inapp";

    public JavaEditorDialog(Frame owner, ChartWorkspacePanel workspacePanel) {
        super(owner, "Eco Chart Pro - Java Indicator Editor", false);
        this.workspacePanelOwner = workspacePanel;
        this.errorIcon = UITheme.getIcon(UITheme.Icons.ERROR_CIRCLE, 12, 12);
        this.compilerNoticeParser = new DiagnosticParser();

        setupUI();
    }

    private void setupUI() {
        JTabbedPane leftTabbedPane = new JTabbedPane();
        leftTabbedPane.addTab("My Scripts", UITheme.getIcon(UITheme.Icons.FOLDER, 16, 16), createFilePanel());
        leftTabbedPane.addTab("Examples", UITheme.getIcon(UITheme.Icons.HELP, 16, 16), createExamplePanel());
        leftTabbedPane.addTab("API Reference", UITheme.getIcon(UITheme.Icons.JUMP_TO, 16, 16), createApiReferencePanel());

        JPanel editorPanel = createEditorPanel();
        JPanel consolePanel = createConsolePanel();

        JSplitPane editorAndConsole = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorPanel, consolePanel);
        editorAndConsole.setResizeWeight(0.8);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftTabbedPane, editorAndConsole);
        mainSplit.setResizeWeight(0.25);

        setLayout(new BorderLayout());
        add(createToolbar(), BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setSize(1200, 800);
        setLocationRelativeTo(getOwner());
    }

    // --- Create Panels ---

    private JToolBar createToolbar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setRollover(true);

        JButton newButton = new JButton(UITheme.getIcon(UITheme.Icons.NEW_FILE, 18, 18));
        newButton.setToolTipText("New Java File");
        newButton.addActionListener(e -> createNewFile());
        toolBar.add(newButton);

        JButton saveButton = new JButton(UITheme.getIcon(UITheme.Icons.SAVE, 18, 18));
        saveButton.setToolTipText("Save Java File");
        saveButton.addActionListener(e -> saveCurrentFile());
        toolBar.add(saveButton);
        toolBar.addSeparator();

        JButton deleteButton = new JButton(UITheme.getIcon(UITheme.Icons.DELETE, 18, 18));
        deleteButton.setToolTipText("Delete Selected Java File");
        deleteButton.addActionListener(e -> deleteSelectedFile());
        toolBar.add(deleteButton);
        toolBar.add(Box.createHorizontalGlue());

        compileButton = new JButton("Compile & Apply", UITheme.getIcon(UITheme.Icons.APPLY, 18, 18));
        compileButton.setToolTipText("Compile the current Java code and apply it to the active chart");
        compileButton.addActionListener(e -> compileAndApply());
        toolBar.add(compileButton);

        return toolBar;
    }

    private JPanel createFilePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Path path) {
                    label.setText(path.getFileName().toString());
                    label.setIcon(UITheme.getIcon(UITheme.Icons.PLUGIN_JAVA, 16, 16));
                }
                return label;
            }
        });

        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Path selected = fileList.getSelectedValue();
                if (selected != null) {
                    loadFileContent(selected);
                }
            }
        });

        populateFileList();
        panel.add(new JScrollPane(fileList), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createExamplePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Indicator Examples");

        DefaultMutableTreeNode basic = new DefaultMutableTreeNode("Basic (Stateless)");
        basic.add(new DefaultMutableTreeNode("Moving Average"));
        root.add(basic);

        DefaultMutableTreeNode advanced = new DefaultMutableTreeNode("Advanced (Stateful)");
        advanced.add(new DefaultMutableTreeNode("Stateful EMA"));
        advanced.add(new DefaultMutableTreeNode("Stateful RSI"));
        root.add(advanced);

        DefaultMutableTreeNode visual = new DefaultMutableTreeNode("Visual & Stateful");
        visual.add(new DefaultMutableTreeNode("Volume Bubbles"));
        visual.add(new DefaultMutableTreeNode("Big Trade Volume Bubbles"));
        visual.add(new DefaultMutableTreeNode("Footprint Delta"));
        visual.add(new DefaultMutableTreeNode("HTF Overlay"));
        visual.add(new DefaultMutableTreeNode("HTF Candle Projections"));
        root.add(visual);

        JTree exampleTree = new JTree(root);
        for (int i = 0; i < exampleTree.getRowCount(); i++) {
            exampleTree.expandRow(i);
        }
        exampleTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                if (leaf) setIcon(UITheme.getIcon(UITheme.Icons.PLUGIN_JAVA, 16, 16));
                else setIcon(UITheme.getIcon(UITheme.Icons.FOLDER, 16, 16));
                return this;
            }
        });
        exampleTree.addTreeSelectionListener(e -> {
            TreePath path = e.getNewLeadSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (node != null && node.isLeaf()) {
                    loadExample(node.getUserObject().toString());
                }
            }
        });
        panel.add(new JScrollPane(exampleTree), BorderLayout.CENTER);
        return panel;
    }
    
    private void loadExample(String exampleName) {
        if (isDirty) {
            int choice = JOptionPane.showConfirmDialog(this, "You have unsaved changes. Discard them?", "Unsaved Changes", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.NO_OPTION) {
                if (currentlyOpenFile != null) fileList.setSelectedValue(currentlyOpenFile, true);
                return;
            }
        }

        String fileName;
        if ("HTF Candle Projections".equals(exampleName)) {
            fileName = "HTFCandleProjections.java.txt";
        } else if ("Big Trade Volume Bubbles".equals(exampleName)) {
            fileName = "BigTradeVolumeBubbles.java.txt";
        } else if ("Footprint Delta".equals(exampleName)) {
            fileName = "FootprintDeltaIndicator.java.txt";
        } else {
            fileName = exampleName.replace(" ", "") + "Indicator.java.txt";
        }

        String resourcePath = "/com/EcoChartPro/editor/templates/" + fileName;

        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                logMessage("ERROR: Could not find example resource: " + resourcePath);
                return;
            }
            clearErrorHighlights();
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            String originalClassName = extractSimpleClassName(content);
            if(originalClassName != null) {
                String newClassName = "My" + originalClassName;
                content = content.replaceAll("\\b" + originalClassName + "\\b", newClassName);
            }

            textArea.setText(content);
            textArea.discardAllEdits();
            currentlyOpenFile = null;
            fileList.clearSelection();
            setDirty(true);
            clearConsole();
            logMessage("Loaded '" + exampleName + "' example. Save it to 'My Scripts' to compile.");
        } catch (IOException e) {
            logMessage("ERROR: Failed to read example file: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Could not read example file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createApiReferencePanel() {
        JTree apiTree = new JTree(ApiReflector.createApiTreeModel());
        apiTree.setCellRenderer(new ApiTreeCellRenderer());
        JEditorPane docPane = new JEditorPane();
        docPane.setContentType("text/html");
        docPane.setEditable(false);
        docPane.setBackground(new Color(0x313335));
        docPane.setText("<html><body style='font-family:sans-serif; color: #A9B7C6; padding: 5px;'>Select an API element to see details.</body></html>");
        apiTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) apiTree.getLastSelectedPathComponent();
            if (selectedNode != null && selectedNode.getUserObject() instanceof ApiNode apiNode) {
                docPane.setText(generateHtmlDocumentation(apiNode));
                docPane.setCaretPosition(0);
            }
        });
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(apiTree), new JScrollPane(docPane));
        splitPane.setResizeWeight(0.6);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createEditorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        textArea = new RSyntaxTextArea(20, 60);
        configureTextArea();
        this.scrollPane = new RTextScrollPane(textArea);
        this.scrollPane.setLineNumbersEnabled(true);
        panel.add(this.scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void configureTextArea() {
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setMarkOccurrences(true);
        textArea.setBracketMatchingEnabled(true);

        AutoCompletion ac = new AutoCompletion(new ApiCompletionProvider());
        ac.setAutoActivationEnabled(true);
        ac.install(textArea);

        textArea.addParser(this.compilerNoticeParser);
        textArea.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getDescription() != null && e.getDescription().startsWith("Line ")) {
                try {
                    String lineStr = e.getDescription().replaceAll("[^0-9]", "");
                    int line = Integer.parseInt(lineStr) - 1;
                    if (line >= 0 && line < textArea.getLineCount()) {
                        textArea.setCaretPosition(textArea.getLineStartOffset(line));
                    }
                } catch (Exception ex) { /* Ignore parsing errors */ }
            }
        });
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { setDirty(true); }
            @Override public void removeUpdate(DocumentEvent e) { setDirty(true); }
            @Override public void changedUpdate(DocumentEvent e) { setDirty(true); }
        });

        try (InputStream in = RSyntaxTextArea.class.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml")) {
            if (in != null) Theme.load(in).apply(textArea);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private JPanel createConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Compiler Output / Console"));
        consoleArea = new JTextArea(5, 0);
        consoleArea.setEditable(false);
        consoleArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        consoleArea.setLineWrap(true);
        consoleArea.setWrapStyleWord(true);
        consoleArea.setBackground(new Color(0x2B2B2B));
        consoleArea.setForeground(new Color(0xA9B7C6));
        consoleArea.setCaretColor(Color.WHITE);
        panel.add(new JScrollPane(consoleArea), BorderLayout.CENTER);
        return panel;
    }

    private void compileAndApply() {
        if (isDirty && currentlyOpenFile != null) {
            saveCurrentFile();
        }
        if (currentlyOpenFile == null) {
            JOptionPane.showMessageDialog(this, "Please save the file before compiling.", "Save Required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String sourceCode = textArea.getText();
        String simpleClassName = extractSimpleClassName(sourceCode);
        if (simpleClassName == null) {
            JOptionPane.showMessageDialog(this, "Could not find a public class declaration in the code.", "Compilation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        compileButton.setEnabled(false);
        clearErrorHighlights();
        clearConsole();
        logMessage("Starting compilation for " + currentlyOpenFile.getFileName() + "...");

        CompilationWorker worker = new CompilationWorker(simpleClassName, sourceCode);
        worker.execute();
    }

    private class CompilationWorker extends SwingWorker<CompilationService.CompilationResult, Void> {
        private final String simpleClassName;
        private final String fullClassName;
        private final String sourceCode;

        public CompilationWorker(String simpleClassName, String sourceCode) {
            this.simpleClassName = simpleClassName;
            this.fullClassName = IN_APP_PACKAGE + "." + simpleClassName;
            this.sourceCode = sourceCode;
        }

        @Override
        protected CompilationService.CompilationResult doInBackground() throws Exception {
            return CompilationService.getInstance().compileAndWriteToDisk(fullClassName, sourceCode);
        }

        @Override
        protected void done() {
            try {
                CompilationService.CompilationResult result = get();

                if (result.success()) {
                    logMessage("Compilation successful. Rescanning plugins...");
                    PluginManager.getInstance().rescanPlugins();

                    Optional<CustomIndicator> newPluginOpt = PluginManager.getInstance().getLoadedIndicators().stream()
                        .filter(p -> p.getClass().getName().equals(fullClassName))
                        .findFirst();

                    if (newPluginOpt.isPresent()) {
                        workspacePanelOwner.applyLiveIndicator(newPluginOpt.get());
                        logMessage("Success! Indicator has been hot-reloaded on the active chart.");
                        JOptionPane.showMessageDialog(JavaEditorDialog.this, "Compilation Succeeded & Applied!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        logMessage("Compilation succeeded, but could not find the reloaded plugin.");
                        JOptionPane.showMessageDialog(JavaEditorDialog.this, "Compilation Succeeded, but could not apply to chart.", "Warning", JOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    logMessage("Compilation failed!");
                    displayDiagnostics(result.diagnostics());
                }

            } catch (Exception e) {
                logMessage("An unexpected error occurred during compilation: " + e.getMessage());
                e.printStackTrace();
            } finally {
                compileButton.setEnabled(true);
            }
        }
    }

    private String extractSimpleClassName(String sourceCode) {
        Pattern pattern = Pattern.compile("public\\s+class\\s+([A-Za-z0-9_]+)");
        Matcher matcher = pattern.matcher(sourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void clearErrorHighlights() {
        compilerNoticeParser.setDiagnostics(null);
        textArea.forceReparsing(compilerNoticeParser);
        scrollPane.getGutter().removeAllTrackingIcons();
    }

    private void displayDiagnostics(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        compilerNoticeParser.setDiagnostics(diagnostics);
        textArea.forceReparsing(compilerNoticeParser);
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            if (diagnostic.getKind() != Diagnostic.Kind.ERROR) continue;
            String message = diagnostic.getMessage(null);
            int line = (int) diagnostic.getLineNumber() - 1;
            if (line < 0) continue;
            logMessage(String.format("ERROR: Line %d, Col %d: %s", diagnostic.getLineNumber(), diagnostic.getColumnNumber(), message));
            try {
                scrollPane.getGutter().addLineTrackingIcon(line, errorIcon, "Line " + (line + 1) + ": " + message);
            } catch (BadLocationException e) {
                logMessage("Could not add gutter icon for error: " + e.getMessage());
            }
        }
    }

    private void loadFileContent(Path file) {
        if (isDirty) {
            int choice = JOptionPane.showConfirmDialog(this, "You have unsaved changes. Discard them?", "Unsaved Changes", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.NO_OPTION) {
                fileList.setSelectedValue(currentlyOpenFile, false);
                return;
            }
        }
        try {
            clearErrorHighlights();
            String content = Files.readString(file);
            textArea.setText(content);
            textArea.discardAllEdits();
            currentlyOpenFile = file;
            setDirty(false);
            clearConsole();
            logMessage("Loaded '" + file.getFileName() + "'.");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not read file: " + e.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            consoleArea.append(message + "\n");
            consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        });
    }

    private void clearConsole() {
        consoleArea.setText("");
    }

    private void populateFileList() {
        fileListModel.clear();
        Optional<Path> scriptsDirOpt = AppDataManager.getScriptsDirectory();
        if (scriptsDirOpt.isPresent()) {
            try (Stream<Path> stream = Files.list(scriptsDirOpt.get())) {
                stream.filter(p -> p.toString().toLowerCase().endsWith(".java"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(fileListModel::addElement);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveCurrentFile() {
        if (currentlyOpenFile == null) {
            createNewFile();
            return;
        }
        try {
            Files.writeString(currentlyOpenFile, textArea.getText(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            setDirty(false);
            logMessage("Saved " + currentlyOpenFile.getFileName());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save file: " + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createNewFile() {
        String fileName = JOptionPane.showInputDialog(this, "Enter new class name (e.g., MyIndicator.java):");
        if (fileName == null || fileName.trim().isEmpty()) return;
        if (!fileName.toLowerCase().endsWith(".java")) fileName += ".java";

        Optional<Path> scriptsDirOpt = AppDataManager.getScriptsDirectory();
        if (scriptsDirOpt.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Could not access source directory.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Path newFilePath = scriptsDirOpt.get().resolve(fileName);
        if (Files.exists(newFilePath)) {
            JOptionPane.showMessageDialog(this, "A file with that name already exists.", "File Exists", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            String simpleClassName = fileName.substring(0, fileName.lastIndexOf('.'));
            String content = isDirty ? textArea.getText().replace(extractSimpleClassName(textArea.getText()), simpleClassName) : getJavaTemplate(simpleClassName);
            Files.writeString(newFilePath, content);
            populateFileList();
            fileList.setSelectedValue(newFilePath, true);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not create file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedFile() {
        Path selectedFile = fileList.getSelectedValue();
        if (selectedFile == null) return;
        int choice = JOptionPane.showConfirmDialog(this, "Are you sure you want to permanently delete '" + selectedFile.getFileName() + "'?", "Confirm Deletion", JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            try {
                String className = selectedFile.getFileName().toString().replace(".java", ".class");
                Optional<Path> classFile = AppDataManager.getClassesDirectory().map(p -> p.resolve(IN_APP_PACKAGE.replace('.', '/'))).map(p -> p.resolve(className));
                if (classFile.isPresent() && Files.exists(classFile.get())) {
                    Files.delete(classFile.get());
                }
                Files.delete(selectedFile);
                if (selectedFile.equals(currentlyOpenFile)) {
                    textArea.setText("");
                    currentlyOpenFile = null;
                    setDirty(false);
                }
                populateFileList();
                PluginManager.getInstance().rescanPlugins();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Could not delete file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void setDirty(boolean dirty) {
        if (isDirty == dirty) return;
        isDirty = dirty;
        String title = "Eco Chart Pro - Java Indicator Editor";
        if (currentlyOpenFile != null) title += " - " + currentlyOpenFile.getFileName().toString();
        else title += " - [Unsaved File]";
        if (isDirty) title += "*";
        setTitle(title);
    }

    private String getJavaTemplate(String className) {
        return """
            package %s;

            import com.EcoChartPro.api.indicator.*;
            import com.EcoChartPro.api.indicator.drawing.*;
            import com.EcoChartPro.core.indicator.IndicatorContext;
            import java.awt.Color;
            import java.math.BigDecimal;
            import java.util.Collections;
            import java.util.List;
            import java.util.Map;

            public class %s implements CustomIndicator {

                @Override
                public String getName() {
                    return "My New Indicator";
                }

                @Override
                public IndicatorType getType() {
                    return IndicatorType.OVERLAY;
                }

                @Override
                public List<Parameter> getParameters() {
                    return Collections.emptyList();
                }

                @Override
                public List<DrawableObject> calculate(IndicatorContext context) {
                    // Your logic here.
                    // List<ApiKLine> data = context.klineData();
                    // Map<String, Object> settings = context.settings();
                    
                    return Collections.emptyList();
                }
            }
            """.formatted(IN_APP_PACKAGE, className);
    }

    private String generateHtmlDocumentation(ApiNode node) {
        String style = "font-family:sans-serif; color: #A9B7C6; background-color: #313335; padding: 8px;";
        String h3Style = "color: #61AFEF; margin-bottom: 5px;";
        String bStyle = "color: #E5C07B;";
        String codeStyle = "font-family: monospace; background-color: #404245; padding: 2px 4px; border-radius: 3px;";
        StringBuilder sb = new StringBuilder("<html><body style='" + style + "'>");
        Object obj = node.getApiObject();
        if (obj instanceof ClassInfo ci) {
            sb.append("<h3 style='").append(h3Style).append("'>").append(ci.isInterface() ? "Interface" : "Class").append(" ").append(ci.getSimpleName()).append("</h3>");
            sb.append("<hr><p>Full Name: <code style='").append(codeStyle).append("'>").append(ci.getName()).append("</code></p>");
        } else if (obj instanceof FieldInfo fi) {
            sb.append("<h3 style='").append(h3Style).append("'>Field: ").append(fi.getName()).append("</h3><hr>");
            sb.append("<p><b style='").append(bStyle).append("'>Type:</b> <code style='").append(codeStyle).append("'>").append(fi.getTypeSignatureOrTypeDescriptor().toStringWithSimpleNames()).append("</code></p>");
        } else if (obj instanceof MethodInfo mi) {
            String returnType = mi.getTypeSignatureOrTypeDescriptor().getResultType().toStringWithSimpleNames();
            String params = Arrays.stream(mi.getParameterInfo()).map(p -> "<code style='" + codeStyle + "'>" + p.getTypeSignatureOrTypeDescriptor().toStringWithSimpleNames() + "</code> " + p.getName()).collect(Collectors.joining(", "));
            sb.append("<h3 style='").append(h3Style).append("'>").append(returnType).append(" ").append(mi.getName()).append("(").append(params).append(")</h3><hr>");
            if (mi.getParameterInfo().length > 0) {
                sb.append("<p><b style='").append(bStyle).append("'>Parameters:</b></p><ul>");
                for (MethodParameterInfo p : mi.getParameterInfo()) {
                    sb.append("<li><code style='").append(codeStyle).append("'>").append(p.getTypeSignatureOrTypeDescriptor().toStringWithSimpleNames()).append("</code> <b style='").append(bStyle).append("'>").append(p.getName()).append("</b></li>");
                }
                sb.append("</ul>");
            }
            sb.append("<p><b style='").append(bStyle).append("'>Returns:</b> <code style='").append(codeStyle).append("'>").append(returnType).append("</code></p>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static class ApiTreeCellRenderer extends DefaultTreeCellRenderer {
        private final Icon classIcon = UITheme.getIcon(UITheme.Icons.API_CLASS, 16, 16);
        private final Icon methodIcon = UITheme.getIcon(UITheme.Icons.API_METHOD, 16, 16);
        private final Icon fieldIcon = UITheme.getIcon(UITheme.Icons.API_FIELD, 16, 16);
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof ApiNode apiNode) {
                switch (apiNode.getType()) {
                    case CLASS -> setIcon(classIcon);
                    case METHOD -> setIcon(methodIcon);
                    case FIELD -> setIcon(fieldIcon);
                }
            }
            return this;
        }
    }

    private static class DiagnosticParser implements Parser {
        private List<Diagnostic<? extends JavaFileObject>> currentDiagnostics;
        private final DefaultParseResult result;
        public DiagnosticParser() {
            this.currentDiagnostics = new ArrayList<>();
            this.result = new DefaultParseResult(this);
        }
        public void setDiagnostics(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
            this.currentDiagnostics = diagnostics;
        }
        @Override
        public ParseResult parse(RSyntaxDocument doc, String style) {
            result.clearNotices();
            if (currentDiagnostics != null) {
                for (Diagnostic<? extends JavaFileObject> diagnostic : currentDiagnostics) {
                    if (diagnostic.getKind() != Diagnostic.Kind.ERROR) continue;
                    int line = (int) diagnostic.getLineNumber() - 1;
                    if (line < 0) continue;
                    int start = (int) diagnostic.getStartPosition();
                    int end = (int) diagnostic.getEndPosition();
                    if (start < 0 || end < start) continue;
                    result.addNotice(new DefaultParserNotice(this, diagnostic.getMessage(null), line, start, end - start));
                }
            }
            return result;
        }
        @Override public boolean isEnabled() { return true; }
        @Override public URL getImageBase() { return null; }
        @Override public ExtendedHyperlinkListener getHyperlinkListener() { return null; }
    }
}