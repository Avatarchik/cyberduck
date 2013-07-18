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

import ch.cyberduck.core.AbstractLoginController;
import ch.cyberduck.core.Credentials;
import ch.cyberduck.core.KeychainFactory;
import ch.cyberduck.core.LoginCanceledException;
import ch.cyberduck.core.LoginController;
import ch.cyberduck.core.LoginControllerFactory;
import ch.cyberduck.core.Preferences;
import ch.cyberduck.core.Protocol;
import ch.cyberduck.core.Session;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.local.LocalFactory;
import ch.cyberduck.ui.Controller;
import ch.cyberduck.ui.cocoa.application.*;
import ch.cyberduck.ui.cocoa.foundation.NSArray;
import ch.cyberduck.ui.cocoa.foundation.NSEnumerator;
import ch.cyberduck.ui.cocoa.foundation.NSNotification;
import ch.cyberduck.ui.cocoa.foundation.NSNotificationCenter;
import ch.cyberduck.ui.cocoa.foundation.NSObject;
import ch.cyberduck.ui.cocoa.resources.IconCache;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.rococoa.Foundation;
import org.rococoa.ID;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @version $Id: PromptLoginController.java 10832 2013-04-10 14:57:36Z dkocher $
 */
public final class PromptLoginController extends AbstractLoginController {
    private static Logger log = Logger.getLogger(PromptLoginController.class);

    public static void register() {
        LoginControllerFactory.addFactory(Factory.NATIVE_PLATFORM, new Factory());
    }

    private static class Factory extends LoginControllerFactory {
        @Override
        protected LoginController create() {
            return new PromptLoginController(TransferController.instance());
        }

        @Override
        protected LoginController create(Controller c) {
            return new PromptLoginController((WindowController) c);
        }

        @Override
        protected LoginController create(Session s) {
            for(BrowserController c : MainController.getBrowsers()) {
                if(c.getSession() == s) {
                    return this.create(c);
                }
            }
            log.warn("No browser to attach login controller for session:" + s);
            return this.create();
        }
    }

    private WindowController parent;

    private PromptLoginController(final WindowController parent) {
        this.parent = parent;
    }

    @Override
    public void warn(String title, String message, String continueButton, String disconnectButton, String preference)
            throws LoginCanceledException {
        final NSAlert alert = NSAlert.alert(title, message,
                continueButton, // Default Button
                null, // Alternate button
                disconnectButton // Other
        );
        alert.setShowsHelp(true);
        alert.setShowsSuppressionButton(true);
        alert.suppressionButton().setTitle(Locale.localizedString("Don't show again", "Credentials"));
        alert.setAlertStyle(NSAlert.NSWarningAlertStyle);
        StringBuilder site = new StringBuilder(Preferences.instance().getProperty("website.help"));
        site.append("/").append(Protocol.FTP.getIdentifier());
        int option = this.parent.alert(alert, site.toString());
        if(alert.suppressionButton().state() == NSCell.NSOnState) {
            // Never show again.
            Preferences.instance().setProperty(preference, true);
        }
        switch(option) {
            case SheetCallback.CANCEL_OPTION:
                throw new LoginCanceledException();
        }
        //Proceed nevertheless.
    }

    @Override
    public void prompt(final Protocol protocol, final Credentials credentials,
                       final String title, final String reason,
                       final boolean enableKeychain, final boolean enablePublicKey,
                       final boolean enableAnonymous) throws LoginCanceledException {
        final SheetController c = new SheetController(parent) {
            @Override
            protected String getBundleName() {
                return "Login";
            }

            @Override
            public void awakeFromNib() {
                this.update();
                super.awakeFromNib();
            }

            @Override
            public void helpButtonClicked(NSButton sender) {
                StringBuilder site = new StringBuilder(Preferences.instance().getProperty("website.help"));
                site.append("/").append(protocol.getProvider());
                openUrl(site.toString());
            }

            @Outlet
            protected NSImageView iconView;

            public void setIconView(NSImageView iconView) {
                this.iconView = iconView;
                this.iconView.setImage(IconCache.iconNamed(protocol.disk()));
            }

            @Outlet
            private NSTextField usernameLabel;

            public void setUsernameLabel(NSTextField usernameLabel) {
                this.usernameLabel = usernameLabel;
                this.usernameLabel.setStringValue(String.format("%s:", credentials.getUsernamePlaceholder()));
            }

            @Outlet
            private NSTextField passwordLabel;

            public void setPasswordLabel(NSTextField passwordLabel) {
                this.passwordLabel = passwordLabel;
                this.passwordLabel.setStringValue(String.format("%s:", credentials.getPasswordPlaceholder()));
            }

            @Outlet
            private NSTextField titleField;

            public void setTitleField(NSTextField titleField) {
                this.titleField = titleField;
                this.updateField(this.titleField, Locale.localizedString(title, "Credentials"));
            }

            @Outlet
            private NSTextField usernameField;

            public void setUsernameField(NSTextField usernameField) {
                this.usernameField = usernameField;
                this.updateField(this.usernameField, credentials.getUsername());
                this.usernameField.cell().setPlaceholderString(credentials.getUsernamePlaceholder());
                NSNotificationCenter.defaultCenter().addObserver(this.id(),
                        Foundation.selector("userFieldTextDidChange:"),
                        NSControl.NSControlTextDidChangeNotification,
                        this.usernameField);
            }

            public void userFieldTextDidChange(NSNotification notification) {
                credentials.setUsername(usernameField.stringValue());
                if(StringUtils.isNotBlank(credentials.getUsername())) {
                    String password = KeychainFactory.get().getPassword(protocol.getScheme(), protocol.getDefaultPort(),
                            protocol.getDefaultHostname(), credentials.getUsername());
                    if(StringUtils.isNotBlank(password)) {
                        passwordField.setStringValue(password);
                        this.passFieldTextDidChange(notification);
                    }
                }
                this.update();
            }

            @Outlet
            private NSTextField textField;

            public void setTextField(NSTextField textField) {
                this.textField = textField;
                try {
                    // For OAuth2
                    final URI uri = new URI(reason);
                    this.textField.setAttributedStringValue(HyperlinkAttributedStringFactory.create(reason));
                    this.textField.setAllowsEditingTextAttributes(true);
                    this.textField.setSelectable(true);
                }
                catch(URISyntaxException e) {
                    this.updateField(this.textField, Locale.localizedString(reason, "Credentials"));
                }
            }

            @Outlet
            private NSSecureTextField passwordField;

            public void setPasswordField(NSSecureTextField passwordField) {
                this.passwordField = passwordField;
                this.updateField(this.passwordField, credentials.getPassword());
                this.passwordField.cell().setPlaceholderString(credentials.getPasswordPlaceholder());
                NSNotificationCenter.defaultCenter().addObserver(this.id(),
                        Foundation.selector("passFieldTextDidChange:"),
                        NSControl.NSControlTextDidChangeNotification,
                        this.passwordField);
            }

            public void passFieldTextDidChange(NSNotification notification) {
                credentials.setPassword(passwordField.stringValue());
            }

            @Outlet
            private NSButton keychainCheckbox;

            public void setKeychainCheckbox(NSButton keychainCheckbox) {
                this.keychainCheckbox = keychainCheckbox;
                this.keychainCheckbox.setTarget(this.id());
                this.keychainCheckbox.setAction(Foundation.selector("keychainCheckboxClicked:"));
                this.keychainCheckbox.setState(Preferences.instance().getBoolean("connection.login.useKeychain")
                        && Preferences.instance().getBoolean("connection.login.addKeychain") ? NSCell.NSOnState : NSCell.NSOffState);
            }

            public void keychainCheckboxClicked(final NSButton sender) {
                final boolean enabled = sender.state() == NSCell.NSOnState;
                Preferences.instance().setProperty("connection.login.addKeychain", enabled);
            }

            @Outlet
            private NSButton anonymousCheckbox;

            public void setAnonymousCheckbox(NSButton anonymousCheckbox) {
                this.anonymousCheckbox = anonymousCheckbox;
                this.anonymousCheckbox.setTarget(this.id());
                this.anonymousCheckbox.setAction(Foundation.selector("anonymousCheckboxClicked:"));
            }

            @Action
            public void anonymousCheckboxClicked(final NSButton sender) {
                if(sender.state() == NSCell.NSOnState) {
                    credentials.setUsername(Preferences.instance().getProperty("connection.login.anon.name"));
                    credentials.setPassword(Preferences.instance().getProperty("connection.login.anon.pass"));
                }
                if(sender.state() == NSCell.NSOffState) {
                    credentials.setUsername(Preferences.instance().getProperty("connection.login.name"));
                    credentials.setPassword(null);
                }
                this.updateField(this.usernameField, credentials.getUsername());
                this.updateField(this.passwordField, credentials.getPassword());
                this.update();
            }

            @Outlet
            private NSTextField pkLabel;

            public void setPkLabel(NSTextField pkLabel) {
                this.pkLabel = pkLabel;
            }

            @Outlet
            private NSButton pkCheckbox;

            public void setPkCheckbox(NSButton pkCheckbox) {
                this.pkCheckbox = pkCheckbox;
                this.pkCheckbox.setTarget(this.id());
                this.pkCheckbox.setAction(Foundation.selector("pkCheckboxSelectionChanged:"));
            }

            private NSOpenPanel publicKeyPanel;

            @Action
            public void pkCheckboxSelectionChanged(final NSButton sender) {
                if(sender.state() == NSCell.NSOnState) {
                    publicKeyPanel = NSOpenPanel.openPanel();
                    publicKeyPanel.setCanChooseDirectories(false);
                    publicKeyPanel.setCanChooseFiles(true);
                    publicKeyPanel.setAllowsMultipleSelection(false);
                    publicKeyPanel.setMessage(Locale.localizedString("Select the private key in PEM or PuTTY format", "Credentials"));
                    publicKeyPanel.setPrompt(Locale.localizedString("Choose"));
                    publicKeyPanel.beginSheetForDirectory(LocalFactory.createLocal("~/.ssh").getAbsolute(),
                            null, this.window(), this.id(),
                            Foundation.selector("pkSelectionPanelDidEnd:returnCode:contextInfo:"), null);
                }
                else {
                    this.pkSelectionPanelDidEnd_returnCode_contextInfo(publicKeyPanel, NSPanel.NSCancelButton, null);
                }
            }

            public void pkSelectionPanelDidEnd_returnCode_contextInfo(NSOpenPanel sheet, int returncode, ID contextInfo) {
                if(returncode == NSPanel.NSOKButton) {
                    NSArray selected = sheet.filenames();
                    final NSEnumerator enumerator = selected.objectEnumerator();
                    NSObject next;
                    while((next = enumerator.nextObject()) != null) {
                        credentials.setIdentity(LocalFactory.createLocal(next.toString()));
                    }
                }
                if(returncode == NSPanel.NSCancelButton) {
                    credentials.setIdentity(null);
                }
                update();
            }

            private void update() {
                this.usernameField.setEnabled(!credentials.isAnonymousLogin());
                this.passwordField.setEnabled(!credentials.isAnonymousLogin());
                {
                    boolean enable = enableKeychain && !credentials.isAnonymousLogin();
                    this.keychainCheckbox.setEnabled(enable);
                    if(!enable) {
                        this.keychainCheckbox.setState(NSCell.NSOffState);
                    }
                }
                this.anonymousCheckbox.setEnabled(enableAnonymous);
                if(enableAnonymous && credentials.isAnonymousLogin()) {
                    this.anonymousCheckbox.setState(NSCell.NSOnState);
                }
                else {
                    this.anonymousCheckbox.setState(NSCell.NSOffState);
                }
                this.pkCheckbox.setEnabled(enablePublicKey);
                if(enablePublicKey && credentials.isPublicKeyAuthentication()) {
                    this.pkCheckbox.setState(NSCell.NSOnState);
                    this.updateField(this.pkLabel, credentials.getIdentity().getAbbreviatedPath());
                    this.pkLabel.setTextColor(NSColor.textColor());
                }
                else {
                    this.pkCheckbox.setState(NSCell.NSOffState);
                    this.pkLabel.setStringValue(Locale.localizedString("No private key selected"));
                    this.pkLabel.setTextColor(NSColor.disabledControlTextColor());
                }
            }

            @Override
            protected boolean validateInput() {
                credentials.setUsername(usernameField.stringValue());
                credentials.setPassword(passwordField.stringValue());
                return credentials.validate(protocol);
            }

            @Override
            public void callback(final int returncode) {
                if(returncode == SheetCallback.DEFAULT_OPTION) {
                    this.window().endEditingFor(null);
                    credentials.setSaved(keychainCheckbox.state() == NSCell.NSOnState);
                    credentials.setUsername(usernameField.stringValue());
                    credentials.setPassword(passwordField.stringValue());
                }
            }
        };
        c.beginSheet();

        if(c.returnCode() == SheetCallback.CANCEL_OPTION) {
            throw new LoginCanceledException();
        }
    }
	
}
