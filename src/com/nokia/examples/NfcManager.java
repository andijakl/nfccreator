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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Date;
import javax.microedition.contactless.*;
import javax.microedition.contactless.ndef.NDEFMessage;
import javax.microedition.contactless.ndef.NDEFRecord;
import javax.microedition.contactless.ndef.NDEFRecordType;
import javax.microedition.contactless.ndef.NDEFTagConnection;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.AlertType;

/**
 * Manages NFC interaction with the target.
 * 
 * @author Andreas Jakl
 */
public class NfcManager implements TargetListener, Runnable {

    private InfoInterface callback;
    /** Manager for discovering targets. */
    private DiscoveryManager dm = null;
    /** Connection to the tag is saved here, as the actual processing is happening in a thread. */
    private NDEFTagConnection ndconn = null;
    /** When cloning a tag, cache the NDEF message in memory. */
    private NDEFMessage cachedMessage = null;
    /** 
     * Set to true to connect to a tag using an NDEF connection if possible, 
     * or false to create a low-level connection.
     * Note: low-level connection currently only supported for Mifare tags.
     */
    private boolean ndefMode = true;
    /** Mifare manager, establishes tag-specific Mifare connections if requested. */
    MifareManager mifareManager;
    
    /** Standardized abbreviations used to save bytes on NDEF URI records. */
    private static final String uriAbbreviations[] = {
        "",
        "http://www.",
        "https://www.",
        "http://",
        "https://",
        "tel:",
        "mailto:",
        "ftp://anonymous:anonymous@",
        "ftp://ftp.",
        "ftps://",
        "sftp://",
        "smb://",
        "nfs://",
        "ftp://",
        "dav://",
        "news:",
        "telnet://",
        "imap:",
        "rtsp://",
        "urn:",
        "pop:",
        "sip:",
        "sips:",
        "tftp:",
        "btspp://",
        "btl2cap://",
        "btgoep://",
        "tcpobex://",
        "irdaobex://",
        "file://",
        "urn:epc:id:",
        "urn:epc:tag:",
        "urn:epc:pat:",
        "urn:epc:raw:",
        "urn:epc:",
        "urn:nfc:"};

    /** Create a new instance of the Nfc Manager. */
    public NfcManager(InfoInterface callback) {
        this.callback = callback;
        mifareManager = new MifareManager(callback);
    }
    
    /**
     * Set to true if this class should establish an NDEF connection.
     * Otherwise, it will establish a direct tag connection to read the
     * raw data.
     * Note: currently, the raw connection is only supported for Mifare tags.
     */
    public void setNdefMode(boolean ndefEnabled) {
        ndefMode = ndefEnabled;
    }

    /**
     * Delete & close all Nfc connections.
     * @param alsoRemoveDiscoveryManager if true, also the DiscoveryManager is
     * deleted and its listener removed.
     */
    public void deleteNfcInstances(boolean alsoRemoveDiscoveryManager) {
        if (ndconn != null) {
            try {
                ndconn.close();
                ndconn = null;
            } catch (IOException ex) {
                callback.displayAlert("IOException during close", ex.toString(), AlertType.ERROR);
            }
        }
        if (alsoRemoveDiscoveryManager && dm != null) {
            dm.removeTargetListener(this, TargetType.NDEF_TAG);
            dm = null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // NFC releated control code
    /**
     * Create a new instance of the discovery manager, so that callbacks can
     * be received.
     * @return whether establishing the connection was successful.
     */
    public boolean createNfcDiscoveryManager() {
        deleteNfcInstances(true);
        
        // Registration of the TargetListener for external contactless
        // Targets (in this RFID_TAG).
        try {        
            // Check that NDEF_TAG target is supported
            TargetType[] targets = DiscoveryManager.getSupportedTargetTypes();
            boolean supported = false;

            for (int i=0; i<targets.length; i++) {
                if (targets[i].equals(TargetType.NDEF_TAG)) {
                    supported = true;
                }
            }

            if (supported) {
                dm = DiscoveryManager.getInstance();
                dm.addTargetListener(this, TargetType.NDEF_TAG);
                return true;
            } else {
                callback.displayAlert("Error registering for NDEF targets", "NDEF Tag type not supported", AlertType.ERROR);
                return false;
            }
        } catch (ContactlessException ce) {
            callback.displayAlert("ContactlessException", "Unable to register TargetListener: " + ce.toString(), AlertType.ERROR);
        }
        return false;
    }

    /**
     * Implementation of the call-back function of the TargetListener.
     * @param targetProperties array of targets found by the phone
     */
    public void targetDetected(TargetProperties[] targetProperties) {
        // In case no targets were found, exit the method
        if (targetProperties.length == 0) {
            callback.displayAlert("Target detected", "No target properties available", AlertType.WARNING);
            return;
        }

        // Make sure no connection is already open
        deleteNfcInstances(false);
        
        boolean continueParsingTag = false;
        
        if (ndefMode) {
            // NDEF Connection for write Operation
            ndconn = getNdefTagConnection(targetProperties);
            if (ndconn != null) {
                continueParsingTag = true;
            }
        } else {
            // Check for mifare connection
            if (mifareManager.connect(targetProperties)) {
                continueParsingTag = true;
            }
        }
               
        if (continueParsingTag) {
            // Start a new thread for processing the tag, as recommended by the API specs.
            Thread t = new Thread(this);
            t.start();
        } else {
            callback.displayAlert("Target detected", "Unable to process tag", AlertType.ERROR);
        }
    }
    
    /**
     * Open the connection to the NDEF tag when a target was found.
     * Shows an alert if there is an issue opening the connection.
     * @param tProp array containing all target properties that will be
     * searched for an NDEF connection.
     * @return a NDEF tag connection if one was found, or null otherwise.
     */
    private NDEFTagConnection getNdefTagConnection(TargetProperties[] tProp) {
        for (int i = 0; i < tProp.length; i++) {
            if (tProp[i].hasTargetType(TargetType.NDEF_TAG)) {
                String url = tProp[i].getUrl();
                if (url != null) {
                    try {
                        return (NDEFTagConnection) Connector.open(url);
                    } catch (Exception e) {
                        callback.displayAlert("Exception: NDEF Tag Connection", e.toString(), AlertType.ERROR);
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Used to inform the callback about a new tag in a separated thread,
     * as recommended by the JSR documentation.
     */
    public void run() {
        // Trigger the UI to launch the appropriate action
        callback.tagReady();
    }
    
    /**
     * Check if the NDEF connection is still set. Will do a callback
     * to the listener class in case no connection is currently active.
     * (in addition to the callback in case of a failure).
     * @return true if a connection is active, false if otherwise.
     */
    private boolean checkNdefConnection() {
        if (ndconn == null) {
            callback.tagError("No active NDEF connection.");
            return false;
        }
        return true;
    }
    
    // ---------------------------------------------------------------------------------------------------------
    // Methods to interact with the physical tag (read / write). The methods also handle the potential errors.
    
    private NDEFMessage readMessageFromTag()
    {
        try {
            return ndconn.readNDEF();
        } catch (Exception ex) {
            handleException(ex);
        }
        return null;
    }

    private boolean writeMessageToTag(NDEFMessage ndefMessage) {
        if (!checkNdefConnection()) {
            return false;
        }

        boolean success = false;
        try {
            ndconn.writeNDEF(ndefMessage);
            success = true;
        } catch (Exception ex) {
            handleException(ex);
        } finally {
            // In case of an exception, close the connection properly
            deleteNfcInstances(false);
        }
        return success;
    }
    
    private void handleException (Exception ex)
    {
        if (ex instanceof IOException)
        {
            if (ex.toString().indexOf("-36") > -1) {
                // KErrDisconnected == -36
                callback.displayAlert("IOException", "-36: Communication problem / " + ex.getMessage(), AlertType.ERROR);
            } else {
                callback.displayAlert("IOException", ex.toString(), AlertType.ERROR);
            }  
        } else if (ex instanceof ContactlessException)
        {
            if (ex.toString().indexOf("-9") > -1) {
                // KErrOverflow == -9
                callback.displayAlert("ContactlessException", "-9: Not enough space on the tag / " + ex.getMessage(), AlertType.ERROR);
            } else if (ex.toString().indexOf("-2") > -1) {
                // KErrGeneral == -2
                // Often for unable to read tag - format not supported
                callback.displayAlert("ContactlessException", "-2: General error / " + ex.getMessage(), AlertType.ERROR);
            } else {
                callback.displayAlert("ContactlessException", ex.toString(), AlertType.ERROR);
            }
        } else {
            callback.displayAlert("Exception", ex.toString(), AlertType.ERROR);
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Read messages
    /**
     * Processing method to read an NDEF message from a pre-established tag connection.
     */
    public void readNDEFMessage() {
        if (!checkNdefConnection()) {
            return;
        }
        // Get the message out of the connection
        NDEFMessage ndefMessage = readMessageFromTag();
        String messageContents = "";
        if (ndefMessage == null || ndefMessage.getNumberOfRecords() <= 0) {
            // No records are found, or no message contained in the connection
            callback.displayAlert("Read NDEF", "No records in the message.", AlertType.ERROR);
            messageContents += "No records in this message\n";
        } else {
            boolean recordHandled;
            // Go through all the records present in the message
            for (int i = 0; i < ndefMessage.getNumberOfRecords(); i++) {
                recordHandled = false;
                NDEFRecord rec = ndefMessage.getRecord(i);
                NDEFRecordType type = rec.getRecordType();
                String typeName = type.getName();
                if (typeName != null) {
                    // Enable specific tag handling
                    if (typeName.equals("Sp")) {
                        // Show Smart Poster
                        // Doesn't do actual parsing of the contents
                        byte[] payload = rec.getPayload();
                        callback.displayAlert("Smart Poster", new String(payload), AlertType.CONFIRMATION);
                        messageContents += "Smart Poster\n" + new String(payload) + "\n";
                        recordHandled = true;
                    } else if (typeName.equals("U")) {
                        // Parse URL
                        // Doesn't do actual parsing of the contents
                        byte[] payload = rec.getPayload();
                        callback.displayAlert("Url", new String(payload), AlertType.CONFIRMATION);
                        messageContents += "URL\n" + new String(payload) + "\n";
                        recordHandled = true;
                    } else if (typeName.equals("T")) {
                        // Parse URL
                        // Doesn't do actual parsing of the contents
                        byte[] payload = rec.getPayload();
                        callback.displayAlert("Text", new String(payload), AlertType.CONFIRMATION);
                        messageContents += "Text\n" + new String(payload) + "\n";
                        recordHandled = true;
                    }
                }
                if (!recordHandled) {
                    // Didn't do special parsing of the record - so just show general info about it
                    callback.displayAlert("Record " + (i + 1) + "/" + ndefMessage.getNumberOfRecords(), "Format = " + type.getFormat() + ", Name = " + type.getName() + "\n", AlertType.CONFIRMATION);
                    messageContents += "Record " + (i + 1) + "/" + ndefMessage.getNumberOfRecords() + "\nFormat = " + type.getFormat() + ", Name = " + type.getName() + "\n";
                }
            }
        }
        callback.logTagInfo(messageContents);
    }
    
    /**
     * Read the message and cache it in memory. The message is not parsed.
     * @return true on success, false when there was a problem reading a valid message.
     */
    public boolean readAndCacheMessage() {
        if (!checkNdefConnection()) {
            return false;
        }
        cachedMessage = readMessageFromTag();
        if (cachedMessage != null) {
            callback.tagSuccess("Learned message from tag");
            return true;
        }
        return false;
    }
    
    /**
     * Read raw data from the tag and log it to a file.
     * @return true if successful
     */
    public boolean readRawData() {
        if (ndefMode) {
            // Reading the raw data is only supported if not establishing an
            // NDEF connection
            callback.displayAlert("Read Raw Data", "Unable to read raw data: app is in NDEF mode", AlertType.ERROR);
            return false;
        }
        
        return mifareManager.readData(null) >= 0;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Delete messages / Format tag (-> write empty message)
    /**
     * Processing method to delete an NDEF message from a pre-established tag connection.
     * Writes an empty message if a message already exists on the tag. Doesn't 
     * do anything if the tag is already empty.
     */
    public void deleteNDEFMessage() {
        if (!checkNdefConnection()) {
            return;
        }
        NDEFMessage ndefMessage = readMessageFromTag();
        if (ndefMessage == null || ndefMessage.getNumberOfRecords() <= 0) {
            callback.tagSuccess("Tag already empty");
        } else {
            NDEFRecord emptyRecord = new NDEFRecord(new NDEFRecordType(NDEFRecordType.EMPTY, null), null, null);
            NDEFRecord[] emptyRecordArray = {emptyRecord};
            NDEFMessage emptyMessage = new NDEFMessage(emptyRecordArray);
            if (writeMessageToTag(emptyMessage)) {
                callback.tagSuccess("Wrote empty message.");
            }
        }

    }

    // ---------------------------------------------------------------------------------------------------------
    // Write messages to tags
    
    /**
     * Processing method to write an NDEF message containing a URI record
     * to a pre-established tag connection.
     * @param fullUrl URI to be written into the URI record of the NDEF message.
     * Any possible abbreviations of the URL are done automatically.
     * @throws UnsupportedEncodingException 
     */
    public void writeUri(String fullUrl) throws UnsupportedEncodingException {
        if (!checkNdefConnection()) {
            return;
        }
        // Create NDEFMessage
        NDEFMessage message = new NDEFMessage();

        // Append the record to the message
        message.appendRecord(createUriRecord(fullUrl));

        // Write message to the tag
        if (writeMessageToTag(message)) {
            callback.tagSuccess("URI written");
        }
    }
    
    /**
     * Processing method to write an NDEF message containing a text record
     * to a pre-established tag connection.
     * @param fullText text to be written into the text record of the NDEF message.
     * @param language language to use for the text record
     * @throws UnsupportedEncodingException 
     */
    public void writeText(String fullText, String language) throws UnsupportedEncodingException {
        if (!checkNdefConnection()) {
            return;
        }
        // Create NDEFMessage
        NDEFMessage message = new NDEFMessage();

        // Append the record to the message
        message.appendRecord(createTextRecord(fullText, language));

        // Write message to the tag
        if (writeMessageToTag(message)) {
            callback.tagSuccess("Text written");
        }
    }

    /**
     * Processing method to write a Smart Poster NDEF message to a 
     * pre-established tag connection.
     * Which parts of the possible information has to be specified in the
     * boolean array writeMessages[]. Note that the URL is mandatory
     * according to the Smart Poster specification.
     * @throws IOException
     * @throws ContactlessException 
     */
    public void writeSmartPoster(boolean writeMessages[], String fullUrl, String fullText, byte posterAction, String imageUri) throws UnsupportedEncodingException, IOException {

        if (!checkNdefConnection()) {
            return;
        }

        // Create the final Smart Poster meta-message
        NDEFMessage messageSmartPoster = new NDEFMessage();

        // Create the record containing all selected smart poster details
        NDEFRecord spRecord = createSpRecord(writeMessages, fullUrl, fullText, posterAction, imageUri);

        // Append the smart poster record to the meta-message
        messageSmartPoster.appendRecord(spRecord);

        // Write message to the tag
        if (writeMessageToTag(messageSmartPoster)) {
            callback.tagSuccess("Smart Poster written");
        }
    }

    public void writeSms(boolean writeMessages[], String smsUrl, String smsBody, String titleText, byte posterAction) throws UnsupportedEncodingException, IOException {
        if (!checkNdefConnection()) {
            return;
        }

        // Assemble SMS URL (contains phone number and body text)
        String tagSmsText = "sms:" + smsUrl + "?body=" + smsBody;

        NDEFRecord smsRecord;
        // Check if to write a smart poster or a URL tag

        if (writeMessages[0] || writeMessages[1]) {
            // Write a smart poster
            boolean writeSpMessages[] = new boolean[4];
            writeSpMessages[0] = true;              // Write URL & body -> always true
            writeSpMessages[1] = writeMessages[0];  // Write title text?
            writeSpMessages[2] = writeMessages[1];  // Write action?
            writeSpMessages[3] = false;             // No image
            smsRecord = createSpRecord(writeSpMessages, tagSmsText, titleText, posterAction, null);
        } else {
            // No title or action set -> write a URI tag
            smsRecord = createUriRecord(tagSmsText);
        }

        // Create the final SMS message
        NDEFMessage messageSms = new NDEFMessage();
        messageSms.appendRecord(smsRecord);
        // Write message to the tag
        if (writeMessageToTag(messageSms)) {
            callback.tagSuccess("Sms tag written");
        }
    }
    
    public void writeAnnotatedUrl(String fullUrl, String fullText) throws UnsupportedEncodingException {
        // Create individual records (Uri + Text)
        NDEFRecord urlRecord = createUriRecord(fullUrl);
        NDEFRecord textRecord = createTextRecord(fullText, "en");
        // Create the final Annotated URL message
        NDEFMessage messageAnnotatedUrl = new NDEFMessage();
        messageAnnotatedUrl.appendRecord(urlRecord);
        messageAnnotatedUrl.appendRecord(textRecord);
        // Write message to the tag
        if (writeMessageToTag(messageAnnotatedUrl)) {
            callback.tagSuccess("Annotated URL tag written");
        }
    }

    public void writeImage(String imageUri) throws IOException {
        if (!checkNdefConnection()) {
            return;
        }

        // Create NDEFMessage
        NDEFMessage message = new NDEFMessage();

        message.appendRecord(createImageRecord(imageUri));

        // Write message to the tag
        if (writeMessageToTag(message)) {
            callback.tagSuccess("Image written");
        }
    }
    
    /**
     * Write geo-coordinates to a URL NDEF message to a 
     * pre-established tag connection.
     * @param latitude
     * @param longitude
     * @param geoType 0 ... geo: URI scheme, according to http://geouri.org/
     * 1 ... Nokia Maps link
     * @throws IOException 
     */
    public void writeGeo(final double latitude, final double longitude, final int geoType) throws IOException {
        if (!checkNdefConnection()) {
            return;
        }

        // Create NDEFMessage
        NDEFMessage message = new NDEFMessage();

        message.appendRecord(createGeoRecord(latitude, longitude, geoType));

        // Write message to the tag
        if (writeMessageToTag(message)) {
            callback.tagSuccess("Geo URI written");
        }
    }

    public void writeCustom(String tagTypeUri, byte[] tagPayload) throws UnsupportedEncodingException {
        if (!checkNdefConnection()) {
            return;
        }
        // Create NDEFMessage
        NDEFMessage message = new NDEFMessage();

        // Append the record to the message
        message.appendRecord(createCustomRecord(tagTypeUri, tagPayload));

        // Write message to the tag
        if (writeMessageToTag(message)) {
            callback.tagSuccess("Custom tag written");
        }
    }
    
    
    public void writeCombination(String tagUrl, String tagTypeUri, byte[] tagPayload) throws UnsupportedEncodingException {
        if (!checkNdefConnection()) {
            return;
        }
        // Create NDEFMessage
        NDEFMessage message = new NDEFMessage();

        // Append the record to the message
        message.appendRecord(createCustomRecord(tagTypeUri, tagPayload));
        message.appendRecord(createUriRecord(tagUrl));

        // Write message to the tag
        if (writeMessageToTag(message)) {
            callback.tagSuccess("Combination tag written");
        }
    }
    
    public void writeVcalendar(String tagCalSummary, Date tagCalStart, Date tagCalEnd, boolean useUtcTime) throws IOException {
        if (!checkNdefConnection()) {
            return;
        }

        // Create NDEFMessage
        NDEFMessage message = new NDEFMessage();
        
        // Append the record to the message
        message.appendRecord(createVcalendarRecord(tagCalSummary, tagCalStart, tagCalEnd, useUtcTime));

        // Write message to the tag
        if (writeMessageToTag(message)) {
            callback.tagSuccess("vCalendar written");
        }
    }
    
    public void writeCachedMessage() {
        if (!checkNdefConnection() || cachedMessage == null) {
            return;
        }
        // Write message to the tag
        if (writeMessageToTag(cachedMessage)) {
            callback.tagSuccess("Tag clone written");
        }
    }
    
    public boolean writeRawData(byte[] tagData) {
        if (ndefMode) {
            // Writing the raw data is only supported if not establishing an
            // NDEF connection
            callback.displayAlert("Write Raw Data", "Unable to write raw data: app is in NDEF mode", AlertType.ERROR);
            return false;
        }
        mifareManager.setDataToWrite(tagData);
        mifareManager.writeData(null);
        
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Create individual records
    private NDEFRecord createUriRecord(final String fullUri) throws UnsupportedEncodingException {
        byte[] urlPrefix = new byte[1];
        byte[] url = null;

        boolean foundAbbreviation = false;
        for (int i = 1; i < uriAbbreviations.length; i++) {
            if (fullUri.startsWith(uriAbbreviations[i])) {
                urlPrefix[0] = (byte) i;
                url = fullUri.substring(uriAbbreviations[i].length(), fullUri.length()).getBytes("utf-8");
                foundAbbreviation = true;
                break;
            }
        }
        if (!foundAbbreviation) {
            // Store the full URL to the tag - always using UTF-8
            urlPrefix[0] = (byte) 0x00;
            url = fullUri.getBytes("utf-8");
        }

        // Create the record for the URL
        NDEFRecord recordUrl = new NDEFRecord(new NDEFRecordType(NDEFRecordType.NFC_FORUM_RTD, "urn:nfc:wkt:U"), null, null);
        recordUrl.appendPayload(urlPrefix);
        recordUrl.appendPayload(url);

        // Create NDEF Record to be added to NDEF Message
        return recordUrl;
    }

    private NDEFRecord createTextRecord(final String text, final String lang) throws UnsupportedEncodingException {
        byte[] lang_bytes = lang.getBytes("US-ASCII");
        byte[] status_lang_len = {(byte) (lang_bytes.length & 0x3f)};
        // Bit at 0x80 of status_lang_len would need to be set to 1 for UTF-16 text.
        // We always use UTF-8 here in this example.
        byte[] textContents = text.getBytes("utf-8");

        NDEFRecord recordText = new NDEFRecord(new NDEFRecordType(NDEFRecordType.NFC_FORUM_RTD, "urn:nfc:wkt:T"), null, null);
        recordText.appendPayload(status_lang_len);
        recordText.appendPayload(lang_bytes);
        recordText.appendPayload(textContents);

        return recordText;
    }

    private NDEFRecord createSpRecord(final boolean[] writeDetails, final String fullUrl, final String title, final byte action, final String imageFilename) throws UnsupportedEncodingException, IOException {
        if (writeDetails.length != 4) {
            return null;
        }

        // Create NDEFMessage
        NDEFMessage message = new NDEFMessage();

        if (writeDetails[0]) {
            // Url
            message.appendRecord(createUriRecord(fullUrl));
        }
        if (writeDetails[1]) {
            // Title
            message.appendRecord(createTextRecord(title, "en"));
        }
        if (writeDetails[2]) {
            // Action
            message.appendRecord(createActionRecord(action));
        }
        if (writeDetails[3]) {
            // Image
            message.appendRecord(createImageRecord(imageFilename));
        }

        // Create the Smart Poster record
        NDEFRecord recordSmartPoster = new NDEFRecord(new NDEFRecordType(NDEFRecordType.NFC_FORUM_RTD, "urn:nfc:wkt:Sp"), null, null);
        // Add the URL and the title as a payload to the Smart Poster record.
        recordSmartPoster.appendPayload(message.toByteArray()); // add content of previously created NDEFMessage

        return recordSmartPoster;
    }

    private NDEFRecord createActionRecord(final byte action) {
        byte[] actionRecord = {0x11, 0x03, 0x01, 'a', 'c', 't', action};

        return new NDEFRecord(actionRecord, 0);
    }

    private NDEFRecord createImageRecord(final String filename) throws IOException {
        // Read image from phone to ByteArrayOutputStream
        ByteArrayOutputStream baos = getImage(filename);

        final String fileExt = filename.substring(filename.lastIndexOf('.') + 1, filename.length());
        String mimeType = "";
        if (fileExt.equalsIgnoreCase("png")) {
            mimeType = "image/png";
        } else if (fileExt.equalsIgnoreCase("jpg") || fileExt.equalsIgnoreCase("jpeg")) {
            mimeType = "image/jpeg";
        } else if (fileExt.equalsIgnoreCase("gif")) {
            mimeType = "image/gif";
        } else {
            callback.displayAlert("Image", "Unrecognized file type", AlertType.WARNING);
        }

        // Create NDEF Record to be added to NDEF Message
        return new NDEFRecord(new NDEFRecordType(
                NDEFRecordType.MIME, mimeType), null, baos.toByteArray());
    }

    /**
     * Read image from phone to a ByteArrayOutputStream
     */
    private ByteArrayOutputStream getImage(final String filename) throws IOException {
        InputStream is = getClass().getResourceAsStream(filename);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int c;
        while ((c = is.read()) != -1) {
            baos.write(c);
        }
        return baos;
    }

    private NDEFRecord createGeoRecord(final double latitude, final double longitude, final int geoType) throws UnsupportedEncodingException {
        // Convert latitude and longitude to strings.
        // Decimal separator: .
        final String latString = Double.toString(latitude);
        final String longString = Double.toString(longitude);
        // Create NDEF Record to be added to NDEF Message
        String uriString;
        switch (geoType)
        {
            case 2: // Generic redirect NfcInteractor.com
                uriString = "http://nfcinteractor.com/m?c=" + latString + "," + longString;
                break;
            case 1: // Nokia Maps link
                uriString = "http://m.ovi.me/?c=" + latString + "," + longString;
                break;
            case 0: // Geo URI
            default:
                uriString = "geo:" + latString + "," + longString;
                break;
        }
        return createUriRecord(uriString);
    }
    
    private NDEFRecord createCustomRecord(final String tagUri, final byte[] payload) throws UnsupportedEncodingException {
        // Create NDEF Record to be added to NDEF Message
        NDEFRecord recordCustom = new NDEFRecord(new NDEFRecordType(NDEFRecordType.EXTERNAL_RTD, tagUri), null, null);
        recordCustom.appendPayload(payload);

        return recordCustom;
    }
    
    private NDEFRecord createVcalendarRecord(String tagCalSummary, Date tagCalStart, Date tagCalEnd, boolean useUtcTime) throws UnsupportedEncodingException {
        String vCalEntry = "BEGIN:VCALENDAR\nVERSION:1.0\nBEGIN:VEVENT\nDTSTART:" + convertToVcalTime(tagCalStart, useUtcTime) + 
                "\nDTEND:" + convertToVcalTime(tagCalEnd, useUtcTime)+ "\nSUMMARY:" + tagCalSummary + "\nEND:VEVENT\nEND:VCALENDAR";
        
        // Two MIME types are most common: text/x-vCalendar and text/Calendar
        // Create NDEF Record to be added to NDEF Message
        // Default character set for iCalendar (RFC 2445) is UTF-8
        return new NDEFRecord(new NDEFRecordType(
                NDEFRecordType.MIME, "text/x-vCalendar"), null, vCalEntry.getBytes("utf-8"));
    }
    
    /**
     * Convert a Date object to the representation required by the vCalendar standard.
     * Resulting format: yyyymmddThhmmss
     * (uppercase T character as separator between date and time).
     * This method isn't time zone aware.
     * @param datetime date and time to convert
     * @return date and time as string suitable for a vCalendar entry.
     */
    private String convertToVcalTime(Date datetime, boolean useUtcTime) {
        // Summary from the iCalendar specifications:
        // DATE-TIME = date "T" time: YYYYMMDDTHHMMSS
        // Example: DTSTART:19980118T230000
        // UTC time has latin captial letter Z suffix: DTSTART:19980119T070000Z
        // Other time zones, e.g.: DTSTART;TZID=US-Eastern:19980119T020000
        // UTC offset MUST NOT be used (= invalid), e.g.: 230000-0800
        Calendar c = Calendar.getInstance();
        c.setTime(datetime);
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);
        String d = year + (month < 10 ? "0" : "") + month + (day < 10 ? "0" : "") + day;
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int min = c.get(Calendar.MINUTE);
        int sec = c.get(Calendar.SECOND);
        String t = (hour < 10 ? "0" : "") + hour + (min < 10 ? "0" : "") + min + (sec < 10 ? "0" : "") + sec;
        return d + "T" + t + (useUtcTime ? "Z" : "");
    }


}
