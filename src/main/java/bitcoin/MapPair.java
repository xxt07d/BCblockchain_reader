package bitcoin;

/**
 * Map-ba tehető paraméteres kulcs-érték pár
 * @param <Key> Kulcs paraméter
 * @param <Value> Érték paraméter
 */
public class MapPair<Key, Value> {
    private Key k;
    private Value v;

    /**
     * Sima konstruktor
     * @param k bemeneti kulcs
     * @param v bemeneti érték
     */
    public MapPair(Key k, Value v){
        this.k = k;
        this.v = v;
    }


    public Value getValue() {
        return v;
    }

    public Key getKey() {
        return k;
    }
}
