package org.eclipse.uide.preferences;


import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.JFaceColors;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.internal.dialogs.ProductInfoDialog;
import org.eclipse.uide.preferences.fields.SafariStringFieldEditor;


public class DetailsDialogForStringFields extends ProductInfoDialog {

	Shell parent = null;
	SafariStringFieldEditor field = null;
	Composite fieldHolder = null;
	ISafariPreferencesService preferencesService = null;
	
	private SafariPreferencesUtilities prefUtils = null;
	
	public DetailsDialogForStringFields (
			Shell parent,
			SafariStringFieldEditor field,
			Composite fieldHolder,
			ISafariPreferencesService preferencesService)
	{
        super(parent);
        
        this.parent = parent;
        this.field = field;
        this.fieldHolder = fieldHolder;
        this.preferencesService = preferencesService;
        this.prefUtils = new SafariPreferencesUtilities(this.preferencesService);	
	}

    	private final static int COPY_ID = IDialogConstants.CLIENT_ID + 1;
	    private final static int REMOVE_ID = IDialogConstants.CLIENT_ID + 2;
    	private final static int SPECIAL_ID = IDialogConstants.CLIENT_ID + 3;
	    private final static int EMPTY_ID = IDialogConstants.CLIENT_ID + 4;


	    
	    /*
	     * Method declared on Dialog.
	     * 
	     * Here mostly assumes that if a button has been pushed then it's okay to
	     * perform the associated action (although there are a few consistency checks).
	     */
	    protected void buttonPressed(int buttonId) {
	        switch (buttonId) {
	        case COPY_ID:
	        	if (!field.isInherited()) break;
	        	String value = field.getTextControl().getText();
	        	prefUtils.setField(field, fieldHolder, value);
		        //field.store();
	            break;
	        case REMOVE_ID:
	        	// Can't clear a field that doesn't have a preference stored to start with;
	        	// the following is one way to check that.
	        	// Why would you do this if there weren't a preference stored?  May want to
	        	// clear a field that was edited (or otherwise set) locally but not yet stored.
	        	if (field.getPreferencesLevel().equals(field.getLevelFromWhichLoaded())) {
	        		preferencesService.clearPreferenceAtLevel(field.getPreferencesLevel(), field.getPreferenceName());
	        	}
	        	prefUtils.setField(field, fieldHolder);
	        	break;
	        case SPECIAL_ID:
	        	prefUtils.setField(field, fieldHolder, field.getSpecialValue());
		        //field.store();
	            break;
	        case EMPTY_ID:
	        	prefUtils.setField(field, fieldHolder, field.getEmptyValue());
		        //field.store();
	        	break;
	        default:
	            super.buttonPressed(buttonId);
	            break;
	        }
	        close();
	    }

	    public boolean close() {
	        return super.close();
	    }

	    /*
	     * (non-Javadoc) Method declared on Window.
	     */
	    protected void configureShell(Shell newShell) {
	        super.configureShell(newShell);
	        newShell.setText(NLS.bind("Details for ''" + field.getLabelText() + "''", null ));
	        //PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell,
			//		IWorkbenchHelpContextIds.ABOUT_DIALOG);
	    }

	    protected Button copyButton = null;
	    protected Button specialButton = null;
	    protected Button emptyButton = null;
	    Button removeButton = null;
	    
	    /**
	     * Add buttons to the dialog's button bar.
	     * 
	     * Subclasses should override.
	     * 
	     * @param parent
	     *            the button bar composite
	     */
	    protected void createButtonsForButtonBar(Composite parent) {
	        parent.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
       
	        boolean enabled;
        
	        if (!field.getPreferencesLevel().equals(ISafariPreferencesService.DEFAULT_LEVEL)) {
	        	copyButton = createButton(parent, COPY_ID, PreferenceDialogConstants.COPY_LABEL, false);
	        	copyButton.setEnabled(field.getPreferencesLevel() != null &&
	        			field.isInherited() &&	
	        			field.getTextControl().isEnabled() && field.getTextControl().getEditable());
	        }
	        
	        specialButton = createButton(parent, SPECIAL_ID, PreferenceDialogConstants.SPECIAL_LABEL, false);
	        specialButton.setEnabled(field.getPreferencesLevel() != null &&
	        	field.hasSpecialValue() && field.getSpecialValue() != null &&	
	        	field.getTextControl().isEnabled() && field.getTextControl().getEditable());
	        
	        emptyButton = createButton(parent, EMPTY_ID, PreferenceDialogConstants.EMPTY_LABEL, false);
	        emptyButton.setEnabled(field.getPreferencesLevel() != null &&
		        	field.isEmptyStringAllowed() && field.getEmptyValue() != null && 
		        	field.getTextControl().isEnabled() && field.getTextControl().getEditable());
	        
	        removeButton = createButton(parent, REMOVE_ID, PreferenceDialogConstants.REMOVE_LABEL, false);
	        removeButton.setEnabled(
	        		(field.getPreferencesLevel() != null) &&
	        		!field.isInherited() &&
	        		field.isRemovable()  && 
		        	field.getTextControl().isEnabled() && field.getTextControl().getEditable());

	        Label l = new Label(parent, SWT.NONE);
	        l.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	        GridLayout layout = (GridLayout) parent.getLayout();
	        layout.numColumns++;
	        layout.makeColumnsEqualWidth = false;

	        Button b = createButton(parent, IDialogConstants.OK_ID,
	                IDialogConstants.OK_LABEL, true);
	        b.setFocus();
	    }

	    
	    protected Composite workArea = null;
	    protected Composite dialogAreaParent = null;
	    
	    /**
	     * Creates and returns the contents of the upper part 
	     * of the dialog (above the button bar).
	     *
	     * Subclasses should overide.
	     *
	     * @param parent  the parent composite to contain the dialog area
	     * @return the dialog area control
	     */
	    protected Control createDialogArea(Composite parent) {
	    	
	    	dialogAreaParent = parent;
	    	
	        final Cursor hand = new Cursor(parent.getDisplay(), SWT.CURSOR_HAND);
	        final Cursor busy = new Cursor(parent.getDisplay(), SWT.CURSOR_WAIT);
	        setHandCursor(hand);
	        setBusyCursor(busy);
	        getShell().addDisposeListener(new DisposeListener() {
	            public void widgetDisposed(DisposeEvent e) {
	                setHandCursor(null);
	                hand.dispose();
	                setBusyCursor(null);
	                busy.dispose();
	            }
	        });

	        
	        // Create a composite which is the parent of the top area and the bottom
	        // button bar.  This allows there to be a second child of the parent with 
	        // a banner background on top but not on the bottom

	        Composite workArea = new Composite(parent, SWT.NONE);
	        workArea = fillInWorkArea(workArea);
	        return workArea;
	    }
	        	
	     
	    private Composite fillInWorkArea(Composite workArea) {
	    	if (workArea.isDisposed()) System.err.println("fillInWorkArea:  workArea is disposed!!!");	
	        GridLayout workLayout = new GridLayout();
	        workLayout.marginHeight = 0;
	        workLayout.marginWidth = 0;
	        workLayout.verticalSpacing = 0;
	        workLayout.horizontalSpacing = 0;
	        workArea.setLayout(workLayout);
	        workArea.setLayoutData(new GridData(GridData.FILL_BOTH));

	        // page group
	        Color background = JFaceColors.getBannerBackground(parent.getDisplay());
	        Color foreground = JFaceColors.getBannerForeground(parent.getDisplay());
	        Composite top = (Composite) super.createDialogArea(workArea);

	        // override any layout inherited from createDialogArea 
	        GridLayout layout = new GridLayout();
	        layout.marginHeight = 0;
	        layout.marginWidth = 0;
	        layout.verticalSpacing = 0;
	        layout.horizontalSpacing = 0;
	        top.setLayout(layout);
	        top.setLayoutData(new GridData(GridData.FILL_BOTH));
	        top.setBackground(background);
	        top.setForeground(foreground);

	        // the image & text	
	        Composite topContainer = new Composite(top, SWT.NONE);
	        topContainer.setBackground(background);
	        topContainer.setForeground(foreground);
	        
	        layout = new GridLayout();
	        layout.numColumns = (1);
	        layout.marginWidth = 0;
	        layout.marginHeight = 0;
	        layout.verticalSpacing = 0;
	        layout.horizontalSpacing = 0;
	        topContainer.setLayout(layout);
	        GridData data = new GridData();
	        data.horizontalAlignment = GridData.FILL;
	        data.grabExcessHorizontalSpace = true;
	        topContainer.setLayoutData(data);

	        String levelString = null;
	        if (field.getPreferencesLevel() == null) {	
	        	levelString = "Details as currently applicable";
	        	if (preferencesService.getProject() != null) {
	        		levelString += " (including project preferences):  ";
	        	} else {
	        		levelString += " (not including any project preferences):  ";
	        	}
	        }
	        else if (field.getPreferencesLevel().equals(ISafariPreferencesService.INSTANCE_LEVEL))
	        	levelString = PreferenceDialogConstants.INSTANCE_LEVEL_STRING;
	        else if (field.getPreferencesLevel().equals(ISafariPreferencesService.CONFIGURATION_LEVEL))
	        	levelString = PreferenceDialogConstants.CONFIGURATION_LEVEL_STRING;
	        else if (field.getPreferencesLevel().equals(ISafariPreferencesService.DEFAULT_LEVEL))
	        	levelString = PreferenceDialogConstants.DEFAULT_LEVEL_STRING;
	        else if (field.getPreferencesLevel().equals(ISafariPreferencesService.PROJECT_LEVEL))
	        	levelString = PreferenceDialogConstants.PROJECT_LEVEL_STRING;
	        else
	        	levelString = PreferenceDialogConstants.UNKNOWN_LEVEL_STRING + field.getPreferencesLevel();
	        
	        Label label = null;
	        
	        label = new Label(topContainer, SWT.LEAD);
	        label.setText(levelString);
	        label.setBackground(prefUtils.colorWhite);
	        
	        label = new Label(topContainer, SWT.LEAD);
	        label.setText("\tCurrent value:  '" + field.getTextControl().getText() + "'");
	        label.setBackground(prefUtils.colorWhite);
	        
	        label = new Label(topContainer, SWT.LEAD);
	        String level = PreferenceDialogConstants.getLevelName(field.getLevelFromWhichLoaded()); 
	        label.setText("\tLevel at which set:  " + level);
	        label.setBackground(prefUtils.colorWhite);
	        
	            
	        label = new Label(topContainer, SWT.LEAD);
	        if (field.isEmptyStringAllowed()) {
		        label.setText(PreferenceDialogConstants.EMPTY_OK	);
	        } else {
		        label.setText(PreferenceDialogConstants.EMPTY_NOT_OK);
	        }
	        label.setBackground(prefUtils.colorWhite);
	        
        	label = new Label(topContainer, SWT.LEAD);
	        if (field.hasSpecialValue()) {
	        	label.setText(PreferenceDialogConstants.HAS_SPECIAL + field.getSpecialValue());
	        } else {
	        	label.setText(PreferenceDialogConstants.NO_SPECIAL);
	        }
	        label.setBackground(prefUtils.colorWhite);
	        
	        if ((field.getPreferencesLevel() != null) && field.getPreferencesLevel().equals(field.getLevelFromWhichLoaded())) {
	        	label = new Label(topContainer, SWT.LEAD);
		        if (field.isRemovable()) {
		        	label.setText(PreferenceDialogConstants.IS_REMOVABLE);
		        } else {
		        	label.setText(PreferenceDialogConstants.NOT_REMOVABLE); 
		        }
	        }
	        label.setBackground(prefUtils.colorWhite);
	        
	        label = new Label(top, SWT.LEAD);
	        label.setText("~~~");
	        label.setVisible(false);
	        
	        
	        this.workArea = workArea;
	        return workArea;
	    }
	    
}