package pt.ulisboa.tecnico.hourglass.optimization.writers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Created by Pedro Joaquim.
 */
public abstract class FileGenerator {

    public void createFile(int[] partitionsSize, int[] costPerMachine, int[] capacityPerMachine, List<int[]> zeroVars) {

        FileOutputStream outputStream = null;
        File outFile = new File(getOutputFilePath(partitionsSize.length));
        PrintWriter writer = null;

        try {

            // if file doesnt exists, then create it
            if (!outFile.exists()) {
                outFile.createNewFile();
            }

            outputStream = new FileOutputStream(outFile);
            writer = new PrintWriter(outputStream);

            writeFileSuper(writer, partitionsSize, costPerMachine, capacityPerMachine, zeroVars);


        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
        finally {
            if(writer != null){
                writer.close();
            }

            if(outputStream != null){
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void writeFileSuper(PrintWriter writer, int[] partitionsSize, int[] costPerMachine, int[] capacityPerMachine, List<int[]> zeroVars) {

        int currentVarIndex = 1;
        int numPartitions = partitionsSize.length;
        int numMachines = costPerMachine.length;

        int[] machineUsageVarsIndexes = new int[numMachines];

        for (int i = 0; i < costPerMachine.length; i++) {
            machineUsageVarsIndexes[i] = currentVarIndex++;
        }

        int[][] assignmentMatrixPerPartition = new int[numPartitions][numMachines];
        int[][] assignmentMatrixPerMachine = new int[numMachines][numPartitions];

        for (int p = 0; p < numPartitions; p++) {
            for (int m = 0; m < numMachines; m++) {
                assignmentMatrixPerPartition[p][m] = currentVarIndex++;
                assignmentMatrixPerMachine[m][p] = assignmentMatrixPerPartition[p][m];
            }
        }

        writeFile(writer, partitionsSize, costPerMachine, capacityPerMachine, zeroVars, machineUsageVarsIndexes, assignmentMatrixPerMachine, assignmentMatrixPerPartition);
    }

    protected abstract void writeFile(PrintWriter writer, int[] partitionsSize, int[] costPerMachine, int[] capacityPerMachine, List<int[]> zeroVars, int[] machineUsageVarsIndexes, int[][] assignmentMatrixPerMachine, int[][] assignmentMatrixPerPartition);

    protected abstract String getOutputFilePath(int numPartitions);

}
