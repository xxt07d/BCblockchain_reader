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
    private static NetworkParameters np;
    private static BlockFileLoader loader;


    /**
     * Az osztály használatakor inicializálja a fent deklarált változókat
     * a hálózati paramétereket a főhálózatból veszi, majd ez alapján generál egy kontextust
     * Végül pedig inicializálja a bitcoinJ blockparser-ét, amihez szükség van a blockchain fájlok listájára is
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
            for ( Transaction tx: block.getTransactions() ) {
                ++amount;
            }
        }

        return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(block.getTime()), amount);
    }
}