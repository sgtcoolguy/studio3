/**
 * Aptana Studio
 * Copyright (c) 2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.snippets.ui.views;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;

import com.aptana.core.logging.IdeLog;
import com.aptana.core.util.StringUtil;
import com.aptana.editor.common.AbstractThemeableEditor;
import com.aptana.editor.common.CommonEditorPlugin;
import com.aptana.editor.common.ExtendedFastPartitioner;
import com.aptana.editor.common.IExtendedPartitioner;
import com.aptana.editor.common.IPartitioningConfiguration;
import com.aptana.editor.common.NullPartitionerSwitchStrategy;
import com.aptana.editor.common.scripting.QualifiedContentType;
import com.aptana.editor.common.scripting.snippets.SnippetTemplateUtil;
import com.aptana.editor.common.text.rules.CompositePartitionScanner;
import com.aptana.editor.common.text.rules.NullSubPartitionScanner;
import com.aptana.editor.common.util.EditorUtil;
import com.aptana.formatter.IScriptFormatterFactory;
import com.aptana.formatter.ScriptFormatterManager;
import com.aptana.formatter.preferences.profile.IProfileManager;
import com.aptana.formatter.preferences.profile.ProfileManager;
import com.aptana.formatter.ui.preferences.FormatterPreviewUtils;
import com.aptana.formatter.ui.preferences.ScriptSourcePreviewerUpdater;
import com.aptana.scripting.model.SnippetElement;
import com.aptana.scripting.model.TriggerType;
import com.aptana.scripting.ui.ScriptingUIPlugin;
import com.aptana.theme.ColorManager;
import com.aptana.theme.Theme;
import com.aptana.theme.ThemePlugin;
import com.aptana.ui.util.UIUtils;

/**
 * PopupDialog that displays the contents of a snippets, formatted and colored based on the preferences
 * 
 * @author nle
 */
public class SnippetPopupDialog extends PopupDialog
{
	private static final String SNIPPETS_POPUP_SETTINGS = "snippets.popup.settings"; //$NON-NLS-1$
	private ToolBar toolbar;
	private Control positionTarget, sizeTarget;
	private List<Image> toolbarImages = new ArrayList<Image>();
	private SnippetElement snippet;
	private ColorManager colorManager;
	private Point popupSize = null;
	private String tabChar;

	/**
	 * The pixel offset of the popup from the bottom corner of the control.
	 */
	private static final int POPUP_OFFSET = 3;

	/**
	 * Indicates that a chosen proposal should be inserted into the field.
	 */
	public static final int PROPOSAL_INSERT = 1;
	private QualifiedContentType translatedQualifiedType;
	private Composite toolbarComp;

	public SnippetPopupDialog(Shell shell, SnippetElement snippet, Control positionTarget, Control sizeTarget)
	{
		super(shell, PopupDialog.INFOPOPUPRESIZE_SHELLSTYLE, true, true, false, false, false, snippet.getDisplayName(),
				null);
		this.positionTarget = positionTarget;
		this.sizeTarget = sizeTarget;
		this.snippet = snippet;
		colorManager = new ColorManager();
		tabChar = Platform.getOS().equals(Platform.OS_MACOSX) ? "\u21E5" : "\u00bb"; //$NON-NLS-1$ //$NON-NLS-2$ 
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.PopupDialog#createContents(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	protected Control createContents(Composite parent)
	{
		Composite composite = new Composite(parent, SWT.BORDER);
		composite.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
		composite.setLayout(GridLayoutFactory.fillDefaults().create());

		return super.createContents(composite);
	}

	@Override
	protected Control createDialogArea(Composite parent)
	{
		Composite control = (Composite) super.createDialogArea(parent);

		Composite custom = new Composite(control, SWT.NONE);
		custom.setLayout(new FillLayout());
		custom.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());

		ISourceViewer snippetViewer = createSnippetViewer(custom);

		toolbarComp = new Composite(control, SWT.BORDER);
		toolbarComp.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_GRAY));
		toolbarComp.setLayout(GridLayoutFactory.fillDefaults().margins(2, 2).create());
		toolbarComp.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());

		toolbar = new ToolBar(toolbarComp, SWT.HORIZONTAL);
		toolbar.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_GRAY));
		toolbar.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());

		ToolItem openSnippetItem = new ToolItem(toolbar, SWT.PUSH);
		Image navigateImage = ScriptingUIPlugin.getImageDescriptor("/icons/full/elcl16/nav_snippet_tsk.png") //$NON-NLS-1$
				.createImage();
		toolbarImages.add(navigateImage);
		openSnippetItem.setImage(navigateImage);
		openSnippetItem.addSelectionListener(new SelectionAdapter()
		{
			/*
			 * (non-Javadoc)
			 * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
			 */
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				EditorUtil.openInEditor(new File(snippet.getPath()));
			}
		});

		return control;
	}

	@Override
	protected Control createTitleControl(Composite parent)
	{
		Control control = super.createTitleControl(parent);

		Label subText = new Label(parent, SWT.WRAP);
		String[] prefixes = snippet.getTriggerTypeValues(TriggerType.PREFIX);
		String[] formattedPrefixes = new String[prefixes.length];
		for (int i = 0; i < formattedPrefixes.length; i++)
		{
			formattedPrefixes[i] = MessageFormat.format("{0}{1}", prefixes[i], tabChar); //$NON-NLS-1$
		}

		String scopeString = snippet.getScope();
		if (scopeString == null)
		{
			scopeString = Messages.SnippetPopupDialog_Scope_None;
		}

		subText.setText(MessageFormat.format(Messages.SnippetPopupDialog_Desciption, scopeString,
				StringUtil.join(",", formattedPrefixes))); //$NON-NLS-1$
		subText.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());

		return control;
	}

	private ColorManager getColorManager()
	{
		return ThemePlugin.getDefault().getColorManager();
	}

	private Theme getCurrentTheme()
	{
		return ThemePlugin.getDefault().getThemeManager().getCurrentTheme();
	}

	@Override
	protected Color getBackground()
	{
		return getColorManager().getColor(getCurrentTheme().getBackground());

	}

	@Override
	protected Color getForeground()
	{
		return getColorManager().getColor(getCurrentTheme().getForeground());
	}

	@Override
	protected List getBackgroundColorExclusions()
	{
		List exclusions = super.getBackgroundColorExclusions();
		exclusions.add(toolbar);
		exclusions.add(toolbarComp);
		return exclusions;
	}

	private ISourceViewer createSnippetViewer(Composite parent)
	{
		ProjectionViewer viewer = new ProjectionViewer(parent, null, null, false, SWT.V_SCROLL | SWT.H_SCROLL);
		StyledText styledText = viewer.getTextWidget();
		styledText.setFont(JFaceResources.getTextFont());

		IScriptFormatterFactory factory = null;
		String contentType = getContentType();

		if (contentType != null)
		{
			factory = ScriptFormatterManager.getSelected(contentType);
		}

		IPreferenceStore generalTextStore = EditorsUI.getPreferenceStore();
		// TODO - Note that we pass the factory's preferences store and not calling to this.getPrefereceStore.
		// In case we decide to unify the preferences into the this plugin, we might need to change this.

		if (factory != null)
		{
			IPreferenceStore store = new ChainedPreferenceStore(new IPreferenceStore[] { factory.getPreferenceStore(),
					generalTextStore });

			SourceViewerConfiguration configuration = (SourceViewerConfiguration) factory
					.createSimpleSourceViewerConfiguration(colorManager, store, null, false);
			viewer.configure(configuration);
			new ScriptSourcePreviewerUpdater(viewer, configuration, store);
		}

		if (viewer.getTextWidget().getTabs() == 0)
		{
			viewer.getTextWidget().setTabs(4);
		}

		viewer.setEditable(false);
		IDocument document = new Document();
		viewer.setDocument(document);

		String expansion = snippet.getExpansion();

		if (expansion != null)
		{
			expansion = SnippetTemplateUtil.evaluateSnippet(snippet, document, new Position(0));
		}

		if (factory != null)
		{
			IPartitioningConfiguration partitioningConfiguration = (IPartitioningConfiguration) factory
					.getPartitioningConfiguration();
			CompositePartitionScanner partitionScanner = new CompositePartitionScanner(
					partitioningConfiguration.createSubPartitionScanner(), new NullSubPartitionScanner(),
					new NullPartitionerSwitchStrategy());
			IDocumentPartitioner partitioner = new ExtendedFastPartitioner(partitionScanner,
					partitioningConfiguration.getContentTypes());
			partitionScanner.setPartitioner((IExtendedPartitioner) partitioner);
			partitioner.connect(document);
			document.setDocumentPartitioner(partitioner);

			IProfileManager manager = ProfileManager.getInstance();
			IResource selectedResource = UIUtils.getSelectedResource();

			if (selectedResource == null)
			{
				IEditorPart activeEditor = UIUtils.getActiveEditor();
				if (activeEditor instanceof IEditorPart)
				{
					IEditorInput editorInput = ((IEditorPart) activeEditor).getEditorInput();
					if (editorInput instanceof IFileEditorInput)
					{
						selectedResource = ((IFileEditorInput) editorInput).getFile();
					}
				}
			}

			if (selectedResource != null)
			{
				FormatterPreviewUtils.updatePreview(viewer, expansion, null, factory,
						manager.getSelected(selectedResource.getProject()).getSettings());
			}
			else
			{
				document.set(expansion);
			}
		}
		else
		{
			document.set(expansion);
		}

		return viewer;
	}

	private String getContentType()
	{
		IEditorPart activeEditor = UIUtils.getActiveEditor();
		if (activeEditor instanceof AbstractThemeableEditor)
		{
			AbstractThemeableEditor abstractThemeableEditor = (AbstractThemeableEditor) activeEditor;

			ISourceViewer sourceViewer = abstractThemeableEditor.getISourceViewer();
			if (sourceViewer != null)
			{
				IDocument document = sourceViewer.getDocument();
				int caretOffset = abstractThemeableEditor.getCaretOffset();
				try
				{

					translatedQualifiedType = CommonEditorPlugin.getDefault().getDocumentScopeManager()
							.getContentType(document, caretOffset);
					if (translatedQualifiedType != null)
					{
						return extractContentType(translatedQualifiedType);
					}
				}
				catch (BadLocationException e)
				{
					IdeLog.logError(ScriptingUIPlugin.getDefault(), MessageFormat.format(
							"Caret offset {0} was out of bounds with a max of {1} for {2}", caretOffset, document.get() //$NON-NLS-1$
									.length(), abstractThemeableEditor.getPartName()),
							com.aptana.editor.common.IDebugScopes.PRESENTATION);
				}
			}
		}

		return null;
	}

	protected String extractContentType(QualifiedContentType qualifiedContentType)
	{
		if (qualifiedContentType == null)
		{
			return null;
		}
		int partCount = qualifiedContentType.getPartCount();
		if (partCount > 2)
		{
			return qualifiedContentType.getParts()[partCount - 2];
		}
		return qualifiedContentType.getParts()[0];
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.PopupDialog#getDialogSettings()
	 */
	@Override
	protected IDialogSettings getDialogSettings()
	{
		IDialogSettings dialogSettings = ScriptingUIPlugin.getDefault().getDialogSettings();
		if (dialogSettings != null)
		{
			IDialogSettings section = dialogSettings.getSection(SNIPPETS_POPUP_SETTINGS);
			if (section == null)
			{
				section = dialogSettings.addNewSection(SNIPPETS_POPUP_SETTINGS);
			}

			return section;
		}

		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.PopupDialog#close()
	 */
	@Override
	public boolean close()
	{
		boolean willClose = super.close();
		if (willClose)
		{
			for (Image image : toolbarImages)
			{
				image.dispose();
			}
			colorManager.dispose();
		}

		return willClose;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.PopupDialog.adjustBounds()
	 */
	protected void adjustBounds()
	{
		// Get our control's location in display coordinates.
		Point location = positionTarget.getDisplay()
				.map(positionTarget.getParent(), null, positionTarget.getLocation());
		Point targetSize = positionTarget.getSize();
		Point sizeSize = sizeTarget.getSize();
		int initialX = location.x + targetSize.x;
		int initialY = location.y + POPUP_OFFSET;

		if (popupSize == null)
		{
			getShell().pack();
			popupSize = getShell().getSize();
			if (popupSize.x > 300)
			{
				popupSize.x = 300;
			}

			if (popupSize.y > sizeSize.y)
			{
				popupSize.y = sizeSize.y;
			}
		}

		// Constrain to the display
		Rectangle constrainedBounds = getConstrainedShellBounds(new Rectangle(initialX, initialY, popupSize.x,
				popupSize.y));

		// If there has been an adjustment causing the popup to overlap
		// with the control, then put the popup above the control.
		if (constrainedBounds.y < initialY)
		{
			getShell().setBounds(initialX, location.y - popupSize.y, popupSize.x, popupSize.y);
		}
		else
		{
			getShell().setBounds(initialX, initialY, popupSize.x, popupSize.y);
		}
	}
}