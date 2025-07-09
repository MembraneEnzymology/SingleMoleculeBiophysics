package analyze;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.FFT;
import ij.plugin.FFTMath;
import ij.plugin.PlugIn;

/**
 * This plugin corrects for drift correction using (fft)correlation between the first and the n-th slice.
 * 
 * @author C.M. Punter
 *
 */
public class FFTDriftCorrection implements PlugIn {

	
	
	@Override
	public void run(String arg0) {
		
		ImagePlus imp = IJ.getImage();
		
		if (imp == null || imp.getNSlices() == 1) {
			IJ.showMessage("This plugin requires an image stack!");
			return;
		}
		
		FFT fft = new FFT();
		ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
		
		for (int slice = 1; slice <= imp.getNSlices(); slice++) {
			imp.setSlice(slice);
			fft.run("fft");
			stack.addSlice(IJ.getImage().getProcessor());
			IJ.getImage().close();
		}
		
		ImagePlus firstImp = new ImagePlus("FFT of first", stack.getProcessor(1));
		ImagePlus fftImp = new ImagePlus("FFT of stack", stack);
		
		FFTMath math = new FFTMath();
		
		for (int slice = 2; slice <= imp.getNSlices(); slice++) {
			fftImp.setSlice(slice);
			math.doMath(firstImp, fftImp);
			
		}
		
		
		fftImp.show();
		
		
		
		
		
		
	}
	
	
	
	
	
	

}
