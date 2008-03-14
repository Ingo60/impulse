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

package org.eclipse.imp.ui;

import org.eclipse.osgi.util.NLS;

public class ActionMessages extends NLS {
    private static final String BUNDLE_NAME= "org.eclipse.imp.ui.ActionMessages";//$NON-NLS-1$

    private ActionMessages() {
        // Do not instantiate
    }

    public static String OpenAction_label;
    public static String OpenAction_tooltip;
    public static String OpenAction_description;
    public static String OpenAction_declaration_label;
    public static String OpenAction_error_messageBadSelection;
    public static String OpenAction_select_element;
    public static String OpenAction_error_message;
    public static String OpenAction_error_messageProblems;
    public static String OpenAction_error_messageArgs;
    public static String OpenAction_error_title;
    public static String NewWizardsActionGroup_new;

    static {
        NLS.initializeMessages(BUNDLE_NAME, ActionMessages.class);
    }
}
