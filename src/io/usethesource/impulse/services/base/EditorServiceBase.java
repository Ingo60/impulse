/*******************************************************************************
* Copyright (c) 2008 IBM Corporation.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*    Stan Sutton (suttons@us.ibm.com) - initial API and implementation

*******************************************************************************/


package io.usethesource.impulse.services.base;

import org.eclipse.core.runtime.IProgressMonitor;

import io.usethesource.impulse.editor.UniversalEditor;
import io.usethesource.impulse.parser.IParseController;
import io.usethesource.impulse.services.IEditorService;

public abstract class EditorServiceBase implements IEditorService {

	protected String name = null;
	
	protected UniversalEditor editor = null;
	
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	

	public void setEditor(UniversalEditor editor) {
		this.editor = editor;
	}
	
	public UniversalEditor getEditor() {
		return editor;
	}
	

	/**
	 * This method will be called when the AST maintained by the parseController
	 * has been updated (subject to the completion of analyses on which this
	 * service depends and on the apparent availability of time in which to
	 * perform this analysis)
	 */
	public abstract void update(IParseController parseController, IProgressMonitor monitor);

}
