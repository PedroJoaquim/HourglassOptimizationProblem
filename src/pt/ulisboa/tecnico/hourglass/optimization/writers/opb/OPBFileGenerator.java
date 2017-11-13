package pt.ulisboa.tecnico.hourglass.optimization.writers.opb;

import pt.ulisboa.tecnico.hourglass.optimization.writers.FileGenerator;

import java.io.PrintWriter;
import java.util.List;

/**
 * Created by Pedro Joaquim.
 */
public class OPBFileGenerator extends FileGenerator{

    private static final String OPB_FILE_PATH = "D:\\Projects\\open-wbo-master\\input\\";

    protected void writeFile(PrintWriter writer, int[] partitionsSize, int[] costPerMachine, int[] capacityPerMachine, List<int[]> zeroVars, int[] machineUsageVarsIndexes, int[][] assignmentMatrixPerMachine, int[][] assignmentMatrixPerPartition) {

        int numPartitions = partitionsSize.length;
        int numMachines = costPerMachine.length;

        /* objective function */

        StringBuilder sb = new StringBuilder();

        sb.append("min: ");

        for (int i = 0; i < machineUsageVarsIndexes.length; i++) {
            sb.append("+").append(costPerMachine[i]).append(" x").append(machineUsageVarsIndexes[i]).append(" ");
        }

        sb.append(";\n");

        writer.write(sb.toString());

        /*constraint 1: every partition has to be assigned to one and only one machine */

        for (int p = 0; p < numPartitions; p++) {

            sb = new StringBuilder();

            for (int m = 0; m < numMachines; m++) {
                sb.append("+1 x").append(assignmentMatrixPerPartition[p][m]).append(" ");
            }

            sb.append("= 1 ;\n");

            writer.write(sb.toString());
        }

        /*constraint 2: if a partitions is assigned to a machine then that machine needs to be selected*/

        for (int m = 0; m < numMachines; m++) {
            for (int p = 0; p < numPartitions; p++) {
                sb = new StringBuilder();

                sb.append("+1 x").append(assignmentMatrixPerMachine[m][p]).append(" -1 x").append(machineUsageVarsIndexes[m]).append(" <= 0 ;\n");

                writer.write(sb.toString());
            }
        }

        /*constraint 3: prevent machine outburst capacity*/

        for (int m = 0; m < numMachines; m++) {

            sb = new StringBuilder();

            for (int p = 0; p < numPartitions; p++) {
                sb.append("+").append(partitionsSize[p]).append(" x").append(assignmentMatrixPerMachine[m][p]).append(" ");
            }

            sb.append("<= ").append(capacityPerMachine[m]).append(" ;\n");

            writer.write(sb.toString());
        }


        /* add zero vars to break Symmetry*/

        for (int i = 0; i < zeroVars.size(); i++) {
            sb = new StringBuilder();

            int machineIndex = zeroVars.get(i)[0];
            int partitionIndex = zeroVars.get(i)[1];

            int varID = assignmentMatrixPerMachine[machineIndex][partitionIndex];

            sb.append("+1 x").append(varID).append(" = 0 ;\n");

            writer.write(sb.toString());
        }

        writer.flush();
        writer.close();
    }

    @Override
    protected String getOutputFilePath(int numPartitions) {
        return  OPB_FILE_PATH + "m" + numPartitions + ".opb";
    }

}
