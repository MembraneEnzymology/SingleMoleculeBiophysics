package analyze;

import java.awt.AWTEvent;
import java.awt.Rectangle;
import java.util.Arrays;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;

public class PeakFitter2 implements ExtendedPlugInFilter, DialogListener {

public static final double SIGMA_TO_FWHM = 2.0 * Math.sqrt(2.0 * Math.log(2));
	
	private int flags = DOES_8G | DOES_16 | DOES_32 | NO_CHANGES | FINAL_PROCESSING | PARALLELIZE_STACKS;
	
	private static double sigma = Prefs.getInt("PeakFitter.sigma", 1);
	private static double minRSquared = Prefs.getDouble("PeakFitter.minRSquared", 0.80);
	
	private static LevenbergMarquardt lm = new LevenbergMarquardt() {
		
		@Override
		public double getValue(double[] x, double[] p) {
			
			double dx = x[0] - p[2];
			double dy = x[1] - p[3];
			
			return p[0] + p[1] * Math.exp(-((dx * dx + dy * dy) / (2 * p[4] * p[4])));
		}

		// https://www.symbolab.com/solver/partial-derivative-calculator/%5Cfrac%7B%5Cpartial%7D%7B%5Cpartial%20y%7D%5Cleft(a%2Bb%5Ccdot%20e%5E%7B-%5Cleft(%5Cleft(x%5E2%2By%5E2%5Cright)%5Cdiv%5Cleft(2%5Ccdot%20s%5E2%5Cright)%5Cright)%7D%5Cright)
		@Override
		public void getGradient(double[] x, double[] p, double[] dyda) {
			
			double dx = x[0] - p[2];
			double dy = x[1] - p[3];
			
			dyda[0] = 1;
			dyda[1] = Math.exp(-((dx * dx + dy * dy) / (2 * p[4] * p[4])));
			dyda[2] = (dyda[1] * p[1] * dx) / (p[4] * p[4]);
			dyda[3] = (dyda[1] * p[1] * dy) / (p[4] * p[4]);
			dyda[4] = (dyda[1] * p[1] * (dx * dx + dy * dy)) / (p[4] * p[4] * p[4]);
			
		}

	};
	
	private ResultsTable table;
	private ImagePlus imp;
	
	@Override
	public void run(ImageProcessor ip) {
		
		Rectangle roi = imp.getRoi().getBounds();
		
		double[][] xs = new double[roi.width * roi.height][2];
		double[] ys = new double[xs.length];
		int n = 0;
		
		for (int y = roi.y; y < roi.y + roi.height; y++) {
			for (int x = roi.x; x < roi.x + roi.width; x++) {
				
				xs[n][0] = x;
				xs[n][1] = y;
				ys[n] = ip.getf(x, y);
				
				if (ys[n] < ip.maxValue())	// ignore saturated values
					n++;
			}
		}
		
		// estimate height and baseline of gaussian peak
		double[] sortedPixels = Arrays.copyOf(ys, n);
		Arrays.sort(sortedPixels);
		
		// 5% lowest, 5% highest
		int m = (sortedPixels.length * 5) / 100;
		if (m == 0) m = 1;
		
		double min = 0;
		double max = 0;
		
		for (int i = 0; i < m; i++) {
			min += sortedPixels[i];
			max += sortedPixels[(n - 1) - i];
		}
		
		min /= m;
		max /= m;
		
		// estimate center of gaussian peak by calculating the centroid of the peak
		double cx = 0;
		double cy = 0;
		double totalWeight = 0;
		
		for (int i = 0; i < n; i++) {
			cx += xs[i][0] * ys[i];
			cy += xs[i][1] * ys[i];
			totalWeight += ys[i];
		}
		
		cx /= totalWeight;
		cy /= totalWeight;
		
		double[] p = new double[]{min, max - min, cx, cy, sigma};
		double[] e = new double[5];
		
		//IJ.log("");
		//IJ.log("baseline  = " + min);
		//IJ.log("amplitude = " + (max - min));
		//IJ.log("cx        = " + cx);
		//IJ.log("cy        = " + cy);
		//IJ.log("sigma     = " + sigma);
		//IJ.log("");
		
		double rSquared = lm.solve(xs, ys, null, n, p, null, e, 0.01);
		boolean valid = true;
		
		for (int i = 0; i < p.length && valid; i++)
			valid = !Double.isNaN(p[i]);
		
		if (valid && rSquared >= minRSquared) {
			
			// calculate residual sum of squares
			double ssq = 0;
			for (int i = 0; i < xs.length; i++)
				ssq += Math.pow(ys[i] - lm.getValue(xs[i], p), 2);
			
			addToResultsTable(table, p, e, (flags & DOES_STACKS) > 0 ? ip.getSliceNumber() : imp.getCurrentSlice(), rSquared, ssq);
			
		}
	}
	
	public static void addToResultsTable(ResultsTable table, double[] parameters, double[] errors, int slice, double rSquared, double ssq) {
		
		// sigma should always be absolute
		parameters[4] = Math.abs(parameters[4]);
		
		table.incrementCounter();
		
		table.addValue("baseline", parameters[0]);
		table.addValue("height",   parameters[1]);
		table.addValue("x",        parameters[2]);
		table.addValue("y",        parameters[3]);
		table.addValue("sigma",    parameters[4]);
		
		double fwhm = parameters[4] * SIGMA_TO_FWHM;
		
		table.addValue("fwhm",     fwhm);
		
		table.addValue("error_baseline", errors[0]);
		table.addValue("error_height",   errors[1]);
		table.addValue("error_x",        errors[2]);
		table.addValue("error_y",    	 errors[3]);
		table.addValue("error_sigma",    errors[4]);
		
		double errorFwhm = errors[4] * SIGMA_TO_FWHM;
		
		table.addValue("error_fwhm",  	 errorFwhm);
		
		table.addValue("slice", slice);
		
		table.addValue("r_squared", rSquared);
		table.addValue("residual_ssq", ssq);
		
	}
	
	@Override
	public int setup(String arg, ImagePlus imp) {
		
		if (arg.equals("final")) {
			table.show("Results");
			return DONE;
		}
		
		this.imp = imp;
		table = Analyzer.getResultsTable();
		
		if (table == null) {
			table = new ResultsTable();
			Analyzer.setResultsTable(table);
		}
		
		return flags;
	}

	@Override
	public boolean dialogItemChanged(GenericDialog dialog, AWTEvent e) {
		
		sigma = dialog.getNextNumber();
		minRSquared = dialog.getNextNumber();
		
		return sigma > 0 && minRSquared >= 0;
	}

	@Override
	public void setNPasses(int arg0) {
		
	}

	@Override
	public int showDialog(ImagePlus imp, String arg, PlugInFilterRunner pfr) {
		
		GenericDialog dialog = new GenericDialog("Peak Fitter");
		
		dialog.addNumericField("Sigma", sigma, 0);
		dialog.addNumericField("Minimum_r_squared", minRSquared, 2);
		
		dialog.addDialogListener(this);
		dialog.showDialog();
		
		if (dialog.wasCanceled())
			return DONE;
		
		flags = IJ.setupDialog(imp, flags);
		return flags;
	}

}
