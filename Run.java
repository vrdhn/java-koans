import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.tools.ToolProvider;

/** Driver for all the koan files. Execute with java directly. */
public class Run {

    private static final File KOAN_FOLDER = new File("koans");
    private static final File CLASS_FOLDER = new File("build");

    private record Koans(File filename, int order, List<String> functions) {}

    private record CompileResult(boolean success, File classFile) {}

    private record LoadResult(boolean success, Class<?> classObject) {}

    private record InvokeResult(boolean success, String message) {}

    private static CompileResult compile(File file) {
        List<String> options =
                List.of(
                        "-g",
                        "-cp",
                        CLASS_FOLDER.toString(),
                        "-d",
                        CLASS_FOLDER.toString(),
                        "-Werror",
                        "-Xdiags:verbose",
                        "-Xlint",
                        "-Xmaxerrs",
                        "1");
        var compiler = ToolProvider.getSystemJavaCompiler();
        var fileManager = compiler.getStandardFileManager(null, null, null);
        var task =
                compiler.getTask(
                        null,
                        fileManager,
                        null,
                        options,
                        null,
                        fileManager.getJavaFileObjects(file));
        var status = task.call();
        if (status == null || status == false) {
            return new CompileResult(false, null);
        } else {
            return new CompileResult(
                    true, new File(CLASS_FOLDER, file.getName().replace(".java", ".class")));
        }
    }

    private static LoadResult load(ClassLoader loader, File classFile) {
        try {
            Class loadedClass = loader.loadClass(classFile.getName().replace(".class", ""));
            return new LoadResult(true, loadedClass);
        } catch (ClassNotFoundException c) {
            return new LoadResult(false, null);
        }
    }

    private static InvokeResult invoke(Class<?> cls, String methodName) {
        try {
            Method method = cls.getDeclaredMethod(methodName);
            method.invoke(null);
            return new InvokeResult(true, null);
        } catch (InvocationTargetException e) {
            switch (e.getCause()) {
                case AssertionError ae:
                    var stackTrace = ae.getStackTrace();
                    String msg =
                            "koans/"
                                    + stackTrace[0].getFileName()
                                    + ":"
                                    + stackTrace[0].getLineNumber()
                                    + ": assert: ";
                    return new InvokeResult(false, msg + e.getCause().getMessage());
                default:
                    return new InvokeResult(false, e.toString());
            }
        } catch (Exception e) {
            return new InvokeResult(false, e.getMessage());
        }
    }

    /**
     * Scan all the java files in the 'koans/' folder to find all the koans in the correct sequence.
     * Replace this by a table if this proves to be too brittle.
     */
    private static List<Koans> findKoans() throws IOException {
        Pattern re =
                Pattern.compile(
                        "^(\\s*@Koan.Order\\((.*)\\))|(\\s*public\\s+static\\s+void\\s+(koan[A-Za-z0-9_]+)\s*\\(\s*\\)).*$");
        List<Koans> koans = new ArrayList<>();
        int seq = Integer.MIN_VALUE;
        for (File kf : KOAN_FOLDER.listFiles()) {
            List<String> fns = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(kf))) {
                for (String line; (line = br.readLine()) != null; ) {
                    var m = re.matcher(line);
                    if (m.matches()) {
                        String order = m.group(2);
                        if (order != null && !order.isEmpty()) {
                            seq = Integer.parseInt(order);
                        }
                        String fnname = m.group(4);
                        if (fnname != null && !fnname.isEmpty()) {
                            fns.add(fnname);
                        }
                    }
                }
            }
            if (!fns.isEmpty()) {
                koans.add(new Koans(kf, seq, fns));
            }
        }
        koans.sort((a, b) -> a.order - b.order);
        // Insert the base class.
        koans.add(0, new Koans(new File(KOAN_FOLDER + "/Koan.java"), Integer.MIN_VALUE, List.of()));
        return koans;
    }

    /** */
    public static void main(String[] args) throws IOException {
        // TODO: check for error, make sure build is directory
        CLASS_FOLDER.mkdirs();

        URL url = CLASS_FOLDER.toURI().toURL(); //
        ClassLoader classLoader = new URLClassLoader(new URL[] {url}, Run.class.getClassLoader());

        // enable assertions for dynamically loaded classes.
        classLoader.setDefaultAssertionStatus(true);

        // generate list of koans by scanning java files for well defined patterns.
        var koansList = findKoans();

        for (Koans koans : koansList) {
            CompileResult compileResult = compile(koans.filename());
            if (compileResult.success == false) {
                System.out.println("Compilation failed. Edit the file and rerun");
                continue;
            }
            LoadResult loadResult = load(classLoader, compileResult.classFile());
            if (loadResult.success == false) {
                System.out.println("Loading of classfile failed, That's very strange.");
                continue;
            }

            for (String fn : koans.functions()) {
                InvokeResult invokeResult = invoke(loadResult.classObject, fn);
                if (invokeResult.success == false) {
                    System.out.println(invokeResult.message());
                }
            }
        }
    }
}
