package org.eclipse.uide.editor;

/*
 * Licensed Materials - Property of IBM,
 * (c) Copyright IBM Corp. 1998, 2004  All Rights Reserved
 */

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;

import lpg.lpgjavaruntime.IToken;
import lpg.lpgjavaruntime.PrsStream;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.text.*;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.text.formatter.IContentFormatter;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlinkPresenter;
import org.eclipse.jface.text.information.IInformationPresenter;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.IPresentationRepairer;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.jface.text.source.projection.ProjectionAnnotationModel;
import org.eclipse.jface.text.source.projection.ProjectionSupport;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.texteditor.BasicTextEditorActionContributor;
import org.eclipse.ui.texteditor.ContentAssistAction;
import org.eclipse.ui.texteditor.IEditorStatusLine;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.MarkerAnnotation;
import org.eclipse.ui.texteditor.TextEditorAction;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.eclipse.uide.core.ErrorHandler;
import org.eclipse.uide.core.Language;
import org.eclipse.uide.core.LanguageRegistry;
import org.eclipse.uide.internal.editor.FoldingController;
import org.eclipse.uide.internal.editor.OutlineController;
import org.eclipse.uide.internal.editor.PresentationController;
import org.eclipse.uide.internal.util.ExtensionPointFactory;
import org.eclipse.uide.parser.IModelListener;
import org.eclipse.uide.parser.IParseController;
import org.eclipse.uide.runtime.RuntimePlugin;

/**
 * An Eclipse editor. This editor is not enhanced using API. Instead, we publish extension points for outline, content assist, hover help, etc.
 * 
 * Credits go to Martin Kersten and Bob Foster for guiding the good parts of this design. Sole responsiblity for the bad parts rest with Chris Laffra.
 * 
 * @author Chris Laffra
 * @author Robert M. Fuhrer
 */
public class UniversalEditor extends TextEditor {
    public static final String EDITOR_ID= RuntimePlugin.UIDE_RUNTIME + ".universalEditor";

    protected Language fLanguage;

    protected ParserScheduler fParserScheduler;

    protected HoverHelpController fHoverHelpController;

    protected OutlineController fOutlineController;

    protected PresentationController fPresentationController;

    protected CompletionProcessor fCompletionProcessor;

    protected IHyperlinkDetector fHyperLinkDetector;

    protected IAutoEditStrategy fAutoEditStrategy;

    private IFoldingUpdater fFoldingUpdater;

    private ProjectionAnnotationModel fAnnotationModel;

    private static final String BUNDLE_FOR_CONSTRUCTED_KEYS= "org.eclipse.uide.editor.messages";//$NON-NLS-1$

    private static ResourceBundle fgBundleForConstructedKeys= ResourceBundle.getBundle(BUNDLE_FOR_CONSTRUCTED_KEYS);

    /**
     * Essentially a clone of the class of the same name from JDT/UI, for
     * navigating from one annotation to the next/previous in a source file.
     * @author rfuhrer
     */
    private static class GotoAnnotationAction extends TextEditorAction {
	public static final String JAVA_UI_ID_PLUGIN= "org.eclipse.jdt.ui";

	public static final String PREFIX= JAVA_UI_ID_PLUGIN + '.';

	private static final String nextAnnotationContextID= PREFIX + "goto_next_error_action";

	private static final String prevAnnotationContextID= PREFIX + "goto_previous_error_action";

	private boolean fForward;

	public GotoAnnotationAction(String prefix, boolean forward) {
	    super(fgBundleForConstructedKeys, prefix, null);
	    fForward= forward;
	    if (forward)
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, nextAnnotationContextID);
	    else
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, prevAnnotationContextID);
	}

	public void run() {
	    UniversalEditor e= (UniversalEditor) getTextEditor();

	    e.gotoAnnotation(fForward);
	}

	public void setEditor(ITextEditor editor) {
	    if (editor instanceof UniversalEditor)
		super.setEditor(editor);
	    update();
	}

	public void update() {
	    setEnabled(getTextEditor() instanceof UniversalEditor);
	}
    }

    public static class TextEditorActionContributor extends BasicTextEditorActionContributor {
	private GotoAnnotationAction fNextAnnotation;

	private GotoAnnotationAction fPreviousAnnotation;

	public TextEditorActionContributor() {
	    super();
	    fPreviousAnnotation= new GotoAnnotationAction("PreviousAnnotation.", false); //$NON-NLS-1$
	    fNextAnnotation= new GotoAnnotationAction("NextAnnotation.", true); //$NON-NLS-1$
	}

	public void init(IActionBars bars, IWorkbenchPage page) {
	    super.init(bars, page);
	    bars.setGlobalActionHandler(ITextEditorActionDefinitionIds.GOTO_NEXT_ANNOTATION, fNextAnnotation);
	    bars.setGlobalActionHandler(ITextEditorActionDefinitionIds.GOTO_PREVIOUS_ANNOTATION, fPreviousAnnotation);
	    bars.setGlobalActionHandler(ActionFactory.NEXT.getId(), fNextAnnotation);
	    bars.setGlobalActionHandler(ActionFactory.PREVIOUS.getId(), fPreviousAnnotation);
	}
	public void setActiveEditor(IEditorPart part) {
	    super.setActiveEditor(part);

	    ITextEditor textEditor= null;

	    if (part instanceof ITextEditor)
		textEditor= (ITextEditor) part;
	    fPreviousAnnotation.setEditor(textEditor);
	    fNextAnnotation.setEditor(textEditor);
	}
    }

    public UniversalEditor() {
	setSourceViewerConfiguration(new Configuration());
	configureInsertMode(SMART_INSERT, true);
	setInsertMode(SMART_INSERT);
    }

    public Object getAdapter(Class required) {
	if (IContentOutlinePage.class.equals(required)) {
	    return fOutlineController;
	}
	return super.getAdapter(required);
    }

    protected void createActions() {
	super.createActions();
	Action action= new ContentAssistAction(ResourceBundle.getBundle("org.eclipse.uide.editor.messages"),
		"ContentAssistProposal.", this);
	action.setActionDefinitionId(ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS);
	setAction("ContentAssistProposal", action);
	markAsStateDependentAction("ContentAssistProposal", true);
    }

    /**
     * Sets the given message as error message to this editor's status line.
     *
     * @param msg message to be set
     */
    protected void setStatusLineErrorMessage(String msg) {
	IEditorStatusLine statusLine= (IEditorStatusLine) getAdapter(IEditorStatusLine.class);
	if (statusLine != null)
	    statusLine.setMessage(true, msg, null);
    }

    /**
     * Sets the given message as message to this editor's status line.
     *
     * @param msg message to be set
     * @since 3.0
     */
    protected void setStatusLineMessage(String msg) {
	IEditorStatusLine statusLine= (IEditorStatusLine) getAdapter(IEditorStatusLine.class);
	if (statusLine != null)
	    statusLine.setMessage(false, msg, null);
    }

    /**
     * Jumps to the next enabled annotation according to the given direction.
     * An annotation type is enabled if it is configured to be in the
     * Next/Previous tool bar drop down menu and if it is checked.
     *
     * @param forward <code>true</code> if search direction is forward, <code>false</code> if backward
     */
    public void gotoAnnotation(boolean forward) {
	ITextSelection selection= (ITextSelection) getSelectionProvider().getSelection();
	Position position= new Position(0, 0);

	if (false /* delayed - see bug 18316 */) {
	    getNextAnnotation(selection.getOffset(), selection.getLength(), forward, position);
	    selectAndReveal(position.getOffset(), position.getLength());
	} else /* no delay - see bug 18316 */{
	    Annotation annotation= getNextAnnotation(selection.getOffset(), selection.getLength(), forward, position);

	    setStatusLineErrorMessage(null);
	    setStatusLineMessage(null);
	    if (annotation != null) {
		updateAnnotationViews(annotation);
		selectAndReveal(position.getOffset(), position.getLength());
		setStatusLineMessage(annotation.getText());
	    }
	}
    }

    /**
     * Returns the annotation closest to the given range respecting the given
     * direction. If an annotation is found, the annotations current position
     * is copied into the provided annotation position.
     *
     * @param offset the region offset
     * @param length the region length
     * @param forward <code>true</code> for forwards, <code>false</code> for backward
     * @param annotationPosition the position of the found annotation
     * @return the found annotation
     */
    private Annotation getNextAnnotation(final int offset, final int length, boolean forward, Position annotationPosition) {
	Annotation nextAnnotation= null;
	Position nextAnnotationPosition= null;
	Annotation containingAnnotation= null;
	Position containingAnnotationPosition= null;
	boolean currentAnnotation= false;

	IDocument document= getDocumentProvider().getDocument(getEditorInput());
	int endOfDocument= document.getLength();
	int distance= Integer.MAX_VALUE;

	IAnnotationModel model= getDocumentProvider().getAnnotationModel(getEditorInput());
	for(Iterator e= model.getAnnotationIterator(); e.hasNext();) {
	    Annotation a= (Annotation) e.next();
	    //	    if ((a instanceof IJavaAnnotation) && ((IJavaAnnotation) a).hasOverlay() || !isNavigationTarget(a))
	    //		continue;
	    if (!(a instanceof MarkerAnnotation))
		continue;

	    Position p= model.getPosition(a);
	    if (p == null)
		continue;

	    if (forward && p.offset == offset || !forward && p.offset + p.getLength() == offset + length) {// || p.includes(offset)) {
		if (containingAnnotation == null
			|| (forward && p.length >= containingAnnotationPosition.length || !forward
				&& p.length >= containingAnnotationPosition.length)) {
		    containingAnnotation= a;
		    containingAnnotationPosition= p;
		    currentAnnotation= p.length == length;
		}
	    } else {
		int currentDistance= 0;

		if (forward) {
		    currentDistance= p.getOffset() - offset;
		    if (currentDistance < 0)
			currentDistance= endOfDocument + currentDistance;

		    if (currentDistance < distance || currentDistance == distance && p.length < nextAnnotationPosition.length) {
			distance= currentDistance;
			nextAnnotation= a;
			nextAnnotationPosition= p;
		    }
		} else {
		    currentDistance= offset + length - (p.getOffset() + p.length);
		    if (currentDistance < 0)
			currentDistance= endOfDocument + currentDistance;

		    if (currentDistance < distance || currentDistance == distance && p.length < nextAnnotationPosition.length) {
			distance= currentDistance;
			nextAnnotation= a;
			nextAnnotationPosition= p;
		    }
		}
	    }
	}
	if (containingAnnotationPosition != null && (!currentAnnotation || nextAnnotation == null)) {
	    annotationPosition.setOffset(containingAnnotationPosition.getOffset());
	    annotationPosition.setLength(containingAnnotationPosition.getLength());
	    return containingAnnotation;
	}
	if (nextAnnotationPosition != null) {
	    annotationPosition.setOffset(nextAnnotationPosition.getOffset());
	    annotationPosition.setLength(nextAnnotationPosition.getLength());
	}

	return nextAnnotation;
    }

    /**
     * Updates the annotation views that show the given annotation.
     *
     * @param annotation the annotation
     */
    private void updateAnnotationViews(Annotation annotation) {
	IMarker marker= null;
	if (annotation instanceof MarkerAnnotation)
	    marker= ((MarkerAnnotation) annotation).getMarker();
	else
	//        if (annotation instanceof IJavaAnnotation) {
	//	    Iterator e= ((IJavaAnnotation) annotation).getOverlaidIterator();
	//	    if (e != null) {
	//		while (e.hasNext()) {
	//		    Object o= e.next();
	//		    if (o instanceof MarkerAnnotation) {
	//			marker= ((MarkerAnnotation) o).getMarker();
	//			break;
	//		    }
	//		}
	//	    }
	//	}

	if (marker != null /*&& !marker.equals(fLastMarkerTarget)*/) {
	    try {
		boolean isProblem= marker.isSubtypeOf(IMarker.PROBLEM);
		IWorkbenchPage page= getSite().getPage();
		IViewPart view= page.findView(isProblem ? IPageLayout.ID_PROBLEM_VIEW : IPageLayout.ID_TASK_LIST); //$NON-NLS-1$  //$NON-NLS-2$
		if (view != null) {
		    Method method= view.getClass().getMethod(
			    "setSelection", new Class[] { IStructuredSelection.class, boolean.class }); //$NON-NLS-1$
		    method.invoke(view, new Object[] { new StructuredSelection(marker), Boolean.TRUE });
		}
	    } catch (CoreException x) {
	    } catch (NoSuchMethodException x) {
	    } catch (IllegalAccessException x) {
	    } catch (InvocationTargetException x) {
	    }
	    // ignore exceptions, don't update any of the lists, just set status line
	}
    }

    public void createPartControl(Composite parent) {
	fLanguage= LanguageRegistry.findLanguage(getEditorInput());

	// Create the hyperlink language service before calling super, since that will
	// try to configure the hyperlink detector via the SourceViewerConfiguration.
	if (fLanguage != null) {
	    fHyperLinkDetector= (IHyperlinkDetector) createExtensionPoint("hyperlink");
	    fFoldingUpdater= (IFoldingUpdater) createExtensionPoint("foldingUpdater");
	}

	super.createPartControl(parent);

	if (fLanguage != null) {
	    try {
		fOutlineController= new OutlineController(this);
		fPresentationController= new PresentationController(getSourceViewer());
		fPresentationController.damage(0, getSourceViewer().getDocument().getLength());
		fParserScheduler= new ParserScheduler("Universal Editor Parser");

		if (false && fFoldingUpdater != null) {
		    ProjectionViewer viewer= (ProjectionViewer) getSourceViewer();
		    ProjectionSupport projectionSupport= new ProjectionSupport(viewer, getAnnotationAccess(), getSharedColors());

		    projectionSupport.install();
		    viewer.doOperation(ProjectionViewer.TOGGLE);
		    fAnnotationModel= viewer.getProjectionAnnotationModel();
		    fParserScheduler.addModelListener(new FoldingController(fAnnotationModel, fFoldingUpdater));
		}

		fOutlineController.setLanguage(fLanguage);
		fPresentationController.setLanguage(fLanguage);
		fCompletionProcessor.setLanguage(fLanguage);
		fHoverHelpController.setLanguage(fLanguage);

		fParserScheduler.addModelListener(fOutlineController);
		fParserScheduler.addModelListener(fPresentationController);
		fParserScheduler.addModelListener(fCompletionProcessor);
		fParserScheduler.addModelListener(fHoverHelpController);
		fParserScheduler.run(new NullProgressMonitor());
	    } catch (Exception e) {
		ErrorHandler.reportError("Could not create part", e);
	    }
	}
    }

    protected ISourceViewer createSourceViewer(Composite parent, IVerticalRuler ruler, int styles) {
	if (fFoldingUpdater == null)
	    return super.createSourceViewer(parent, ruler, styles);

	fAnnotationAccess= createAnnotationAccess();
	fOverviewRuler= createOverviewRuler(getSharedColors());

	ISourceViewer viewer= new ProjectionViewer(parent, ruler, getOverviewRuler(), isOverviewRulerVisible(), styles);
	// ensure decoration support has been created and configured.
	getSourceViewerDecorationSupport(viewer);

	return viewer;
    }

    protected void doSetInput(IEditorInput input) throws CoreException {
	super.doSetInput(input);
	setInsertMode(SMART_INSERT);
    }

    private Object createExtensionPoint(String extensionPoint) {
	return ExtensionPointFactory.createExtensionPoint(fLanguage, RuntimePlugin.UIDE_RUNTIME, extensionPoint);
    }

    /**
     * Add a Model listener to this editor. Anytime the underlying AST is recomputed, the listener is notified.
     * 
     * @param listener the listener to notify of Model changes
     */
    public void addModelListener(IModelListener listener) {
	fParserScheduler.addModelListener(listener);
    }

    class Configuration extends SourceViewerConfiguration {
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
	    PresentationReconciler reconciler= new PresentationReconciler();
	    reconciler.setRepairer(new PresentationRepairer(), IDocument.DEFAULT_CONTENT_TYPE);
	    return reconciler;
	}

	public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
	    ContentAssistant ca= new ContentAssistant();
	    fCompletionProcessor= new CompletionProcessor();
	    ca.setContentAssistProcessor(fCompletionProcessor, IDocument.DEFAULT_CONTENT_TYPE);
	    ca.setInformationControlCreator(getInformationControlCreator(sourceViewer));
	    return ca;
	}

	public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType) {
	    return fHoverHelpController= new HoverHelpController();
	}

	public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
	    if (fLanguage != null)
		return (IAnnotationHover) createExtensionPoint("annotationHover");
	    else
		return super.getAnnotationHover(sourceViewer);
	}

	public IAutoEditStrategy[] getAutoEditStrategies(ISourceViewer sourceViewer, String contentType) {
	    if (fLanguage != null)
		fAutoEditStrategy= (IAutoEditStrategy) createExtensionPoint("autoEditStrategy");

	    if (fAutoEditStrategy == null)
		fAutoEditStrategy= super.getAutoEditStrategies(sourceViewer, contentType)[0];

	    return new IAutoEditStrategy[] { fAutoEditStrategy };
	}

	public IContentFormatter getContentFormatter(ISourceViewer sourceViewer) {
	    return super.getContentFormatter(sourceViewer);
	}

	public String[] getDefaultPrefixes(ISourceViewer sourceViewer, String contentType) {
	    return super.getDefaultPrefixes(sourceViewer, contentType);
	}

	public ITextDoubleClickStrategy getDoubleClickStrategy(ISourceViewer sourceViewer, String contentType) {
	    return super.getDoubleClickStrategy(sourceViewer, contentType);
	}

	public IHyperlinkDetector[] getHyperlinkDetectors(ISourceViewer sourceViewer) {
	    if (fHyperLinkDetector != null)
		return new IHyperlinkDetector[] { fHyperLinkDetector };
	    return super.getHyperlinkDetectors(sourceViewer);
	}

	public IHyperlinkPresenter getHyperlinkPresenter(ISourceViewer sourceViewer) {
	    return super.getHyperlinkPresenter(sourceViewer);
	}

	public String[] getIndentPrefixes(ISourceViewer sourceViewer, String contentType) {
	    return super.getIndentPrefixes(sourceViewer, contentType);
	}

	public IInformationControlCreator getInformationControlCreator(ISourceViewer sourceViewer) {
	    return super.getInformationControlCreator(sourceViewer);
	}

	public IInformationPresenter getInformationPresenter(ISourceViewer sourceViewer) {
	    return super.getInformationPresenter(sourceViewer);
	}

	public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType, int stateMask) {
	    return super.getTextHover(sourceViewer, contentType, stateMask);
	}

	public IUndoManager getUndoManager(ISourceViewer sourceViewer) {
	    return super.getUndoManager(sourceViewer);
	}

	public IAnnotationHover getOverviewRulerAnnotationHover(ISourceViewer sourceViewer) {
	    return super.getOverviewRulerAnnotationHover(sourceViewer);
	}
    }

    class PresentationRepairer implements IPresentationRepairer {
	IDocument document;

	public void createPresentation(TextPresentation presentation, ITypedRegion damage) {
	    try {

		if (fPresentationController != null) {
		    PrsStream parseStream= fParserScheduler.parseController.getParser().getParseStream();
		    int damagedToken= fParserScheduler.parseController.getTokenIndexAtCharacter(damage.getOffset());
		    IToken[] adjuncts= parseStream.getFollowingAdjuncts(damagedToken);
		    int endOffset= (adjuncts.length == 0) ? parseStream.getEndOffset(damagedToken)
			    : adjuncts[adjuncts.length - 1].getEndOffset();
		    int length= endOffset - damage.getOffset();
		    fPresentationController.damage(damage.getOffset(), (length > damage.getLength() ? length : damage
			    .getLength()));
		}
		if (fParserScheduler != null) {
		    fParserScheduler.cancel();
		    fParserScheduler.schedule();
		}
	    } catch (Exception e) {
		ErrorHandler.reportError("Could not repair damage ", e);
	    }
	}

	public void setDocument(IDocument document) {
	    this.document= document;
	}
    }

    class CompletionProcessor implements IContentAssistProcessor, IModelListener {
	private final IContextInformation[] NO_CONTEXTS= new IContextInformation[0];

	private ICompletionProposal[] NO_COMPLETIONS= new ICompletionProposal[0];

	private IParseController parseController;

	private IContentProposer contentProposer;

	public CompletionProcessor() {}

	public void setLanguage(Language language) {
	    contentProposer= (IContentProposer) ExtensionPointFactory.createExtensionPoint(language,
		    RuntimePlugin.UIDE_RUNTIME, "contentProposer");
	}

	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
	    try {
		if (parseController != null && contentProposer != null) {
		    return contentProposer.getContentProposals(parseController, offset);
		}
	    } catch (Throwable e) {
		ErrorHandler.reportError("Universal Editor Error", e);
	    }
	    return NO_COMPLETIONS;
	}

	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
	    return NO_CONTEXTS;
	}

	public char[] getCompletionProposalAutoActivationCharacters() {
	    return null;
	}

	public char[] getContextInformationAutoActivationCharacters() {
	    return null;
	}

	public IContextInformationValidator getContextInformationValidator() {
	    return null;
	}

	public String getErrorMessage() {
	    return null;
	}

	public void update(IParseController parseResult, IProgressMonitor monitor) {
	    this.parseController= parseResult;
	}
    }

    /*
     * Parsing may take a long time, and is not done inside the UI thread. Therefore, we create a job that is executed in a background thread by the
     * platform's job service.
     */
    class ParserScheduler extends Job {
	protected IParseController parseController;

	protected List astListeners= new ArrayList();

	ParserScheduler(String name) {
	    super(name);
	    setSystem(true); // do not show this job in the Progress view
	    parseController= (IParseController) createExtensionPoint("parser");
	}

	protected IStatus run(IProgressMonitor monitor) {
	    try {
		IDocument document= getDocumentProvider().getDocument(getEditorInput());
		// Don't need to retrieve the AST; we don't need it.
		// Just make sure the document contents gets parsed once (and only once).
		parseController.parse(document.get(), false, monitor);
		if (!monitor.isCanceled())
		    notifyAstListeners(parseController, monitor);
		//		else
		//			System.out.println("Bypassed AST listeners (cancelled).");
	    } catch (Exception e) {
		ErrorHandler.reportError("Error running parser for " + fLanguage, e);
	    }
	    return Status.OK_STATUS;
	}

	public void addModelListener(IModelListener listener) {
	    astListeners.add(listener);
	}

	public void notifyAstListeners(IParseController parseController, IProgressMonitor monitor) {
	    if (parseController != null)
		for(int n= astListeners.size() - 1; n >= 0; n--)
		    ((IModelListener) astListeners.get(n)).update(parseController, monitor);
	}
    }

    class HoverHelpController implements ITextHover, IModelListener {
	private IParseController controller;

	private IHoverHelper hoverHelper;

	public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
	    return new Region(offset, 0);
	}

	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
	    try {
		if (controller != null && hoverHelper != null)
		    return hoverHelper.getHoverHelpAt(controller, hoverRegion.getOffset());
	    } catch (Throwable e) {
		ErrorHandler.reportError("Universal Editor Error", e);
	    }
	    return null;
	}

	public void update(IParseController controller, IProgressMonitor monitor) {
	    this.controller= controller;
	}

	public void setLanguage(Language language) {
	    hoverHelper= (IHoverHelper) ExtensionPointFactory.createExtensionPoint(language, RuntimePlugin.UIDE_RUNTIME,
		    "hoverHelper");
	}
    }
}
