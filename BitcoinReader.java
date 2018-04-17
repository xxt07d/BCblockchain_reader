package com.company;

import java.io.File;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bitcoinj.core.*;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.utils.BlockFileLoader;


public class BitcoinReader {

    public static final long BLOCK_BEGIN_HEIGHT = 0;

    // Location of block files.
    static String PATH = "d:/bitcoin_blockchain/blocks/";

    public void read() {

            // kezdeti setup
            NetworkParameters np = new MainNetParams();
            Context.getOrCreate(MainNetParams.get());

            //ez tölti be
            BlockFileLoader loader = new BlockFileLoader(np,buildList());

            //napi tranzakciók
            Map<String, Integer> dailyTotalTransactions = new HashMap<>();

            long blockCounter = BLOCK_BEGIN_HEIGHT;


        try{
            for (Block block : loader) {

                blockCounter++;
                System.out.println("Analysing block "+blockCounter);
                String day = new SimpleDateFormat("yyyy-MM-dd").format(block.getTime());
                if (!dailyTotalTransactions.containsKey(day)) {
                    dailyTotalTransactions.put(day, 0);
                }
                for ( Transaction tx: block.getTransactions() ) {
                    dailyTotalTransactions.put(day,dailyTotalTransactions.get(day)+1);
                }
                //System.out.println("Debugging");
                //negativearrayexception a 481815 blokknál :ASD:ASD:S

            }


        } catch (Exception e){
            e.printStackTrace();
        } finally {
            try {
                PrintWriter writer = new PrintWriter("stat.txt");
                for ( String d: dailyTotalTransactions.keySet()) {
                    writer.println(d+";"+dailyTotalTransactions.get(d));
                }
                writer.close();
            } catch (Exception fileException){
                fileException.printStackTrace();
            }
        }

    }


    //fájllistát csinál
    private List<File> buildList() {
        List<File> list = new LinkedList<File>();
        for (int i = 0; true; i++) {
            File file = new File(PATH + String.format(Locale.US, "blk%05d.dat", i));
            if (!file.exists())
                break;//ha elfogyott
            list.add(file);
        }
        return list;
    }


    public static void main(String[] args) {
        BitcoinReader br = new BitcoinReader();
        br.read();
    }

}