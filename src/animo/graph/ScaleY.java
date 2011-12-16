package animo.graph;


import java.awt.Rectangle;

/**
 * The class used to contain the scaling informations for a graph.
 * When a new series is added to the graph, we make sure that the
 * update the minimum and maximum values for the graph, so that it will
 * automatically display the data in the best possible zoom level.
 */
public class ScaleY {
	private double maxY = Double.NEGATIVE_INFINITY;
	private double minY = Double.POSITIVE_INFINITY;
	private double dataMaxY = Double.NEGATIVE_INFINITY; //The ones with the "data" are the actual minimum and maximum values encountered while reading the data. The ones without the "data" define the area to be plotted
	private double dataMinY = Double.POSITIVE_INFINITY;
	private double scaleY = 1;
	
	public ScaleY() {
		//nothing
	}
	
	public ScaleY copy() {
		ScaleY other = new ScaleY();
		other.maxY = this.maxY;
		other.minY = this.minY;
		other.dataMinY = this.dataMinY;
		other.dataMaxY = this.dataMaxY;
		other.scaleY = this.scaleY;
		return other;
	}
	
	//reset the scales
	public void reset() {
		maxY = minY = Double.NaN;
		scaleY = 1;
	}

	public double getYScale() {
		return scaleY;
	}
	
	public double getMaxY() {
		return maxY;
	}
	
	public void setMaxY(double maxY) {
		this.maxY = maxY;
	}
	
	public double getMinY() {
		return minY;
	}
	
	public void setMinY(double minY) {
		this.minY = minY;
	}
	
	public double getMaximumValue() {
		return dataMaxY;
	}
	
	public double getMinimumValue() {
		return dataMinY;
	}

	public void addData(P[] data) {
		for (int i=0; i < data.length; i++) {
			if (Double.isNaN(maxY) || maxY < data[i].y) {
				maxY = data[i].y;
			}
			if (Double.isNaN(minY) || minY > data[i].y) {
				minY = data[i].y;
			}
		}
		if (minY < dataMinY) {
			dataMinY = minY;
		}
		if (maxY > dataMaxY) {
			dataMaxY = maxY;
		}
	}

	public void computeScale(Rectangle bounds) {
		scaleY = bounds.height / Math.abs(maxY - minY);
	}
	
	public void adaptToScale(ScaleY other) {
		double otherTotalWidth = other.dataMaxY - other.dataMinY;
		double myTotalWidth = this.dataMaxY - this.dataMinY;
		double myMinDist = (other.minY - other.dataMinY) / otherTotalWidth * myTotalWidth;
		double myMaxDist = (other.maxY - other.dataMaxY) / otherTotalWidth * myTotalWidth;
		this.minY = this.dataMinY + myMinDist;
		this.maxY = this.dataMaxY + myMaxDist;
	}
	
}
