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

import com.nokia.nfc.nxp.mfstd.MFKey;
import com.nokia.nfc.nxp.mfstd.MFStandardConnection;
import com.nokia.nfc.nxp.mfstd.MFStandardException;
import java.io.IOException;
import javax.microedition.contactless.TargetProperties;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.AlertType;

/**
 * Encapsulates tag-specific interaction with Mifare tags.
 * 
 * @author Andreas Jakl
 */
public class MifareManager {
    /** Callback interface */
    private InfoInterface callback;
    /** Default key A and B according to Mifare specs. */
    static private byte[] KEY_BYTES_FF = { (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff };
    /** Connection to the tag. */
    private MFStandardConnection conn = null;
    /** Cached data in the tag / data to write to the tag. */
    private byte[] tagData = null;
    
    /**
     * Create a new instance of the mifare manager.
     * Can be reused for multiple connections.
     * @param callback receives information about the tags found.
     */
    public MifareManager(InfoInterface callback) {
        this.callback = callback;
    }
    
    /**
     * Connect to a Mifare target.
     * @param tProp target properties of the target in range.
     * @return if establishing a connection was successful.
     */
    public boolean connect(TargetProperties[] tProp) {
        // Establish new Mifare connection
        MFStandardConnection newConn = getMifareTagConnection(tProp);
        if (newConn != null) {
            // Successfully established a connection
            if (conn != null) {
                try {
                    // Close previous connection if it's still active
                    conn.close();
                } catch (IOException ex) { }
                conn = null;
            }
            // Keep only the new connection
            conn = newConn;
            return true;
        }
        return false;
    }
   
    /**
     * Search through available connections to the tag, and establish a 
     * connection in case it's possible to create a tag-specific Mifare connection.
     * @param tProp target properties of the target in range.
     * @return tag-specific Mifare connection, if successful.
     */
    private MFStandardConnection getMifareTagConnection(TargetProperties[] tProp) {
        for (int i = 0; i < tProp.length; i++) {
            // Check if we can open a low-level MiFare connection
            Class[] connectionNames = tProp[i].getConnectionNames();
            if (connectionNames != null) {
                // Check all connection names to see if there is a Mifare one
                // (In addition, there can be for example also an NDEF connection)
                for (int j = 0; j < connectionNames.length; j++) {
                    //System.out.println("Connection name: " + connectionNames[j].getName());
                    if (connectionNames[j].getName().equals("com.nokia.nfc.nxp.mfstd.MFStandardConnection")) {
                        try {
                            // Mifare connection found - open it
                            return (MFStandardConnection) Connector.open(tProp[i].getUrl(connectionNames[j]));
                        } catch (Exception e) {
                            callback.displayAlert("Exception: Mifare Tag Connection", e.toString(), AlertType.ERROR);
                        }
                    }
                }
            }
        }
        return null;
    }
        
    /**
     * Read the complete raw data from the tag, using the specified key.
     * If no key is provided (= null), the default key KEY_BYTES_FF will be used.
     * The contents of the tag are saved to a file, the callback will receive
     * information about the tag.
     * @param key Key A to use for the conneciton. KEY_BYTES_FF (= Mifare default)
     * is used when no key is specified.
     * @return number of bytes read from the tag.
     */
    public int readData(MFKey.KeyA key) {
        if (conn == null) {
            return -1;
        }
        if (key == null)
            key = new MFKey.KeyA(KEY_BYTES_FF);
        try {
            final int numSectors = conn.getSectorCount();
            final int numTotalBlocks = conn.getBlockCount();
            final int dataSize = conn.size();
            System.out.println("Sectors: " + numSectors + ", Total blocks: " + numTotalBlocks + ", Data size: " + dataSize);
            tagData = new byte[dataSize];
            int bytesRead = conn.read(key, tagData, 0, 0, dataSize);
            System.out.println("Number of bytes read from data area: " + bytesRead);
            String filename = TagFileManager.logTagInfo(conn.getClass().getName(), tagData);
            callback.displayAlert("Mifare tag read", "Mifare data saved to file", AlertType.CONFIRMATION);
            callback.logTagInfo("Mifare tag\nSectors: " + numSectors + ", Size: " + dataSize + ", Read: " + bytesRead + "\nSaved to: " + filename);
            return bytesRead;
        } catch (MFStandardException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        
        return -1;
    }
    
    /**
     * Cache the data to write to a tag. Call this first, then writeData().
     * @param data byte array containing the raw tag data to write.
     */
    public void setDataToWrite(byte[] data) {
        tagData = data;
    }
    
    /**
     * Write cached data to the tag, using the specified key.
     * Uses the default key KEY_BYTES_FF if no key is specified (= null).
     * @param key Key A to use for the conneciton. KEY_BYTES_FF (= Mifare default)
     * is used when no key is specified.
     * @return whether writing was successful.
     */
    public boolean writeData(MFKey.KeyA key) {
        if (conn == null || tagData == null) {
            return false;
        }
        if (key == null) {
            key = new MFKey.KeyA(KEY_BYTES_FF);
        }
        try {
            conn.write(key, tagData, 0);
            callback.displayAlert("Mifare tag written", "Mifare data written to tag (" + tagData.length + " bytes)", AlertType.CONFIRMATION);
            return true;
        } catch (MFStandardException ex) {
            callback.displayAlert("Authentication error", ex.toString(), AlertType.ERROR);
        } catch (IOException ex) {
            callback.displayAlert("Connection error", ex.toString(), AlertType.ERROR);
        }
        return false;
    }
    
}
