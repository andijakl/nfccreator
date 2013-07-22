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

import javax.microedition.lcdui.AlertType;

/**
 * Callback interface for Nfc Classes to interact with the UI / controller.
 * 
 * @author Andreas Jakl
 */
public interface InfoInterface {
    /**
     * Callback when reading / writing to a text was
     * not successful.
     * @param text Text to show in a message box if desired.
     */
    public void tagError(final String text);

    /**
     * Callback when writing to a tag was successful.
     * @param text Text to show in a message box if desired.
     */
    public void tagSuccess(final String text);
    
    /**
     * Utility function to show a Java ME alert, as used for informing the user
     * about events in this demo app.
     * @param title title text to use for the message box.
     * @param text text to show as the main message in the box.
     * @param type one of the available alert types, defining the icon, sound
     * and display length.
     */
    public void displayAlert(final String title, final String text, final AlertType type);
    
    /**
     * Log information about a tag in textual form.
     * @param text Text that contains information about the tag.
     */
    public void logTagInfo(final String text);
    
    /**
     * Callback when a tag was found. The NFC manager will then
     * establish a new thread and call this method, so that commands can be issued
     * to the tag.
     */
    public void tagReady();
}
