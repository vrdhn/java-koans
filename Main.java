
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreePath;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Driver for all the koan files. Execute with java directly.
 */
public class Main {

    private static final Path KOAN_SOURCE_FOLDER = Paths.get("koans");
    private static final Path HELPER_FILES_LISTFILE = KOAN_SOURCE_FOLDER.resolve("HelperFiles.txt");
    private static final Path KOAN_FILES_LISTFILE = KOAN_SOURCE_FOLDER.resolve("KoanFiles.txt");
    private static final Path CLASSPATH = Paths.get("build");

    private static final ColorLogger LOG = new ColorLogger();

    private static class ColorLogger {

        private static Logger log;

        public ColorLogger() {

            System.setProperty(
                    "java.util.logging.SimpleFormatter.format", "%4$s : %5$s%6$s%n");
            this.log = Logger.getAnonymousLogger();
        }

        public void severe(String fmt, Object... args) {
            log.log(Level.SEVERE, fmt, args);
        }

        public void severe(String msg, Throwable t) {
            log.log(Level.SEVERE, msg, t);
        }

        public void info(String fmt, Object... args) {
            log.log(Level.INFO, fmt, args);
        }

    }

    private static class Filter implements BiPredicate<Path, String> {

        private final String[] args;

        public Filter(String[] args) {
            this.args = args;
        }

        @Override
        public boolean test(Path t, String u) {
            return t.toString().contains(this.args[0]);
        }
    }

    public static void main(String[] args) {

        if (Files.exists(CLASSPATH)) {
            if (!Files.isDirectory(CLASSPATH)) {
                LOG.severe("Build directory {0} is not a directory !", CLASSPATH);
                return;
            }
        } else {
            if (!CLASSPATH.toFile().mkdirs()) {
                LOG.severe("Error creating Build directory {0} !", CLASSPATH);
                return;
            }
        }

        // Right now, the command line argument is only to select a subset of koans to run,
        // and is oriented for the developer of koans, rather than the user of koans.
        BiPredicate<Path, String> filterPred = (args.length == 0) ? (a, b) -> true : new Filter(args);
        try {
            Main runner = new Main(HELPER_FILES_LISTFILE, KOAN_FILES_LISTFILE, KOAN_SOURCE_FOLDER, CLASSPATH, filterPred);
            runner.runKoans();
        } catch (Exception e) {
            LOG.severe("Caught unexpected exception", e);
        }
    }

    private final Path helperFilesListfile;
    private final Path koanFilesListfile;
    private final Path koanSourceFolder;
    private final Path koanClasspath;
    private final BiPredicate<Path, String> filterPred;
    private final WatchService watcher;
    private final Set<Path> watchedFolders = new HashSet<>();

    private Main(Path helperFilesListfile,
            Path koanFilesListFile,
            Path koanSourceFolder,
            Path koanClasspath,
            BiPredicate<Path, String> filterPred)
            throws IOException {
        this.helperFilesListfile = helperFilesListfile;
        this.koanFilesListfile = koanFilesListFile;
        this.koanSourceFolder = koanSourceFolder;
        this.koanClasspath = koanClasspath;
        this.filterPred = filterPred;

        this.watcher = FileSystems.getDefault().newWatchService();
    }

    private static class ByteClassLoader extends URLClassLoader {

        public ByteClassLoader(Path classPath) throws MalformedURLException {
            super(new URL[]{classPath.toUri().toURL()}, ByteClassLoader.class.getClassLoader());
            this.setDefaultAssertionStatus(true);
        }
    }

    private static class TreeVisitor extends SimpleTreeVisitor<Void, CompilationUnitTree> {

        private record NameDoc(String name, String doc) {

        }
        private final List<NameDoc> methods;
        private final DocTrees docTree;
        private final Iterable<? extends CompilationUnitTree> asts;
        private String packageName;
        private String className;

        public TreeVisitor(JavacTask task) throws IOException {
            this.methods = new ArrayList<>();
            this.docTree = DocTrees.instance(task);
            this.asts = task.parse();
        }

        public TreeVisitor visit() throws IOException {
            for (CompilationUnitTree ast : asts) {
                visit(ast.getPackage(), ast);
                visit(ast.getTypeDecls(), ast);
            }
            return this;
        }

        @Override
        public Void visitClass(ClassTree node, CompilationUnitTree ast) {
            if (node.getModifiers().getFlags().contains(Modifier.PUBLIC)) {
                this.className = node.getSimpleName().toString();
                return visit(node.getMembers(), ast);
            }
            return null;
        }

        @Override
        public Void visitMethod(MethodTree node, CompilationUnitTree ast) {
            String name = node.getName().toString();
            // Only > public static void fn()
            if (node.getModifiers().getFlags().containsAll(List.of(Modifier.PUBLIC, Modifier.STATIC))
                    && node.getParameters().isEmpty()
                    && node.getReturnType() instanceof PrimitiveTypeTree prim
                    && prim.getPrimitiveTypeKind().equals(TypeKind.VOID)) {
                DocCommentTree javaDoc = docTree.getDocCommentTree(TreePath.getPath(ast, node));
                NameDoc km = new NameDoc(name, " " + javaDoc);
                methods.add(km);
            }
            return null;
        }

        @Override
        public Void visitPackage(PackageTree node, CompilationUnitTree p) {
            this.packageName = node.getPackageName().toString();
            return null;
        }

    }

    private void runKoans()
            throws Exception {

        boolean finished = false;
        Set<String> finishedKoans = new HashSet<>();
        while (!finished) {
            List<Path> helperFiles = new ArrayList<>();
            List<Path> koanFiles = new ArrayList<>();

            finished = checkFiles(helperFiles, koanFiles)
                    && compileHelperFiles(this.koanClasspath, helperFiles)
                    && runAllKoans(finishedKoans, koanFiles);
            if (!finished) {
                waitForSourceChange();
            }
        }
        LOG.info("Congratulations, you finished koans!");
    }

    public void waitForSourceChange()
            throws IOException, InterruptedException {
        boolean foundSourceChange = false;
        LOG.info("");
        LOG.info("Edit the file(s) and save to continue");
        LOG.info("");
        while (!foundSourceChange) {
            watchFolder(this.koanSourceFolder);

            for (WatchKey e = watcher.poll(); e != null; e = watcher.poll()) {
                e.pollEvents();
                e.reset();
            }

            WatchKey w = watcher.take();
            Thread.sleep(300);
            foundSourceChange = javaSourceEvent(foundSourceChange, w.pollEvents());
            w.reset();
            for (WatchKey e = watcher.poll(); e != null; e = watcher.poll()) {
                foundSourceChange = javaSourceEvent(foundSourceChange, e.pollEvents());
                e.reset();
            }
        }
    }

    private static boolean javaSourceEvent(boolean srcChange, List<WatchEvent<?>> events) {
        if (srcChange) {
            return true;
        }
        for (WatchEvent<?> event : events) {
            @SuppressWarnings("unchecked")
            WatchEvent<Path> ev = (WatchEvent<Path>) event;
            if (isJavaSource(ev.context())) {
                return true;
            }
        }
        return false;
    }

    private boolean checkFiles(List<Path> helperFiles, List<Path> koanFiles)
            throws IOException {

        Set<Path> diskJavaFiles = new HashSet<>();
        getDiskJavaFiles(diskJavaFiles, this.koanSourceFolder);

        helperFiles.addAll(readList(this.koanSourceFolder, this.helperFilesListfile));
        koanFiles.addAll(readList(this.koanSourceFolder, this.koanFilesListfile));

        boolean ret = true;
        // Some set jugglery now.
        Set<Path> common = intersection(helperFiles, koanFiles);
        if (!common.isEmpty()) {
            LOG.severe("HELPERS and SOURCES in Main.java have common elements:{0}", common);
            ret = false;
        }
        Set<Path> list = union(helperFiles, koanFiles);
        Set<Path> missingOnDisk = difference(list, diskJavaFiles);
        Set<Path> extraOnDisk = difference(diskJavaFiles, list);
        if (!missingOnDisk.isEmpty()) {
            List<Path> actual = missingOnDisk.stream().filter(Main::isJavaSource).toList();
            if (!actual.isEmpty()) {
                LOG.severe("Files missing on disk:  {0}", missingOnDisk);
            }
            ret = false;
        }
        if (!extraOnDisk.isEmpty()) {
            List<Path> actual = extraOnDisk.stream().filter(Main::isJavaSource).toList();
            if (!actual.isEmpty()) {
                LOG.severe("Files missing in {0} or {1}: {2}", this.helperFilesListfile, this.koanFilesListfile, extraOnDisk);
            }
            ret = false;
        }
        return ret;
    }

    private static boolean isJavaSource(Path p) {
        String f = p.getFileName().toString();
        return f.endsWith(".java") && !f.startsWith(".");
    }

    // Note that this assumes that on case insensitive file system, the extension java is lower case.
    private static void getDiskJavaFiles(Set<Path> output, Path folder)
            throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (Files.isDirectory(file) && !"..".equals(name) && !".".equals(name)) {
                    getDiskJavaFiles(output, file);
                } else if (isJavaSource(file)) {
                    output.add(file);
                }
            }
        }
    }

    private static List<Path> readList(Path sourceFolder, Path txtFile)
            throws IOException {
        return Files.readAllLines(txtFile, StandardCharsets.UTF_8)
                .stream()
                .map(line -> line.replaceFirst("#.*", "").trim())
                .filter(line -> !line.isBlank())
                .map(line -> sourceFolder.resolve(line))
                .toList();
    }

    private static boolean compileHelperFiles(Path classPath, List<Path> helperFiles) {
        if (helperFiles.isEmpty()) {
            return true;
        }
        boolean status = compile(classPath, helperFiles);
        if (!status) {
            LOG.severe("Compilation of helper classes has failed, which should not happen.");
        }
        return status;
    }

    private boolean runAllKoans(Set<String> finishedKoans, List<Path> koanFiles)
            throws IOException, ClassNotFoundException, NoSuchMethodException {
        int totalClass = koanFiles.size();
        int currClass = 0;
        for (Path srcFile : koanFiles) {
            currClass++;
            if (this.filterPred.test(srcFile, "")) {
                boolean compilationStatus = compile(this.koanClasspath, List.of(srcFile));
                if (!compilationStatus) {
                    return false;
                }

                /*@Nullable*/
                KoansFile koanFile = loadClass(this.koanClasspath, srcFile);
                if (koanFile == null) {
                    return false;
                }

                if (!finishedKoans.contains(koanFile.classFullName)) {
                    if (!runSingleKoan(finishedKoans, koanFile)) {
                        return false;
                    }
                    finishedKoans.add(koanFile.classFullName);
                    LOG.info("Koan set done [{0}/{1}]: {2}", currClass, totalClass, koanFile.classFullName);
                } else {
                    LOG.info("Koan set skipped [{0}/{1}]: {2}", currClass, totalClass, koanFile.classFullName);
                }
            }
        }
        return true;
    }

    private boolean runSingleKoan(Set<String> finishedKoans, KoansFile koanFile) {
        int total = koanFile.methods.size();
        int curr = 0;
        for (KoanMethod m : koanFile.methods) {
            curr++;
            String methodName = koanFile.classFullName + "::" + m.name;
            if (!finishedKoans.contains(methodName)) {
                boolean invokeStatus = invoke(koanFile, m, methodName);
                if (!invokeStatus) {
                    return false;
                }
                LOG.info("Koan done  [{0}/{1}]: {2}", curr, total, methodName);
                finishedKoans.add(methodName);
            } else {
                LOG.info("Koan skipped [{0}/{1}]: {2}", curr, total, methodName);
            }
        }
        return true;
    }

    private boolean invoke(KoansFile f, KoanMethod m, String method) {
        try {
            m.method.invoke(null);
            return true;
        } catch (Throwable e) {
            LOG.info("Invocation failed when running {0}", method);
            LOG.info("  /------------------------------------------------------------");
            for (String line : m.desc.split("\\n")) {
                LOG.info("  |" + line);
            }
            LOG.info("  |=============================================================");
            LOG.info("  |");
            if (e instanceof InvocationTargetException ie) {
                if (ie.getCause() instanceof AssertionError ae) {
                    StackTraceElement frame = ae.getStackTrace()[0];
                    //TODO: frame.getFileName() should be same as f.filename
                    LOG.severe("| {0}:{1}: assert: {2}", f.filename, frame.getLineNumber(), ae.getMessage());

                } else {
                    LOG.info("| Unrecognized exception:");
                    LOG.info("  |    {0}", ie.getCause().toString());
                    for (StackTraceElement frame : ie.getCause().getStackTrace()) {
                        // print only top lines which are from koan files.
                        if (frame.getModuleName() != null) {
                            LOG.info("  |        ...");
                            break;
                        }
                        LOG.info(
                                "  |        at {0}.{1}({2}:{3})",
                                frame.getClassName(),
                                frame.getMethodName(),
                                frame.getFileName(),
                                frame.getLineNumber()
                        );
                    }
                }
            } else {
                LOG.severe("Inernal ERROR", e);
            }
            LOG.info("  |");
            LOG.info("  \\------------------------------------------------------------");
            return false;
        }
    }

    private record KoanMethod(String name, Method method, String desc) {

    }

    private record KoansFile(Path filename, String classFullName, Class<?> klass, List<KoanMethod> methods) {

    }

    private static /*@Nullable */ KoansFile loadClass(Path classPath, Path koanFile)
            throws IOException, ClassNotFoundException, NoSuchMethodException {

        StringWriter output = new StringWriter();
        JavacTask task = (JavacTask) getCompilationTask(output, classPath, List.of(koanFile));
        var treeVisitor = new TreeVisitor(task).visit();

        String className = treeVisitor.packageName != null
                ? (treeVisitor.packageName + "." + treeVisitor.className)
                : treeVisitor.className;

        final Class<?> loadedClass = loadClassAgain(classPath, className);

        List<KoanMethod> methods = new ArrayList<>(treeVisitor.methods.size());
        for (var nd : treeVisitor.methods) {
            Method m = loadedClass.getMethod(nd.name);
            methods.add(new KoanMethod(nd.name, m, nd.doc));
        }
        return new KoansFile(koanFile, className, loadedClass, methods);
    }

    private static Class<?> loadClassAgain(Path classPath, String className)
            throws MalformedURLException, IOException, ClassNotFoundException {
        try (ByteClassLoader classLoader = new ByteClassLoader(classPath)) {
            return classLoader.loadClass(className);
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
    private static boolean compile(Path classPath, List<Path> fileNames) {
        StringWriter output = new StringWriter();
        var task = getCompilationTask(output, classPath, fileNames);
        var status = task.call();
        String out = output.toString();
        if (!out.isBlank()) {
            LOG.severe("Compilation failed when compiling {0}", fileNames);
            LOG.info("  /------------------------------------------------------------");
            LOG.info("  |");
            for (String line : out.split("\\n")) {
                if (line.contains(": error:")) {
                    LOG.severe("| {0}", line);
                } else {
                    LOG.info("  | {0}", line);
                }
            }
            LOG.info("  \\------------------------------------------------------------");
        }
        if (status != null && status) {
            return true;
        } else {
            return false;
        }
    }

    private static JavaCompiler.CompilationTask getCompilationTask(StringWriter output, Path classPath, List<Path> sourceFiles) {
        String cp = classPath.toAbsolutePath().toString();

        List<String> options
                = List.of("-g", "-cp", cp, "-d", cp,
                        "-Werror", "-Xdiags:verbose", "-Xlint", "-Xmaxerrs", "1");
        String[] fileNames = new String[sourceFiles.size()];
        for (int j = 0; j < fileNames.length; j++) {
            fileNames[j] = sourceFiles.get(j).toString();
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        var fileManager = compiler.getStandardFileManager(null, null, null);
        var task = compiler.getTask(output, fileManager,
                null, options, null, fileManager.getJavaFileObjects(fileNames));
        return task;
    }

    private void watchFolder(Path p) throws IOException {
        if (!this.watchedFolders.contains(p)) {
            p.register(watcher,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);
            this.watchedFolders.add(p);
        }
        for (Path s : Files.newDirectoryStream(p, Files::isDirectory)) {
            watchFolder(s);
        }
    }

    private static Set<Path> intersection(Collection<Path> a, Collection<Path> b) {
        Set<Path> ret = new HashSet<>(a);
        ret.retainAll(b);
        return ret;
    }

    private static Set<Path> union(Collection<Path> a, Collection<Path> b) {
        Set<Path> ret = new HashSet<>(a);
        ret.addAll(b);
        return ret;
    }

    private static Set<Path> difference(Collection<Path> a, Collection<Path> b) {
        Set<Path> ret = new HashSet<>(a);
        ret.removeAll(b);
        return ret;
    }
}
