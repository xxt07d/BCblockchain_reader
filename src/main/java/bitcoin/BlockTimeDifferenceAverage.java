package bitcoin;

import java.text.SimpleDateFormat;

/**
 * Az osztály arra való, hogy blokkok között eltelt átlagos időt számoljunk egy adott napra.
 * Eltárolja a legutoljára beadott blokk idejét, ami elegendő, mivel a blokkok sorfojtonosan vannak.
 * Azt, hogy melyik nappal dolgozik, valamint, hogy mennyi blokk átlagát számolja.
 */
public class BlockTimeDifferenceAverage {
    private long lastBlockTime;
    private String day = null;
    private int blockCounter = 0;
    private long differenceSum = 0;


    /**
     * Konstruktor, jelenleg nem csinál semmi különöset.
     */
    public BlockTimeDifferenceAverage(){
    }

    /**
     * Ez a függvény szolgál arra, hogy hozzáadjunk további blokkokat.
     * Csak akkor adunk hozzá, ha eddig nem adtunk hozzá egyet sem, vagy ha megegyezik a blokk keletkezési napja
     * az eltárolt (day) nappal.
     * @param blockTime A beadott blokk keletkezési ideje.
     */
    public void addBlockTime(long blockTime){
        if(day == null){
            this.lastBlockTime = blockTime;
            this.day = new SimpleDateFormat("yyyy-MM-dd").format(blockTime);
            ++blockCounter;
        } else if( day.equals(new SimpleDateFormat("yyyy-MM-dd").format(blockTime))){
            ++blockCounter;
            differenceSum += blockTime - lastBlockTime;
            lastBlockTime = blockTime;
        }
    }

    /**
     * Egy wrapper objektumban kiszámolja az eddigi átlagot
     * @return Az átlag.
     */
    public Double makeAverage(){
        return differenceSum/(double)blockCounter;
    }
}
