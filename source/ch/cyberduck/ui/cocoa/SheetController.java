package ch.cyberduck.ui.cocoa;

/*
 *  Copyright (c) 2005 David Kocher. All rights reserved.
 *  http://cyberduck.ch/
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Bug fixes, suggestions and comments should be sent to:
 *  dkocher@cyberduck.ch
 */

import ch.cyberduck.ui.cocoa.application.AppKitFunctionsLibrary;
import ch.cyberduck.ui.cocoa.application.NSApplication;
import ch.cyberduck.ui.cocoa.application.NSButton;
import ch.cyberduck.ui.cocoa.application.NSPanel;
import ch.cyberduck.ui.cocoa.application.NSWindow;
import ch.cyberduck.ui.cocoa.foundation.NSThread;
import ch.cyberduck.ui.threading.ControllerMainAction;

import org.apache.log4j.Logger;
import org.rococoa.Foundation;
import org.rococoa.ID;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * @version $Id: SheetController.java 10695 2012-12-21 14:39:35Z dkocher $
 */
public abstract class SheetController extends WindowController implements SheetCallback {
    private static Logger log = Logger.getLogger(SheetController.class);

    /**
     * The controller of the parent window
     */
    protected final WindowController parent;

    /**
     * Dismiss button clicked
     */
    private int returncode;

    private CountDownLatch signal;

    /**
     * The sheet window must be provided later with #setWindow (usually called when loading the NIB file)
     *
     * @param parent The controller of the parent window
     */
    public SheetController(final WindowController parent) {
        this.parent = parent;
    }

    /**
     * Use this if no custom sheet is given (and no NIB file loaded)
     *
     * @param parent The controller of the parent window
     * @param sheet  The window to attach as the sheet
     */
    public SheetController(final WindowController parent, NSWindow sheet) {
        this.parent = parent;
        this.window = sheet;
    }

    /**
     * @return Null by default, a sheet with no custom NIB
     */
    @Override
    protected String getBundleName() {
        return null;
    }

    /**
     * @return The controller of this sheet parent window
     */
    protected WindowController getParentController() {
        return parent;
    }

    /**
     * Translate return codes from sheet selection
     *
     * @param selected Button pressed
     * @return Sheet callback constant
     * @see SheetCallback#DEFAULT_OPTION
     * @see SheetCallback#CANCEL_OPTION
     */
    protected int getCallbackOption(NSButton selected) {
        if(selected.tag() == NSPanel.NSOKButton) {
            return SheetCallback.DEFAULT_OPTION;
        }
        if(selected.tag() == NSPanel.NSCancelButton) {
            return SheetCallback.CANCEL_OPTION;
        }
        throw new RuntimeException("Unexpected tag:" + selected.tag());
    }

    /**
     * This must be the target action for any button in the sheet dialog. Will validate the input
     * and close the sheet; #sheetDidClose will be called afterwards
     *
     * @param sender A button in the sheet dialog
     */
    @Action
    public void closeSheet(final NSButton sender) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Close sheet with button %s", sender.title()));
        }
        if(this.getCallbackOption(sender) == DEFAULT_OPTION || this.getCallbackOption(sender) == ALTERNATE_OPTION) {
            if(!this.validateInput()) {
                AppKitFunctionsLibrary.beep();
                return;
            }
        }
        NSApplication.sharedApplication().endSheet(this.window(), this.getCallbackOption(sender));
    }

    /**
     * @return The tag of the button this sheet was dismissed with
     */
    public int returnCode() {
        return this.returncode;
    }

    /**
     * Check input fields for any errors
     *
     * @return true if a valid input has been given
     */
    protected boolean validateInput() {
        return true;
    }

    /**
     *
     */
    public void beginSheet() {
        synchronized(parent.window()) {
            this.signal = new CountDownLatch(1);
            if(NSThread.isMainThread()) {
                // No need to call invoke on main thread
                this.beginSheetImpl();
            }
            else {
                invoke(new ControllerMainAction(this) {
                    @Override
                    public void run() {
                        //Invoke again on main thread
                        beginSheetImpl();
                    }
                }, true);
                if(log.isDebugEnabled()) {
                    log.debug("Await sheet dismiss");
                }
                // Synchronize on parent controller. Only display one sheet at once.
                try {
                    this.signal.await();
                }
                catch(InterruptedException e) {
                    log.error("Error waiting for sheet dismiss", e);
                }
            }
        }
    }

    /**
     * Keep a reference to the sheet to protect it from being
     * deallocated as a weak reference before the callback from the runtime
     */
    protected static final Set<SheetController> sheetRegistry
            = new HashSet<SheetController>();

    protected void beginSheetImpl() {
        this.loadBundle();
        parent.window().makeKeyAndOrderFront(null);
        NSApplication.sharedApplication().beginSheet(this.window(), //window
                parent.window(), // modalForWindow
                this.id(), // modalDelegate
                Foundation.selector("sheetDidClose:returnCode:contextInfo:"),
                null); //context
        sheetRegistry.add(this);
    }

    /**
     * Called by the runtime after a sheet has been dismissed. Ends any modal session and
     * sends the returncode to the callback implementation. Also invalidates this controller to be
     * garbage collected and notifies the lock object
     *
     * @param sheet       Sheet window
     * @param returncode  Identifier for the button clicked by the user
     * @param contextInfo Not used
     */
    public void sheetDidClose_returnCode_contextInfo(final NSWindow sheet, final int returncode, ID contextInfo) {
        sheet.orderOut(null);
        this.returncode = returncode;
        this.callback(returncode);
        this.signal.countDown();
        if(!this.isSingleton()) {
            this.invalidate();
        }
        sheetRegistry.remove(this);
    }

    /**
     * @return True if the class is a singleton and the object should
     *         not be invlidated upon the sheet is closed
     * @see #sheetDidClose_returnCode_contextInfo(ch.cyberduck.ui.cocoa.application.NSWindow, int, org.rococoa.ID)
     */
    @Override
    public boolean isSingleton() {
        return false;
    }
}