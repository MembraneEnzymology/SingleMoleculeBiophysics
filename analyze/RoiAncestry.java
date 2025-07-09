package analyze;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.io.RoiEncoder;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageStatistics;

public class RoiAncestry implements PlugIn, ActionListener, MouseListener {

	private DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
	private DefaultTreeModel treeModel = new DefaultTreeModel(root);
	private JTree ancestryTree = new JTree(treeModel);
	private ArrayList<RoiTreeNode> nodes = new ArrayList<RoiTreeNode>();
	
	private ImagePlus imp;
	private JFrame frame;	
	private JToggleButton addButton = new JToggleButton("Add");
	private JButton removeButton = new JButton("Remove");
	private JButton saveButton = new JButton("Save");
	private JButton openButton = new JButton("Open");
	private JButton measureButton = new JButton("Measure");
	
	class RoiTreeNode extends DefaultMutableTreeNode {
		private static final long serialVersionUID = 1L;
		
		public RoiTreeNode(Roi roi) {
			super(roi);
		}

		@Override
		public String toString() {
			
			return getRoi().getName();
		}
		
		public Roi getRoi() {
			return (Roi)getUserObject();
		}
		
	}
	
	private void createFrame() {
		
		addButton.addActionListener(this);
		removeButton.addActionListener(this);
		openButton.addActionListener(this);
		saveButton.addActionListener(this);
		measureButton.addActionListener(this);
		
		frame = new JFrame("Ancestry");
		frame.setLayout(new BorderLayout());
		
		JPanel panel = new JPanel();
		panel.add(addButton);
		panel.add(removeButton);
		panel.add(openButton);
		panel.add(saveButton);
		panel.add(measureButton);
		
		
		frame.add(panel, BorderLayout.NORTH);
		frame.add(new JScrollPane(ancestryTree), BorderLayout.CENTER);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		frame.pack();
		frame.setVisible(true);
		
	}
	
	public void addChild(Roi parent, Roi child) {
		
		// check if child is already in tree
		for (RoiTreeNode node: nodes) {
			if (node.getRoi() == child)
				return;		// child is already in tree so we don't do anything
		}
		
		DefaultMutableTreeNode parentNode = root;
		
		if (parent != null) {
			for (RoiTreeNode node: nodes) {
				if (node.getRoi() == parent)
					parentNode = node;
			}
		}
		
		RoiTreeNode childNode = new RoiTreeNode(child);
		treeModel.insertNodeInto(childNode, parentNode, 0);
		nodes.add(childNode);
		
		child.setStrokeColor(Color.CYAN);
		imp.updateAndDraw();
	}
	
	public void removeNode(RoiTreeNode node) {
		nodes.remove(node);
		for (int i = 0; i < node.getChildCount(); i++)
			removeNode((RoiTreeNode)node.getChildAt(i));
	}
	
	public void removeChild(Roi child) {
		
		DefaultMutableTreeNode parentNode = null;
		DefaultMutableTreeNode childNode = null;
		
		for (RoiTreeNode node: nodes) {
			if (node.getRoi() == child) {
				treeModel.removeNodeFromParent(node);
				parentNode = (DefaultMutableTreeNode)node.getParent();
				childNode = node;
				
				removeNode(node);
			}
		}
		
		if (parentNode != null)
			parentNode.remove(childNode);
		
		child.setStrokeColor(Roi.getColor());
		imp.updateAndDraw();
	}
	
	public String getTreeAsCSV() {
		String csv = "";
		
		for (RoiTreeNode node: nodes) {
			String parentName = node.getParent().toString();
			String childName = node.toString();
			csv += String.format("%s,%s\n", parentName, childName);
		}
		
		return csv;
	}
	
	@Override
	public void run(String arg0) {

		imp = IJ.getImage();
		imp.getWindow().getCanvas().addMouseListener(this);
		
		// make sure the roiManager shows names instead of labels
		IJ.runMacro("roiManager(\"UseNames\", \"true\"); roiManager(\"Show All with labels\"); ");
		
		createFrame();
		
	}

	private void remove() {
		DefaultMutableTreeNode child = (DefaultMutableTreeNode)ancestryTree.getLastSelectedPathComponent();
		
		if (child != null && child != root)
			removeChild(((RoiTreeNode)child).getRoi());
	}
	
	private void save() {
		
		RoiManager roiManager = RoiManager.getInstance();
		
		if (roiManager != null) {
			
			JFileChooser fileChooser = new JFileChooser();
			int option = fileChooser.showSaveDialog(frame);
			
			if (option == JFileChooser.APPROVE_OPTION) {
				
				try {
					File f = fileChooser.getSelectedFile();
					ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(f));
					Roi[] rois = roiManager.getRoisAsArray();
					
					for (Roi roi: rois) {
						ZipEntry entry = new ZipEntry(roi.getName() + ".roi");
						zos.putNextEntry(entry);
						zos.write(RoiEncoder.saveAsByteArray(roi));
						zos.closeEntry();
					}
					
					ZipEntry entry = new ZipEntry("ancestry.csv");
					zos.putNextEntry(entry);
					zos.write(getTreeAsCSV().getBytes());
					zos.closeEntry();
					
					zos.flush();
					zos.close();
					
					
				} catch (Exception e) {
					IJ.log(e.getMessage());
				}
				
			}
			
		}
	}
	
	private void open() {
		
		JFileChooser fileChooser = new JFileChooser();
		int option = fileChooser.showOpenDialog(frame);
		
		if (option == JFileChooser.APPROVE_OPTION) {
			
			IJ.open(fileChooser.getSelectedFile().getPath());
			
			try {
				
				ZipInputStream zis = new ZipInputStream(new FileInputStream(fileChooser.getSelectedFile()));
				ZipEntry entry;
				
				while ((entry = zis.getNextEntry()) != null) {
					
					if (entry.getName().equals("ancestry.csv")) {
						
						root.removeAllChildren();
						treeModel.reload();
						
						RoiManager roiManager = RoiManager.getInstance();
						HashMap<String, Roi> nameRoi = new HashMap<String, Roi>();
						
						for (Roi roi: roiManager.getRoisAsArray())
							nameRoi.put(roi.getName(), roi);
						
						int len;
						byte[] b = new byte[1024];
						String csv = "";
						
						while ((len = zis.read(b)) != -1)
							csv += new String(b, 0, len);
						
						for (String line: csv.split("\\n")) {
							
							String[] columns = line.split(",");
							String parent = columns[0];
							String child = columns[1];
							
							Roi parentRoi = nameRoi.get(parent);
							Roi childRoi = nameRoi.get(child);
							
							addChild(parentRoi, childRoi);
						}
						
					}
					
				}
				
				zis.close();
			}
			catch (Exception e) {
				IJ.log(e.getMessage());
			}
			
		}
		
	}
	
	private void measure() {
		
		String[] images = WindowManager.getImageTitles();
		
		GenericDialog dialog = new GenericDialog("Measure");
		dialog.addChoice("Image", images, images[0]);
		dialog.addStringField("Results table title", "Results");
		dialog.showDialog();
		
		if (dialog.wasCanceled())
			return;
		
		IJ.selectWindow(dialog.getNextChoice());
		ImagePlus measureImp = IJ.getImage();
		String title = dialog.getNextString();
		
		ResultsTable table = Analyzer.getResultsTable();
		
		if (table == null || !title.equals("Results"))
			table = new ResultsTable();
		
		table.reset();
		
		
		//IJ.log("number of slices : " + measureImp.getNSlices());
		
		for (int r = 0; r < measureImp.getNSlices(); r++)
			table.setValue("slice", r, r + 1);
		
		for (TreeNode node: nodes) {
			
			if (node.getChildCount() == 0) {
				
				//IJ.log("measure trace from node : " + node);
				
				double[] values = new double[measureImp.getNSlices()];
				
				for (int i = 0; i < values.length; i++)
					values[i] = -1;
				
				String name = ((RoiTreeNode)node).getRoi().getName();
				
				while (node != root) {
					
					//IJ.log("node : " + node);
					
					RoiTreeNode roiNode = (RoiTreeNode)node;
					Roi roi = roiNode.getRoi();
					
					measureImp.setSlice(roi.getPosition());
					measureImp.setRoi(roi);
					
					ImageStatistics statistics = measureImp.getStatistics();
					values[roi.getPosition() - 1] = statistics.mean;
					
					node = node.getParent();
				}
				
				for (int r = 0; r < measureImp.getNSlices(); r++)
					table.setValue(name, r, values[r]);
				
			}
			
		}
		
		table.show(title);
		
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		
		if (e.getSource() == removeButton)
			remove();
		else if (e.getSource() == saveButton)
			save();
		else if (e.getSource() == openButton)
			open();
		else if (e.getSource() == measureButton)
			measure();
		
	}

	public void selectRoi(Roi roi) {
		
		for (RoiTreeNode node: nodes) {
			if (node.getRoi() == roi)
				ancestryTree.setSelectionPath(new TreePath(node.getPath()));
		}
		
	}
	
	public void addRoi(Roi roi) {
		
		Object selectedNode = ancestryTree.getLastSelectedPathComponent();
		Roi parentRoi = null;
		Roi childRoi = roi;
		
		if (selectedNode != null && selectedNode != root)
			parentRoi = ((RoiTreeNode) selectedNode).getRoi();
		
		addChild(parentRoi, childRoi);
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {
		
		if (addButton.isSelected()) {
			
			ImageCanvas canvas = imp.getWindow().getCanvas();
			Point p = canvas.getCursorLoc();
			int x = (int)p.getX();
			int y = (int)p.getY();
			int slice = imp.getSlice();
			
			RoiManager roiManager = RoiManager.getInstance();
			
			if (roiManager != null) {
				Roi[] rois = roiManager.getRoisAsArray();
				
				for (int i = 0; i < rois.length; i++) {
					
					if (rois[i].getPosition() == slice && rois[i].contains(x, y)) {
						addRoi(rois[i]);
						
						if (e.getButton() == MouseEvent.BUTTON2 || e.isAltDown())	// middle mouse button 
							selectRoi(rois[i]);
						
					}
				}
			}
		}
	}

	private String lastToolName = "";
	
	@Override
	public void mouseEntered(MouseEvent e) {
		if (addButton.isSelected()) {
			lastToolName = IJ.getToolName();
			IJ.setTool(Toolbar.CROSSHAIR);
		}
	}

	@Override
	public void mouseExited(MouseEvent e) {
		IJ.setTool(lastToolName);
	}

	@Override
	public void mousePressed(MouseEvent e) {
	}

	@Override
	public void mouseReleased(MouseEvent e) {
	}
	
}
