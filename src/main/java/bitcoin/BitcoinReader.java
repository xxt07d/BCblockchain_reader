package bitcoin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

import org.bitcoinj.core.*;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.utils.BlockFileLoader;

/**
 * A bitcoinJ api segítségével a blockchain olvasásra használt osztály
 * SegWit-es tranzakciók olvasására jelenleg (2018-03-30) nem alkalmas
 */
public class BitcoinReader {

    // Elérési útvonal a nem SegWites blockchain-hez
    private static final String PATH = "d:/bitcoin_blockchain/blocks/";

    /*
    Az a blokkmagasság amitől kezdjük az olvasást
    TUDNI KELL, HOGY MELYIK FÁJLBAN VAN
     */
    private static final long BLOCK_BEGIN_HEIGHT = 0;

    // Hálózati paraméterek, amelyek az api által definiált fő vagy teszt paraméterek, esetleg saját
    @SuppressWarnings("FieldCanBeLocal")
    private static NetworkParameters np;
    private static BlockFileLoader loader;


    /*
      Az osztály használatakor inicializálja a fent deklarált változókat
      a hálózati paramétereket a főhálózatból veszi, majd ez alapján generál egy kontextust
      Végül pedig inicializálja a bitcoinJ blockparser-ét, amihez szükség van a blockchain fájlok listájára is
     */
    static {
        np = new MainNetParams();
        Context.getOrCreate(MainNetParams.get());
        loader = new BlockFileLoader(np,buildList());
    }


    /**
     * A blokkok olvasását végző függvény
     */
    public void read() {

        /*
        A blokk számlálót azzal a magassággal inicializáljuk, amelynél a blockchain vizsgálatát kezdjük.
        Fontos tudni, hogy az első blokk magassága valójában NULLA
         */
        long blockCounter = BLOCK_BEGIN_HEIGHT;

        try{
            for (Block block : loader) {

                // minden megvizsgált blokk esetén továbblépünk
                blockCounter++;
                // jelzi, hogy a feldolgozás halad
                System.out.println("Analysing block " + blockCounter);

                App.doStatistic(block, true);

            }


        } catch (Exception e){
            //e.printStackTrace();  // ha Exception lenne
            /*
            Itt valójában ha a teljes blockchaint olvassuk, akkor mindig Exceptiont fogunk kapni
            Mivel a bitcoinJ api jelenleg nincs felkészítve a SegWit-es tranzakciók feldolgozására
            Így a SegWit engedélyezésétől számított első blokknál megakad
             */
        } finally {
            System.out.println("End of bitcoinJ part");
        }

    }


    /**
     * Ez a függvény listába szedi a letöltött blockchain .dat fájlait
     * A blokkfájlok mintája: blkXXXXX.dat
     * X = 0-9 bármilyen szám
     * A blk fájlok időben növekvő számozásúak és a bennük lévő tranzakciók is időben növekvő magasságúak
     * @return A fájlokból elkészített lista
     */
    private static List<File> buildList() {
        List<File> list = new LinkedList<>();
        for (int i = 0/*847*/; true; ++i) {
            File file = new File(PATH + String.format(Locale.US, "blk%05d.dat", i));
            if (!file.exists())
                break;
            list.add(file);
        }
        return list;
    }

    /**
     * Megnézi, hogy az adott blokkban melyik a legnagyobb összkimenetelű tranzakció
     * @param block A blokk, amiben keresünk
     * @return A blokk keletkezés napja és a legnagyobb összkimenet egy MapPair-ben
     */
    public static MapPair<String, Object> getBlockMaxValuePair(Block block){
        long maxTxOutputSum = 0;
        if(block.getTransactions() != null){
            for(Transaction tx : block.getTransactions()){
                if ( maxTxOutputSum < tx.getOutputSum().value){
                    maxTxOutputSum = tx.getOutputSum().value;
                }
            }
        }
        return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(block.getTime()), maxTxOutputSum);
    }

    /**
     * Megszámolja, hogy hány darab tranzakció van a megadott blokkon belül
     * @param block A blokk, amiben a paramétereket számoljuk
     * @return A blokk keletkezési napja és a tranzakciószám egy MapPair-ben
     */
    public static MapPair<String, Object> getBlockTransactionAmount(Block block) {
        int amount = 0;
        if(block.getTransactions() != null){
            for ( @SuppressWarnings("unused") Transaction tx: block.getTransactions() ) {
                ++amount;
            }
        }

        return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(block.getTime()), amount);
    }

    /**
     * Összegzi, hogy az adott blokkban mennyi az összes kimeneti Bitcoin
     * @param block A blokk, aminek számoljuk a Bitcoin kimenetét
     * @return A blokk keletkezési napja és a bitcoin kimenet egy MapPair-ben
     */
    public static MapPair<String, Object> getBlockBitcoinOutputSum(Block block) {
        long sum = 0;
        if(block.getTransactions() != null){
            for ( Transaction tx: block.getTransactions() ) {
                sum += tx.getOutputSum().value;
            }
        }

        return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(block.getTime()), sum);
    }

    /**
     * (Szükség van egy függvényobjektumra, hogy generikus maradhasson a program, és mivel egy adott blokk függvényét
     * nem tudjuk sajnos lekérni, mert nem statikus függvény, így ezt használjuk helyette.)
     * @param block A vizsgált blokk
     * @return A blokk keletkezési napja és a nehézsége.
     */
    public static MapPair<String, Object> getBlockDifficulty(Block block){
        return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(block.getTime()), block.getDifficultyTarget());
    }

    /**
     * Egy adott blokktól szerzi meg a keletkezési idejét, és adja tovább
     * @param block A vizsgált blokk
     * @param blockTimeDifferenceAverage Az objektum, ami az aktuális átlagot számolja
     * @return A blokk keletkezési ideje, és az átlagot számoló objektum.
     */
    public static MapPair<String, Object> calculateAverageTimeDifference(Block block, BlockTimeDifferenceAverage blockTimeDifferenceAverage){
        if(blockTimeDifferenceAverage != null){
            blockTimeDifferenceAverage.addBlockTime(block.getTime().getTime());
            return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(block.getTime()), blockTimeDifferenceAverage);
        } else {
            return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(block.getTime()), null);
        }
    }

    /**
     * A bitcoinJ API sajátossága, hogy a tranzakcióknál mindig a legelső az, amelyiknél az új bitcoinok létrejönnek
     * Ezzel csak annyi a teendő, hogy megnézzük, hogy mennyit termel. Erről köztudott, hogy 50->25->12.5... értékű
     * @param block A vizsgált blokkgyűjtjük
     * @return A blokk keletkezési napja, és a blokkal keletkezett új Bitcoinok
     */
    public static MapPair<String, Object> getBlockNewBitCoins(Block block){
        @SuppressWarnings("ConstantConditions") long satoshi = block.getTransactions().get(0).getOutput(0).getValue().getValue();
        Double newBitcoin = 0.0;
        if((int)(satoshi/5000000000.0) == 1){
            newBitcoin += 50;
        } else if((int)(satoshi/2500000000.0) == 1){
            newBitcoin += 25;
        } else{
            newBitcoin += 12.5; // utána kövi APIt használjuk
        }
        return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(block.getTime()), newBitcoin);
    }


    /**
     * Sztring formában visszatér a blokk keletkezési idejével
     * @param block A vizsgált blokk
     * @return MapPair, de csak a keletkezési nap van benne.
     */
    public static MapPair<String, Object> getBlockDay(Block block){
        return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(block.getTime()), null);
    }
}