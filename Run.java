import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;

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

/** Driver for all the koan files. Execute with java directly. */
public class Run {

    /** Update this when a new helper source file is added in koans folder */
    private static final String HELPER_CLASSES = "Koan ";

    /** Update this when a new koans source file is added in koans folder */
    private static final String KOANS_CLASSES = "BasicKoan ";

    private static final Logger LOG;

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tT : %4$s : %5$s%6$s%n");
        LOG = Logger.getAnonymousLogger();
    }

    public static void main(String[] args) {
        String sourcePath = "koans/";
        String classPath = "build/";
        String[] helperClasses = HELPER_CLASSES.split("\\s+");
        String[] koansClasses = KOANS_CLASSES.split("\\s+");

        File src = new File(sourcePath);
        if (!src.isDirectory()) {
            LOG.severe("Source directory " + sourcePath + " is not a directory !");
            return;
        }
        File cp = new File(classPath);
        if (cp.exists()) {
            if (!cp.isDirectory()) {
                LOG.severe("Build directory " + cp + " is not a directory !");
                return;
            }
        } else {
            if (false == cp.mkdirs()) {
                LOG.severe("Error creating Build directory " + cp + " !");
                return;
            }
        }

        Set<String> diff = asSet(helperClasses);
        diff.retainAll(asSet(koansClasses));
        if (!diff.isEmpty()) {
            LOG.severe("HELPERS and SOURCES in Run.java have common elements:" + diff.toString());
            return;
        }

        try {
            Run runner = new Run(sourcePath, classPath, helperClasses, koansClasses);
            runner.runKoans();
        } catch (Throwable e) {
            LOG.log(Level.SEVERE, "Caught exception ", e);
        }
    }

    private final String sourcePath;
    private final String classPath;
    private final String[] helperClasses;
    private final String[] koansClasses;
    private final WatchService watcher;
    private final WatchKey watchKey;

    private Run(String sourcePath, String classPath, String[] helperClasses, String[] koansClasses)
            throws IOException {
        this.sourcePath = sourcePath;
        this.classPath = classPath;
        this.helperClasses = helperClasses;
        this.koansClasses = koansClasses;

        this.watcher = FileSystems.getDefault().newWatchService();
        this.watchKey =
                Paths.get(sourcePath)
                        .register(
                                watcher,
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
                watchKey.pollEvents();
                while (null != watcher.poll())
                    ;
                LOG.info("");
                LOG.info("Edit the file(s) and save to continue");
                LOG.info("");
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
            LOG.severe("Files not found in " + sourcePath + ": " + declared.toString());
            ret = false;
        }
        if (!undeclared.isEmpty()) {
            LOG.severe("Classes not declared in Run.java : " + undeclared.toString());
            ret = false;
        }
        if (!ret) {
            LOG.severe(
                    "Update Run.java and/or add/remove Java files in " + sourcePath + " and rerun");
        }
        return ret;
    }

    private boolean compileHelperFiles() {
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
            if (!finishedKoans.contains(koanClass)) {
                boolean compilationStatus = compile(koanClass);
                if (!compilationStatus) {
                    return false;
                }
                /*@Nullable */
                List<Method> methods = loadClass(koanClass);
                if (methods == null) {
                    return false;
                }
                int total = methods.size();
                int curr = 0;
                for (Method m : methods) {
                    curr++;
                    String methodName = koanClass + "::" + m.getName();
                    if (!finishedKoans.contains(methodName)) {
                        boolean invokeStatus = invoke(m, methodName);
                        if (invokeStatus == false) {
                            return false;
                        }
                        LOG.info("Koan done  [" + curr + "/" + total + "]: " + methodName);
                        finishedKoans.add(methodName);
                    } else {
                        LOG.info("Koan skipped [" + curr + "/" + total + "]: " + methodName);
                    }
                }
                finishedKoans.add(koanClass);
                LOG.info("Koan set done [" + currClass + "/" + totalClass + "]: " + koanClass);
            } else {
                LOG.info("Koan set skipped [" + currClass + "/" + totalClass + "]: " + koanClass);
            }
        }
        return true;
    }

    private boolean invoke(Method m, String method) {
        try {
            m.invoke(null);
            return true;
        } catch (Throwable e) {
            LOG.severe("Invocation failed when running " + method);
            LOG.info("-._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.-");
            if (e instanceof InvocationTargetException ie) {
                e = ie.getCause();
                if (e instanceof AssertionError) {
                    StackTraceElement frame = e.getStackTrace()[0];
                    LOG.severe(
                            "koans/"
                                    + frame.getFileName()
                                    + ":"
                                    + frame.getLineNumber()
                                    + ": assert: "
                                    + e.getMessage());

                } else {
                    LOG.log(Level.SEVERE, "Unrecognized exception", e);
                }
            } else {
                LOG.log(Level.SEVERE, "Unrecognized exception", e);
            }
            LOG.info("-._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.-");
            return false;
        }
    }

    private static class ByteClassLoader extends URLClassLoader {
        public ByteClassLoader(String classPath) throws MalformedURLException {
            super(
                    new URL[] {new File(classPath).toURI().toURL()},
                    ByteClassLoader.class.getClassLoader());
            this.setDefaultAssertionStatus(true);
        }
    }

    private /*@Nullable */ List<Method> loadClass(String koanClass) {
        try {
            // Hoping that this reloads the file as required.
            ByteClassLoader classLoader = new ByteClassLoader(this.classPath);
            Class<?> loadedClass = classLoader.loadClass(koanClass);
            if (!loadedClass.accessFlags().contains(AccessFlag.PUBLIC)) {
                LOG.severe("Class is not public : " + koanClass);
                return null;
            }
            Map<String, Integer> order = getMethodOrder(koanClass);
            List<Method> methods = new ArrayList<>();
            for (Method m : loadedClass.getDeclaredMethods()) {
                if (m.accessFlags().containsAll(List.of(AccessFlag.PUBLIC, AccessFlag.STATIC))
                        && m.getReturnType().equals(void.class)
                        && m.getParameterCount() == 0) {
                    methods.add(m);
                    if (!order.containsKey(m.getName())) {
                        LOG.severe("Error in parsing java, didn't find method:" + m);
                    }
                }
            }
            methods.sort(Comparator.comparingInt(m -> order.get(m.getName())));
            return methods;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Compile one or more files together, and leave the classes in CLASS_FOLDER. Only tells about
     * the success and failure, not about the class file generated. Error message are sent to the
     * screen. The files should have the KOAN_FOLDER prefix.
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
            LOG.info("-._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.-");
            for (String line : out.split("\\n")) {
                LOG.info(line);
            }
            LOG.info("-._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.--._.-");
        }
        if (status != null && status) {
            return true;
        } else {
            return false;
        }
    }

    private Map<String, Integer> getMethodOrder(String className) throws IOException {
        Map<String, Integer> methods = new HashMap<>();
        StringWriter output = new StringWriter();
        JavacTask task = (JavacTask) getCompilationTask(output, className);
        Iterable<? extends CompilationUnitTree> ast = task.parse();
        // output should be empty, as we are compiling it second time.
        for (CompilationUnitTree tree : ast) {
            for (Tree decl : tree.getTypeDecls()) {
                if (decl instanceof ClassTree kls) {
                    for (Tree member : kls.getMembers()) {
                        if (member instanceof MethodTree method) {
                            methods.put(method.getName().toString(), methods.size());
                        }
                    }
                }
            }
        }
        return methods;
    }

    private JavaCompiler.CompilationTask getCompilationTask(
            StringWriter output, String... classNames) {
        List<String> options =
                List.of(
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
        var task =
                compiler.getTask(
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
