package pt.ulisboa.tecnico.hourglass.optimization.solvers.glpk;

import org.gnu.glpk.GLPK;
import org.gnu.glpk.GlpkException;
import org.gnu.glpk.glp_prob;
import pt.ulisboa.tecnico.hourglass.optimization.solvers.OptimizationSolver;

/**
 * Created by Pedro Joaquim.
 */
public class OptimizationSolverGLPK extends OptimizationSolver {

    @Override
    protected void solve(int numPartitions, int numMachines) throws Exception {


        glp_prob lp = GLPK.glp_create_prob();



    }
}
