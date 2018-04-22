package bitcoin;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import info.blockchain.api.APIException;
import info.blockchain.api.HttpClient;
import info.blockchain.api.blockexplorer.BlockExplorer;
import info.blockchain.api.blockexplorer.entity.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A maven-es APP. Ebben van a Main függvény.
 */
public class App {

    // a blockchain SegWit-es tranzakciókat tartalmazó részéhez elérési útvonal
    private static final String PATH = "e:/blockchain_SEGWIT/";

    // előre definiált magasságok, lépésközök, egyéb számok
    private static final long ONLINE_BLOCK_BEGIN_HEIGHT = 515311;
    private static final long OFFLINE_BLOCK_BEGIN_HEIGHT = 481815;
    private static final long OFFLINE_BLOCK_END_HEIGHT = 515311;
    private static final long OFFLINE_BLOCK_STEP = 20;

    // hálózati paraméterek, feldolgozáshoz szükséges objektumok
    private static Map<String, String> HttpClientParams = new HashMap<>();
    private static JsonParser jsonParser = new JsonParser();

    // online és offline mód kezdeti magasságára beállítjuk a blokkszámlálót
    private static long onlineBlockCounter = ONLINE_BLOCK_BEGIN_HEIGHT;
    private static long offlineBlockCounter = OFFLINE_BLOCK_BEGIN_HEIGHT;

    // A statisztikai adatokat kiválasztó flag-ek
    private static boolean COUNT_DAILY_TRANSACTIONS = true;
    private static boolean SEARCH_DAILY_MAXIMUM_OUTPUT_TRANSACTION = true;

    // Map objektumok deklarációja a különböző statisztikákhoz, ezekben lévő adatokat írjuk ki
    private static Map<String, Object> dailyTotTxs = null;
    private static Map<String, Object> dailyMaxTx = null;

    // Blockchain Data Api blockparserének inicializálása
    private static BlockExplorer blockExplorer = new BlockExplorer();

    // A Blockchain Data API és a bitcoinJ API által lehetővé tett függvények halmazai
    private static Map<String, BlockFunctionInterface> BlockchainDataAPIFuncitons = new HashMap<>();
    private static Map<String, BlockFunctionInterface> bitcoinJAPIFuncitons = new HashMap<>();

    /**
     * Az osztály használatakor a HTTP paramétereket beállítja.
     * Ha valamelyik statisztikai adat flag-je true, akkor annak a Map objektumját inicializálja.
     * Emellett minden flag-hez tartozhat egy-egy API specifikus függvény. Ezeket lambda expression-ök segítségével
     * belerakja a függvény Map-ekbe.
     */
    static {
        HttpClientParams.put("format", "json");
        if(COUNT_DAILY_TRANSACTIONS){
            dailyTotTxs = new HashMap<>();
            BlockchainDataAPIFuncitons.put("COUNT_DAILY_TRANSACTIONS", (Object o) -> getBlockTransactionAmount((Block)o));
            bitcoinJAPIFuncitons.put("COUNT_DAILY_TRANSACTIONS", (Object o) -> BitcoinReader.getBlockTransactionAmount((org.bitcoinj.core.Block)o));
        }
        if(SEARCH_DAILY_MAXIMUM_OUTPUT_TRANSACTION){
            dailyMaxTx = new HashMap<>();
            BlockchainDataAPIFuncitons.put("SEARCH_DAILY_MAXIMUM_OUTPUT_TRANSACTION", (Object o) -> getBlockMaxValuePair((Block)o));
            bitcoinJAPIFuncitons.put("SEARCH_DAILY_MAXIMUM_OUTPUT_TRANSACTION", (Object o) -> BitcoinReader.getBlockMaxValuePair((org.bitcoinj.core.Block)o));
        }
    }

    /**
     * A beolvasást illetve a fájlok letöltését végző függvény. Offline és Online mód is lehetséges
     * Online mód:
     *      Először megtudja a jelenlegi maximális blokkmagasságot
     *      Majd ezt korlátként alkalmazva letölti a kezdési magasságtól kezdve a legvégéig a blokkokat.
     *
     * Offline mód:
     *      A kezdeti letöltött magasságtól kezdve több, a STEP által megadott, szállal olvassuk be a blokkokat.
     *      A blokkokat null ellenőrzés után feldolgozzuk.
     *      A legvégén pedig kiíratjuk txt fájlokba a statisztikákat.
     * @param online A paraméter, amely eldönti, hogy milyen módban fusson a függvény.
     *               Az online mód letölt és lement.
     *               Az offline mód feldolgoz és statisztikát készít fájlokba.
     */
    private void read(Boolean online){


        long maxHeight;

        // get a block by hash and read the number of transactions in the block
        try {
            if (online){
                List<SimpleBlock> todaysBlocks = blockExplorer.getBlocks();
                maxHeight = todaysBlocks.get(todaysBlocks.size()-1).getHeight();
                for(long i = ONLINE_BLOCK_BEGIN_HEIGHT; i < maxHeight; ++i){
                    getBlockAtHeightHTTP(i);
                    onlineBlockCounter++;
                    System.out.println("Processing Block #"+ onlineBlockCounter);
                }
            } else {
                for(long i = OFFLINE_BLOCK_BEGIN_HEIGHT; i < OFFLINE_BLOCK_END_HEIGHT; i += OFFLINE_BLOCK_STEP){
                    Block[] blockArray = getBlockAtHeightHDD((int) OFFLINE_BLOCK_STEP);
                    for(int j = 0; j < blockArray.length; ++j){
                        System.out.println("Processing Block #"+ (i+j));
                        if(blockArray[j] != null){
                            doStatistic(blockArray[j], false);
                        } else {
                            System.out.println("Block could not be loaded");
                        }
                    }
                }

            }

        } catch (APIException | IOException e) {
            // ha itt bármi gond van az annyira fontos, hogy látni szertnénk
            e.printStackTrace();
        } finally {
            // minden esetben ki szeretnénk írni az eddigi eredményeket offline módban
            if (!online){
                try {
                    writeToFiles();
                } catch (Exception fileException) {
                    fileException.printStackTrace();
                }
            }
        }

    }

    /**
     * Ez a függvény a program online részéhez használandó.
     * A lényeg, hogy egy HTTP kérés segítségével lekér egy adott magasságú blokkot a blockchain.info adattárából
     * Erre egy olyan választ kap, amiben nem csak maga a blokk van ami kell, hanem több adat, nemmellesleg több blokk.
     * A több blokk a blockchain különböző ágai miatt lehetséges, az első helyen mindig a fő ágból való blokk van.
     * Csak az első helyen lévőt tartja meg a függvény, azt pedig egy String formájában fájlba írja,
     * de előtte megnézi, hogy valóban a fő láncban van-e.
     * @param height A lekérendő blokk magassága
     * @throws APIException A HTTP kérés során merülhet fel
     * @throws IOException Fájlba írás során merülhet fel
     */
    private static void getBlockAtHeightHTTP(long height) throws APIException, IOException {
        String response = HttpClient.getInstance().get("block-height/" + height, HttpClientParams);
        JsonObject blocksJson = jsonParser.parse(response).getAsJsonObject();
        Iterator var6 = blocksJson.get("blocks").getAsJsonArray().iterator();
        JsonObject output = ((JsonElement)var6.next()).getAsJsonObject();
        String toSerialize = output.toString();
        if(new Block(output).isMainChain()){
            PrintWriter pw = new PrintWriter("block" + onlineBlockCounter + ".txt");
            pw.println(toSerialize);
            pw.close();
        }
    }


    /**
     * Ez a függvény a már letöltött (offline) részhez használandó.
     * Az előre megadott paraméter számból kiindulva létrehoz egy howMany mértű tömböt a Blokkoknak
     * és az olvasó szálaknak.
     *
     * Létrehoz új olvasó szálakat, mindnek 1-1 blokk fájljához specifikus paraméterekt adva, egészen amíg el nem érik
     * a paraméterben megadott korlátot, vagy a letöltött blokkok közül a legnagyobb magasságot.
     *
     * Utána egy while ciklusban megvárja, hogy mindegyik függvény elvégezze a dolgát (a done tagváltozót true-ra
     * állítsa), majd megpróbálja kiolvasni a blokkokat a szálakból.
     * A kiolvasás figyelembe veszi, ha egy szál esetleg nem tudott olvasni,
     * és egybefüggően rakja bele a tömbbe a blokkokat.
     *
     * Végül visszatér a blokkok tömbjével.
     * @param howMany Korlát, amivel megadjuk, hogy maximum hány szál olvasson be és hány blokkot.
     * @return A lekérdezett blokkok tömbje.
     */
    private static Block[] getBlockAtHeightHDD(int howMany) {
        Block[] outputs = new Block[howMany];
        ParserThread[] threads = new ParserThread[howMany];
        int threshold = howMany;
        for(int i = 0; i < howMany && offlineBlockCounter < OFFLINE_BLOCK_END_HEIGHT; ++i, ++offlineBlockCounter){
            threads[i] = new ParserThread(offlineBlockCounter, PATH);
            threads[i].start();
            threshold = i+1;
        }
        int done_sum = 0;
        while(done_sum < threshold){
            for(int i = 0; i < threshold; ++i){
                if(threads[i].done) done_sum++;
            }
            if(done_sum < threshold){
                done_sum = 0;
            }
            else{
                for(int i = 0; i < threshold; ++i){
                    if(threads[i].blk != null){
                        outputs[i] = threads[i].blk;
                    } else {
                        i--;
                        threshold--;
                    }
                }
            }
        }

        return outputs;
    }


    /**
     * A Blockchain Data API által biztosított blokkokban a keletkezés ideje másodpercekben van eltárolva.
     * A java.util.Date létrehozásához milliszekundum kell.
     * A függvény lényege (eltelt másodpercek) -> java.util.Date
     * @param seconds
     * @return
     */
    private static Date timeToDate(long seconds) {
        return new Date(seconds * 1000L);
    }

    /**
     * Megnézi, hogy az adott blokkban melyik a legnagyobb összkimenetelű tranzakció
     * @param block A blokk, amiben keresünk
     * @return A blokk keletkezés napja és a legnagyobb összkimenet egy MapPair-ben
     */
    private static MapPair<String, Object> getBlockMaxValuePair(Block block){
        String day = new SimpleDateFormat("yyyy-MM-dd").format(timeToDate(block.getTime()));
        long maxTxOutputSum = 0;
        for(Transaction tx : block.getTransactions()){
            long sum = 0;
            for(Output output : tx.getOutputs()){
                sum += output.getValue();
            }
            if (maxTxOutputSum < sum){
                maxTxOutputSum = sum;
            }
        }
        return new MapPair<>(day, maxTxOutputSum);
    }

    /**
     * Megszámolja, hogy hány darab tranzakció van a megadott blokkon belül
     * @param block A blokk, amiben a paramétereket számoljuk
     * @return A blokk keletkezési napja és a tranzakciószám egy MapPair-ben
     */
    private static MapPair<String, Object> getBlockTransactionAmount(Block block) {
        return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(timeToDate(block.getTime())), block.getTransactions().size());
    }

    /**
     * Először létrehoz egy Map-et, amibe a bitcoinJ által kiválaszott függvénykészletet tölti be.
     * Ezután végignézi, hogy melyik statisztikákkal kell foglalkoznia, majd az adott statisztikához kapcsolódó
     * feladatokat elvégzi.
     * @param schroedingerBlock Vagy bitcoinJ-s blokk, vagy Blockchain Data API-s blokk
     * @param bitcoinJ Flag, ami eldönti, hogy bitcoinJ-s függvényeket kell-e használni
     */
    public static void doStatistic(Object schroedingerBlock, boolean bitcoinJ){
        Map<String, BlockFunctionInterface> functionsToUse;
        if(bitcoinJ){
            functionsToUse = bitcoinJAPIFuncitons;
        } else {
            functionsToUse = BlockchainDataAPIFuncitons;
        }
        if(COUNT_DAILY_TRANSACTIONS){
            MapPair<String, Object> blockTransactionAmount = functionsToUse.get("COUNT_DAILY_TRANSACTIONS").function(schroedingerBlock);
            if (!dailyTotTxs.containsKey(blockTransactionAmount.getKey())) {
                dailyTotTxs.put(blockTransactionAmount.getKey(), blockTransactionAmount.getValue());
            } else {
                dailyTotTxs.put(blockTransactionAmount.getKey(), (Integer)dailyTotTxs.get(blockTransactionAmount.getKey()) + (Integer)blockTransactionAmount.getValue());
            }
        }
        if(SEARCH_DAILY_MAXIMUM_OUTPUT_TRANSACTION){
            MapPair<String, Object> blockHighestOutput = functionsToUse.get("SEARCH_DAILY_MAXIMUM_OUTPUT_TRANSACTION").function(schroedingerBlock);
            if(!dailyMaxTx.containsKey(blockHighestOutput.getKey()) || (dailyMaxTx.containsKey(blockHighestOutput.getKey()) && (Long)dailyMaxTx.get(blockHighestOutput.getKey()) < (Long)blockHighestOutput.getValue())){
                dailyMaxTx.put(blockHighestOutput.getKey(), blockHighestOutput.getValue());
            }
        }
    }


    /**
     * Adott statisztikai flagek meghatározzák, hogy melyik fájlokat kell létrehozni
     * Ezeknek a létrehozása megtörténik, valamint kiírja az odaillő Map adatait is.
     * @throws FileNotFoundException Ha valami okból nem sikerülne megnyitni valamelyik fájlt, akkor dob egy ilyet
     */
    public static void writeToFiles() throws FileNotFoundException{
        if(COUNT_DAILY_TRANSACTIONS){
            PrintWriter writer = new PrintWriter("napi_tx_sum.txt");
            for (String d : dailyTotTxs.keySet()) {
                writer.println(d + ";" + dailyTotTxs.get(d));
            }
            writer.close();
        }
        if(SEARCH_DAILY_MAXIMUM_OUTPUT_TRANSACTION){
            PrintWriter writer = new PrintWriter("napi_max_TX.txt");
            for (String d : dailyMaxTx.keySet()) {
                writer.println(d + ";" + dailyMaxTx.get(d));
            }
            writer.close();
        }
    }

    /**
     * A main függvény, itt indítjuk el a beolvasásokat.
     * @param args nincs használva
     */
    public static void main(String[] args) {
        App app = new App();
        BitcoinReader bitcoinReader = new BitcoinReader();
        BitcoinReaderThread brt = new BitcoinReaderThread(bitcoinReader);
        brt.start();
        app.read(false);
    }

}

