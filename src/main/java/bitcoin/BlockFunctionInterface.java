package bitcoin;

/**
 * Functional Interface függvények tárolására
 */
@FunctionalInterface
public interface BlockFunctionInterface {
    MapPair<String, Object> function(Object o);
}
