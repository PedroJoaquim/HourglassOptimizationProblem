package pt.ulisboa.tecnico.hourglass.optimization.solvers;

import pt.ulisboa.tecnico.hourglass.optimization.solvers.cplex.OptimizationSolverCPLEX;

/**
 * Created by Pedro Joaquim.
 */
public abstract class OptimizationSolver {

    protected static final int NUM_INSTANCE_TYPES = 5;
    protected static final int HIGH_HEAT_CAPACITY = 4;
    protected static final int LOW_HEAT_CAPACITY = 1;


    public static final int NONE = 0;
    public static final int INFO = 1;
    public static final int DEBUG = 2;


    private int verbosityLevel;

    protected int[] partitionSize;

    protected int[] capacityPerMachine;

    protected int[] costPerMachine;

    protected int[] numInstancesPerType;

    public OptimizationSolver setPartitionSize(int[] partitionSize) {
        this.partitionSize = partitionSize;
        return this;
    }

    public OptimizationSolver setCapacityPerMachine(int[] capacityPerMachine) {
        this.capacityPerMachine = capacityPerMachine;
        return this;
    }

    public OptimizationSolver setCostPerMachine(int[] costPerMachine) {
        this.costPerMachine = costPerMachine;
        return this;
    }

    public OptimizationSolver setNumInstancesPerType(int[] numInstancesPerType){
        this.numInstancesPerType = numInstancesPerType;
        return this;
    }

    public void solve() throws Exception {

        int numMachines = costPerMachine.length;
        int numPartitions = partitionSize.length;

        if(verbosityLevel == DEBUG ) {
            System.out.println("[INFO] CL MACHINES: 0 to " + (getFirstIndex(1) - 1));
            System.out.println("[INFO] CXL MACHINES: " + getFirstIndex(1) + " to " + (getFirstIndex(2) - 1));
            System.out.println("[INFO] C2XL MACHINES: " + getFirstIndex(2) + " to " + (getFirstIndex(3) - 1));
            System.out.println("[INFO] C4XL MACHINES: " + getFirstIndex(3) + " to " + (getFirstIndex(4) - 1));
            System.out.println("[INFO] C8XL MACHINES: " + getFirstIndex(4) + " to " + (numMachines - 1));

            for (int i = 0; i < numPartitions; i++) {
                System.out.println("[INFO] PARTITION " + i + " SIZE: " + partitionSize[i]);
            }
        }

        solve(numPartitions, numMachines);

    }

    protected abstract void solve(int numPartitions, int numMachines) throws Exception;


    protected int getFirstIndex(int machineTypeIndex) {

        int result = 0;

        for (int i = 0; i < machineTypeIndex; i++) {
            result += numInstancesPerType[i];
        }

        return result;
    }

    public int getVerbosityLevel() {
        return verbosityLevel;
    }
}
