package bitcoin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import info.blockchain.api.blockexplorer.entity.Block;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Ez a szál felel a Blockchain Data API által letöltött, és a felhasználó által valahova elhelyezett blockNNNNN.txt
 * fájlok beolvasásáért.
 */
public class ParserThread extends Thread {
    private String toParse = null;
    private String PATH;
    private long offlineBlockCounter;

    public Block blk = null;
    public boolean done = false;

    /**
     * Konstruktor, amelyben megadhatjuk az elérési paramétereket.
     * @param offlineBlockCounter Annak a blokknak a magassága, amelyet offline módban a szálnak be kell olvasnia
     * @param PATH Az elérési útvonal a blockchain SegWit-es részéhez
     */
    public ParserThread(long offlineBlockCounter, String PATH) { this.offlineBlockCounter = offlineBlockCounter; this.PATH = PATH;}

    /**
     * A szál először a megadott blokkmagasság és az elérési útvonal segítségével beolvas egy adott fájlt
     * az általam definiált könyvtárszerkezetből, ami jelenleg így néz ki:
     * /blockchain/XXX/blockXXXYYY.txt
     * XXX = a blokkmagasság első három számjegye
     * YYY = a blokkmagasság második három számjegye
     */
    public void run() {
        try {
            BufferedReader inputFile = new BufferedReader(new FileReader(PATH  + (int)(offlineBlockCounter / 1000) + "/block" + offlineBlockCounter + ".txt" ));
            toParse = inputFile.readLine();
            inputFile.close();
            if(toParse == null){    // ha nem volt bemeneti paraméterünk, akkor jelezzük, hogy a szál végzett a dolgával
                done = true;
            }
        } catch (IOException e) {   // ha nem tudtuk beolvasni a bemeneti paraméterek által jelzett úton, akkor is DONE
            //e.printStackTrace();
            done = true;
        }
        if(toParse != null){        // amennyiben van adat amivel dolgozhatunk
            JsonParser jsonParser = new JsonParser();
            blk = new Block(((JsonObject)jsonParser.parse(toParse)));   // String -> JsonObject -> Block  ERŐFORRÁSIGÉNYES!!
            done = true;
        }
    }
}
