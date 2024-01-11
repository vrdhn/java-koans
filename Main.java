
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreePath;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Driver for all the koan files. Execute with java directly.
 */
public class Main {

    /**
     * Update this when a new helper source file is added in koans folder
     */
    private static final String HELPER_CLASSES = " ";

    /**
     * Update this when a new koans source file is added in koans folder
     */
    private static final String KOANS_CLASSES = """
        Intro1
        Intro2
        Variable
        """;

    private static final Logger LOG;

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s : %5$s%6$s%n");
        LOG = Logger.getAnonymousLogger();
    }

    public static void main(String[] args) {
        String sourcePath = "koans/";
        String classPath = "build/";
        String[] helperClasses = HELPER_CLASSES.split("\\s+");
        String[] koansClasses = KOANS_CLASSES.split("\\s+");

        File src = new File(sourcePath);
        if (!src.isDirectory()) {
            LOG.log(Level.SEVERE, "Source directory {0} is not a directory !", sourcePath);
            return;
        }
        File cp = new File(classPath);
        if (cp.exists()) {
            if (!cp.isDirectory()) {
                LOG.log(Level.SEVERE, "Build directory {0} is not a directory !", cp);
                return;
            }
        } else {
            if (!cp.mkdirs()) {
                LOG.log(Level.SEVERE, "Error creating Build directory {0} !", cp);
                return;
            }
        }

        Set<String> diff = asSet(helperClasses);
        diff.retainAll(asSet(koansClasses));
        if (!diff.isEmpty()) {
            LOG.log(Level.SEVERE, "HELPERS and SOURCES in Main.java have common elements:{0}", diff);
            return;
        }

        // Run some files if command line
        Set<String> toSkip = new HashSet<>();
        if (args.length > 0) {
            for (String k : koansClasses) {
                if (!k.contains(args[0])) {
                    toSkip.add(k);
                }
            }
        }

        try {
            Main runner = new Main(sourcePath, classPath, helperClasses, koansClasses, toSkip);
            runner.runKoans();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Caught unexpected exception", e);
        }
    }

    private record KoanMethod(String name, Method method, int seq, String desc) {

    }

    private final String sourcePath;
    private final String classPath;
    private final String[] helperClasses;
    private final String[] koansClasses;
    private final Set<String> toSkip;
    private final WatchService watcher;
    private final WatchKey watchKey;

    private Main(String sourcePath, String classPath, String[] helperClasses, String[] koansClasses, Set<String> toSkip)
            throws IOException {
        this.sourcePath = sourcePath;
        this.classPath = classPath;
        this.helperClasses = helperClasses;
        this.koansClasses = koansClasses;
        this.toSkip = toSkip;

        this.watcher = FileSystems.getDefault().newWatchService();
        this.watchKey = Paths.get(sourcePath)
                .register(watcher,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE);
    }

    private void runKoans() throws Exception {

        boolean finished = false;
        Set<String> finishedKoans = new HashSet<>();
        while (!finished) {
            finished = checkFiles() && compileHelperFiles() && runAllKoans(finishedKoans);

            if (!finished) {
                LOG.info("");
                LOG.info("Edit the file(s) and save to continue");
                LOG.info("");

                watchKey.pollEvents();
                while (null != watcher.poll()) {
                    /*nothing to do*/
                }
                watchKey.reset();
                watcher.take();
                Thread.sleep(300);
            }
        }
        LOG.info("Congratulations, you finished koans!");
    }

    private boolean checkFiles() {
        Predicate<String> javaSource = Pattern.compile("^[A-Z][A-Za-z0-9_]+.java$").asPredicate();
        File[] diskFiles = new File(sourcePath).listFiles((dir, name) -> javaSource.test(name));
        Set<String> declared = asSet(helperClasses);
        declared.addAll(asSet(koansClasses));
        Set<String> undeclared = new HashSet<>();
        for (File file : diskFiles) {
            String name = file.getName().replace(".java", "");
            if (!declared.remove(name)) {
                undeclared.add(name);
            }
        }
        boolean ret = true;
        if (!declared.isEmpty()) {
            LOG.log(Level.SEVERE, "Files not found in {0}: {1}", new Object[]{sourcePath, declared});
            ret = false;
        }
        if (!undeclared.isEmpty()) {
            LOG.log(Level.SEVERE, "Classes not declared in Main.java : {0}", undeclared);
            ret = false;
        }
        if (!ret) {
            LOG.log(Level.SEVERE, "Update Main.java and/or add/remove Java files in {0} and rerun", sourcePath);
        }
        return ret;
    }

    private boolean compileHelperFiles() {
        if (this.helperClasses.length == 0) {
            return true;
        }
        boolean status = compile(this.helperClasses);
        if (!status) {
            LOG.severe("Compilation of helper classes has failed, which should not happen.");
        }
        return status;
    }

    private boolean runAllKoans(Set<String> finishedKoans) {
        int totalClass = this.koansClasses.length;
        int currClass = 0;
        for (String koanClass : this.koansClasses) {
            currClass++;
            if (!finishedKoans.contains(koanClass) && !toSkip.contains(koanClass)) {
                if (!runSingleKoan(finishedKoans, koanClass)) {
                    return false;
                }
                LOG.log(Level.INFO, "Koan set done [{0}/{1}]: {2}", new Object[]{currClass, totalClass, koanClass});
            } else {
                LOG.log(Level.INFO, "Koan set skipped [{0}/{1}]: {2}", new Object[]{currClass, totalClass, koanClass});
            }
        }
        return true;
    }

    private boolean runSingleKoan(Set<String> finishedKoans, String koanClass) {
        boolean compilationStatus = compile(koanClass);
        if (!compilationStatus) {
            return false;
        }
        /*@Nullable */ List<KoanMethod> methods = loadClass(koanClass);
        if (methods == null) {
            return false;
        }
        int total = methods.size();
        int curr = 0;
        for (KoanMethod m : methods) {
            curr++;
            String methodName = koanClass + "::" + m.name();
            if (!finishedKoans.contains(methodName)) {
                boolean invokeStatus = invoke(m, methodName);
                if (!invokeStatus) {
                    return false;
                }
                LOG.log(Level.INFO, "Koan done  [{0}/{1}]: {2}", new Object[]{curr, total, methodName});
                finishedKoans.add(methodName);
            } else {
                LOG.log(Level.INFO, "Koan skipped [{0}/{1}]: {2}", new Object[]{curr, total, methodName});
            }
        }
        finishedKoans.add(koanClass);
        return true;
    }

    private boolean invoke(KoanMethod m, String method) {
        try {
            m.method.invoke(null);
            return true;
        } catch (Throwable e) {
            LOG.log(Level.SEVERE, "Invocation failed when running {0}", method);
            LOG.info("  /------------------------------------------------------------");
            for (String line : m.desc.split("\\n")) {
                LOG.info("  |" + line);
            }
            LOG.info("  |=============================================================");
            LOG.info("  |");
            if (e instanceof InvocationTargetException ie) {
                if (ie.getCause() instanceof AssertionError ae) {
                    StackTraceElement frame = ae.getStackTrace()[0];
                    LOG.log(Level.SEVERE, "| koans/{0}:{1}: assert: {2}", new Object[]{frame.getFileName(), frame.getLineNumber(), ae.getMessage()});

                } else {
                    LOG.log(Level.SEVERE, "| Unrecognized exception:");
                    LOG.log(Level.INFO, "  |    {0}", ie.getCause().toString());
                    for (StackTraceElement frame : ie.getCause().getStackTrace()) {
                        // print only top lines which are from koan files.
                        if (frame.getModuleName() != null) {
                            LOG.log(Level.INFO, "  |        ...");
                            break;
                        }
                        LOG.log(
                                Level.INFO,
                                "  |        at {0}.{1}({2}:{3})",
                                new Object[]{
                                    frame.getClassName(),
                                    frame.getMethodName(),
                                    frame.getFileName(),
                                    frame.getLineNumber()
                                });
                    }
                }
            } else {
                LOG.log(Level.SEVERE, "Inernal ERROR", e);
            }
            LOG.info("  |");
            LOG.info("  \\------------------------------------------------------------");
            return false;
        }
    }

    private static class ByteClassLoader extends URLClassLoader {

        public ByteClassLoader(String classPath) throws MalformedURLException {
            super(
                    new URL[]{new File(classPath).toURI().toURL()},
                    ByteClassLoader.class.getClassLoader());
            this.setDefaultAssertionStatus(true);
        }
    }

    private static class TreeVisitor extends SimpleTreeVisitor<Void, CompilationUnitTree> {

        private final List<KoanMethod> methods;
        private final DocTrees docTree;
        private final Map<String, Method> koanMethods;
        int seq = 0;

        public TreeVisitor(
                JavacTask task, Map<String, Method> koanMethods, List<KoanMethod> methods)
                throws IOException {
            this.methods = methods;
            this.koanMethods = koanMethods;
            Iterable<? extends CompilationUnitTree> asts = task.parse();
            this.docTree = DocTrees.instance(task);
            for (CompilationUnitTree ast : asts) {
                visit(ast.getTypeDecls(), ast);
            }
        }

        @Override
        public Void visitClass(ClassTree node, CompilationUnitTree ast) {
            return visit(node.getMembers(), ast);
        }

        @Override
        public Void visitMethod(MethodTree node, CompilationUnitTree ast) {
            String name = node.getName().toString();
            DocCommentTree javaDoc = docTree.getDocCommentTree(TreePath.getPath(ast, node));
            KoanMethod km
                    = new KoanMethod(
                            name,
                            koanMethods.get(name),
                            seq++,
                            ' ' + javaDoc.toString());
            methods.add(km);
            return null;
        }
    }

    private /*@Nullable */ List<KoanMethod> loadClass(String koanClass) {
        try {
            final Class<?> loadedClass;
            try (ByteClassLoader classLoader = new ByteClassLoader(this.classPath)) {
                loadedClass = classLoader.loadClass(koanClass);
            }
            if (!loadedClass.accessFlags().contains(AccessFlag.PUBLIC)) {
                LOG.severe("Class is not public : " + koanClass);
                return null;
            }

            // Find all the public static void <Method>(void)
            Map<String, Method> koanMethods = new HashMap<>();
            for (Method m : loadedClass.getDeclaredMethods()) {
                if (m.accessFlags().containsAll(List.of(AccessFlag.PUBLIC, AccessFlag.STATIC))
                        && m.getReturnType().equals(void.class)
                        && m.getParameterCount() == 0) {
                    koanMethods.put(m.getName(), m);
                }
            }

            // Now get the methods and javadoc methods.
            List<KoanMethod> methods = new ArrayList<>();
            StringWriter output = new StringWriter();
            JavacTask task = (JavacTask) getCompilationTask(output, koanClass);
            new TreeVisitor(task, koanMethods, methods);
            methods.sort(Comparator.comparingInt(m -> m.seq()));
            return methods;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Compile one or more files together, and leave the classes in
     * CLASS_FOLDER. Only tells about the success and failure, not about the
     * class file generated. Error message are sent to the screen. The files
     * should have the KOAN_FOLDER prefix.
     *
     * @return true if compilation succeed, false if compilation failed
     */
    private boolean compile(String... classNames) {
        StringWriter output = new StringWriter();
        var task = getCompilationTask(output, classNames);
        var status = task.call();
        String out = output.toString();
        if (!out.isBlank()) {
            LOG.severe("Compilation failed when compiling " + Arrays.asList(classNames));
            LOG.info("  /------------------------------------------------------------");
            LOG.info("  |");
            for (String line : out.split("\\n")) {
                LOG.info("  | " + line);
            }
            LOG.info("  \\------------------------------------------------------------");
        }
        if (status != null && status) {
            return true;
        } else {
            return false;
        }
    }

    private JavaCompiler.CompilationTask getCompilationTask(
            StringWriter output, String... classNames) {
        List<String> options
                = List.of(
                        "-g",
                        "-cp",
                        this.classPath,
                        "-d",
                        this.classPath,
                        "-Werror",
                        "-Xdiags:verbose",
                        "-Xlint",
                        "-Xmaxerrs",
                        "1");
        String[] fileNames = new String[classNames.length];
        for (int j = 0; j < classNames.length; j++) {
            fileNames[j] = this.sourcePath + "/" + classNames[j] + ".java";
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        var fileManager = compiler.getStandardFileManager(null, null, null);
        var task
                = compiler.getTask(
                        output,
                        fileManager,
                        null,
                        options,
                        null,
                        fileManager.getJavaFileObjects(fileNames));
        return task;
    }

    private static Set<String> asSet(String[] elem) {
        return new HashSet<>(Arrays.asList(elem));
    }
}
