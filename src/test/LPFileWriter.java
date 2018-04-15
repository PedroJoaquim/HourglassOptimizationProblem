package test;

import ilog.concert.IloException;
import ilog.concert.IloIntExpr;
import ilog.concert.IloIntVar;
import ilog.cplex.IloCplex;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class LPFileWriter {

    private static final int RANDOM_NUM_SIMS = 100;

    private static final Pattern PATTERN = Pattern.compile("[\t ]");

    private ReducedSymmetricalMatrixInt microPartitionsCrossingEdges;

    private int numMicroPartitions;

    private int numMachines;

    public static void main(String[] args) throws IOException, IloException {

        LPFileWriter app = new LPFileWriter();
        app.start(args[0], args[1], Integer.parseInt(args[2]), Boolean.parseBoolean(args[3]));

    }

    private void start(String inputPath, String outputPath, int numMachines, boolean solve) throws IOException, IloException {

        this.numMachines = numMachines;

        processInputPath(inputPath);

        //writeProblem(outputPath);

        if(solve){
            solveProblem();
        }
        else {
            assignRandom();
        }
    }

    private void assignRandom() {

        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        long avg = 0;

        int numPartitionsPerMachine = numMicroPartitions / numMachines + (numMicroPartitions % numMachines == 0 ? 0 : 1);

        System.out.println("[INFO] DOING " + RANDOM_NUM_SIMS + " RANDOM GENERATION FOR " + numMicroPartitions + " MICRO PARTITIONS " + numMachines + " MACHINES "
         + " AND " + numPartitionsPerMachine + " NUM PARTITIONS PER MACHINE");

        Random rnd = new Random();

        for (int i = 0; i < RANDOM_NUM_SIMS; i++) {

            int edgeCut = 0;
            int rndIndex;

            List<Integer> allMachines = new ArrayList<>(numMachines);

            int[] partitionsAssigned = new int[numMachines];

            int[] partitionsToMachinesMapping = new int[numMicroPartitions];


            for (int j = 0; j < numMachines; j++) {
                allMachines.add(j);
            }


            for (int j = 0; j < numMicroPartitions; j++) {

                while (partitionsAssigned[allMachines.get(rndIndex = rnd.nextInt(allMachines.size()))] >=  numPartitionsPerMachine){
                    allMachines.remove(rndIndex);
                }

                partitionsAssigned[allMachines.get(rndIndex)]++;
                partitionsToMachinesMapping[j] = allMachines.get(rndIndex);
            }


            for (int j = 0; j < numMicroPartitions; j++) {
                for (int k = j+1; k < numMicroPartitions; k++) {
                    if(partitionsToMachinesMapping[j] != partitionsToMachinesMapping[k]){
                        edgeCut += this.microPartitionsCrossingEdges.readValue(j,k);
                    }
                }
            }

            avg += edgeCut;

            if(edgeCut < min){
                min = edgeCut;
            }

            if(edgeCut > max){
                max = edgeCut;
            }
        }

        avg = avg/RANDOM_NUM_SIMS;

        System.out.println("[INFO] RANDOM MIN EDGE CUT = " + min);
        System.out.println("[INFO] RANDOM MAX EDGE CUT = " + max);
        System.out.println("[INFO] RANDOM AVG EDGE CUT = " + avg);

    }

    private void solveProblem() throws IloException {

        long start = System.currentTimeMillis();

        IloCplex cplex = new IloCplex();

        ReducedSymmetricalMatrixIlo microPartitionCoLocation = new ReducedSymmetricalMatrixIlo(numMicroPartitions);

        for (int i = 0; i < numMicroPartitions; i++) {
            for (int j = i + 1; j < numMicroPartitions; j++) {
                microPartitionCoLocation.setValue(i, j, cplex.boolVar());
            }
        }

        IloIntVar[][] microPartitionToMachineMapping = new IloIntVar[numMicroPartitions][numMachines];
        IloIntVar[][] machineToMicroPartitionMapping = new IloIntVar[numMachines][numMicroPartitions];

        for (int i = 0; i < numMicroPartitions; i++) {

            microPartitionToMachineMapping[i] = cplex.boolVarArray(numMachines);

            for (int j = 0; j < numMachines; j++) {
                machineToMicroPartitionMapping[j][i] = microPartitionToMachineMapping[i][j];
            }
        }

        /*constraint 1: every partition has to be assigned to one and only one machine */

        for (int i = 0; i < numMicroPartitions; i++) {
            cplex.addEq(cplex.sum(microPartitionToMachineMapping[i]), 1);
        }

        int maxMachineLoad = (numMicroPartitions % numMachines) == 0 ? (numMicroPartitions / numMachines) : (numMicroPartitions % numMachines) + 1;

        /*constraint 2: prevent machine outburst capacity*/


        for (int i = 0; i < numMachines; i++) {
            cplex.addLe(cplex.sum(machineToMicroPartitionMapping[i]), maxMachineLoad);
        }

        /*constraint 3: partitions assigned to the same machine do not count for the edge cut*/

        for (int i = 0; i < numMicroPartitions; i++) {
            for (int j = i+1; j < numMicroPartitions; j++) {
                for (int k = 0; k < numMachines; k++) {

                    cplex.addGe(cplex.sum(variableNegation(microPartitionToMachineMapping[i][k], cplex),
                                          variableNegation(microPartitionToMachineMapping[j][k], cplex),
                                          variableNegation(microPartitionCoLocation.readValue(i,j), cplex)), 1);
                }
            }
        }

        /*constraint 4: partitions assigned to different machines count for the edge cut*/

        for (int i = 0; i < numMicroPartitions; i++) {
            for (int j = 0; j < numMachines; j++) {
                for (int k = i+1; k < numMicroPartitions; k++) {
                    for (int l = 0; l < numMachines; l++) {
                        if(l != j){
                            cplex.addGe(cplex.sum(variableNegation(microPartitionToMachineMapping[i][j], cplex),
                                    variableNegation(microPartitionToMachineMapping[k][l], cplex),
                                    microPartitionCoLocation.readValue(i,k)), 1);
                        }
                    }
                }
            }
        }


        //minimization problem

        IloIntVar[] matrix = microPartitionCoLocation.getMatrix();

        cplex.addMinimize(cplex.scalProd(this.microPartitionsCrossingEdges.getMatrix(), matrix));

        cplex.setOut(null);

        cplex.solve();

        long end = System.currentTimeMillis();

        System.out.println("[INFO] TIME TO SOLVE PROBLEM = " + (end-start)/1000.0d + " secs");


        System.out.println("**********************SOLUTION***********************");


        long finalEdgeCut = 0;

        for (int i = 0; i < numMicroPartitions; i++) {
            for (int j = i+1; j < numMicroPartitions; j++) {
                if(cplex.getValue(microPartitionCoLocation.readValue(i,j)) == 1){
                    finalEdgeCut +=this.microPartitionsCrossingEdges.readValue(i,j);
                }

            }
        }

        System.out.println("[INFO] FINAL EDGE CUT = " + finalEdgeCut);

        for (int i = 0; i < numMachines; i++) {

            System.out.print("[INFO] MACHINE " + i + " GOT MICRO PARTITIONS: ");

            for (int j = 0; j < numMicroPartitions; j++) {
                if(cplex.getValue(machineToMicroPartitionMapping[i][j]) == 1){
                    System.out.print(j + " ");
                }
            }

            System.out.println();
        }


       /* for (int i = 0; i < numMicroPartitions; i++) {
            for (int j = i+1; j < numMicroPartitions; j++) {
                if(cplex.getValue(microPartitionCoLocation.readValue(i,j)) == 0){
                    System.out.println("[INFO] CO LOCATED: " + i + " and " + j);
                }
            }
        }*/
    }

    public IloIntExpr variableNegation(IloIntVar var, IloCplex cplex) throws IloException {
        return cplex.sum(1, cplex.negative(var));
    }


    private void writeProblem(String outputPath) throws IOException {

        int varIndex = 1;

        ReducedSymmetricalMatrixInt microPartitionCoLocation = new ReducedSymmetricalMatrixInt(numMicroPartitions);

        for (int i = 0; i < numMicroPartitions; i++) {
            for (int j = i + 1; j < numMicroPartitions; j++) {
                microPartitionCoLocation.setValue(i, j, varIndex++);
            }
        }

        ReducedSymmetricalMatrixInt microPartitionCoLocationNOT = new ReducedSymmetricalMatrixInt(numMicroPartitions);

        for (int i = 0; i < numMicroPartitions; i++) {
            for (int j = i + 1; j < numMicroPartitions; j++) {
                microPartitionCoLocationNOT.setValue(i, j, varIndex++);
            }
        }

        int[][] microPartitionToMachineMapping = new int[numMicroPartitions][numMachines];

        for (int i = 0; i < numMicroPartitions; i++) {
            for (int j = 0; j < numMachines; j++) {
                microPartitionToMachineMapping[i][j] = varIndex++;
            }
        }

        int[][] microPartitionToMachineMappingNOT = new int[numMicroPartitions][numMachines];

        for (int i = 0; i < numMicroPartitions; i++) {
            for (int j = 0; j < numMachines; j++) {
                microPartitionToMachineMappingNOT[i][j] = varIndex++;
            }
        }


        int maxMachineLoad = (numMicroPartitions % numMachines) == 0 ? (numMicroPartitions / numMachines) : (numMicroPartitions % numMachines) + 1;


        PrintWriter writer = new PrintWriter(new FileWriter(new File(outputPath)));

        writer.write("Minimize\n");

        StringBuilder sb = new StringBuilder();

        sb.append(" obj: ");

        for (int i = 0; i < numMicroPartitions; i++) {
            for (int j = i+1; j < numMicroPartitions; j++) {
                if(i != 0) sb.append("+ ");
                sb.append(this.microPartitionsCrossingEdges.readValue(i,j)).append(" x").append(microPartitionCoLocation.readValue(i,j)).append(" \n");
            }
        }


        sb.append("Subject To\n");

        int consID = 1;

        /*NEGATION CONSTRAINT: */

        for (int i = 0; i < numMicroPartitions; i++) {
            for (int j = i+1; j < numMicroPartitions; j++) {
                sb.append(" C").append(consID++).append(": ");
                sb.append(" x").append(microPartitionCoLocation.readValue(i,j)).append(" + x")
                        .append(microPartitionCoLocationNOT.readValue(i,j)).append(" = 1\n");
            }
        }

        for (int i = 0; i < numMicroPartitions; i++) {
            for (int j = 0; j < numMachines; j++) {
                sb.append(" C").append(consID++).append(": ");
                sb.append(" x").append(microPartitionToMachineMapping[i][j]).append(" + x").
                        append(microPartitionToMachineMappingNOT[i][j]).append(" = 1\n");
            }
        }


        /*constraint 1: every partition has to be assigned to one and only one machine */

        for (int i = 0; i < numMicroPartitions; i++) {
            sb.append(" C").append(consID++).append(": ");
            for (int j = 0; j < numMachines; j++) {
                sb.append("+ 1 x").append(microPartitionToMachineMapping[i][j]).append("\n");
            }
            sb.append("= 1\n");
        }

        /*constraint 2: prevent machine outburst capacity*/
        for (int i = 0; i < numMachines; i++) {
            sb.append(" C").append(consID++).append(": ");
            for (int j = 0; j < numMicroPartitions; j++) {
                sb.append("+ 1 x").append(microPartitionToMachineMapping[j][i]).append("\n");
            }
            sb.append(" <= ").append(maxMachineLoad).append("\n");
        }

        /*constraint 3: partitions assigned to the same machine do not count for the edge cut*/

        for (int i = 0; i < numMicroPartitions; i++) {
            for (int j = i+1; j < numMicroPartitions; j++) {
                for (int k = 0; k < numMachines; k++) {
                    sb.append(" C").append(consID++).append(": ");
                    sb.append("+ 1 x").append(microPartitionToMachineMappingNOT[i][k]).append(" + 1 x")
                            .append(microPartitionToMachineMappingNOT[j][k]).append(" + 1 x").append(microPartitionCoLocationNOT.readValue(i,j));
                    sb.append(" >= 1\n");
                }
            }
        }

        sb.append("Binaries\n");

        for (int i = 0; i < numMicroPartitions; i++) {
            for (int j = i+1; j < numMicroPartitions; j++) {
                sb.append(" x").append(microPartitionCoLocation.readValue(i,j)).append("\n");
                sb.append(" x").append(microPartitionCoLocationNOT.readValue(i,j)).append("\n");
            }

            for (int j = 0; j < numMachines; j++) {
                sb.append(" x").append(microPartitionToMachineMapping[i][j]).append("\n");
                sb.append(" x").append(microPartitionToMachineMappingNOT[i][j]).append("\n");
            }
        }

        writer.write(sb.toString());

        writer.write("End\n");

        writer.flush();
        writer.close();

    }

    private void processInputPath(String inputPath) {


        FileInputStream inputStream = null;
        Scanner sc = null;

        try {

            inputStream = new FileInputStream(inputPath);
            sc = new Scanner(inputStream, "UTF-8");

            int sourceVertex = -1;

            while (sc.hasNextLine()){

                String[] line = PATTERN.split(sc.nextLine());

                if(++sourceVertex == 0){
                    this.numMicroPartitions = Integer.valueOf(line[0]);
                    System.out.println("[INFO] NUM MICRO PARTITIONS = " + this.numMicroPartitions);
                    this.microPartitionsCrossingEdges = new ReducedSymmetricalMatrixInt(numMicroPartitions);
                }
                else {

                    for (int i = 0; i < line.length - 1; i+=2) {

                        int targetVertex = Integer.valueOf(line[i]);

                        if(targetVertex > sourceVertex){
                            int edgeValue = Integer.valueOf(line[i+1]);

                            this.microPartitionsCrossingEdges.setValue(sourceVertex - 1, targetVertex - 1, edgeValue);
                        }
                    }
                }
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }



    private class ReducedSymmetricalMatrixIlo {

        private int matrixDim;

        private int numEntries;

        private IloIntVar[] matrix;

        public ReducedSymmetricalMatrixIlo(int matrixDim) {
            this.matrixDim = matrixDim;
            this.numEntries = ((matrixDim * matrixDim) - matrixDim)/2;
            this.matrix = new IloIntVar[numEntries];
        }

        public void setValue(int cl, int l, IloIntVar value){

            int idx = convertIndex(cl, l);

            this.matrix[idx] = value;
        }

        public IloIntVar readValue(int cl, int l){
            return this.matrix[convertIndex(cl,l)];
        }

        private int convertIndex(int cl, int l) {

            int x,y;

            if(cl < l){
                x = cl;
                y = l;
            }
            else {
                x = l;
                y = cl;
            }

            return x * matrixDim - ((((x+1) * (x+1)) - (x+1))/2) + (y - (x+1));
        }

        public IloIntVar[] getMatrix() {
            return matrix;
        }
    }

    private class ReducedSymmetricalMatrixInt {

        private int matrixDim;

        private int numEntries;

        private int[] matrix;

        public ReducedSymmetricalMatrixInt(int matrixDim) {
            this.matrixDim = matrixDim;
            this.numEntries = ((matrixDim * matrixDim) - matrixDim)/2;
            this.matrix = new int[numEntries];
        }

        public void setValue(int cl, int l, int value){

            int idx = convertIndex(cl, l);

            this.matrix[idx] = value;
        }

        public int readValue(int cl, int l){
            return this.matrix[convertIndex(cl,l)];
        }

        private int convertIndex(int cl, int l) {

            int x,y;

            if(cl < l){
                x = cl;
                y = l;
            }
            else {
                x = l;
                y = cl;
            }

            return x * matrixDim - ((((x+1) * (x+1)) - (x+1))/2) + (y - (x+1));
        }

        public int[] getMatrix() {
            return matrix;
        }
    }

}

