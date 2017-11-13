package pt.ulisboa.tecnico.hourglass.optimization;

import pt.ulisboa.tecnico.hourglass.optimization.solvers.cplex.OptimizationSolverCPLEX;
import pt.ulisboa.tecnico.hourglass.optimization.util.DataPoint;
import pt.ulisboa.tecnico.hourglass.optimization.writers.lp.LPFileGenerator;
import pt.ulisboa.tecnico.hourglass.optimization.writers.opb.OPBFileGenerator;
import pt.ulisboa.tecnico.hourglass.optimization.util.SpotInstancesPriceReader;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Created by Pedro Joaquim.
 */
public class OptimizationPerformanceTest{

        private int[] MEM_FACTOR_PER_INSTANCE_TYPE = new int[]{1,2,4,8,16};

        private static final int[] NUM_PARTITIONS = new int[]{2000};

        private static final int HOT_PARTITION_SIZE = 4;

        private static final int COLD_PARTITION_SIZE = 1;

        public static void main(String[] args) throws Exception {
            OptimizationPerformanceTest app = new OptimizationPerformanceTest();
            app.start();
        }

        private void start() throws Exception {

            for (int numPartitions : NUM_PARTITIONS) {

                System.out.println("");
                System.out.println("[INFO] SOLVING FOR " + numPartitions + " PARTITIONS");

                int[] partitionsSize = createPartitionsSizeArray(numPartitions);

                int[][] costAndCapacity = createCostPerMachineAndCapacityPerMachine(numPartitions);

                OptimizationSolverCPLEX cplexSolver = new OptimizationSolverCPLEX();

                cplexSolver.setPartitionSize(partitionsSize)
                        .setCostPerMachine(costAndCapacity[0])
                        .setCapacityPerMachine(costAndCapacity[1])
                        .setNumInstancesPerType(costAndCapacity[2]);

                long start = System.currentTimeMillis();
                cplexSolver.solve();
                long end = System.currentTimeMillis();

                new OPBFileGenerator().createFile(partitionsSize, costAndCapacity[0], costAndCapacity[1], cplexSolver.getZeroVars());
                new LPFileGenerator().createFile(partitionsSize, costAndCapacity[0], costAndCapacity[1], cplexSolver.getZeroVars());

                System.out.println("[INFO] SOLVE TIME = " + (end - start) + " MILLIS");
            }
        }

        private int[][] createCostPerMachineAndCapacityPerMachine(int numPartitions) {

            int[][] result = new int[3][];

            int[] numMachinesPerType = calcNumMachinesPerType(numPartitions);

            int totalNumMachines = 0;

            int[] costPerInstanceType = readRandomCostPerMachine();

            for (int aNumMachinesPerType : numMachinesPerType) {
                totalNumMachines += aNumMachinesPerType;
            }

            int[] resultCostPerMachine = new int[totalNumMachines];
            int[] resultCapacityPerMachine = new int[totalNumMachines];

            int prevIndex = 0;

            for (int i = 0; i < 5; i++) {

                int price = costPerInstanceType[i];
                int capacity = MEM_FACTOR_PER_INSTANCE_TYPE[i];
                int numMachinesForType = numMachinesPerType[i];

                Arrays.fill(resultCostPerMachine, prevIndex, prevIndex + numMachinesForType, price);
                Arrays.fill(resultCapacityPerMachine, prevIndex, prevIndex + numMachinesForType, capacity);

                prevIndex += numMachinesForType;
            }


            result[0] = resultCostPerMachine;
            result[1] = resultCapacityPerMachine;
            result[2] = numMachinesPerType;

            return result;
        }

        private int[] readRandomCostPerMachine() {

            int[] result = new int[5];

            List<DataPoint> clDatapoints = SpotInstancesPriceReader.readCLDatapoints();
            List<DataPoint> cxlDatapoints = SpotInstancesPriceReader.readCXLDatapoints();
            List<DataPoint> c2xlDatapoints = SpotInstancesPriceReader.readC2XLDatapoints();
            List<DataPoint> c4xlDatapoints = SpotInstancesPriceReader.readC4XLDatapoints();
            List<DataPoint> c8xlDatapoints = SpotInstancesPriceReader.readC8XLDatapoints();

            Random rnd = new Random();

            int randomIndex = rnd.nextInt(clDatapoints.size());

            result[0] = clDatapoints.get(Math.min(clDatapoints.size() - 1, randomIndex)).getPriceInt();
            result[1] = cxlDatapoints.get(Math.min(cxlDatapoints.size() - 1, randomIndex)).getPriceInt();
            result[2] = c2xlDatapoints.get(Math.min(c2xlDatapoints.size() - 1, randomIndex)).getPriceInt();
            result[3] = c4xlDatapoints.get(Math.min(c4xlDatapoints.size() - 1, randomIndex)).getPriceInt();
            result[4] = c8xlDatapoints.get(Math.min(c8xlDatapoints.size() - 1, randomIndex)).getPriceInt();


            System.out.println("[INFO] COST CL: " + result[0]);
            System.out.println("[INFO] COST CXL: " + result[1]);
            System.out.println("[INFO] COST C2XL: " + result[2]);
            System.out.println("[INFO] COST C4XL: " + result[3]);
            System.out.println("[INFO] COST C8XL: " + result[4]);

            return result;
        }

        private int[] calcNumMachinesPerType(int numPartitions) {

            int numHotPartitions = numPartitions / 2;
            int numColdPartitions = numPartitions - numHotPartitions;

            int[] result = new int[5];

            int capacity, numMachinesForHot, numMachinesForCold;

            for (int i = 0; i < 5; i++) {

                capacity = MEM_FACTOR_PER_INSTANCE_TYPE[i];

                if(capacity >= HOT_PARTITION_SIZE){
                    numMachinesForHot = numHotPartitions / (capacity / HOT_PARTITION_SIZE);
                }
                else {
                    numMachinesForHot = 0;
                }

                numMachinesForCold = numColdPartitions / capacity;

                result[i] = numMachinesForHot + numMachinesForCold + 2;
            }

            return result;
        }

        private int[] createPartitionsSizeArray(int numPartitions) {

            int[] result = new int[numPartitions];

            int midIndex = (numPartitions / 2);

            Arrays.fill(result, 0, midIndex, HOT_PARTITION_SIZE);
            Arrays.fill(result, midIndex, numPartitions, COLD_PARTITION_SIZE);

            return result;
        }
}
