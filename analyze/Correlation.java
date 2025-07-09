package analyze;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

public class Correlation implements PlugInFilter {

	public static final int flags = DOES_32 | PARALLELIZE_STACKS;
	private ImageProcessor templateIp;
	private ImageStatistics templateIpStats;
	
	@Override
	public void run(ImageProcessor ip) {
		
		ImageProcessor duplicateIp = ip.duplicate();
		
		int cx = templateIp.getWidth() / 2;
		int cy = templateIp.getHeight() / 2;
		
		for (int y1 = 0; y1 < duplicateIp.getHeight(); y1++) {
			for (int x1 = 0; x1 < duplicateIp.getWidth(); x1++) {

				duplicateIp.setRoi(x1 - cx, y1 - cy, templateIp.getWidth(), templateIp.getHeight());
				ImageStatistics duplicateIpStats = duplicateIp.getStats();
				double corr = 0;
				
				for (int y2 = 0; y2 < templateIp.getHeight(); y2++) {
					for (int x2 = 0; x2 < templateIp.getWidth(); x2++) {
						
						corr += ((duplicateIp.getPixelValue((x1 + x2) - cx, (y1 + y2) - cy) - duplicateIpStats.mean)
								* (templateIp.getPixelValue(x2, y2) - templateIpStats.mean))
								/ (duplicateIpStats.stdDev * templateIpStats.stdDev);
						
					}
				}
				
				corr /= templateIp.getWidth() * templateIp.getHeight();
				ip.putPixelValue(x1, y1, corr);
				
			}
		}
		

	}

	@Override
	public int setup(String arg, ImagePlus imp) {
		
		String[] titles = WindowManager.getImageTitles();
		
		GenericDialog dialog = new GenericDialog("Correlate images");
		dialog.addChoice("Template", titles, titles[0]);
		dialog.showDialog();
		
		templateIp = WindowManager.getImage(dialog.getNextChoice()).getProcessor();
		templateIpStats = templateIp.getStats();
		
		return IJ.setupDialog(imp, flags);
		
	}

}
