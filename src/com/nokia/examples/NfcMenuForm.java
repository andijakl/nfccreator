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
import java.util.Date;
import javax.microedition.lcdui.*;

/**
 * User interface and controller for the application.
 * 
 * @author Andreas Jakl
 */
public class NfcMenuForm extends Form implements CommandListener, ItemStateListener, InfoInterface, Runnable {
    /** Midlet class to exit the app when the command is selected by the user. */
    private NfcCreatorMidlet midlet;
    
    /** Is NFC supported by this device? */
    private boolean nfcSupported = false;
    /** The nfcManager handles all interaction with the contactless communication API. */
    private NfcManager nfcManager;
    
    /** Command to exit the app. */
    private Command exitCommand;
    /** Command to go back to the big selection screen. */
    private Command backCommand;
    
    // Operation mode UI
    /** Selection UI element to choose the current interaction mode. */
    private ChoiceGroup operationModeSelector;
    /** Names of the operating modes, to be used in the UI element. */
    private static final String operatingModeNames[] = {
        "Read",
        "Write Smart Poster",
        "Write URI",
        "Write Text",
        "Write SMS",
        "Write Annotated URL",
        "Write Image",
        "Write Geo",
        "Write Custom",
        "Write Combination",
        "Write vCalendar",
        "Read Raw Mifare",
        "Write Raw Mifare",
        "Clone Tag",
        "Delete / Format"
    };
    /** Current operation mode. Can be either READ_TAG, WRITE_TAG or DELETE_TAG. */
    private int operationMode = READ_TAG;
    /** When touching an NFC tag: the tag will be read. */
    private static final int READ_TAG = 0;
    /** When touching an NFC tag: the Smart Poster info currently visible in the form is written to the tag. */
    private static final int WRITE_SP_TAG = 1;
    /** When touching an NFC tag: write a uri to the tag. */
    private static final int WRITE_URI_TAG = 2;
    /** When touching an NFC tag: write text to the tag. */
    private static final int WRITE_TEXT_TAG = 3;
    /** When touching an NFC tag: write an sms link to the tag. */
    private static final int WRITE_SMS_TAG = 4;
    /** When touching an NFC tag: write an annotated url (url record and text record 
     * without Smart Poster meta-record) to the tag. Used by some Qt Mobility examples. */
    private static final int WRITE_ANNOTATED_URL_TAG = 5;
    /** When touching an NFC tag: write an image to the tag. */
    private static final int WRITE_IMAGE_TAG = 6;
    /** When touching an NFC tag: write a geo URI to the tag. Specs: http://tools.ietf.org/rfc/rfc5870 */
    private static final int WRITE_GEO_TAG = 7;
    /** When touching an NFC tag: write a custom tag format. */
    private static final int WRITE_CUSTOM_TAG = 8;
    /** When touching an NFC tag: write a combination tag format:
     * 1. Custom record (for handling with a custom content handler plug-in) & 
     * 2. URL (e.g., a link to the Nokia Store to download the app including
     * the content-handler plug-in). */
    private static final int WRITE_COMBINATION_TAG = 9;
    /** When touching an NFC tag: write a simple, customizable vCalendar entry.
     * Specifications: http://tools.ietf.org/html/rfc2445 */
    private static final int WRITE_VCALENDAR_TAG = 10;
    /** When touching an NFC tag: raw data is read from the tag and logged to a file. */
    private static final int READ_RAW_TAG = 11;
    /** When touching an NFC tag: write the newest data file to the tag. */
    private static final int WRITE_RAW_TAG = 12;
    /** Copy NDEF message contents of one tag to another tag. */
    private static final int CLONE_TAG = 13;
    /** When touching an NFC tag: the record currently present on the tag is overwritten with an empty record. */
    private static final int DELETE_TAG = 14;
    // Reading modes
    /** UI element to show info about the discovered tags when reading. */
    private TextField tagContents;
    // Settings for the writing modes
    /** UI element to choose which messages to write to the tag. */
    private ChoiceGroup posterEnabledMessages;
    /** UI element to choose the action associated with the Smart Poster. Only visible when in WRITE_TAG operation mode. */
    private ChoiceGroup posterAction;
    /** UI element to enter the URL of a record. */
    private TextField tagUrl;
    /** UI element to enter the text of a record. */
    private TextField tagText;
    /** UI element to enter the text language of a record. */
    private TextField tagTextLanguage;
    /** UI element to enter the Type Uri of a record. */
    private TextField tagTypeUri;
    /** UI element to enter the payload of a record. */
    private TextField tagCustomPayload;
    /** UI element to choose which image to store on the tag. */
    private ChoiceGroup tagChooseImage;
    /** UI element to choose which parts to write for an sms tag. */
    private ChoiceGroup tagSmsEnabledMessages;
    /** UI element to enter the SMS recipient number. */
    private TextField tagSmsNumber;
    /** UI element to enter the SMS body. */
    private TextField tagSmsBody;
    /** UI element to enter the latitude. */
    private TextField tagLatitude;
    /** UI element to enter the longitude. */
    private TextField tagLongitude;
    /** Choose mechanism to write geo tag. */
    private ChoiceGroup tagGeoType;
    /** UI element to enter the summary of the vCalendar entry. */
    private TextField tagCalSummary;
    /** UI element to enter the starting date & time for a vCalendar entry. */
    private DateField tagCalStart;
    /** UI element to enter the ending date & time for a vCalendar entry. */
    private DateField tagCalEnd;
    /** UI element that shows further instructions when cloning a tag. */
    private StringItem cloneTagStatus;
    /** Status code when cloning a tag. */
    private int cloneStatus = 1;
    
    public NfcMenuForm(NfcCreatorMidlet midlet) {
        super("Nfc Creator");
        this.midlet = midlet;
    }
    
    public void init() {
        // Initialize the class in an own thread
        Thread t = new Thread(this);
        t.start();
    }
    
    public void run() {
        privateInit();
    }
    
    /**
     * Initialize the UI and the NFC interaction.
     * To be executed from an own thread to ensure fast app startup.
     */
    private void privateInit() {
        // Construct the UI
        exitCommand = new Command("Exit", Command.EXIT, 1);
        this.addCommand(exitCommand);
        backCommand = new Command("Back", Command.BACK, 1);
        
        // Check NFC availability
        String nfcVersion = System.getProperty("microedition.contactless.version");
        if ((nfcVersion == null) || (nfcVersion.length () == 0) || nfcVersion.equalsIgnoreCase ("null"))
        {
            // NFC APIs not supported
            String deviceName = System.getProperty ("microedition.platform");
            if (deviceName.startsWith("NokiaC7-00")) {
                // If using a Nokia C7, updating the firmware will enable NFC support
                this.append("Please upgrade the device firmware to experience the required NFC functionality.");
            } else {
                // On all other devices, print that NFC is not supported.
                this.append("NFC is not supported by your device.");
            }
        } else {
            // NFC is supported - construct full UI and NFC manager class.
            nfcSupported = true;
            createMainUi();
        }
        
        this.setItemStateListener(this);
        this.setCommandListener(this);
        
        if (nfcSupported) {
            nfcManager = new NfcManager(this);
            nfcSupported = nfcManager.createNfcDiscoveryManager();
        }
    }
    
    
    public void shutdown() {
        if (nfcManager != null) {
            nfcManager.deleteNfcInstances(true);
        }        
    }
    
    /**
     * Create the main UI and all the UI elements that are used by the 
     * different writing modes.
     */
    private void createMainUi() {
        // Choice group to choose the operation mode
        operationModeSelector = new ChoiceGroup("Choose Mode", ChoiceGroup.POPUP, operatingModeNames, null);
        setupFormHeader(WRITE_URI_TAG, READ_TAG);
        activateOperationMode(READ_TAG);
        
        tagContents = new TextField("Results", "", 1024, TextField.ANY);
        this.append(tagContents);

        // Prepare the UI elements that are only visible and relevant when writing a tag
        // URL field (URI tag, Smart Poster, Annotated URL)
        tagUrl = new TextField("URL", "http://nokia.com/", 255, TextField.URL);

        // Text field (Text only tag, Smart Poster)
        tagText = new TextField("Text", "Nokia", 255, TextField.ANY);
        tagTextLanguage = new TextField("Language", "en", 5, TextField.ANY);

        // Action (Smart Poster)
        posterAction = new ChoiceGroup("Action", ChoiceGroup.EXCLUSIVE);
        posterAction.append("Do the action", null);
        posterAction.append("Save for later", null);
        posterAction.append("Open for editing", null);
        posterAction.setSelectedIndex(0, true);

        // Selection which messages to write (Smart Poster)
        posterEnabledMessages = new ChoiceGroup("Messages", ChoiceGroup.MULTIPLE);
        posterEnabledMessages.append("URL", null);
        posterEnabledMessages.append("Title", null);
        posterEnabledMessages.append("Action", null);
        posterEnabledMessages.append("Icon", null);
        boolean[] posterEnabledFlags = {true, true, false, false};
        posterEnabledMessages.setSelectedFlags(posterEnabledFlags);

        // Custom tag
        tagTypeUri = new TextField("Tag URI", "urn:nfc:ext:nokia.com:custom", 255, TextField.URL);
        tagCustomPayload = new TextField("Payload", "Nokia", 255, TextField.ANY);

        // Image chooser
        tagChooseImage = new ChoiceGroup("Choose image", ChoiceGroup.EXCLUSIVE);
        tagChooseImage.append("Minimal GIF (48 bytes)", null);
        tagChooseImage.append("Minimal PNG (80 bytes)", null);
        tagChooseImage.append("Nokia PNG (225 bytes)", null);
        tagChooseImage.setSelectedIndex(1, true);

        // SMS
        tagSmsEnabledMessages = new ChoiceGroup("SMS Options", ChoiceGroup.MULTIPLE);
        tagSmsEnabledMessages.append("Title text (-> Sp)", null);
        tagSmsEnabledMessages.append("Action (-> Sp)", null);
        boolean[] smsEnabledFlags = {false, false};
        tagSmsEnabledMessages.setSelectedFlags(smsEnabledFlags);
        tagSmsNumber = new TextField("SMS Recipient", "+1234", 255, TextField.PHONENUMBER);
        tagSmsBody = new TextField("SMS Body", "Hello", 255, TextField.ANY);

        // Geo Uri
        tagLatitude = new TextField("Latitude (dec deg., WGS-84)", "60.17", 255, TextField.DECIMAL);
        tagLongitude = new TextField("Longitude (dec deg., WGS-84)", "24.829", 255, TextField.DECIMAL);
        tagGeoType = new ChoiceGroup("Choose Geo tag type", ChoiceGroup.EXCLUSIVE);
        tagGeoType.append("geo: URI scheme", null); // http://geouri.org/
        tagGeoType.append("Nokia Maps link", null);
        tagGeoType.append("Generic redirect NfcInteractor.com", null);
        tagGeoType.setSelectedIndex(0, true);
        
        // vCalendar
        tagCalSummary = new TextField("Summary", "Develop NFC app", 255, TextField.ANY);
        tagCalStart = new DateField("Start", DateField.DATE_TIME);
        tagCalEnd = new DateField("End", DateField.DATE_TIME);
        Date now = new Date();
        tagCalStart.setDate(now);
        tagCalEnd.setDate(new Date(now.getTime() + 3600000));   // 3 600 000 milliseconds = 60 minutes
        
        // Clone Tag
        cloneTagStatus = new StringItem(null, null);
        cloneStatus = 1;
    }

    /**
     * Call-back from the choice group that selects the operation mode.
     */
    public void itemStateChanged(Item item) {
        if (item == operationModeSelector) {
            final int newOperationMode = operationModeSelector.getSelectedIndex();
            // Activate the new operation mode.
            activateOperationMode(newOperationMode);
        }
    }

    /**
     * Switches the internal operation mode between reading, writing and deleting tags.
     * Also adjusts the UI in the form accordingly
     * @param newOperationMode one of the defined operation modes 
     * (READ_TAG, WRITE_TAG or DELETE_TAG) that should be activated.
     */
    private void activateOperationMode(final int newOperationMode) {
        if (operationMode != newOperationMode) {            
            setupFormHeader(operationMode, newOperationMode);
            // Does this mode require the nfc manager to be in the NDEF mode,
            // or the raw mode?
            boolean ndefMode = true;
            
            // The new operation mode is writing tags (previously, something
            // different was active): make the input items visible for
            // entering the tag type specific info.
            switch (newOperationMode) {
                case READ_TAG:
                    tagContents.setString("");
                    this.append(tagContents);
                    break;
                case WRITE_SP_TAG:
                    this.append(posterEnabledMessages);
                    this.append(tagText);
                    this.append(tagUrl);
                    this.append(posterAction);
                    this.append(tagChooseImage);
                    break;
                case WRITE_URI_TAG:
                    this.append(tagUrl);
                    break;
                case WRITE_TEXT_TAG:
                    this.append(tagText);
                    this.append(tagTextLanguage);
                    break;
                case WRITE_SMS_TAG:
                    this.append(tagSmsEnabledMessages);
                    this.append(tagSmsNumber);
                    this.append(tagSmsBody);
                    this.append(tagText);
                    this.append(posterAction);
                    break;
                case WRITE_ANNOTATED_URL_TAG:
                    this.append(tagText);
                    this.append(tagUrl);
                    break;
                case WRITE_IMAGE_TAG:
                    this.append(tagChooseImage);
                    break;
                case WRITE_GEO_TAG:
                    this.append(tagLatitude);
                    this.append(tagLongitude);
                    this.append(tagGeoType);
                    break;
                case WRITE_CUSTOM_TAG:
                    this.append(tagTypeUri);
                    this.append(tagCustomPayload);
                    break;
                case WRITE_COMBINATION_TAG:
                    this.append("First Record (Custom)");
                    this.append(tagTypeUri);
                    this.append(tagCustomPayload);
                    this.append("Second Record (URI)");
                    this.append(tagUrl);
                    break;
                case WRITE_VCALENDAR_TAG:
                    this.append(tagCalSummary);
                    this.append(tagCalStart);
                    this.append(tagCalEnd);
                    break;
                case READ_RAW_TAG:
                    tagContents.setString("");
                    this.append(tagContents);
                    ndefMode = false;
                    break;
                case WRITE_RAW_TAG:
                    ndefMode = false;
                    break;
                case CLONE_TAG:
                    cloneTagStatus.setLabel("Touch a tag to learn its contents");
                    cloneStatus = 1;
                    this.append(cloneTagStatus);
                    break;
            }
            operationMode = newOperationMode;
            if (nfcManager != null) {
                nfcManager.setNdefMode(ndefMode);
            }
        }
    }

    /**
     * Callback when a tag was found. The NFC manager will then
     * establish a new thread and call this method, so that commands can be issued
     * to the tag.
     */
    public void tagReady() {
        try {
            /*if (operationMode != READ_TAG)
            {
            // Especially in case there was an issue with writing before, reading the current
            // contents once before writing makes the successive writing successful again.
            // Disadvantage: the example doesn't attempt to write to the target in case reading
            // doesn't succeed at all.
            ndconn.readNDEF();
            }*/
            // Call the handling method depending on the current operation mode.
            switch (operationMode) {
                case READ_TAG:
                    nfcManager.readNDEFMessage();
                    break;
                case WRITE_SP_TAG: {
                    boolean writeMessages[] = new boolean[posterEnabledMessages.size()];
                    posterEnabledMessages.getSelectedFlags(writeMessages);
                    nfcManager.writeSmartPoster(writeMessages, tagUrl.getString(), tagText.getString(), (byte) posterAction.getSelectedIndex(), getSelectedImageName());
                    break;
                }
                case WRITE_IMAGE_TAG:
                    nfcManager.writeImage(getSelectedImageName());
                    break;
                case WRITE_URI_TAG:
                    nfcManager.writeUri(tagUrl.getString());
                    break;
                case WRITE_TEXT_TAG:
                    nfcManager.writeText(tagText.getString(), tagTextLanguage.getString());
                    break;
                case WRITE_SMS_TAG: {
                    boolean writeMessages[] = new boolean[tagSmsEnabledMessages.size()];
                    tagSmsEnabledMessages.getSelectedFlags(writeMessages);
                    nfcManager.writeSms(writeMessages, tagSmsNumber.getString(), tagSmsBody.getString(), tagText.getString(), (byte) posterAction.getSelectedIndex());
                    break;
                }
                case WRITE_ANNOTATED_URL_TAG:
                    nfcManager.writeAnnotatedUrl(tagUrl.getString(), tagText.getString());
                    break;
                case WRITE_GEO_TAG:
                    nfcManager.writeGeo(Double.parseDouble(tagLatitude.getString()), Double.parseDouble(tagLongitude.getString()), tagGeoType.getSelectedIndex());
                    break;
                case WRITE_CUSTOM_TAG:
                    nfcManager.writeCustom(tagTypeUri.getString(), tagCustomPayload.getString().getBytes("utf-8"));
                    break;
                case WRITE_COMBINATION_TAG:
                    nfcManager.writeCombination(tagUrl.getString(), tagTypeUri.getString(), tagCustomPayload.getString().getBytes("utf-8"));
                    break;
                case WRITE_VCALENDAR_TAG:
                    nfcManager.writeVcalendar(tagCalSummary.getString(), tagCalStart.getDate(), tagCalEnd.getDate(), false);
                    break;
                case READ_RAW_TAG: {
                    nfcManager.readRawData();
                    break; }
                case WRITE_RAW_TAG: {
                    final String fileName = TagFileManager.getNewestNfcFile();
                    if (fileName == null) {
                        displayAlert("No data file", "Didn't find data file in " + TagFileManager.nfcDir(), AlertType.ERROR);
                    } else {
                        displayAlert("Using file", fileName, AlertType.INFO);
                        byte[] data = TagFileManager.readFile(fileName);
                        nfcManager.writeRawData(data);
                    }
                    break; }
                case CLONE_TAG:
                    if (cloneStatus == 1) {
                        // Read tag
                        if (nfcManager.readAndCacheMessage()) {
                            cloneStatus = 2;
                            cloneTagStatus.setLabel("Touch another tag to write the cached NDEF message.");
                        }
                    } else if (cloneStatus == 2) {
                        // Write tag
                        nfcManager.writeCachedMessage();
                    }
                    break;
                case DELETE_TAG:
                    nfcManager.deleteNDEFMessage();
                    break;
                default:
                    displayAlert("Illegal Action", "", AlertType.ERROR);
                    break;
            }

        } catch (IOException ex) {
            displayAlert("IOException", "Error loading image" + ex.toString(), AlertType.ERROR);
        } 

    }
    
    /**
     * Setup the UI for the new operation mode. Will show the correct selection
     * UI element and the according instructions. All other UI elements are cleared
     * from the screen.
     * @param oldOperationMode operation mode that was active before.
     * @param newOperationMode operation mode to be activated.
     */
    private void setupFormHeader(final int oldOperationMode, final int newOperationMode) {

        if (!isWriteOperationMode(oldOperationMode) && 
                isWriteOperationMode(newOperationMode)) {
            // Switched from a read/delete mode to a write mode:
            // Make operation mode selector small and add instructions
            this.deleteAll();
            //operationModeSelector = new ChoiceGroup("Choose Mode", ChoiceGroup.POPUP, operatingModeNames, null);
            operationModeSelector.setSelectedIndex(newOperationMode, true);
            this.append(operationModeSelector);
            String instructionsTxt;
            if (newOperationMode == WRITE_RAW_TAG) {
                final String fileName = TagFileManager.getNewestNfcFile();
                if (fileName == null) {
                    instructionsTxt = "No data file available in " + TagFileManager.nfcDir() + "\nSave a raw tag first.";
                } else {
                    instructionsTxt = "Will write tag contents from " + fileName;
                }
            } else {
                instructionsTxt = "Touch a tag to write the currently visible settings";
            }
            StringItem instructions = new StringItem(instructionsTxt, null);
            this.append(instructions);
        } else if (isWriteOperationMode(oldOperationMode) &&
                isWriteOperationMode(newOperationMode)) {
            // Was write before and is write now - leave first two elements
            // Clean up UI elements - everything except the first two elements
            // (-> which is the operation mode choice group and the instructions)
            resetFormExceptFirstX(2);
        } else if (isWriteOperationMode(oldOperationMode) &&
                !isWriteOperationMode(newOperationMode)) {
            // Switched from write operation mode to read/delete
            // Make selector big again
            this.deleteAll();
            //operationModeSelector = new ChoiceGroup("Choose Mode", ChoiceGroup.EXCLUSIVE, operatingModeNames, null);
            operationModeSelector.setSelectedIndex(newOperationMode, true);
            this.append(operationModeSelector);
        }
        if (!isWriteOperationMode(newOperationMode)) {
            // Make sure the correct instructions are set for read / delete
            resetFormExceptFirstX(1);
            if (newOperationMode == READ_TAG || newOperationMode == READ_RAW_TAG) {
                StringItem instructions = new StringItem("Touch a tag to read its contents", null);
                this.append(instructions);
            } else if (newOperationMode == DELETE_TAG) {
                StringItem instructions = new StringItem("Touch a tag to delete its contents (-> write an empty NDEF message)", null);
                this.append(instructions);
            }
            // Use the exit command
            this.removeCommand(backCommand);
            this.addCommand(exitCommand);
        } else {
            // Writable mode: use the back command.
            this.removeCommand(exitCommand);
            this.addCommand(backCommand);
        }
    }
    
    /**
     * Clear all elements of the Form UI-element except the first few.
     * @param numRemainingElements how many UI elements should remain from the
     * beginning of the UI.
     */
    private void resetFormExceptFirstX(final int numRemainingElements) {
        final int numChoiceElements = this.size();
        if (numChoiceElements > numRemainingElements) {
            for (int i = numChoiceElements - 1; i > numRemainingElements - 1; i--) {
                this.delete(i);
            }
        }
    }
    
    /**
     * Check if the specified operating mode is one that would write a tag 
     * (-> true) or one that reads / deletes a tag (-> false).
     * @param operationMode operation mode to check
     * @return true if the operation mode will write user-specified data to the
     * tag, false if it reads / deletes the tag.
     */
    private boolean isWriteOperationMode(final int operationMode) {
        return !(operationMode == READ_TAG || operationMode == READ_RAW_TAG || operationMode == DELETE_TAG || operationMode == CLONE_TAG);
    }
    
    private String getSelectedImageName() {
        switch (tagChooseImage.getSelectedIndex()) {
            case 0:
                return "/minimal.gif";
            case 1:
                return "/minimal.png";
            default:
                return "/nokia.png";
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // UI Handling code
    /**
     * Implementation of the call-back function of the CommandListener
     * @param command command key pressed
     * @param displayable associated displayable Object
     */
    public void commandAction(Command command, Displayable displayable) {
        if (command == exitCommand) {
            // Exit the application
            midlet.exitApp();
        } else if (command == backCommand) {
            // Go back to read mode
            activateOperationMode(READ_TAG);
        }
    }

    /**
     * Callback from the NFC Manager when reading / writing to a text was
     * not successful.
     * @param text Text to show in a message box if desired.
     */
    public void tagError(final String text) {
        displayAlert(this.getTitle(), text, AlertType.ERROR);
    }

    /**
     * Callback from the NFC Manager when writing to a tag was successful.
     * @param text Text to show in a message box if desired.
     */
    public void tagSuccess(final String text) {
        displayAlert(this.getTitle(), text, AlertType.CONFIRMATION);
    }

    /**
     * Utility function to show a Java ME alert, as used for informing the user
     * about events in this demo app.
     * @param title title text to use for the message box.
     * @param text text to show as the main message in the box.
     * @param type one of the available alert types, defining the icon, sound
     * and display length.
     */
    public void displayAlert(final String title, final String text, final AlertType type) {
        Alert al = new Alert(title, text, null, type);
        Display.getDisplay(midlet).setCurrent(al, this);
    }

    public void logTagInfo(String text) {
        // Replace previous contents with new text
        tagContents.setString(text);
    }

    
}
