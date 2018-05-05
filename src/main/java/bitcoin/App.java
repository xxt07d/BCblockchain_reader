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
    private static final long ONLINE_BLOCK_BEGIN_HEIGHT = 519172;
    private static final long OFFLINE_BLOCK_BEGIN_HEIGHT = 481815;
    private static final long OFFLINE_BLOCK_END_HEIGHT = 519172;
    private static final long OFFLINE_BLOCK_STEP = 13;

    // hálózati paraméterek, feldolgozáshoz szükséges objektumok
    private static Map<String, String> HttpClientParams = new HashMap<>();
    private static JsonParser jsonParser = new JsonParser();

    // online és offline mód kezdeti magasságára beállítjuk a blokkszámlálót
    private static long onlineBlockCounter = ONLINE_BLOCK_BEGIN_HEIGHT;
    private static long offlineBlockCounter = OFFLINE_BLOCK_BEGIN_HEIGHT;

    // A statisztikai adatokat kiválasztó flag-ek
    private static boolean COUNT_DAILY_TRANSACTIONS = false;
    private static boolean SEARCH_DAILY_MAXIMUM_OUTPUT_TRANSACTION = false;
    private static boolean SUM_DAILY_BITCOIN_OUTPUTS = false;
    private static boolean GET_DAILY_MAXIMUM_DIFFICULTY = false;
    private static boolean CALCULATE_AVERAGE_TIME_BETWEEN_BLOCKS = false;
    private static boolean SUMMARIZE_DAILY_BITCOIN_PRODUCTION = false;
    private static boolean SUMMARIZE_DAILY_BLOCK_PRODUCTION = true;

    // Map objektumok deklarációja a különböző statisztikákhoz, ezekben lévő adatokat írjuk ki
    private static Map<String, Object> dailyTotTxs = null;
    private static Map<String, Object> dailyMaxTxs = null;
    private static Map<String, Object> dailyBitcoinOutputs = null;
    private static Map<String, Object> dailyMaxDifficulties = null;
    private static Map<String, Object> dailyAverageDifferences = null;
    private static Map<String, Object> dailyBitcoinProductions = null;
    private static Map<String, Object> dailyBlockProductions = null;

    // Blockchain Data Api blockparserének inicializálása
    private static BlockExplorer blockExplorer = new BlockExplorer();

    // A Blockchain Data API és a bitcoinJ API által lehetővé tett függvények halmazai
    private static Map<String, BlockFunctionInterface> BlockchainDataAPIFuncitons = new HashMap<>();
    private static Map<String, BlockFunctionInterface> bitcoinJAPIFuncitons = new HashMap<>();

    /*
      Az osztály használatakor a HTTP paramétereket beállítja.
      Ha valamelyik statisztikai adat flag-je true, akkor annak a Map objektumját inicializálja.
      Emellett minden flag-hez tartozhat egy-egy API specifikus függvény. Ezeket lambda expression-ök segítségével
      belerakja a függvény Map-ekbe.
     */
    static {
        HttpClientParams.put("format", "json");
        if(COUNT_DAILY_TRANSACTIONS){
            dailyTotTxs = new HashMap<>();
            BlockchainDataAPIFuncitons.put("COUNT_DAILY_TRANSACTIONS", (Object... o) -> getBlockTransactionAmount((Block)o[0]));
            bitcoinJAPIFuncitons.put("COUNT_DAILY_TRANSACTIONS", (Object... o) -> BitcoinReader.getBlockTransactionAmount((org.bitcoinj.core.Block)o[0]));
        }
        if(SEARCH_DAILY_MAXIMUM_OUTPUT_TRANSACTION){
            dailyMaxTxs = new HashMap<>();
            BlockchainDataAPIFuncitons.put("SEARCH_DAILY_MAXIMUM_OUTPUT_TRANSACTION", (Object... o) -> getBlockMaxValuePair((Block)o[0]));
            bitcoinJAPIFuncitons.put("SEARCH_DAILY_MAXIMUM_OUTPUT_TRANSACTION", (Object... o) -> BitcoinReader.getBlockMaxValuePair((org.bitcoinj.core.Block)o[0]));
        }
        if(SUM_DAILY_BITCOIN_OUTPUTS){
            dailyBitcoinOutputs = new HashMap<>();
            BlockchainDataAPIFuncitons.put("SUM_DAILY_BITCOIN_OUTPUTS", (Object... o) -> getBlockBitcoinOutputSum((Block)o[0]));
            bitcoinJAPIFuncitons.put("SUM_DAILY_BITCOIN_OUTPUTS", (Object... o) -> BitcoinReader.getBlockBitcoinOutputSum((org.bitcoinj.core.Block)o[0]));
        }
        if(GET_DAILY_MAXIMUM_DIFFICULTY){
            dailyMaxDifficulties = new HashMap<>();
            BlockchainDataAPIFuncitons.put("GET_DAILY_MAXIMUM_DIFFICULTY", (Object... o) -> getBlockDifficulty((Block)o[0]));
            bitcoinJAPIFuncitons.put("GET_DAILY_MAXIMUM_DIFFICULTY", (Object... o) -> BitcoinReader.getBlockDifficulty((org.bitcoinj.core.Block)o[0]));
        }
        if(CALCULATE_AVERAGE_TIME_BETWEEN_BLOCKS){
            dailyAverageDifferences = new HashMap<>();
            BlockchainDataAPIFuncitons.put("CALCULATE_AVERAGE_TIME_BETWEEN_BLOCKS", (Object... o) -> calculateAverageTimeDifference((Block)o[0], (BlockTimeDifferenceAverage)o[1]));
            bitcoinJAPIFuncitons.put("CALCULATE_AVERAGE_TIME_BETWEEN_BLOCKS", (Object... o) -> BitcoinReader.calculateAverageTimeDifference((org.bitcoinj.core.Block)o[0], (BlockTimeDifferenceAverage)o[1]));
        }
        if(SUMMARIZE_DAILY_BITCOIN_PRODUCTION){
            dailyBitcoinProductions = new HashMap<>();
            BlockchainDataAPIFuncitons.put("SUMMARIZE_DAILY_BITCOIN_PRODUCTION", (Object... o) -> getBlockNewBitCoins((Block)o[0]));
            bitcoinJAPIFuncitons.put("SUMMARIZE_DAILY_BITCOIN_PRODUCTION", (Object... o) -> BitcoinReader.getBlockNewBitCoins((org.bitcoinj.core.Block)o[0]));
        }
        if(SUMMARIZE_DAILY_BLOCK_PRODUCTION){
            dailyBlockProductions = new HashMap<>();
            BlockchainDataAPIFuncitons.put("SUMMARIZE_DAILY_BLOCK_PRODUCTION", (Object... o) -> getBlockDay((Block)o[0]));
            bitcoinJAPIFuncitons.put("SUMMARIZE_DAILY_BLOCK_PRODUCTION", (Object... o) -> BitcoinReader.getBlockDay((org.bitcoinj.core.Block)o[0]));
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
            System.out.println("End of Blockchain Data API part");
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
     * @param seconds 1970-01-01 óta eltelt idő másodpercben
     * @return a beadott idő Date formátumban
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
     * Összegzi, hogy az adott blokkban mennyi az összes kimeneti Bitcoin
     * @param block A blokk, aminek számoljuk a Bitcoin kimenetét
     * @return A blokk keletkezési napja és a bitcoin kimenet egy MapPair-ben
     */
    private static MapPair<String, Object> getBlockBitcoinOutputSum(Block block) {
        String day = new SimpleDateFormat("yyyy-MM-dd").format(timeToDate(block.getTime()));
        long sum = 0;
        if(block.getTransactions() != null){
            for ( Transaction tx: block.getTransactions() ) {
                for(Output output : tx.getOutputs()){
                    sum += output.getValue();
                }
            }
        }

        return new MapPair<>(day, sum);
    }

    /**
     * (Szükség van egy függvényobjektumra, hogy generikus maradhasson a program, és mivel egy adott blokk függvényét
     * nem tudjuk sajnos lekérni, mert nem statikus függvény, így ezt használjuk helyette.)
     * @param block A vizsgált blokk
     * @return A blokk keletkezési napja és a nehézsége.
     */
    private static MapPair<String, Object> getBlockDifficulty(Block block){
        return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(timeToDate(block.getTime())), block.getBits());
    }

    /**
     * Egy adott blokktól szerzi meg a keletkezési idejét, és adja tovább
     * @param block A vizsgált blokk
     * @param blockTimeDifferenceAverage Az objektum, ami az aktuális átlagot számolja
     * @return A blokk keletkezési ideje, és az átlagot számoló objektum.
     */
    private static MapPair<String, Object> calculateAverageTimeDifference(Block block, BlockTimeDifferenceAverage blockTimeDifferenceAverage){
        if(blockTimeDifferenceAverage != null){
            blockTimeDifferenceAverage.addBlockTime(block.getTime() * 1000);
            return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(timeToDate(block.getTime())), blockTimeDifferenceAverage);
        } else {
            return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(timeToDate(block.getTime())), null);
        }
    }

    /**
     * Sajnos az API nem tartja sorrendben a tranzakciókat a blokkban, így nekünk kell megkeresni a generáló tranzakciót.
     * Ez minden esetben a legkésőbbi tranzakciót fogja jelenteni.
     * @param block A vizsgált blokk
     * @return A blokk keletkezési ideje, és a keletkezett bitcoinok.
     */
    private static MapPair<String, Object> getBlockNewBitCoins(Block block){
         Transaction latest = null;
         for(Transaction t : block.getTransactions()){
             if(latest == null || latest.getTime() < t.getTime()){
                 latest = t;
             }
         }
         Double newBitcoin = 0.0;
         if (latest != null) {
             if((int)(latest.getOutputs().get(0).getValue()/1250000000) == 1 ){
                 newBitcoin += 12.5; // 2018.05.02, a jelenlegi blokkok ehhez az API-hoz mind 12.5 BitCoint "termelnek"
             } else{
                 newBitcoin += 6.25; // utána meg évekig még ez is jó lesz...
             }
         }
        return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(timeToDate(block.getTime())), newBitcoin);
    }

    /**
     * Sztring formában visszatér a blokk keletkezési idejével
     * @param block A vizsgált blokk
     * @return MapPair, de csak a keletkezési nap van benne.
     */
    private static MapPair<String, Object> getBlockDay(Block block){
        return new MapPair<>(new SimpleDateFormat("yyyy-MM-dd").format(timeToDate(block.getTime())), null);
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
            if(!dailyMaxTxs.containsKey(blockHighestOutput.getKey()) || (dailyMaxTxs.containsKey(blockHighestOutput.getKey()) && (Long) dailyMaxTxs.get(blockHighestOutput.getKey()) < (Long)blockHighestOutput.getValue())){
                dailyMaxTxs.put(blockHighestOutput.getKey(), blockHighestOutput.getValue());
            }
        }
        if(SUM_DAILY_BITCOIN_OUTPUTS){
            MapPair<String, Object> blockBitcoinOutputSum = functionsToUse.get("SUM_DAILY_BITCOIN_OUTPUTS").function(schroedingerBlock);
            if (!dailyBitcoinOutputs.containsKey(blockBitcoinOutputSum.getKey())) {
                dailyBitcoinOutputs.put(blockBitcoinOutputSum.getKey(), blockBitcoinOutputSum.getValue());
            } else {
                dailyBitcoinOutputs.put(blockBitcoinOutputSum.getKey(), (Long) dailyBitcoinOutputs.get(blockBitcoinOutputSum.getKey()) + (Long)blockBitcoinOutputSum.getValue());
            }
        }
        if(GET_DAILY_MAXIMUM_DIFFICULTY){
            MapPair<String, Object> blockDifficulty = functionsToUse.get("GET_DAILY_MAXIMUM_DIFFICULTY").function(schroedingerBlock);
            if(!dailyMaxDifficulties.containsKey(blockDifficulty.getKey()) || (dailyMaxDifficulties.containsKey(blockDifficulty.getKey()) && (Long) dailyMaxDifficulties.get(blockDifficulty.getKey()) < (Long)blockDifficulty.getValue())){
                dailyMaxDifficulties.put(blockDifficulty.getKey(), blockDifficulty.getValue());
            }
        }
        if(CALCULATE_AVERAGE_TIME_BETWEEN_BLOCKS){
            /*Két paramétert vár a függvény*/
            /*Kicsit más, mint a többi, abból a szempontból, hogy az átlagot egy külön objektummal kell kezelnünk, és ezt is át kell adni a függvénynek */
            /*Minden nap elkezdésekor létre kell hozni ebből az objektumból egyet, amelyet a többi alkalommal használjuk*/
            String date = functionsToUse.get("CALCULATE_AVERAGE_TIME_BETWEEN_BLOCKS").function(schroedingerBlock, null).getKey();
            if(!dailyAverageDifferences.containsKey(date)){
                MapPair<String, Object> newerAverage = functionsToUse.get("CALCULATE_AVERAGE_TIME_BETWEEN_BLOCKS").function(schroedingerBlock, new BlockTimeDifferenceAverage());
                dailyAverageDifferences.put(newerAverage.getKey(), newerAverage.getValue());
            } else {
                MapPair<String, Object> newerAverage = functionsToUse.get("CALCULATE_AVERAGE_TIME_BETWEEN_BLOCKS").function(schroedingerBlock, dailyAverageDifferences.get(date));
                dailyAverageDifferences.put(newerAverage.getKey(), newerAverage.getValue());
            }
        }
        if(SUMMARIZE_DAILY_BITCOIN_PRODUCTION){
            MapPair<String, Object> blockBitcoinProduction = functionsToUse.get("SUMMARIZE_DAILY_BITCOIN_PRODUCTION").function(schroedingerBlock);
            if(!dailyBitcoinProductions.containsKey(blockBitcoinProduction.getKey())){
                dailyBitcoinProductions.put(blockBitcoinProduction.getKey(), blockBitcoinProduction.getValue());
            } else {
                dailyBitcoinProductions.put(blockBitcoinProduction.getKey(), (Double)dailyBitcoinProductions.get(blockBitcoinProduction.getKey()) + (Double)blockBitcoinProduction.getValue());
            }
        }
        if(SUMMARIZE_DAILY_BLOCK_PRODUCTION){
            MapPair<String, Object> blockProduction = functionsToUse.get("SUMMARIZE_DAILY_BLOCK_PRODUCTION").function(schroedingerBlock);
            if(!dailyBlockProductions.containsKey(blockProduction.getKey())){
                dailyBlockProductions.put(blockProduction.getKey(), 1);
            } else {
                dailyBlockProductions.put(blockProduction.getKey(), (Integer)dailyBlockProductions.get(blockProduction.getKey()) + 1);
            }
        }
    }


    /**
     * Adott statisztikai flagek meghatározzák, hogy melyik fájlokat kell létrehozni
     * Ezeknek a létrehozása megtörténik, valamint kiírja az odaillő Map adatait is.
     * @throws FileNotFoundException Ha valami okból nem sikerülne megnyitni valamelyik fájlt, akkor dob egy ilyet
     */
    private static void writeToFiles() throws FileNotFoundException{
        if(COUNT_DAILY_TRANSACTIONS){
            PrintWriter writer = new PrintWriter("napi_tx_sum.txt");
            for (String d : dailyTotTxs.keySet()) {
                writer.println(d + ";" + dailyTotTxs.get(d));
            }
            writer.close();
        }
        if(SEARCH_DAILY_MAXIMUM_OUTPUT_TRANSACTION){
            PrintWriter writer = new PrintWriter("napi_max_TX.txt");
            for (String d : dailyMaxTxs.keySet()) {
                writer.println(d + ";" + dailyMaxTxs.get(d));
            }
            writer.close();
        }
        if(SUM_DAILY_BITCOIN_OUTPUTS){
            PrintWriter writer = new PrintWriter("napi_bitcoin_output_sum.txt");
            for (String d : dailyBitcoinOutputs.keySet()) {
                writer.println(d + ";" + dailyBitcoinOutputs.get(d));
            }
            writer.close();
        }
        if(GET_DAILY_MAXIMUM_DIFFICULTY){
            PrintWriter writer = new PrintWriter("napi_maximum_nehézség.txt");
            for (String d : dailyMaxDifficulties.keySet()) {
                writer.println(d + ";" + dailyMaxDifficulties.get(d));
            }
            writer.close();
        }
        if(CALCULATE_AVERAGE_TIME_BETWEEN_BLOCKS){
            PrintWriter writer = new PrintWriter("napi_blokkok_között_eltelt_átlagos_idő.txt");
            for (String d : dailyAverageDifferences.keySet()) {
                writer.println(d + ";" + ((BlockTimeDifferenceAverage) dailyAverageDifferences.get(d)).makeAverage());
            }
            writer.close();
        }
        if(SUMMARIZE_DAILY_BITCOIN_PRODUCTION){
            PrintWriter writer = new PrintWriter("napi_megtermelt_új_bitcoin.txt");
            for (String d : dailyBitcoinProductions.keySet()) {
                writer.println(d + ";" + dailyBitcoinProductions.get(d));
            }
            writer.close();
        }
        if(SUMMARIZE_DAILY_BLOCK_PRODUCTION){
            PrintWriter writer = new PrintWriter("napi_megtermelt_új_blokk.txt");
            for (String d : dailyBlockProductions.keySet()) {
                writer.println(d + ";" + dailyBlockProductions.get(d));
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
        try {
            brt.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            writeToFiles();
        } catch (Exception fileException) {
            fileException.printStackTrace();
        }
    }

}

