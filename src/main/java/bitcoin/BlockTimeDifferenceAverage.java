package bitcoin;

import java.text.SimpleDateFormat;

public class BlockTimeDifferenceAverage {
    private long lastBlockTime;
    private String day = null;
    private int blockCounter = 0;
    private long differenceSum = 0;


    public BlockTimeDifferenceAverage(){
    }

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

    public Double makeAverage(){
        return differenceSum/(double)blockCounter;
    }
}
