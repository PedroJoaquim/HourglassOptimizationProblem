package pt.ulisboa.tecnico.hourglass.optimization.solvers.util;

import ilog.concert.IloIntVar;

/**
 * Created by Pedro Joaquim.
 */
public class Machine<I> {

    private int id;

    private int capacity;

    private I[] assignmentVarsPerPartition;

    public Machine(int id, int capacity, I[] assignmentVarsPerPartition) {
        this.id = id;
        this.capacity = capacity;
        this.assignmentVarsPerPartition = assignmentVarsPerPartition;
    }

    public int getCapacity() {
        return capacity;
    }

    public I[] getAssignmentVarsPerPartition() {
        return assignmentVarsPerPartition;
    }

    public int getId() {
        return id;
    }
}
