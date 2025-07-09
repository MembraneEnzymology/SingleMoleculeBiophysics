package util;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

public class DuplicateView implements PlugIn {

	@Override
	public void run(String arg0) {
		
		ImagePlus imp = IJ.getImage();
		imp.show();	// make sure the image is visible and on top
		
		if (imp != null) {

			ImageWindow window = imp.getWindow();
			ImageCanvas canvas = imp.getCanvas();
			
			if (window != null) {
				
				try {
					Robot robot = new Robot();
					Rectangle screenRect = canvas.getBounds();
					screenRect.x += window.getBounds().x;
					screenRect.y += window.getBounds().y;
					
					BufferedImage image = robot.createScreenCapture(screenRect);
					
					ImagePlus duplicateImp = IJ.createImage(imp.getTitle() + "-duplicate", image.getWidth(), image.getHeight(), 1, 24);
					ImageProcessor duplicateIp = duplicateImp.getProcessor();
					
					for (int y = 0; y < image.getHeight(); y++) {
						for (int x = 0; x < image.getWidth(); x++) {
							int rgb = image.getRGB(x, y);
							duplicateIp.putPixel(x, y, rgb);
						}
					}
					
					duplicateImp.show();
					
				}
				catch (AWTException e) {
					IJ.log(e.getMessage());
				}
				
			}
			
		}
		
	}

	
}
