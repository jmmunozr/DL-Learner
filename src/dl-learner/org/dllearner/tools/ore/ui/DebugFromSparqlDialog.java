package org.dllearner.tools.ore.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.dllearner.tools.ore.OREApplication;
import org.dllearner.tools.ore.sparql.IncrementalInconsistencyFinder;
import org.dllearner.tools.ore.sparql.SPARQLProgressMonitor;
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator;
import org.protege.editor.core.ui.list.MList;
import org.protege.editor.core.ui.list.MListItem;
import org.protege.editor.core.ui.list.MListSectionHeader;
import org.protege.editor.core.ui.util.ComponentFactory;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import com.clarkparsia.owlapi.explanation.PelletExplanation;

public class DebugFromSparqlDialog extends JDialog implements ActionListener, PropertyChangeListener, DocumentListener, SPARQLProgressMonitor {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6379950242859458507L;
	
	public static final int CANCEL_RETURN_CODE = 0;
	public static final int OK_RETURN_CODE = 1;
	
	private int returnCode;
	
	private JButton okButton = null;
	@SuppressWarnings("unused")
	private JButton cancelButton = null;
	
	private JComboBox comboBox;
	private JTextField defaultGraphField;
	
	private JButton searchStopButton;
	private JButton addNamespaceButton;
	private JButton deleteNamespaceButton;
	
	private JLabel messageLabel;
	private String progressMessage;
	private int progress;
	private boolean canceled;
	private JProgressBar progressBar;
	
	private JCheckBox useLinkedDataCheckBox;
	private JCheckBox useCacheCheckBox;
	private JCheckBox restrictNamespacesCheckBox;
	
	private JList linkedDataNamespaceslist;
    private DefaultListModel linkedDataNamespaceslistModel;
    private JScrollPane linkedDataNamespaceslistScrollPane;

	private MList namespacesList;
	private IncrementalInconsistencyFinder inc;
	
	private JPanel optionsPanel;
	private JToggleButton optionsButton;
	private ImageIcon toggledIcon = new ImageIcon(OREApplication.class.getResource("toggled.gif"));
	private ImageIcon untoggledIcon = new ImageIcon(OREApplication.class.getResource("untoggled.gif"));
	
	private List<String> namespaces = new ArrayList<String>();
	
	private static final String URL_HELP_TEXT = "<html><table border=\"1\">" +
			"<tr>" +
			"<th>SPARQL endpoint URL</th>" +
			"<th>The URL of the SPARQL endpoint</th>" +
			"</tr>" +
			"<tr>" +
			"<th>Default graph URI</th>" +
			"<th>Absolute URL of RDF data source(s) to populate the background graph</th>" +
			"</tr>							" +
			"</table></html>"; 
	
	private OntologyExtractingTask extractTask;
	private ProgressMonitor mon;
	private Timer t;
	
	private Map<URI, List<String>> endpointToDefaultGraph;
	
	public DebugFromSparqlDialog(JFrame owner) {
		super(owner, "Search for inconsistencies at SPARQL endpoint", true);
		getLocale();
		Locale.setDefault(Locale.ENGLISH);
		
		// Create the controls
		createControls();
		//create main panel
		createSparqlPanel();
		//add predefined endpoints
//		addPredefinedEndpoints();
		positionErrorDialog(owner);
		addPredefinedEndpoints();
	}
	 
	private void createControls() {
		getContentPane().setLayout(new BorderLayout());

		// Create the dialog buttons
		// Create a box to hold the buttons - to give the right spacing between
		// them
		Box buttonBox = Box.createHorizontalBox();

		// Create a panel to hold a box with the buttons in it - to give it the
		// right space around them
		JPanel buttonPanel = new JPanel();
		buttonPanel.add(buttonBox);
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// Create the buttons and add them to the box (leading strut will give
		// the dialog box its width)
		buttonBox.add(okButton = createButton("Ok", 'o'));
		okButton.setEnabled(false);
		buttonBox.add(Box.createHorizontalGlue());
		buttonBox.add(Box.createHorizontalStrut(4));
		buttonBox.add(cancelButton = createButton("Cancel", 'c'));
		buttonBox.add(Box.createHorizontalStrut(10));

		// Add the button panel to the bottom of the BorderLayout
		getContentPane().add(buttonPanel, BorderLayout.SOUTH);
	}
	 
	private void createSparqlPanel() {
		JPanel panel = new JPanel();
		GridBagConstraints c = new GridBagConstraints();
		panel.setLayout(new GridBagLayout());
		c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		c.gridwidth = GridBagConstraints.REMAINDER;

		JPanel endPointHolderPanel = new JPanel();
		endPointHolderPanel.setLayout(new GridLayout(0, 1));
//		endPointHolderPanel.setBorder(new TitledBorder("SPARQL endpoint"));
		comboBox = new JComboBox();
		comboBox.setEditable(true);
		comboBox.setActionCommand("endpoints");
		comboBox.addActionListener(this);
		((JTextComponent)comboBox.getEditor().getEditorComponent()).getDocument().addDocumentListener(this);
		AutoCompleteDecorator.decorate(this.comboBox);
		
		endPointHolderPanel.add(new JLabel("URL"));
		endPointHolderPanel.add(comboBox);
		defaultGraphField = new JTextField();
		endPointHolderPanel.add(new JLabel("Default graph URI (optional)"));
		endPointHolderPanel.add(defaultGraphField);
		HelpablePanel endPointHelpPanel = new HelpablePanel(endPointHolderPanel);
		endPointHelpPanel.setHelpText(URL_HELP_TEXT);
		endPointHelpPanel.setBorder(new TitledBorder("SPARQL endpoint"));
		panel.add(endPointHelpPanel, c);

		searchStopButton = createButton("Search", 's');
		searchStopButton.setEnabled(false);
		c.fill = GridBagConstraints.NONE;
		panel.add(searchStopButton, c);
		
		progressBar = new JProgressBar();
		panel.add(progressBar, c);
		
		messageLabel = new JLabel("");
		panel.add(messageLabel, c);
		
		optionsButton = new JToggleButton(new AbstractAction("Advanced options") {

			/**
			 * 
			 */
			private static final long serialVersionUID = -4395104616001102604L;

			@Override
			public void actionPerformed(ActionEvent e) {
				JToggleButton button = (JToggleButton) e.getSource();

				if (!button.getModel().isSelected()) {
					collapseOptionsPanel();
				} else {
					expandOptionsPanel();
				}

			}
		});
		optionsButton.setIcon(untoggledIcon);
		optionsButton.setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
		optionsButton.setHorizontalAlignment(JButton.LEADING); // optional
		optionsButton.setBorderPainted(false);
		optionsButton.setContentAreaFilled(false);
		optionsButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				optionsButton.setBorderPainted(true);
				optionsButton.setContentAreaFilled(true);
			};
			@Override
			public void mouseExited(MouseEvent e) {
				optionsButton.setBorderPainted(false);
				optionsButton.setContentAreaFilled(false);
			}
		
		});
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.WEST;
		panel.add(optionsButton, c);
        
		c.anchor = GridBagConstraints.CENTER;
		c.fill = GridBagConstraints.HORIZONTAL;
		optionsPanel = createSPARQLOptionsPanel();
		panel.add(optionsPanel, c);
		optionsPanel.setVisible(false);
		
		JLabel padding = new JLabel();
		c.weighty = 1.0;
		panel.add(padding, c);
		getContentPane().add(panel, BorderLayout.CENTER);
	}
	
	private JPanel createSPARQLOptionsPanel(){
		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		
		c.gridwidth = GridBagConstraints.REMAINDER;
		
		useCacheCheckBox = new JCheckBox("Use cache");
		panel.add(useCacheCheckBox, c);
		
		c.gridwidth = 1;
		useLinkedDataCheckBox = new JCheckBox("Retrieve remote RDF data available as Linked Data");
		useLinkedDataCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				restrictNamespacesCheckBox.setEnabled(useLinkedDataCheckBox.isSelected());
//				linkedDataNamespaceslist.setEnabled(restrictNamespacesCheckBox.isSelected() && useLinkedDataCheckBox.isSelected());
//				addNamespaceButton.setEnabled(restrictNamespacesCheckBox.isSelected() && useLinkedDataCheckBox.isSelected());
//				deleteNamespaceButton.setEnabled(restrictNamespacesCheckBox.isSelected() && useLinkedDataCheckBox.isSelected());
			}
		});
		panel.add(useLinkedDataCheckBox, c);
		
		c.gridwidth = GridBagConstraints.REMAINDER;
		restrictNamespacesCheckBox = new JCheckBox("Restrict namespaces to:");
		restrictNamespacesCheckBox.setEnabled(false);
		restrictNamespacesCheckBox.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
//				linkedDataNamespaceslist.setEnabled(restrictNamespacesCheckBox.isSelected() && useLinkedDataCheckBox.isSelected());
//				addNamespaceButton.setEnabled(restrictNamespacesCheckBox.isSelected() && useLinkedDataCheckBox.isSelected());
//				deleteNamespaceButton.setEnabled(restrictNamespacesCheckBox.isSelected() && useLinkedDataCheckBox.isSelected());
			}
		});
		panel.add(restrictNamespacesCheckBox, c);
		c.weightx = 1.0;
		c.gridx = 1;
//		linkedDataNamespaceslistModel = new DefaultListModel();
//		linkedDataNamespaceslist = new JList(linkedDataNamespaceslistModel);
//		linkedDataNamespaceslist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//		linkedDataNamespaceslist.setSelectedIndex(0);
//		linkedDataNamespaceslist.setVisibleRowCount(5);
//		linkedDataNamespaceslist.setEnabled(false);
//		linkedDataNamespaceslistScrollPane = new JScrollPane(linkedDataNamespaceslist);
//		linkedDataNamespaceslistScrollPane.setVisible(false);
//        c.fill = GridBagConstraints.HORIZONTAL;
//        panel.add(linkedDataNamespaceslistScrollPane, c);
//        
//        Box buttonBox = Box.createHorizontalBox();
//        addNamespaceButton = createButton("Add", 'a');
//        addNamespaceButton.setEnabled(false);
//        buttonBox.add(addNamespaceButton);
//        
//        buttonBox.add(Box.createHorizontalGlue());
//		buttonBox.add(Box.createHorizontalStrut(4));
//        deleteNamespaceButton = createButton("Delete", 'a');
//        deleteNamespaceButton.setEnabled(false);
//        buttonBox.add(deleteNamespaceButton);
//        
//        JPanel buttonPanel = new JPanel();
//		buttonPanel.add(buttonBox);
//        c.anchor = GridBagConstraints.WEST;
//        c.fill = GridBagConstraints.HORIZONTAL;
//        panel.add(buttonPanel, c);
        
        
        ///////////////////
        JPanel namespacesHolder = new JPanel(new BorderLayout());
        namespacesHolder.setBorder(ComponentFactory.createTitledBorder("Namespaces"));
        namespacesList = new MList() {
            /**
             * 
             */
            private static final long serialVersionUID = 6590889767286900162L;


            protected void handleAdd() {
                addURI();
            }


            protected void handleDelete() {
                deleteSelectedBookmark();
            }
        };

        namespacesList.setCellRenderer(new NamespaceItemListRenderer());
        namespacesHolder.add(new JScrollPane(namespacesList));
        panel.add(new JScrollPane(namespacesList), c);
        fillList();
        
		return panel;
	}
	
	private void addPredefinedEndpoints(){
		endpointToDefaultGraph = new HashMap<URI, List<String>>();
		endpointToDefaultGraph.put(URI.create("http://dbpedia-live.openlinksw.com/sparql/"), Collections.singletonList("http://dbpedia.org"));	
//		endpointToDefaultGraph.put(URI.create("http://localhost:8890/sparql"), Collections.singletonList("http://opencyc2.org"));	
		for(URI url : endpointToDefaultGraph.keySet()){
			comboBox.addItem(url.toString());
		}		
	}
	
	private void positionErrorDialog(JFrame owner) {
		if (owner == null || !owner.isVisible()) {
			Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
			setLocation(screenDimension.width / 2 - getSize().width / 2, screenDimension.height / 2 - getSize().height / 2);
		}
	}
	 

	private void expandOptionsPanel(){
		optionsButton.setIcon(toggledIcon);
		optionsPanel.setVisible(true);
	}

	private void collapseOptionsPanel(){
		optionsButton.setIcon(untoggledIcon);
		optionsPanel.setVisible(false);
	}
	 
	 private JButton createButton (String label, char mnemonic)  {
			//  Create the new button object
			JButton newButton = new JButton (label);
			newButton.setActionCommand(label);
			
			newButton.setPreferredSize (new Dimension (90, 30));
			newButton.setMargin (new Insets (2, 2, 2, 2));
			
			if (mnemonic != '\0')  {
				//  Specify the button's mnemonic
				newButton.setMnemonic (mnemonic);
			}
			
			//  Setup the dialog to listen to events
			newButton.addActionListener (this);
			
			return newButton;
	}
	 
	 public int showDialog(){
		 setSize(700, 600);
		 setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);	
		 SwingUtilities.invokeLater(new Runnable() {
			
			@Override
			public void run() {
				 Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
				setLocation(screenSize.width / 2 - getWidth() / 2,
                        screenSize.height / 2 - getHeight() / 2);
//				 setVisible(true);
				
			}
		});
		 setVisible(true);
		 return returnCode;
	 }
	 	
	private void searchInconsistency() {
		canceled = false;
		messageLabel.setText("Checking SPARQL endpoint availability");
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		mon = new ProgressMonitor(this, "Extracting fragment", "", 0, 100);
		mon.setMillisToDecideToPopup(0);
		mon.setMillisToPopup(0);
		mon.getAccessibleContext().getLocale();
		
		
		t = new Timer(1000,new ActionListener() {
			
			
			@Override
			public void actionPerformed(ActionEvent e) {
				
				if(mon.isCanceled()){
					extractTask.cancel(true);
					setCursor(null);
					t.stop();
					
				}
				
			}
		});
		t.start();
		
		extractTask = new OntologyExtractingTask(this, this);
		extractTask.addPropertyChangeListener(this);
		extractTask.execute();
		
		
		
	}
	
	private void stopSearching() {
		canceled = true;
		setCursor(Cursor.getDefaultCursor());
//		extractTask.cancel(true);
	}
	
	@SuppressWarnings("unused")
	private boolean URLExists(){
		try {
			HttpURLConnection.setFollowRedirects(false);
			HttpURLConnection con = (HttpURLConnection)new URL(comboBox.getSelectedItem().toString()).openConnection();
			con.setRequestMethod("HEAD");
			return con.getResponseCode() == HttpURLConnection.HTTP_OK;
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private boolean urlIsConnectable()
	{	
		
	
		URL url = null;
		try {
//			List<String> defaultGraphURIS = new ArrayList<String>(1);
//			defaultGraphURIS.add(defaultGraphField.getText());	
//			SparqlEndpoint endpoint = new SparqlEndpoint(new URL(comboBox.getSelectedItem().toString()), defaultGraphURIS, Collections.<String>emptyList());
//			url = new URL(endpoint.getHTTPRequest() + "SELECT * WHERE {?s ?p ?o} LIMIT 1");
			url = new URL((String)comboBox.getSelectedItem());
		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	   try {
		if(url.openConnection().getContentLength() > 0){
			   return true;
		   } else {
			   return false;
		   }
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
		return false;
	}
	
	private boolean urlIsValid(){
		Document urlDoc = ((JTextComponent)comboBox.getEditor().getEditorComponent()).getDocument();
		String url = "";
		try {
			url = urlDoc.getText(0, urlDoc.getLength());
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(url.toLowerCase().startsWith("http://")){
			return true;
		} else {
			return false;
		}
	}
	
	private void setExtractButtonEnabledToValidInput(){		
		searchStopButton.setEnabled(urlIsValid());
	}
	
	private void showSearchButton(){
		searchStopButton.setText("Search");
		searchStopButton.setActionCommand("Search");
	}
	
	private void showStopButton(){
		searchStopButton.setText("Stop");
		searchStopButton.setActionCommand("Stop");
	}
	
	
	public OWLOntology getOWLOntology(){
		return inc.getOntology();
	}
	
	private class OntologyExtractingTask extends SwingWorker<Void, Void>{
		
		private SPARQLProgressMonitor mon;
		private JDialog dialog;
		
		public OntologyExtractingTask(JDialog dialog, SPARQLProgressMonitor mon) {		
			this.mon = mon;
			this.dialog = dialog;
		}

		@Override
		public Void doInBackground() {
			if(urlIsConnectable()){
				messageLabel.setText("Successfully connected to SPARQL endpoint");
				
			} else {
				messageLabel.setText("<html><font color=\"red\">Could not connect to SPARQL endpoint</html>");
				cancel(true);
			}	
			messageLabel.setText("Searching ...");
			String endpointURI = comboBox.getSelectedItem().toString();
			String defaultGraphURI = defaultGraphField.getText();
			try {
				inc = new IncrementalInconsistencyFinder();
				inc.setUseLinkedData(useLinkedDataCheckBox.isSelected());
				if(restrictNamespacesCheckBox.isSelected()){
					inc.setLinkedDataNamespaces(new HashSet<String>(namespaces));
				} else {
					inc.setLinkedDataNamespaces(Collections.<String>emptySet());
				}
				inc.setUseCache(useCacheCheckBox.isSelected());
				inc.setProgressMonitor(mon);
				inc.run(endpointURI, defaultGraphURI);
			} catch (OWLOntologyCreationException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			

			return null;
		}

		@Override
		public void done() {
			showSearchButton();
			dialog.setCursor(Cursor.getDefaultCursor());
			messageLabel.setText(!inc.isConsistent() ? "Inconsistency detected" : "No inconsistency detected");
			if(!isCancelled()){
				okButton.setEnabled(true);
			} else if(isCancelled()){
				System.out.println("Canceled");
			}
		}
		
		
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getActionCommand().equals("Search")){
			showStopButton();
			canceled = false;
			searchInconsistency();
		} else if(e.getActionCommand().equals("Stop")){
			showSearchButton();
			stopSearching();
		} else if(e.getActionCommand().equals("Ok")){
			returnCode = OK_RETURN_CODE;
			closeDialog();
		} else if(e.getActionCommand().equals("Cancel")){
			canceled = true;
			returnCode = CANCEL_RETURN_CODE;
			closeDialog();
		} else if(e.getActionCommand().equals("Add")){ 
			String s = (String)JOptionPane.showInputDialog(
                    this,
                    "Enter linked data namespace",
                    "Enter namespace",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    "");
			if(s != null){
				linkedDataNamespaceslistModel.addElement(s);
				linkedDataNamespaceslistScrollPane.setVisible(true);
				validate();
			}
		} else if(e.getActionCommand().equals("Delete")){ 
			if(linkedDataNamespaceslist.getSelectedValue() != null){
				linkedDataNamespaceslistModel.removeElement(linkedDataNamespaceslist.getSelectedValue());
				if(linkedDataNamespaceslistModel.isEmpty()){
					linkedDataNamespaceslistScrollPane.setVisible(false);
					validate();
				}
				
			}
		} else if(e.getActionCommand().equals("endpoints")){
			messageLabel.setText("");
			JComboBox cb = (JComboBox)e.getSource();
			if(cb.getSelectedIndex() >= 0){
		        URI endpointURL = URI.create((String)cb.getSelectedItem());
		        List<String> defaultGraphs = endpointToDefaultGraph.get(endpointURL);
		        if(defaultGraphs != null && !defaultGraphs.isEmpty()){
		        	defaultGraphField.setText(defaultGraphs.iterator().next());
		        } else {
		        	defaultGraphField.setText("");
		        }
			}

		}
		
	}
	
	private void closeDialog(){
		setVisible(false);
		dispose();
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		 if ("progress" == evt.getPropertyName() ) {
			 if(mon.isCanceled()){
				 extractTask.cancel(true);
				 this.setCursor(null);
			 }
		 }		
	}

	@Override
	public void changedUpdate(DocumentEvent e) {
		setExtractButtonEnabledToValidInput();		
	}

	@Override
	public void insertUpdate(DocumentEvent e) {
		setExtractButtonEnabledToValidInput();		
	}

	@Override
	public void removeUpdate(DocumentEvent e) {
		setExtractButtonEnabledToValidInput();	
	}
	
	@Override
	public boolean isCancelled() {
		return canceled;
	}

	@Override
	public void setFinished() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setIndeterminate(boolean b) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setMessage(String message) {
		this.progressMessage = message;
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
//				messageLabel.setText(progressMessage);
			}
		});
	}

	@Override
	public void setProgress(long progressLenght) {
		this.progress = (int)progressLenght;
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				progressBar.setValue(progress);
			}
		});
		
	}

	@Override
	public void setSize(long size) {
		progressBar.setMaximum((int)size);
		
	}

	@Override
	public void setStarted() {
		// TODO Auto-generated method stub
		
	}	
	
	@Override
	public void inconsistencyFound(Set<OWLAxiom> explanation) {
		System.out.println("FOUND: " + explanation);
	}
	
	public static void main(String[] args){
		PelletExplanation.setup();
		DebugFromSparqlDialog dialog = new DebugFromSparqlDialog(null);
		dialog.showDialog();
	}
	
	private void addURI() {
        String namespace = JOptionPane.showInputDialog(this, "Please enter a namespace", "Namespace", JOptionPane.PLAIN_MESSAGE);
        if (namespace != null) {
        	namespaces.add(namespace);
            fillList();
        }
    }
	
	private void fillList() {
		ArrayList<Object> data = new ArrayList<Object>();

        data.add(new AddURIItem());
        for(String namespace : namespaces){
        	data.add(new NamspaceListItem(namespace));
        }
        namespacesList.setListData(data.toArray());
    }
	
	private void deleteSelectedBookmark() {
        Object selObj = namespacesList.getSelectedValue();
        if (!(selObj instanceof NamspaceListItem)) {
            return;
        }
        NamspaceListItem item = (NamspaceListItem) selObj;
        namespaces.remove(item.namespace);
        fillList();
    }
	
	private class NamespaceItemListRenderer extends DefaultListCellRenderer {

        /**
         * 
         */
        private static final long serialVersionUID = -833970269120392171L;

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof NamspaceListItem) {
            	NamspaceListItem item = (NamspaceListItem) value;
                label.setText(item.namespace);
            }
            return label;
        }
    }


    private class AddURIItem implements MListSectionHeader {

        public String getName() {
            return "Namespaces";
        }


        public boolean canAdd() {
            return true;
        }
    }


    private class NamspaceListItem implements MListItem {

        private String namespace;


        public NamspaceListItem(String namespace) {
            this.namespace = namespace;
        }


        public boolean isEditable() {
            return false;
        }


        public void handleEdit() {
        }


        public boolean isDeleteable() {
            return true;
        }


        public boolean handleDelete() {
            return true;
        }


        public String getTooltip() {
            return namespace;
        }
    }

	
}


