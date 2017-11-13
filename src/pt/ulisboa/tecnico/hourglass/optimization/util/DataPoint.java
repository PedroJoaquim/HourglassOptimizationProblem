package pt.ulisboa.tecnico.hourglass.optimization.util;

/**
 * Created by Pedro Joaquim.
 */
public class DataPoint {

    private long timestamp;

    private double price;

    public DataPoint(long timestamp, double price) {
        this.timestamp = timestamp;
        this.price = price;
    }


    public long getTimestamp() {
        return timestamp;
    }

    public double getPriceDouble() {
        return price;
    }

    public int getPriceInt(){
        return  (int) (price * 10000);
    }
}
