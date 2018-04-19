package bitcoin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import info.blockchain.api.blockexplorer.entity.Block;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class ParserThread extends Thread {
    private String toParse = null;
    private String PATH = null;
    private long offlineBlockCounter;

    public Block blk = null;
    public boolean done = false;

    public ParserThread(long offlineBlockCounter, String PATH) { this.offlineBlockCounter = offlineBlockCounter; this.PATH = PATH;}

    public void run() {
        try {
            BufferedReader inputFile = new BufferedReader(new FileReader(PATH  + (int)(offlineBlockCounter / 1000) + "/block" + offlineBlockCounter + ".txt" ));
            toParse = inputFile.readLine();
            inputFile.close();
            if(toParse == null){
                done = true;
            }
        } catch (FileNotFoundException e) {
            //e.printStackTrace();
            done = true;
        } catch (IOException e) {
            //e.printStackTrace();
            done = true;
        }
        if(toParse != null){
            JsonParser jsonParser = new JsonParser();
            blk = new Block(((JsonObject)jsonParser.parse(toParse)));
            done = true;
        }
    }
}
