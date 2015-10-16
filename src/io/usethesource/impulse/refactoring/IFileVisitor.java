/*******************************************************************************
* Copyright (c) 2007 IBM Corporation.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*    Robert Fuhrer (rfuhrer@watson.ibm.com) - initial API and implementation

*******************************************************************************/

package io.usethesource.impulse.refactoring;

import org.eclipse.core.resources.IFile;

/**
 * 
 */
public interface IFileVisitor {
    public void enterFile(IFile file);
    public void leaveFile(IFile file);
}