package bitcoin;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import info.blockchain.api.APIException;
import info.blockchain.api.HttpClient;
import info.blockchain.api.blockexplorer.BlockExplorer;
import info.blockchain.api.blockexplorer.entity.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class App {

    static String PATH = "e:/blockchain_SEGWIT/";
    public static final long ONLINE_BLOCK_BEGIN_HEIGHT = 515311;//513181;//496560;//496333;//488746;//488147;//484307;//481815;
    public static final long OFFLINE_BLOCK_BEGIN_HEIGHT = 481815;
    public static final long OFFLINE_BLOCK_END_HEIGHT = 515311;
    public static Map<String, String> HttpClientParams = new HashMap();
    public static JsonParser jsonParser = new JsonParser();
    public static long onlineBlockCounter = ONLINE_BLOCK_BEGIN_HEIGHT;
    public static long offlineBlockCounter = OFFLINE_BLOCK_BEGIN_HEIGHT;
    // instantiate a block explorer
    BlockExplorer blockExplorer = new BlockExplorer();

    static {
        HttpClientParams.put("format", "json");
    }

    public void read(Boolean online){


        long maxHeight = 0;
        Map<String, Integer> dailyTotTxs = new HashMap();
        // get a block by hash and read the number of transactions in the block
        try {
            if (online){
                List<SimpleBlock> todaysBlocks = blockExplorer.getBlocks();
                maxHeight = todaysBlocks.get(todaysBlocks.size()-1).getHeight();
                for(long i = ONLINE_BLOCK_BEGIN_HEIGHT; i < maxHeight; ++i){
                    Block downloadedBlock = getBlockAtHeightHTTP(i);
                    if (downloadedBlock.isMainChain()){
                        onlineBlockCounter++;
                        // There you can see the progress
                        System.out.println("Processing Block #"+ onlineBlockCounter);
                    }
                }
            } else {
                for(long i = OFFLINE_BLOCK_BEGIN_HEIGHT; i < OFFLINE_BLOCK_END_HEIGHT; i += 25){
                    Block[] blockArray = getBlockAtHeightHDD(25);
                    // There you can see the progress
                    for(int j = 0; j < blockArray.length; ++j){
                        System.out.println("Processing Block #"+ (i+j));
                        if(blockArray[j] != null){
                            String day = new SimpleDateFormat("yyyy-MM-dd").format(timeToDate(blockArray[j].getTime()));
                            if (!dailyTotTxs.containsKey(day)) {
                                dailyTotTxs.put(day, blockArray[j].getTransactions().size());
                            } else {
                                dailyTotTxs.put(day,dailyTotTxs.get(day) + blockArray[j].getTransactions().size());
                            }
                        } else {
                            System.out.println("Block could not be loaded");
                        }
                    }
                }

            }

        } catch (APIException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (!online){
                try {
                    PrintWriter writer = new PrintWriter("stat.txt");
                    // Finally, let's print the results
                    for (String d : dailyTotTxs.keySet()) {               //napi tranzakciók kiíratása
                        writer.println(d + ";" + dailyTotTxs.get(d));
                    }
                    writer.close();
                } catch (Exception fileException) {
                    fileException.printStackTrace();
                }
            }
        }

    }

    public static Block getBlockAtHeightHTTP(long height) throws APIException, IOException {
        String response = HttpClient.getInstance().get("block-height/" + height, HttpClientParams);
        JsonObject blocksJson = jsonParser.parse(response).getAsJsonObject();
        Iterator var6 = blocksJson.get("blocks").getAsJsonArray().iterator();
        JsonObject output = ((JsonElement)var6.next()).getAsJsonObject();
        String toSerialize = output.toString();
        PrintWriter pw = new PrintWriter("block" + onlineBlockCounter + ".txt");
        pw.println(toSerialize);
        pw.close();
        return new Block(output);
    }

    public static Block getBlockAtHeightHDD(long height) throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(PATH  + (int)(offlineBlockCounter / 1000) + "/block" + offlineBlockCounter + ".txt" ));
        JsonObject rebuiltObject = (JsonObject)jsonParser.parse(in.readLine());
        in.close();
        return new Block(rebuiltObject);
    }

    public static Block[] getBlockAtHeightHDD(int howMany) throws IOException {
        Block[] outputs = new Block[howMany];
        ParserThread[] threads = new ParserThread[howMany];
        int threshold = howMany;
        for(int i = 0; i < howMany && offlineBlockCounter < OFFLINE_BLOCK_END_HEIGHT; ++i, ++offlineBlockCounter){
            threads[i] = new ParserThread(offlineBlockCounter, PATH);
            threads[i].start();
            threshold = i+1;
        }
        int done_sum = 0;
        System.out.println("while elott");
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
        System.out.println("while után");
        return outputs;
    }


    public Date timeToDate(long seconds) {
        return new Date(seconds * 1000L);
    }


    public static void main(String[] args) {
        App br = new App();
        br.read(false);
    }

}

