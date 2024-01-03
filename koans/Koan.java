/** All koans should derive from this */
class Koan {

    /**
     * All koans should be annotated with this for them to be presented to user in the correct order
     */
    public @interface Order {
        int value();
    }

    /** All koans should derive from this for using the common functions. */
    public static class Base {}
}
