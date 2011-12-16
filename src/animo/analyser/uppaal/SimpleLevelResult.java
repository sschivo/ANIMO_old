package animo.analyser.uppaal;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Map.Entry;

import animo.analyser.LevelResult;
import animo.util.Pair;

/**
 * A very simple data container for the concentration/time data.
 * 
 * @author Brend Wanders
 * 
 */
public class SimpleLevelResult implements LevelResult, Serializable {
	private static final long serialVersionUID = 5440819034905472745L;
	Map<String, SortedMap<Double, Double>> levels;
	private double max = Double.NEGATIVE_INFINITY;

	/**
	 * @param levels the levels to enter
	 */
	public SimpleLevelResult(Map<String, SortedMap<Double, Double>> levels) {
		this.levels = levels;
		for (String k : levels.keySet()) {
			SortedMap<Double, Double> map = levels.get(k);
			for (Double t : map.keySet()) {
				double v = map.get(t);
				if (v > max) {
					max = v;
				}
			}
		}
	}

	@Override
	public double getConcentration(String id, double time) {
		assert this.levels.containsKey(id) : "Can not retrieve level for unknown identifier.";

		SortedMap<Double, Double> data = this.levels.get(id);

		// determine level at requested moment in time:
		// it is either the level set at the requested moment, or the one set
		// before that
		//assert !data.headMap(time + 1).isEmpty() : "Can not retrieve data from any moment before the start of time.";
		//int exactTime = data.headMap(time + 1).lastKey();
		double exactTime = -1;
		for (Double k : data.keySet()) {
			if (k > time) break;
			exactTime = k;
		}

		// use exact time to get value
		return data.get(exactTime);
	}
	
	@Override
	public Double getConcentrationIfAvailable(String id, double time) {
		assert this.levels.containsKey(id) : "Can not retrieve level for unknown identifier.";

		SortedMap<Double, Double> data = this.levels.get(id);
		
		return data.get(time);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		
		b.append("Result["+this.getReactantIds()+"] ");

		for (Entry<String, SortedMap<Double, Double>> r : this.levels.entrySet()) {
			b.append(r.getKey() + ": " + r.getValue() + "\n");
		}

		return b.toString();
	}

	@Override
	public List<Double> getTimeIndices() {
		SortedSet<Double> accumulator = new TreeSet<Double>();

		for (SortedMap<Double, Double> e : this.levels.values()) {
			accumulator.addAll(e.keySet());
		}

		return new ArrayList<Double>(accumulator);
	}

	@Override
	public Set<String> getReactantIds() {
		return Collections.unmodifiableSet(this.levels.keySet());
	}

	@Override
	public boolean isEmpty() {
		return levels.isEmpty();
	}
	
	@Override
	public double getMaximumValue() {
		return max;
	}
	
	@Override
	public LevelResult filter(Vector<String> acceptedNames) {
		Map<String, SortedMap<Double, Double>> lev = new HashMap<String, SortedMap<Double, Double>>();
		for (String s : levels.keySet()) {
			if (!acceptedNames.contains(s)) continue;
			SortedMap<Double, Double> m = levels.get(s);
			lev.put(s, m);
		}
		SimpleLevelResult res = new SimpleLevelResult(lev);
		return res;
	}
	
	@Override
	public Pair<LevelResult, LevelResult> split(Vector<String> onlyInTheSecond) {
		Map<String, SortedMap<Double, Double>> lev1 = new HashMap<String, SortedMap<Double, Double>>(),
											   lev2 = new HashMap<String, SortedMap<Double, Double>>();
		for (String s : levels.keySet()) {
			SortedMap<Double, Double> m = levels.get(s);
			if (onlyInTheSecond.contains(s)) {
				lev2.put(s, m);
			} else {
				lev1.put(s, m);
			}
		}
		SimpleLevelResult res1 = new SimpleLevelResult(lev1),
						  res2 = new SimpleLevelResult(lev2);
		return new Pair<LevelResult, LevelResult>(res1, res2);
	}
}
