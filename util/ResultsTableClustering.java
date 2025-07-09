package util;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;

public class ResultsTableClustering implements PlugIn {
	
	private double threshold_distance = 10;
	private int threshold_size= 20;
	
	@Override
	public void run(String arg0) {
		
		ResultsTable table = Analyzer.getResultsTable();
		
		if (table == null)
			return;
		
		GenericDialog dialog = new GenericDialog("Cluster");
		dialog.addNumericField("distance_threshold", threshold_distance, 2);
		dialog.addNumericField("size_threshold", threshold_size, 0);
		dialog.showDialog();
		
		threshold_distance = dialog.getNextNumber();
		threshold_size = (int)dialog.getNextNumber();
		
		int n = table.getCounter();
		double[] cluster_x = new double[n];
		double[] cluster_y = new double[n];
		int[] cluster_n = new int[n];
		
		for (int row = 0; row < n; row++) {
			cluster_x[row] = table.getValue("x", row);
			cluster_y[row] = table.getValue("y", row);
			cluster_n[row] = 1;
		}
		
		boolean new_cluster = true;
		
		while (n > 1 && new_cluster) {
			
			double min_distance = threshold_distance;
			int cluster1 = -1;
			int cluster2 = -1;
			
			for (int i = 0; i < n; i++) {
				
				double x0 = cluster_x[i];
				double y0 = cluster_y[i];
				
				for (int j = i + 1; j < n; j++) {
					
					double x1 = cluster_x[j] / cluster_n[j];
					double y1 = cluster_y[j] / cluster_n[j];
					double dx = x1 - x0;
					double dy = y1 - y0;
					
					if (dx < threshold_distance && dy < threshold_distance) {
						
						double distance = Math.sqrt(dx * dx + dy * dy);
						
						if (distance < min_distance) {
							min_distance = distance;
							cluster1 = i;
							cluster2 = j;
						}
						
					}
					
				}
			}
			
			if (min_distance < threshold_distance) {
				
				// merge clusters
				cluster_x[cluster1] += cluster_x[cluster2];
				cluster_y[cluster1] += cluster_y[cluster2];
				cluster_n[cluster1] += cluster_n[cluster2];
				
				// delete one of the merged clusters
				n--;
				cluster_x[cluster2] = cluster_x[n];
				cluster_y[cluster2] = cluster_y[n];
				cluster_n[cluster2] = cluster_n[n];
				
			}
			else
				new_cluster = false;
			
			IJ.showStatus("Number of clusters : " + n);
		}
		
		int m = 0;
		
		for (int i = 0; i < n; i++) {
			if (cluster_n[i] >= threshold_size) {
				cluster_x[m] = cluster_x[i];
				cluster_y[m] = cluster_y[i];
				cluster_n[m] = cluster_n[i];
				m++;
				
				
				IJ.log("cluster : " + (cluster_x[i] / cluster_n[i]) + ", " + (cluster_x[i] / cluster_n[i]));
			}
		}
		
		// add results to the results table
		for (int row = 0; row < table.getCounter(); row++) {
			
			double x1 = table.getValue("x", row);
			double y1 = table.getValue("y", row);
			double min_distance = threshold_distance;
			int cluster = -1;
			
			for (int i = 0; i < m; i++) {
				
				double x2 = cluster_x[i] / cluster_n[i];
				double y2 = cluster_y[i] / cluster_n[i];
				double dx = x2 - x1;
				double dy = y2 - y1;
				double distance = Math.sqrt(dx * dx + dy * dy);
				
				if (distance < min_distance) {
					min_distance = distance;
					cluster = i;
				}
				
			}
			
			if (cluster >= 0) {
				table.setValue("cluster", row, cluster);
				table.setValue("cluster_x", row, cluster_x[cluster] / cluster_n[cluster]);
				table.setValue("cluster_y", row, cluster_x[cluster] / cluster_n[cluster]);
				table.setValue("cluster_n", row, cluster_n[cluster]);
			}
			
		}
		
		table.updateResults();
		
	}

}



