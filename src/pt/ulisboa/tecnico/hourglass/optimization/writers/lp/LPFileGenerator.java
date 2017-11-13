package pt.ulisboa.tecnico.hourglass.optimization.writers.lp;

import pt.ulisboa.tecnico.hourglass.optimization.writers.FileGenerator;

import java.io.PrintWriter;
import java.util.List;

/**
 * Created by Pedro Joaquim.
 */
public class LPFileGenerator extends FileGenerator {

    private static final String OPB_FILE_PATH = "D:\\Projects\\open-wbo-master\\input\\";

    @Override
    protected void writeFile(PrintWriter writer, int[] partitionsSize, int[] costPerMachine, int[] capacityPerMachine, List<int[]> zeroVars, int[] machineUsageVarsIndexes, int[][] assignmentMatrixPerMachine, int[][] assignmentMatrixPerPartition) {

        int numPartitions = partitionsSize.length;
        int numMachines = costPerMachine.length;

        StringBuilder sb = new StringBuilder();

        writer.write("Minimize\n");

        sb.append(" obj: ");

        for (int i = 0; i < machineUsageVarsIndexes.length; i++) {
            if(i != 0) sb.append("+ ");
            sb.append(costPerMachine[i]).append(" x").append(machineUsageVarsIndexes[i]).append(" \n");
        }

        writer.write(sb.toString());

        writer.write("Subject To\n");

        /*constraint 1: every partition has to be assigned to one and only one machine */

        int consID = 1;

        for (int p = 0; p < numPartitions; p++) {

            sb = new StringBuilder();

            sb.append(" C").append(consID++).append(": ");

            for (int m = 0; m < numMachines; m++) {
                sb.append("+ 1 x").append(assignmentMatrixPerPartition[p][m]).append("\n");
            }

            sb.append("= 1\n");

            writer.write(sb.toString());
        }

         /*constraint 2: if a partitions is assigned to a machine then that machine needs to be selected*/

        for (int m = 0; m < numMachines; m++) {
            for (int p = 0; p < numPartitions; p++) {
                sb = new StringBuilder();
                sb.append(" C").append(consID++).append(": ");

                sb.append("+ 1 x").append(assignmentMatrixPerMachine[m][p]).append(" - 1 x").append(machineUsageVarsIndexes[m]).append(" <= 0\n");

                writer.write(sb.toString());
            }
        }

        /*constraint 3: prevent machine outburst capacity*/

        for (int m = 0; m < numMachines; m++) {

            sb = new StringBuilder();
            sb.append(" C").append(consID++).append(":");

            for (int p = 0; p < numPartitions; p++) {
                sb.append(" + ").append(partitionsSize[p]).append(" x").append(assignmentMatrixPerMachine[m][p]).append("\n");
            }

            sb.append(" <= ").append(capacityPerMachine[m]).append("\n");

            writer.write(sb.toString());
        }

        /* add zero vars to break Symmetry*/

        for (int i = 0; i < zeroVars.size(); i++) {
            sb = new StringBuilder();
            sb.append(" C").append(consID++).append(": ");

            int machineIndex = zeroVars.get(i)[0];
            int partitionIndex = zeroVars.get(i)[1];

            int varID = assignmentMatrixPerMachine[machineIndex][partitionIndex];

            sb.append("+ 1 x").append(varID).append(" = 0\n");

            writer.write(sb.toString());
        }

        writer.write("Binaries\n");

        sb = new StringBuilder();

        for (int m = 0; m < machineUsageVarsIndexes.length; m++) {
            sb.append(" x").append(machineUsageVarsIndexes[m]).append("\n");
        }

        for (int m = 0; m < numMachines; m++) {
            for (int p = 0; p < numPartitions; p++) {
                sb.append(" x").append(assignmentMatrixPerMachine[m][p]).append("\n");
            }
        }

        writer.write(sb.toString());

        writer.write("End\n");

        writer.flush();
        writer.close();
    }

    @Override
    protected String getOutputFilePath(int numPartitions) {
        return  OPB_FILE_PATH + "m" + numPartitions + ".lp";
    }
}
