package pt.ulisboa.tecnico.hourglass.optimization.solvers.cplex;

import ilog.concert.*;
import ilog.cplex.IloCplex;
import pt.ulisboa.tecnico.hourglass.optimization.solvers.OptimizationSolver;
import pt.ulisboa.tecnico.hourglass.optimization.solvers.util.Machine;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
/**
 * Created by Pedro Joaquim.
 */

public class OptimizationSolverCPLEX extends OptimizationSolver {

    private IloCplex cplex;

    private List<int[]> zeroVars;

    public OptimizationSolverCPLEX() {
        this.zeroVars = new ArrayList<>();
    }

    public void solve(int numPartitions, int numMachines) throws IloException {

        this.cplex = new IloCplex();


        IloIntVar[][] assignmentMatrixPerPartition = new IloIntVar[numPartitions][];
        IloIntVar[][] assignmentMatrixPerMachine = new IloIntVar[numMachines][numPartitions];

        IloIntVar[] machineUsageVars = cplex.boolVarArray(numMachines);

        for (int p = 0; p < numPartitions; p++) {

            assignmentMatrixPerPartition[p] = cplex.boolVarArray(numMachines);

            for (int m = 0; m < numMachines; m++) {
                assignmentMatrixPerMachine[m][p] = assignmentMatrixPerPartition[p][m];
            }
        }

        List<List<Machine<IloIntVar>>> assignmentVarsPerMachine = createMachineTypeList(assignmentMatrixPerMachine);
        addHotColdMachineRestrictions(cplex, assignmentVarsPerMachine, numPartitions);
        addSimilarMachineConstraint(cplex, assignmentVarsPerMachine, numPartitions);

        for (int p = 0; p < numPartitions; p++) {
            //constraint 2: every partition has to be assigned to one and only one machine

            cplex.addEq(cplex.sum(assignmentMatrixPerPartition[p]), 1);

            /*for (int m = 0; m < numMachines; m++) {
                //constraint 1 : if a partition is assigned to one machine then this machine has to be selected

                cplex.addLe(cplex.sum(assignmentMatrixPerPartition[p][m], cplex.negative(machineUsageVars[m])), 0);
            }*/
        }

        //constraint 3: a machine can only be assigned a set of partitions that do not outburst its capacity
        for (int m = 0; m < numMachines; m++) {
            cplex.add(cplex.or(cplex.le(cplex.sum(assignmentMatrixPerMachine[m]), 0.1), cplex.eq(machineUsageVars[m], 1)));

            cplex.addLe(cplex.scalProd(assignmentMatrixPerMachine[m], partitionSize), capacityPerMachine[m]);
        }


        //DEPLOYMENT COST MINIMIZATION
        cplex.addMinimize(cplex.scalProd(machineUsageVars, costPerMachine));

        cplex.setOut(null);

        cplex.solve();

        if(getVerbosityLevel() >= INFO) {


            System.out.println("**********************SOLUTION***********************");

            int cost = 0;

            for (int i = 0; i < numMachines; i++) {

                if (cplex.getValue(machineUsageVars[i]) == 1) {
                    cost += costPerMachine[i];
                    System.out.println("MACHINE " + i + " CAPACITY = " + capacityPerMachine[i] + " COST: " + costPerMachine[i]);
                }
            }


            for (int i = 0; i < numPartitions; i++) {

                IloIntVar[] perPartitionAssignment = assignmentMatrixPerPartition[i];

                for (int j = 0; j < numMachines; j++) {
                    if (cplex.getValue(perPartitionAssignment[j]) == 1) {
                        System.out.println("PARTITION  " + i + " ASSIGNED TO : " + j);
                    }
                }

            }

            System.out.println("[INFO] COST: " + cost);
        }
        else {
            int cost = 0;

            for (int i = 0; i < numMachines; i++) {
                if (cplex.getValue(machineUsageVars[i]) == 1) {
                    cost += costPerMachine[i];
                }
            }

            System.out.println("[INFO] COST: " + cost);


        }

        cplex.getStatus();

        cplex.end();


    }

    private void addSimilarMachineConstraint(IloCplex cplex, List<List<Machine<IloIntVar>>> assignmentVarsPerMachine, int numPartitions) throws IloException {

        for (int i = 0; i < assignmentVarsPerMachine.size(); i++) {

            List<Machine<IloIntVar>> targetMachine = assignmentVarsPerMachine.get(i);

            int targetMachineCapacity = targetMachine.get(0).getCapacity();

            for (int j = i + 1; j < assignmentVarsPerMachine.size(); j++) {

                List<Machine<IloIntVar>> comparisonMachines = assignmentVarsPerMachine.get(j);

                int comparisonMachineCapacity = comparisonMachines.get(0).getCapacity();

                int memFactor = targetMachineCapacity / comparisonMachineCapacity;

                int targetMachineStartingIndex = 0;

                if(comparisonMachineCapacity < HIGH_HEAT_CAPACITY && targetMachineCapacity >= HIGH_HEAT_CAPACITY){
                    targetMachineStartingIndex = calcStartingIndexForLowPartitions(targetMachine.size(), targetMachineCapacity, numPartitions);
                }

                for (int k = targetMachineStartingIndex; k < targetMachine.size(); k++) {

                    int targetComparisonMachineIndex = k * memFactor;

                    for (int l = 0; l < memFactor; l++) {
                        if(comparisonMachines.size() > targetComparisonMachineIndex + l){


                            Machine<IloIntVar> machine1 = targetMachine.get(k);
                            Machine<IloIntVar> machine2 = comparisonMachines.get(targetComparisonMachineIndex + l);

                            cplex.or(cplex.not(cplex.ge(cplex.sum(machine1.getAssignmentVarsPerPartition()), 1)), cplex.eq(cplex.sum(machine2.getAssignmentVarsPerPartition()), 0));
                            cplex.or(cplex.not(cplex.ge(cplex.sum(machine2.getAssignmentVarsPerPartition()), 1)), cplex.eq(cplex.sum(machine1.getAssignmentVarsPerPartition()), 0));
                        }
                    }
                }

            }
        }
    }

    private void addHotColdMachineRestrictions(IloCplex cplex, List<List<Machine<IloIntVar>>> assignmentVarsPerMachine, int numPartitions) throws IloException {

        int numHighHeatPartitions = numPartitions / 2;
        int numLowHeatPartitions = numPartitions - numHighHeatPartitions;

        List<IloIntVar> varsToZero = new ArrayList<>();

        for (int i = 0; i < 5; i++) {

            List<Machine<IloIntVar>> targetMachines = assignmentVarsPerMachine.get(i);

            if(i < 3) {

                int numHighHeatPartitionsPerMachine = targetMachines.get(0).getCapacity() / HIGH_HEAT_CAPACITY;
                int numLowHeatPartitionsPerMachine = targetMachines.get(0).getCapacity() / LOW_HEAT_CAPACITY;

                int numMachinesForHighHeat = numHighHeatPartitions / numHighHeatPartitionsPerMachine + (numHighHeatPartitions % numHighHeatPartitionsPerMachine == 0 ? 0 : 1);

                for (int j = 0; j < targetMachines.size(); j++) {

                    IloIntVar[] assignmentVarsPerPartition = targetMachines.get(j).getAssignmentVarsPerPartition();

                    if (j < numMachinesForHighHeat) { //machine for high heat partitions

                        int partitionsThatCanBeAssignedLow = j * numHighHeatPartitionsPerMachine;
                        int partitionsThatCanBeAssignedHigh = partitionsThatCanBeAssignedLow + (numHighHeatPartitionsPerMachine - 1);

                        for (int k = 0; k < numHighHeatPartitions; k++) {
                            if (k < partitionsThatCanBeAssignedLow || k > partitionsThatCanBeAssignedHigh) {
                                if(getVerbosityLevel() == DEBUG) System.out.println("[CONSTRAINT] NOT " + k + " ON MACHINE " + targetMachines.get(j).getId());
                                //todo cplex.addEq(assignmentVarsPerPartition[k], 0);
                                varsToZero.add(assignmentVarsPerPartition[k]);
                                addNewZeroVar(targetMachines.get(j).getId(), k);
                            }
                        }

                        /*restricting partitions with low that can be assigned to machines for high heat partitions*/

                        partitionsThatCanBeAssignedLow = j * numLowHeatPartitionsPerMachine + numHighHeatPartitions;
                        partitionsThatCanBeAssignedHigh = partitionsThatCanBeAssignedLow + (numLowHeatPartitionsPerMachine - 1);

                        for (int k = numHighHeatPartitions; k < assignmentVarsPerPartition.length; k++) {
                            if (k < partitionsThatCanBeAssignedLow || k > partitionsThatCanBeAssignedHigh) {
                                if(getVerbosityLevel() == DEBUG) System.out.println("[CONSTRAINT] NOT " + k + " ON MACHINE " + targetMachines.get(j).getId());
                                //todo cplex.addEq(assignmentVarsPerPartition[k], 0);
                                varsToZero.add(assignmentVarsPerPartition[k]);
                                addNewZeroVar(targetMachines.get(j).getId(), k);
                            }
                        }
                    } else { //machine for low heat partitions

                        for (int k = 0; k < numHighHeatPartitions; k++) {
                            if(getVerbosityLevel() == DEBUG) System.out.println("[CONSTRAINT] NOT " + k + " ON MACHINE " + targetMachines.get(j).getId());
                            //todo cplex.addEq(assignmentVarsPerPartition[k], 0);
                            varsToZero.add(assignmentVarsPerPartition[k]);
                            addNewZeroVar(targetMachines.get(j).getId(), k);
                        }

                        int partitionsThatCanBeAssignedLow = ((j - numMachinesForHighHeat ) * numLowHeatPartitionsPerMachine) + numHighHeatPartitions;
                        int partitionsThatCanBeAssignedHigh = partitionsThatCanBeAssignedLow + (numLowHeatPartitionsPerMachine - 1);

                        for (int k = numHighHeatPartitions; k < assignmentVarsPerPartition.length; k++) {
                            if (k < partitionsThatCanBeAssignedLow || k > partitionsThatCanBeAssignedHigh) {
                                if(getVerbosityLevel() == DEBUG) System.out.println("[CONSTRAINT] NOT " + k + " ON MACHINE " + targetMachines.get(j).getId());
                                //todo cplex.addEq(assignmentVarsPerPartition[k], 0);
                                varsToZero.add(assignmentVarsPerPartition[k]);
                                addNewZeroVar(targetMachines.get(j).getId(), k);
                            }
                        }
                    }
                }
            }
            else {

                int numLowHeatPartitionsPerMachine = targetMachines.get(0).getCapacity() / LOW_HEAT_CAPACITY;

                for (int j = 0; j < targetMachines.size(); j++) {

                    IloIntVar[] assignmentVarsPerPartition = targetMachines.get(j).getAssignmentVarsPerPartition();

                    for (int k = 0; k < numHighHeatPartitions; k++) {
                        if(getVerbosityLevel() == DEBUG) System.out.println("[CONSTRAINT] NOT " + k + " ON MACHINE " + targetMachines.get(j).getId());
                        //todo cplex.addEq(assignmentVarsPerPartition[k], 0);
                        varsToZero.add(assignmentVarsPerPartition[k]);
                        addNewZeroVar(targetMachines.get(j).getId(), k);
                    }

                    int partitionsThatCanBeAssignedLow = (j * numLowHeatPartitionsPerMachine) + numHighHeatPartitions;
                    int partitionsThatCanBeAssignedHigh = partitionsThatCanBeAssignedLow + (numLowHeatPartitionsPerMachine - 1);

                    for (int k = numHighHeatPartitions; k < assignmentVarsPerPartition.length; k++) {
                        if (k < partitionsThatCanBeAssignedLow || k > partitionsThatCanBeAssignedHigh) {
                            if(getVerbosityLevel() == DEBUG) System.out.println("[CONSTRAINT] NOT " + k + " ON MACHINE " + targetMachines.get(j).getId());
                            //todo cplex.addEq(assignmentVarsPerPartition[k], 0);
                            varsToZero.add(assignmentVarsPerPartition[k]);
                            addNewZeroVar(targetMachines.get(j).getId(), k);
                        }
                    }

                }

            }
        }

        IloIntVar[] varsToZeroArray = varsToZero.toArray(new IloIntVar[varsToZero.size()]);

        cplex.addEq(cplex.sum(varsToZeroArray), 0);
    }

    private void addNewZeroVar(int machineID, int partitionID) {
        this.zeroVars.add(new int[]{machineID, partitionID});
    }

    private List<List<Machine<IloIntVar>>> createMachineTypeList(IloIntVar[][] assignmentMatrixPerMachine) {

        List<Machine<IloIntVar>> clMachines = new ArrayList<>();
        List<Machine<IloIntVar>> cxlMachines = new ArrayList<>();
        List<Machine<IloIntVar>> c2xlMachines = new ArrayList<>();
        List<Machine<IloIntVar>> c4xlMachines = new ArrayList<>();
        List<Machine<IloIntVar>> c8xlMachines = new ArrayList<>();


        int firstIndexCL = getFirstIndex(0);
        int firstIndexCXL = getFirstIndex(1);
        int firstIndexC2XL = getFirstIndex(2);
        int firstIndexC4XL = getFirstIndex(3);
        int firstIndexC8XL = getFirstIndex(4);

        for (int i = firstIndexCL; i < firstIndexCXL; i++) {
            clMachines.add(new Machine<>(i, 1, assignmentMatrixPerMachine[i]));
        }

        for (int i = firstIndexCXL; i < firstIndexC2XL; i++) {
            cxlMachines.add(new Machine<>(i, 2, assignmentMatrixPerMachine[i]));
        }

        for (int i = firstIndexC2XL; i < firstIndexC4XL; i++) {
            c2xlMachines.add(new Machine<>(i, 4, assignmentMatrixPerMachine[i]));
        }

        for (int i = firstIndexC4XL; i < firstIndexC8XL; i++) {
            c4xlMachines.add(new Machine<>(i, 8, assignmentMatrixPerMachine[i]));
        }

        for (int i = firstIndexC8XL; i < assignmentMatrixPerMachine.length; i++) {
            c8xlMachines.add(new Machine<>(i, 16, assignmentMatrixPerMachine[i]));
        }

        List<List<Machine<IloIntVar>>> result = new ArrayList<>();
        result.add(c8xlMachines);
        result.add(c4xlMachines);
        result.add(c2xlMachines);
        result.add(cxlMachines);
        result.add(clMachines);

        return result;
    }

    private void addMachineUsageConstraints(IloCplex cplex, IloIntVar[] machineUsageVars, int numPartitions) throws IloException {

        int firstIndexCL = getFirstIndex(0);
        int firstIndexCXL = getFirstIndex(1);
        int firstIndexC2XL = getFirstIndex(2);
        int firstIndexC4XL = getFirstIndex(3);
        int firstIndexC8XL = getFirstIndex(4);

        List<IloIntVar> cLMachines = new ArrayList<>();

        cLMachines.addAll(Arrays.asList(machineUsageVars).subList(firstIndexCL, firstIndexCXL));

        List<IloIntVar> cXLMachines = new ArrayList<>();

        cXLMachines.addAll(Arrays.asList(machineUsageVars).subList(firstIndexCXL, firstIndexC2XL));

        List<IloIntVar> c2XLMachines = new ArrayList<>();

        c2XLMachines.addAll(Arrays.asList(machineUsageVars).subList(firstIndexC2XL, firstIndexC4XL));

        List<IloIntVar> c4XLMachines = new ArrayList<>();

        c4XLMachines.addAll(Arrays.asList(machineUsageVars).subList(firstIndexC4XL, firstIndexC8XL));

        List<IloIntVar> c8XLMachines = new ArrayList<>();

        c8XLMachines.addAll(Arrays.asList(machineUsageVars).subList(firstIndexC8XL, machineUsageVars.length));


        List<List<IloIntVar>> machinePerTypeArray = new ArrayList<>();
        machinePerTypeArray.add(c8XLMachines);
        machinePerTypeArray.add(c4XLMachines);
        machinePerTypeArray.add(c2XLMachines);
        machinePerTypeArray.add(cXLMachines);
        machinePerTypeArray.add(cLMachines);


        for (int i = 0; i < machinePerTypeArray.size(); i++) {

            List<IloIntVar> targetMachine = machinePerTypeArray.get(i);

            int targetMachineCapacity = (int) Math.pow(2, (4 - i));

            for (int j = i + 1; j < machinePerTypeArray.size(); j++) {

                int comparisonMachineCapacity = (int) Math.pow(2, (4 - j));

                List<IloIntVar> comparisonMachines = machinePerTypeArray.get(j);

                int memFactor = (int) Math.pow(2, (j - i));

                int targetMachineStartingIndex = 0;

                if(comparisonMachineCapacity < HIGH_HEAT_CAPACITY && targetMachineCapacity >= HIGH_HEAT_CAPACITY){
                    targetMachineStartingIndex = calcStartingIndexForLowPartitions(targetMachine.size(), targetMachineCapacity, numPartitions);
                }

                for (int k = targetMachineStartingIndex; k < targetMachine.size(); k++) {

                    int targetComparisonMachineIndex = k * memFactor;

                    for (int l = 0; l < memFactor; l++) {
                        if(comparisonMachines.size() > targetComparisonMachineIndex + l){
                            cplex.addLe(cplex.sum(targetMachine.get(k), comparisonMachines.get(targetComparisonMachineIndex + l)), 1);
                        }
                    }
                }

            }
        }
    }

    private int calcStartingIndexForLowPartitions(int numMachines, int machineCapacity,int numPartitions) {

        int numHighHeatPartitions = numPartitions / 2;

        int numHighHeatPartitionsPerMachine = machineCapacity / HIGH_HEAT_CAPACITY;

        return numHighHeatPartitions / numHighHeatPartitionsPerMachine;
    }

    private void addConsecutiveMachineUsageConstraints(IloCplex cplex, IloIntVar[] machineUsageVars) throws IloException {

        int lastIndex = 1;

        for (int i = 0; i < NUM_INSTANCE_TYPES; i++) {

            int numInstances = this.numInstancesPerType[i];

            for (int j = lastIndex; j < lastIndex + numInstances - 1; j++) {
                cplex.addLe(cplex.sum(machineUsageVars[j], cplex.negative(machineUsageVars[j-1])), 0);
            }

            lastIndex += numInstances;
        }

    }

    public List<int[]> getZeroVars() {
        return zeroVars;
    }

    /* public void solveInt() throws IloException {

        IloOplFactory.setDebugMode(false);
        IloOplFactory oplF = new IloOplFactory();
        IloCP cp = oplF.createCP();

        int numMachines = costPerMachine.length;
        int numPartitions = 0;

        for (int i = 0; i < partitionSize.length; i++) {
            numPartitions += partitionSize[i];
        }

        IloIntVar[] machineUsageVars = cp.boolVarArray(numMachines);

        IloIntVar[] partitionsAssignmentInfo = cp.intVarArray(numPartitions, 0, numMachines - 1);

        int currentPartitionIndex = 0;

        for (int p = 0; p < partitionSize.length; p++) {
            if(partitionSize[p] > 1){
                for (int pSize = currentPartitionIndex + 1; pSize < partitionSize[p]; pSize++) {
                    cp.add(cp.eq(partitionsAssignmentInfo[currentPartitionIndex], partitionsAssignmentInfo[pSize]));
                }

                currentPartitionIndex += partitionSize[p];
            }
            else {
                currentPartitionIndex++;
            }
        }

        //if one partition is assigned to one partition then that machine as to be used
        for (int i = 0; i < numPartitions; i++) {
            cp.add(cp.eq(cp.element(machineUsageVars, partitionsAssignmentInfo[i]), 1));
        }

        //prevent machine capacity outburst
        for (int i = 0; i < numMachines; i++) {
            cp.add(cp.le(cp.count(partitionsAssignmentInfo, i), capacityPerMachine[i]));
        }

        cp.addMinimize(cp.scalProd(machineUsageVars, costPerMachine));

        cp.setOut(null);
        cp.solve();

        System.out.println("**********************SOLUTION***********************");

        for (int i = 0; i < numMachines; i++) {
            if(cp.getValue(machineUsageVars[i]) == 1){
                System.out.println("MACHINE " + i + " CAPACITY = " + capacityPerMachine[i]);
            }
        }

        for (int i = 0; i < numPartitions; i++) {
            System.out.println("PARTITION  " + i + " ASSIGNED TO : " + cp.getValue(partitionsAssignmentInfo[i]));
        }

        cp.end();
    }*/


}
