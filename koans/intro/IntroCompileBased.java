package koans.intro;

public class IntroCompileBased {

    /**
     * Some koans will generate a compilation error, like this one.
     *
     * In this case, this helpful text is not displayed, but Java compiler
     * generate filename and line number, and you can read the helpful comment
     * in the editor directly.
     */
    public static void compileBased() {
        assert True : "True is not spelled right.";
    }


    /**
     * The effort of learning Java can be categorized in three area
     *
     * 1. Project structure, i.e. folder layout, module, packages, java source, unit tests,
     *        resource, assets placement, build system etc.
     * 2. Class structure, how to put members in regular class, record, enum, interface,
     *        anotations etc.
     * 3. Method structure, dealing with the statements put in methods, and static blocks.
     *
     * We'll start with the method structure, and master all the statements that Java supports
     * before vernturing into the class structure.
     *
     *
     * The first statement type which you've alredy seen is the assert statement.
     *    assert  (boolean-expression) : (string expression for display message )
     */
    public static void assertStatement() {
        assert  1 + 1 = 11 : "arithmatic doesn't really work that way";
    }

}
