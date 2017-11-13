package pt.ulisboa.tecnico.hourglass.optimization.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

/**
 * Created by Pedro Joaquim.
 */
public class SpotInstancesPriceReader {

    private static final String CL_PATH = "D:\\GitHub\\HourglassOptimizationProblem\\spot-prices-history\\c4-large-1month-us-east-1b.json";
    private static final String CXL_PATH = "D:\\GitHub\\HourglassOptimizationProblem\\spot-prices-history\\c4-xlarge-1month-us-east-1b.json";
    private static final String C2XL_PATH = "D:\\GitHub\\HourglassOptimizationProblem\\spot-prices-history\\c4-2xlarge-1month-us-east-1b.json";
    private static final String C4XL_PATH = "D:\\GitHub\\HourglassOptimizationProblem\\spot-prices-history\\c4-4xlarge-1month-us-east-1b.json";
    private static final String C8XL_PATH = "D:\\GitHub\\HourglassOptimizationProblem\\spot-prices-history\\c4-8xlarge-1month-us-east-1b.json";

    private static String TIMESTAMP_ATTR = "Timestamp";
    private static String PRICE_ATTR = "SpotPrice";
    private static String MAIN_JSON_ATTR = "SpotPriceHistory";


    public static List<DataPoint> readCLDatapoints(){
        return parseJSON(new File(CL_PATH));
    }

    public static List<DataPoint> readCXLDatapoints(){
        return parseJSON(new File(CXL_PATH));
    }

    public static List<DataPoint> readC2XLDatapoints(){
        return parseJSON(new File(C2XL_PATH));
    }

    public static List<DataPoint> readC4XLDatapoints(){
        return parseJSON(new File(C4XL_PATH));
    }

    public static List<DataPoint> readC8XLDatapoints(){
        return parseJSON(new File(C8XL_PATH));
    }

    private static List<DataPoint> parseJSON(File jsonFile){

        List<DataPoint> result = new ArrayList<DataPoint>();

        try {

            String jsonContent = new Scanner(jsonFile).useDelimiter("\\Z").next();

            JSONArray dataPointsArray = (JSONArray)  new JSONObject(jsonContent).get(MAIN_JSON_ATTR);

            for (int i = 0 ; i < dataPointsArray.length(); i++) {

                JSONObject obj = dataPointsArray.getJSONObject(i);

                long timestamp = getTimeStamp(obj.getString(TIMESTAMP_ATTR));
                double price = obj.getDouble(PRICE_ATTR);

                result.add(new DataPoint(timestamp, price));
            }

        } catch (FileNotFoundException | ParseException e) {
            e.printStackTrace();
        }

        return result;
    }

    private static long getTimeStamp(String timestampStr) throws ParseException {

        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        Date date = formatter.parse(timestampStr);

        return date.getTime();
    }

}
