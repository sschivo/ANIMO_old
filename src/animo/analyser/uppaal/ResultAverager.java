package animo.analyser.uppaal;


import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;

import animo.analyser.AnalysisException;
import animo.cytoscape.RunAction;
import animo.model.Model;

import cytoscape.task.TaskMonitor;

/**
 * Used to produce the average result of a series of simulation queries.
 * Standard deviation can also be produced on request.
 */
public class ResultAverager {
	public static final String STD_DEV = "_stddev"; //NOTICE: it needs to be lowercase, because elsewhere we assume it is so. We also assume that it starts with "_"
	private TaskMonitor monitor = null; //If we are operating via the user interface, we can show the point at which we are with the simulations
	private RunAction runAction = null; //If we are operating via the user interface, this will tell us if the user has requested that we cancel the simulations
	
	public ResultAverager(TaskMonitor monitor, RunAction runAction) {
		this.monitor = monitor;
		this.runAction = runAction;
	}
	
	/**
	 * Analyse the given model, with a reachability query E<> (globalTime > timeTo) (with timeTo given),
	 * and produce a result showing the average activity levels of all reactants in the model during the simulation
	 * interval. If computeStdDev is true, adds also series to show the Standard Deviation from the averages.
	 * @param m The model
	 * @param timeTo The time up to which a single simulation will run
	 * @param nRuns How many simulation runs we need to compute the average of
	 * @param computeStdDev Tells us whether the user has asked for the standard deviation from the average
	 * @return A SimpleLevelResult showing the averages (and, is requested, the standard deviations) of activity levels of all reactants in the given model
	 * @throws AnalysisException
	 * @throws Exception
	 */
	public SimpleLevelResult analyzeAverage(Model m, int timeTo, int nRuns, boolean computeStdDev) throws AnalysisException, Exception {
		Vector<SimpleLevelResult> results = new Vector<SimpleLevelResult>(nRuns);
		UppaalModelAnalyserFasterConcrete analyzer = new UppaalModelAnalyserFasterConcrete(monitor, runAction);
		for (int i=0;i<nRuns;i++) {
			if (runAction != null && runAction.needToStop()) {
				throw new AnalysisException("User interrupted");
			}
			if (monitor != null) {
				monitor.setPercentCompleted((int)((double)i / nRuns * 100));
			}
			System.err.print((i+1));
			results.add((SimpleLevelResult)(analyzer.analyze(m, timeTo)));
		}
		return average(results, computeStdDev);
	}
	
	/**
	 * Given a vector of SimpleLevelResults, computes a new SimpleLevelResult in which the
	 * series represent the averages (and, if requested, standard deviations) of the series
	 * contained in the given vector. Of course, all SimpleLevelResults in the vector are expected
	 * to have the exact same series names. Time instants can be instead different: the number of time
	 * instants contained in the result will correspond to the average number of time instants
	 * found in the input SimpleLevelResults.
	 * @param results The vector containing all the SimpleLevelResults of which we have to compute the average/StdDev
	 * @param computeStdDev Tells us whether we have to compute the standard deviation for all the series
	 * @return A single SimpleLevelResult containing the averages (and StdDevs, if needed) of the
	 * series present in the input SimpleLevelResults
	 * @throws Exception
	 */
	public SimpleLevelResult average(Vector<SimpleLevelResult> results, boolean computeStdDev) throws Exception {
		if (results.isEmpty()) throw new Exception("Empty result set");
		Map<String, SortedMap<Double, Double>> result = new HashMap<String, SortedMap<Double, Double>>();
		Set<String> reactantIds = results.firstElement().getReactantIds();
		
		for (String k : reactantIds) {
			result.put(k, new TreeMap<Double, Double>());
			if (computeStdDev) result.put(k + STD_DEV, new TreeMap<Double, Double>());
		}
		
		double finalTime = results.firstElement().getTimeIndices().get(results.firstElement().getTimeIndices().size()-1);
		int avgSize = 0;
		for (SimpleLevelResult l : results) {
			avgSize += l.getTimeIndices().size();
		}
		avgSize = (int)Math.round(1.0 * avgSize / results.size());
		double increment = finalTime / avgSize;
		int nValues = results.size();
		double sum = 0, sumSqrs = 0, average = 0, stdDev = 0;
		for (double i=0;i<finalTime;i+=increment) {
			for (String k : reactantIds) {
				sum = 0;
				if (computeStdDev) sumSqrs = 0;
				for (SimpleLevelResult l : results) {
					double val = l.getConcentration(k, i);
					sum += val;
					if (computeStdDev) sumSqrs += val * val;
				}
				average = sum / nValues;
				if (computeStdDev) stdDev = Math.sqrt((nValues * sumSqrs - sum * sum)/ (nValues * (nValues - 1)));
				result.get(k).put(i, average);
				if (computeStdDev) result.get(k + STD_DEV).put(i, stdDev);
			}
		}
		for (String k : reactantIds) {
			sum = 0;
			if (computeStdDev) sumSqrs = 0;
			for (SimpleLevelResult l : results) {
				double val = l.getConcentration(k, finalTime);
				sum += val;
				if (computeStdDev) sumSqrs += val * val;
			}
			average = sum / nValues;
			if (computeStdDev) stdDev = Math.sqrt((nValues * sumSqrs - sum * sum)/ (nValues * (nValues - 1)));
			result.get(k).put(finalTime, average);
			if (computeStdDev) result.get(k + STD_DEV).put(finalTime, stdDev);
		}
		
		
		return new SimpleLevelResult(result);
	}
	
}
