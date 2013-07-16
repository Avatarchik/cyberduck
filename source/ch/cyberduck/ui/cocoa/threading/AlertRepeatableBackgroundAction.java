package ch.cyberduck.ui.cocoa.threading;

/*
 *  Copyright (c) 2008 David Kocher. All rights reserved.
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

import ch.cyberduck.core.Path;
import ch.cyberduck.core.Preferences;
import ch.cyberduck.core.Session;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.threading.BackgroundException;
import ch.cyberduck.core.threading.RepeatableBackgroundAction;
import ch.cyberduck.ui.cocoa.AbstractTableDelegate;
import ch.cyberduck.ui.cocoa.Action;
import ch.cyberduck.ui.cocoa.AlertController;
import ch.cyberduck.ui.cocoa.ErrorController;
import ch.cyberduck.ui.cocoa.ListDataSource;
import ch.cyberduck.ui.cocoa.Outlet;
import ch.cyberduck.ui.cocoa.SheetCallback;
import ch.cyberduck.ui.cocoa.SheetController;
import ch.cyberduck.ui.cocoa.TableColumnFactory;
import ch.cyberduck.ui.cocoa.WindowController;
import ch.cyberduck.ui.cocoa.application.NSAlert;
import ch.cyberduck.ui.cocoa.application.NSButton;
import ch.cyberduck.ui.cocoa.application.NSCell;
import ch.cyberduck.ui.cocoa.application.NSTableColumn;
import ch.cyberduck.ui.cocoa.application.NSTableView;
import ch.cyberduck.ui.cocoa.application.NSTextView;
import ch.cyberduck.ui.cocoa.foundation.NSAttributedString;
import ch.cyberduck.ui.cocoa.foundation.NSNotification;
import ch.cyberduck.ui.cocoa.foundation.NSObject;
import ch.cyberduck.ui.cocoa.view.ControllerCell;

import org.rococoa.Foundation;
import org.rococoa.ID;
import org.rococoa.Rococoa;
import org.rococoa.cocoa.CGFloat;
import org.rococoa.cocoa.foundation.NSInteger;

import java.util.ArrayList;
import java.util.List;

/**
 * @version $Id: AlertRepeatableBackgroundAction.java 10446 2012-10-18 17:20:41Z dkocher $
 */
public abstract class AlertRepeatableBackgroundAction extends RepeatableBackgroundAction {

    private WindowController controller;

    public AlertRepeatableBackgroundAction(WindowController controller) {
        this.controller = controller;
    }

    @Override
    public void finish() {
        super.finish();
        // If there was any failure, display the summary now
        if(this.hasFailed() && !this.isCanceled()) {
            // Display alert if the action was not canceled intentionally
            this.alert();
        }
        this.reset();
    }

    private void callback(final int returncode) {
        if(returncode == SheetCallback.DEFAULT_OPTION) {
            //Try Again
            for(BackgroundException e : getExceptions()) {
                final Path workdir = e.getPath();
                if(null == workdir) {
                    continue;
                }
                for(Session session : this.getSessions()) {
                    session.cache().invalidate(workdir.getReference());
                }
            }
            AlertRepeatableBackgroundAction.this.reset();
            // Re-run the action with the previous lock used
            controller.background(AlertRepeatableBackgroundAction.this);
        }
    }

    /**
     * Display an alert dialog with a summary of all failed tasks
     */
    protected void alert() {
        if(controller.isVisible()) {
            if(this.getExceptions().size() == 1) {
                final BackgroundException failure = this.getExceptions().get(0);
                String detail = failure.getDetailedCauseMessage();
                String title = failure.getReadableTitle() + ": " + failure.getMessage();
                NSAlert alert = NSAlert.alert(title, //title
                        Locale.localizedString(detail),
                        Locale.localizedString("Try Again", "Alert"), // default button
                        AlertRepeatableBackgroundAction.this.isNetworkFailure() ? Locale.localizedString("Network Diagnostics") : null, //other button
                        Locale.localizedString("Cancel") // alternate button
                );
                alert.setShowsHelp(true);
                final AlertController c = new AlertController(AlertRepeatableBackgroundAction.this.controller, alert) {
                    @Override
                    public void callback(final int returncode) {
                        if(returncode == SheetCallback.ALTERNATE_OPTION) {
                            AlertRepeatableBackgroundAction.this.diagnose();
                        }
                        if(returncode == SheetCallback.DEFAULT_OPTION) {
                            AlertRepeatableBackgroundAction.this.callback(returncode);
                        }
                    }

                    @Override
                    protected void help() {
                        StringBuilder site = new StringBuilder(Preferences.instance().getProperty("website.help"));
                        if(null != failure.getPath()) {
                            site.append("/").append(failure.getPath().getSession().getHost().getProtocol().getProvider());
                        }
                        controller.openUrl(site.toString());
                    }
                };
                c.beginSheet();
            }
            else {
                final SheetController c = new AlertSheetController();
                c.beginSheet();
            }
        }
    }

    /**
     *
     */
    private class AlertSheetController extends SheetController {

        public AlertSheetController() {
            super(AlertRepeatableBackgroundAction.this.controller);
        }

        @Override
        protected String getBundleName() {
            return "Alert";
        }

        @Override
        public void awakeFromNib() {
            final boolean transcript = AlertRepeatableBackgroundAction.this.hasTranscript();
            this.setState(transcriptButton, transcript && Preferences.instance().getBoolean("alert.toggle.transcript"));
            transcriptButton.setEnabled(transcript);
            super.awakeFromNib();
        }

        @Override
        protected void invalidate() {
            errorView.setDataSource(null);
            errorView.setDelegate(null);
            super.invalidate();
        }

        @Outlet
        private NSButton diagnosticsButton;

        public void setDiagnosticsButton(NSButton diagnosticsButton) {
            this.diagnosticsButton = diagnosticsButton;
            this.diagnosticsButton.setTarget(this.id());
            this.diagnosticsButton.setAction(Foundation.selector("diagnosticsButtonClicked:"));
            this.diagnosticsButton.setHidden(!AlertRepeatableBackgroundAction.this.isNetworkFailure());
        }

        @Action
        public void diagnosticsButtonClicked(final NSButton sender) {
            AlertRepeatableBackgroundAction.this.diagnose();
        }

        @Outlet
        private NSButton transcriptButton;

        public void setTranscriptButton(NSButton transcriptButton) {
            this.transcriptButton = transcriptButton;
        }

        private final TableColumnFactory tableColumnsFactory = new TableColumnFactory();

        @Outlet
        private NSTableView errorView;
        private ListDataSource model;
        private AbstractTableDelegate<ErrorController> delegate;

        private List<ErrorController> errors;

        public void setErrorView(NSTableView errorView) {
            this.errorView = errorView;
            this.errorView.setRowHeight(new CGFloat(77));
            this.errors = new ArrayList<ErrorController>();
            for(BackgroundException e : getExceptions()) {
                errors.add(new ErrorController(e));
            }
            this.errorView.setDataSource((model = new ListDataSource() {
                @Override
                public NSInteger numberOfRowsInTableView(NSTableView view) {
                    return new NSInteger(errors.size());
                }

                @Override
                public NSObject tableView_objectValueForTableColumn_row(NSTableView view, NSTableColumn tableColumn, NSInteger row) {
                    return null;
                }
            }).id());
            this.errorView.setDelegate((delegate = new AbstractTableDelegate<ErrorController>() {
                @Override
                public void tableColumnClicked(NSTableView view, NSTableColumn tableColumn) {
                }

                @Override
                public void tableRowDoubleClicked(final ID sender) {
                }

                @Override
                public boolean selectionShouldChange() {
                    return false;
                }

                @Override
                public void selectionDidChange(NSNotification notification) {
                }

                @Override
                protected boolean isTypeSelectSupported() {
                    return false;
                }

                @Override
                public void enterKeyPressed(final ID sender) {
                }

                @Override
                public void deleteKeyPressed(final ID sender) {
                }

                @Override
                public String tooltip(ErrorController e) {
                    return e.getTooltip();
                }

                public void tableView_willDisplayCell_forTableColumn_row(NSTableView view, NSCell cell, NSTableColumn tableColumn, NSInteger row) {
                    Rococoa.cast(cell, ControllerCell.class).setView(errors.get(row.intValue()).view());
                }
            }).id());
            {
                NSTableColumn c = tableColumnsFactory.create("Error");
                c.setMinWidth(50f);
                c.setWidth(400f);
                c.setMaxWidth(1000f);
                c.setDataCell(prototype);
                this.errorView.addTableColumn(c);
            }
        }

        private final NSCell prototype = ControllerCell.controllerCell();

        @Outlet
        private NSTextView transcriptView;

        public void setTranscriptView(NSTextView transcriptView) {
            this.transcriptView = transcriptView;
            this.transcriptView.textStorage().setAttributedString(
                    NSAttributedString.attributedStringWithAttributes(AlertRepeatableBackgroundAction.this.getTranscript(), FIXED_WITH_FONT_ATTRIBUTES));
        }

        @Override
        public void callback(final int returncode) {
            Preferences.instance().setProperty("alert.toggle.transcript", this.transcriptButton.state());
            AlertRepeatableBackgroundAction.this.callback(returncode);
        }
    }
}