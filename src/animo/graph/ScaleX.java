package animo.graph;


import java.awt.Rectangle;

/**
 * The class used to contain the scaling informations for a graph.
 * When a new series is added to the graph, we make sure that the
 * update the minimum and maximum values for the graph, so that it will
 * automatically display the data in the best possible zoom level.
 */
public class ScaleX {
	private double maxX = Double.NaN;
	private double minX = Double.NaN;
	private double scaleX = 1;
	
	public ScaleX() {
		//nothing
	}
	
	//reset the scales
	public void reset() {
		maxX = minX = Double.NaN;
		scaleX = 1;
	}

	public double getXScale() {
		return scaleX;
	}
	
	public double getMaxX() {
		return maxX;
	}
	
	public void setMaxX(double maxX) {
		this.maxX = maxX;
	}
	
	public double getMinX() {
		return minX;
	}
	
	public void setMinX(double minX) {
		this.minX = minX;
	}
	
	public void addData(P[] data) {
		for (int i=0; i < data.length; i++) {
			if (Double.isNaN(maxX) || maxX < data[i].x){
				maxX = data[i].x;
			}
			if (Double.isNaN(minX) || minX > data[i].x) {
				minX = data[i].x;
			}
		}
	}

	public void computeScale(Rectangle bounds) {
		scaleX = bounds.width / Math.abs(maxX - minX);
	}
	
}
