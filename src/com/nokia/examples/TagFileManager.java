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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Enumeration;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;

/**
 * Stores and loads tag information from files.
 * 
 * @author Andreas Jakl
 */
public class TagFileManager {
    /** 
     * If this drive is available, store the logs on this drive.
     * Otherwise use the first drive available.
     */
    private final static String preferredDrive = "E:/";
    /** 
     * Default directory on the drive to store the files.
     * Will be created if it doesn't exist beforehand.
     */
    private final static String nfcDir = "nfc/";
    /**
     * File extension of the log files.
     */
    private final static String fileExt = ".txt";
    
    /***
     * Log information about the tag to a file.
     * The file name will be selected automatically.
     * @param tagType Class name of the tag type implementation. Will be added
     * to the file name - everything after the last "." in the name.
     * @param tagData Raw tag data to write into the log file.
     * @return Drive, directory and filename of the created log file if
     * successful. Doesn't include "file:///".
     * Error message if unsuccessful.
     */
    public static String logTagInfo(String tagType, final byte[] tagData) {
        // Only use last part of tagType (after last ".")
        final int lastDotPos = tagType.lastIndexOf('.');
        tagType = tagType.substring(lastDotPos + 1, tagType.length());
        
        // Get date and time and compile the filename
        Calendar cal = Calendar.getInstance();
        String fileName = ensureTwoCharNumber(cal.get(Calendar.YEAR)) + "." + 
                ensureTwoCharNumber(cal.get(Calendar.MONTH)) + "." + 
                ensureTwoCharNumber(cal.get(Calendar.DAY_OF_MONTH)) + " " +
                ensureTwoCharNumber(cal.get(Calendar.HOUR_OF_DAY)) + "-" + 
                ensureTwoCharNumber(cal.get(Calendar.MINUTE)) + "-" + 
                ensureTwoCharNumber(cal.get(Calendar.SECOND)) + " " + 
                tagType + fileExt;
        
        try {
            final String fileDir = nfcDir();
            final String completeFileDir = "file:///" + fileDir;
            FileConnection fc = (FileConnection) Connector.open(completeFileDir, Connector.READ_WRITE);
            if (!fc.exists()) {
                fc.mkdir();
            }
            
            // Create file
            fc = (FileConnection) Connector.open(completeFileDir + fileName, Connector.READ_WRITE);
            if (!fc.exists()) {
                fc.create();
            }
            
            // Write tag contents to the file
            OutputStream outputStream = fc.openOutputStream(fc.fileSize());
            outputStream.write(tagData);
            outputStream.flush();
            fc.close();
            
            // Return name of created file
            return fileDir + fileName;
        } catch (Exception ex) {
            ex.printStackTrace();
            return "Error: " + ex.toString();
        }
    }
    
    /**
     * Search the data directory and return the newest (= last) file found.
     * @return Filename of the last file in the data directory.
     */
    public static String getNewestNfcFile() {
        String fileName = null;
        try {
            // Create full URL to the data dir
            final String completeFileDir = "file:///" + nfcDir();
            // Open the directory
            FileConnection fc = (FileConnection) Connector.open(completeFileDir, Connector.READ);
            // List all files with the right extension
            Enumeration fileList = fc.list("*" + fileExt, false);  
            // Iterate over the whole array and keep the last element.
            while(fileList.hasMoreElements()) {
                fileName = (String) fileList.nextElement();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return fileName;
    }
    
    /**
     * Read a data file and return its contents.
     * @param fileName file to open, searched for in the default data directory.
     * @return contents of the file as a byte array, null if unsuccessful.
     */
    public static byte[] readFile(String fileName) {
        try {
            FileConnection fc = (FileConnection) Connector.open("file:///" + nfcDir() + fileName, Connector.READ);
            InputStream is = fc.openInputStream();
            int fileSize = (int) fc.fileSize();
            byte[] fileContents = new byte[fileSize];
            is.read(fileContents, 0, fileSize);
            fc.close();
            return fileContents;
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    } 
    
    /**
     * Return the number as a String with at least two characters.
     * For example, "4" would be returned as "04". "12" remains as "12".
     * @param num number to convert to a 2-character string.
     * @return String based on the number, at least two characters.
     */
    private static String ensureTwoCharNumber(int num) {
        return (num < 10) ? "0" + num : String.valueOf(num);
    }
    
    /**
     * Get the default data directory.
     * Will use the preferred drive if available, otherwise the first (default)
     * drive. Data directory name can be defined through the member variables.
     * @return drive and directory of the data directory. Doesn't contain
     * "file:///" designator.
     */
    public static String nfcDir() {
        Enumeration rootsEnum = FileSystemRegistry.listRoots();

        // Scan returned drives, use E if found, otherwise return
        // to first element.
        String fileDrive = "";
        String firstDrive = "";
        while(rootsEnum.hasMoreElements()) {
            String root = (String) rootsEnum.nextElement();
            if (root.indexOf(preferredDrive) > -1) {
                // Check if we find our preferred drive
                fileDrive = preferredDrive;
                break;
            }
            if (firstDrive.equals("")) {
                // Keep the first drive we find in the list
                firstDrive = root;
            }
        }
        if (fileDrive.equals("")) {
            // No memory card found - use the first drive of the enum
            fileDrive = firstDrive;
        }

        // Create directory
        return fileDrive + nfcDir;
    }
    
}
