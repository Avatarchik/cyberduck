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

import ch.cyberduck.core.NSObjectPathReference;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.Preferences;
import ch.cyberduck.core.ProgressListener;
import ch.cyberduck.core.Session;
import ch.cyberduck.core.date.UserDateFormatterFactory;
import ch.cyberduck.core.formatter.SizeFormatterFactory;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.core.transfer.TransferAction;
import ch.cyberduck.core.transfer.TransferPrompt;
import ch.cyberduck.core.transfer.download.DownloadTransfer;
import ch.cyberduck.core.transfer.synchronisation.SyncTransfer;
import ch.cyberduck.core.transfer.upload.UploadTransfer;
import ch.cyberduck.ui.cocoa.application.*;
import ch.cyberduck.ui.cocoa.foundation.NSAttributedString;
import ch.cyberduck.ui.cocoa.foundation.NSIndexSet;
import ch.cyberduck.ui.cocoa.foundation.NSNotification;
import ch.cyberduck.ui.cocoa.foundation.NSObject;
import ch.cyberduck.ui.cocoa.resources.IconCache;
import ch.cyberduck.ui.cocoa.threading.WindowMainAction;
import ch.cyberduck.ui.cocoa.view.OutlineCell;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.rococoa.Foundation;
import org.rococoa.ID;
import org.rococoa.Rococoa;
import org.rococoa.cocoa.CGFloat;
import org.rococoa.cocoa.foundation.NSInteger;
import org.rococoa.cocoa.foundation.NSUInteger;

import java.text.MessageFormat;

/**
 * @version $Id: TransferPromptController.java 10586 2012-11-21 16:14:24Z dkocher $
 */
public abstract class TransferPromptController extends SheetController implements TransferPrompt {
    private static Logger log = Logger.getLogger(TransferPromptController.class);

    public static TransferPromptController create(WindowController parent, final Transfer transfer) {
        if(transfer instanceof DownloadTransfer) {
            return new DownloadPromptController(parent, transfer);
        }
        if(transfer instanceof UploadTransfer) {
            return new UploadPromptController(parent, transfer);
        }
        if(transfer instanceof SyncTransfer) {
            return new SyncPromptController(parent, transfer);
        }
        throw new IllegalArgumentException(transfer.toString());
    }

    private final TableColumnFactory tableColumnsFactory
            = new TableColumnFactory();

    public TransferPromptController(final WindowController parent, final Transfer transfer) {
        super(parent);
        this.transfer = transfer;
    }

    @Override
    protected String getBundleName() {
        return "Prompt";
    }

    @Outlet
    private NSButton toggleDetailsButton;

    public void setToggleDetailsButton(NSButton toggleDetailsButton) {
        this.toggleDetailsButton = toggleDetailsButton;
    }

    @Override
    public void awakeFromNib() {
        for(Session s : transfer.getSessions()) {
            s.addProgressListener(l);
        }
        this.reloadData();
        if(browserView.numberOfRows().intValue() > 0) {
            browserView.selectRowIndexes(NSIndexSet.indexSetWithIndex(new NSInteger(0)), false);
        }
        this.setState(this.toggleDetailsButton, Preferences.instance().getBoolean("transfer.toggle.details"));

        super.awakeFromNib();
    }

    @Override
    public void invalidate() {
        for(Session s : transfer.getSessions()) {
            s.removeProgressListener(l);
        }
        browserView.setDataSource(null);
        browserView.setDelegate(null);
        browserModel.invalidate();
        super.invalidate();
    }

    /**
     *
     */
    private ProgressListener l = new ProgressListener() {
        @Override
        public void message(final String msg) {
            invoke(new WindowMainAction(TransferPromptController.this) {
                @Override
                public void run() {
                    // Update the status label at the bottom of the browser window
                    statusLabel.setAttributedStringValue(NSAttributedString.attributedStringWithAttributes(msg,
                            TRUNCATE_MIDDLE_ATTRIBUTES));
                }
            });
        }
    };

    protected TransferAction action
            = TransferAction.forName(Preferences.instance().getProperty("queue.prompt.action.default"));

    public TransferAction getAction() {
        return action;
    }

    protected Transfer transfer;

    @Override
    public void callback(final int returncode) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Callback with return code %d", returncode));
        }
        if(returncode == CANCEL_OPTION) { // Abort
            action = TransferAction.ACTION_CANCEL;
        }
        Preferences.instance().setProperty("transfer.toggle.details", this.toggleDetailsButton.state());
    }

    /**
     * Reload the files in the prompt dialog
     */
    public void reloadData() {
        if(log.isDebugEnabled()) {
            log.debug("Reload table view");
        }
        statusIndicator.startAnimation(null);
        browserView.reloadData();
        statusIndicator.stopAnimation(null);
        statusLabel.setAttributedStringValue(NSAttributedString.attributedStringWithAttributes(
                MessageFormat.format(Locale.localizedString("{0} Files"), String.valueOf(browserView.numberOfRows())),
                TRUNCATE_MIDDLE_ATTRIBUTES));
    }

    @Override
    public TransferAction prompt() {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Prompt for transfer action of %s", transfer.getName()));
        }
        for(Path next : transfer.getRoots()) {
            if(browserModel.filter().accept(next)) {
                browserModel.add(next);
            }
        }
        this.beginSheet();
        return action;
    }

    private static final NSAttributedString UNKNOWN_STRING = NSAttributedString.attributedStringWithAttributes(
            Locale.localizedString("Unknown"),
            TRUNCATE_MIDDLE_ATTRIBUTES);

    // Setting appearance attributes
    final NSLayoutManager layoutManager = NSLayoutManager.layoutManager();

    /**
     * A browsable listing of duplicate files and folders
     */
    @Outlet
    protected NSOutlineView browserView;

    protected TransferPromptModel browserModel;

    protected AbstractPathTableDelegate browserViewDelegate;

    public void setBrowserView(final NSOutlineView view) {
        this.browserView = view;
        this.browserView.setHeaderView(null);
        this.browserView.setRowHeight(new CGFloat(layoutManager.defaultLineHeightForFont(
                NSFont.systemFontOfSize(Preferences.instance().getFloat("browser.font.size"))).intValue() + 2));
        this.browserView.setDataSource(this.browserModel.id());
        this.browserView.setDelegate((this.browserViewDelegate = new AbstractPathTableDelegate() {

            @Override
            public void enterKeyPressed(final ID sender) {
                //
            }

            @Override
            public void deleteKeyPressed(final ID sender) {
                //
            }

            @Override
            public void tableColumnClicked(NSTableView view, NSTableColumn tableColumn) {
                //
            }

            @Override
            public void tableRowDoubleClicked(final ID sender) {
                //
            }

            @Override
            public void selectionDidChange(NSNotification notification) {
                if(browserView.selectedRow().intValue() == -1) {
                    remoteURLField.setStringValue(StringUtils.EMPTY);
                    remoteSizeField.setStringValue(StringUtils.EMPTY);
                    remoteModificationField.setStringValue(StringUtils.EMPTY);
                    localURLField.setStringValue(StringUtils.EMPTY);
                    localSizeField.setStringValue(StringUtils.EMPTY);
                    localModificationField.setStringValue(StringUtils.EMPTY);
                }
                else {
                    final Path p = browserModel.lookup(new NSObjectPathReference(
                            browserView.itemAtRow(browserView.selectedRow())));
                    localURLField.setAttributedStringValue(NSAttributedString.attributedStringWithAttributes(
                            p.getLocal().getAbsolute(),
                            TRUNCATE_MIDDLE_ATTRIBUTES));
                    if(p.getLocal().attributes().getSize() == -1) {
                        localSizeField.setAttributedStringValue(UNKNOWN_STRING);
                    }
                    else {
                        localSizeField.setAttributedStringValue(NSAttributedString.attributedStringWithAttributes(
                                SizeFormatterFactory.get().format(p.getLocal().attributes().getSize()),
                                TRUNCATE_MIDDLE_ATTRIBUTES));
                    }
                    if(p.getLocal().attributes().getModificationDate() == -1) {
                        localModificationField.setAttributedStringValue(UNKNOWN_STRING);
                    }
                    else {
                        localModificationField.setAttributedStringValue(NSAttributedString.attributedStringWithAttributes(
                                UserDateFormatterFactory.get().getLongFormat(p.getLocal().attributes().getModificationDate()),
                                TRUNCATE_MIDDLE_ATTRIBUTES));
                    }
                    remoteURLField.setAttributedStringValue(NSAttributedString.attributedStringWithAttributes(
                            p.toURL(false),
                            TRUNCATE_MIDDLE_ATTRIBUTES));
                    if(p.attributes().getSize() == -1) {
                        remoteSizeField.setAttributedStringValue(UNKNOWN_STRING);
                    }
                    else {
                        remoteSizeField.setAttributedStringValue(NSAttributedString.attributedStringWithAttributes(
                                SizeFormatterFactory.get().format(p.attributes().getSize()),
                                TRUNCATE_MIDDLE_ATTRIBUTES));
                    }
                    if(p.attributes().getModificationDate() == -1) {
                        remoteModificationField.setAttributedStringValue(UNKNOWN_STRING);
                    }
                    else {
                        remoteModificationField.setAttributedStringValue(NSAttributedString.attributedStringWithAttributes(
                                UserDateFormatterFactory.get().getLongFormat(p.attributes().getModificationDate()),
                                TRUNCATE_MIDDLE_ATTRIBUTES));
                    }
                }
            }

            @Override
            protected boolean isTypeSelectSupported() {
                return true;
            }

            public String tableView_typeSelectStringForTableColumn_row(NSTableView view,
                                                                       NSTableColumn column,
                                                                       NSInteger row) {
                final Path p = browserModel.lookup(new NSObjectPathReference(browserView.itemAtRow(row)));
                return p.getName();
            }

            public void outlineView_willDisplayCell_forTableColumn_item(NSOutlineView view, NSCell cell,
                                                                        NSTableColumn column, NSObject item) {
                if(null == item) {
                    return;
                }
                final String identifier = column.identifier();
                final Path path = browserModel.lookup(new NSObjectPathReference(item));
                if(identifier.equals(TransferPromptModel.INCLUDE_COLUMN)) {
                    cell.setEnabled(!transfer.isSkipped(path) && !getAction().equals(TransferAction.ACTION_SKIP));
                }
                if(identifier.equals(TransferPromptModel.FILENAME_COLUMN)) {
                    (Rococoa.cast(cell, OutlineCell.class)).setIcon(IconCache.instance().iconForPath(path, 16));
                }
                if(cell.isKindOfClass(Foundation.getClass(NSTextFieldCell.class.getSimpleName()))) {
                    if(transfer.isSkipped(path) || !transfer.isSelected(path) || getAction().equals(TransferAction.ACTION_SKIP)) {
                        Rococoa.cast(cell, NSTextFieldCell.class).setTextColor(NSColor.disabledControlTextColor());
                    }
                    else {
                        Rococoa.cast(cell, NSTextFieldCell.class).setTextColor(NSColor.controlTextColor());
                    }
                }
            }
        }).id());
        // selection properties
        this.browserView.setAllowsMultipleSelection(true);
        this.browserView.setAllowsEmptySelection(true);
        this.browserView.setAllowsColumnResizing(true);
        this.browserView.setAllowsColumnSelection(false);
        this.browserView.setAllowsColumnReordering(true);
        this.browserView.setUsesAlternatingRowBackgroundColors(Preferences.instance().getBoolean("browser.alternatingRows"));
        if(Preferences.instance().getBoolean("browser.horizontalLines") && Preferences.instance().getBoolean("browser.verticalLines")) {
            this.browserView.setGridStyleMask(new NSUInteger(NSTableView.NSTableViewSolidHorizontalGridLineMask.intValue() | NSTableView.NSTableViewSolidVerticalGridLineMask.intValue()));
        }
        else if(Preferences.instance().getBoolean("browser.verticalLines")) {
            this.browserView.setGridStyleMask(NSTableView.NSTableViewSolidVerticalGridLineMask);
        }
        else if(Preferences.instance().getBoolean("browser.horizontalLines")) {
            this.browserView.setGridStyleMask(NSTableView.NSTableViewSolidHorizontalGridLineMask);
        }
        else {
            this.browserView.setGridStyleMask(NSTableView.NSTableViewGridNone);
        }
        {
            NSTableColumn c = tableColumnsFactory.create(TransferPromptModel.FILENAME_COLUMN);
            c.headerCell().setStringValue(Locale.localizedString("Filename"));
            c.setMinWidth(100f);
            c.setWidth(220f);
            c.setMaxWidth(800f);
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask | NSTableColumn.NSTableColumnUserResizingMask);
            c.setEditable(false);
            c.setDataCell(outlineCellPrototype);
            this.browserView.addTableColumn(c);
            this.browserView.setOutlineTableColumn(c);
        }
        {
            NSTableColumn c = tableColumnsFactory.create(TransferPromptModel.SIZE_COLUMN);
            c.headerCell().setStringValue(StringUtils.EMPTY);
            c.setMinWidth(50f);
            c.setWidth(80f);
            c.setMaxWidth(100f);
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask | NSTableColumn.NSTableColumnUserResizingMask);
            c.setEditable(false);
            c.setDataCell(textCellPrototype);
            this.browserView.addTableColumn(c);
        }
        {
            NSTableColumn c = tableColumnsFactory.create(TransferPromptModel.WARNING_COLUMN);
            c.headerCell().setStringValue(StringUtils.EMPTY);
            c.setMinWidth(20f);
            c.setWidth(20f);
            c.setMaxWidth(20f);
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask);
            c.setEditable(false);
            c.setDataCell(imageCellPrototype);
            c.dataCell().setAlignment(NSText.NSCenterTextAlignment);
            this.browserView.addTableColumn(c);
        }
        {
            NSTableColumn c = tableColumnsFactory.create(TransferPromptModel.INCLUDE_COLUMN);
            c.headerCell().setStringValue(StringUtils.EMPTY);
            c.setMinWidth(20f);
            c.setWidth(20f);
            c.setMaxWidth(20f);
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask);
            c.setEditable(false);
            final NSButtonCell cell = buttonCellPrototype;
            cell.setTitle(StringUtils.EMPTY);
            cell.setControlSize(NSCell.NSSmallControlSize);
            cell.setButtonType(NSButtonCell.NSSwitchButton);
            cell.setAllowsMixedState(false);
            cell.setTarget(this.id());
            cell.setAlignment(NSText.NSCenterTextAlignment);
            c.setDataCell(cell);
            this.browserView.addTableColumn(c);
        }
        this.browserView.sizeToFit();
    }

    protected final NSButtonCell buttonCellPrototype = NSButtonCell.buttonCell();
    protected final NSTextFieldCell outlineCellPrototype = OutlineCell.outlineCell();
    protected final NSImageCell imageCellPrototype = NSImageCell.imageCell();
    protected final NSTextFieldCell textCellPrototype = NSTextFieldCell.textFieldCell();

    @Outlet
    private NSTextField remoteURLField;

    public void setRemoteURLField(final NSTextField f) {
        this.remoteURLField = f;
    }

    @Outlet
    private NSTextField remoteSizeField;

    public void setRemoteSizeField(final NSTextField f) {
        this.remoteSizeField = f;
    }

    @Outlet
    private NSTextField remoteModificationField;

    public void setRemoteModificationField(final NSTextField f) {
        this.remoteModificationField = f;
    }

    @Outlet
    private NSTextField localURLField;

    public void setLocalURLField(final NSTextField f) {
        this.localURLField = f;
    }

    @Outlet
    private NSTextField localSizeField;

    public void setLocalSizeField(final NSTextField f) {
        this.localSizeField = f;
    }

    @Outlet
    private NSTextField localModificationField;

    public void setLocalModificationField(final NSTextField f) {
        this.localModificationField = f;
    }

    @Outlet
    private NSProgressIndicator statusIndicator;

    public void setStatusIndicator(final NSProgressIndicator f) {
        this.statusIndicator = f;
        this.statusIndicator.setDisplayedWhenStopped(false);
    }

    @Outlet
    private NSTextField statusLabel;

    public void setStatusLabel(final NSTextField f) {
        this.statusLabel = f;
    }

    @Outlet
    protected NSPopUpButton actionPopup;

    public void setActionPopup(final NSPopUpButton actionPopup) {
        this.actionPopup = actionPopup;
        this.actionPopup.removeAllItems();

        final TransferAction defaultAction
                = TransferAction.forName(Preferences.instance().getProperty("queue.prompt.action.default"));

        boolean renameSupported = true;
        for(Session s : transfer.getSessions()) {
            if(!s.isRenameSupported(transfer.getRoot())) {
                renameSupported = false;
                break;
            }
        }

        final TransferAction[] actions = new TransferAction[]{
                transfer.isResumable() ? TransferAction.ACTION_RESUME : null,
                TransferAction.ACTION_OVERWRITE,
                TransferAction.ACTION_RENAME,
                TransferAction.ACTION_SKIP,
                renameSupported ? TransferAction.ACTION_RENAME_EXISTING : null};

        for(TransferAction action : actions) {
            if(null == action) {
                continue; //Not resumeable
            }
            this.actionPopup.addItemWithTitle(action.getLocalizableString());
            this.actionPopup.lastItem().setRepresentedObject(action.toString());
            if(action.equals(defaultAction)) {
                this.actionPopup.selectItem(actionPopup.lastItem());
            }
        }
        this.action = TransferAction.forName(this.actionPopup.selectedItem().representedObject());
        this.actionPopup.setTarget(this.id());
        this.actionPopup.setAction(Foundation.selector("actionPopupClicked:"));
    }

    @Action
    public void actionPopupClicked(NSPopUpButton sender) {
        final TransferAction selected = TransferAction.forName(sender.selectedItem().representedObject());

        if(action.equals(selected)) {
            return;
        }
        Preferences.instance().setProperty("queue.prompt.action.default", selected.toString());
        action = selected;
        this.reloadData();
    }
}
