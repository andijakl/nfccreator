/****************************************************************************
**
** Copyright (C) 2011-2013 Andreas Jakl.
** All rights reserved.
** Contact: Andreas Jakl (andreas.jakl@mopius.com)
**
** This file may be used under the terms of the GNU General
** Public License version 3.0 as published by the Free Software Foundation
** and appearing in the file LICENSE included in the packaging of this
** file. Please review the following information to ensure the GNU General
** Public License version 3.0 requirements will be met:
** http://www.gnu.org/copyleft/gpl.html.
**
****************************************************************************/
package com.nokia.examples;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/**
 * Main Midlet class, handles app lifecycle and instantiates UI.
 * 
 * @author Andreas Jakl
 */
public class NfcCreatorMidlet extends MIDlet {

    /** To determine the first app start. */
    private boolean initialized = false;
    /** Main UI form. */
    private NfcMenuForm nfcMenu;

    /**
     * Constructor of the MIDlet. Initializes the UI.
     */
    public NfcCreatorMidlet() {
    }

    /**
     * Called when the midlet is entering the active state.
     * On the very first start-up, this registers listening for NDEF tags.
     */
    public void startApp() {
        if (!initialized) {
            nfcMenu = new NfcMenuForm(this);
            nfcMenu.init();
        }
        Display.getDisplay(this).setCurrent(nfcMenu);
    }

    public void pauseApp() {
    }

    public void destroyApp(boolean unconditional) {
        nfcMenu.shutdown();
    }
    
    public void exitApp() {
        destroyApp(false);
        notifyDestroyed();
    }
}
