package bitcoin;

/**
 * Ez a szál felel a nem SegWit-es tranzakciók beolvasásáért
 */
public class BitcoinReaderThread extends Thread {
    private BitcoinReader bitcoinReader;    // BitcoinJ-t használó olvasó osztály

    /**
     * Konstruktor
     * @param bitcoinReader Az objektum, amivel a beolvasás történik
     */
    public BitcoinReaderThread(BitcoinReader bitcoinReader){
        this.bitcoinReader = bitcoinReader;
    }

    /**
     * Elindítjuk ténylegesen az olvasást
     */
    public void run(){
        bitcoinReader.read();
    }
}
