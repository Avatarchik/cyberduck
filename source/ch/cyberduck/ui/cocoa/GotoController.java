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

import ch.cyberduck.core.NullComparator;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathFactory;
import ch.cyberduck.core.PathFilter;
import ch.cyberduck.core.Session;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.ui.cocoa.application.NSAlert;
import ch.cyberduck.ui.cocoa.application.NSComboBox;
import ch.cyberduck.ui.cocoa.foundation.NSObject;
import ch.cyberduck.ui.cocoa.resources.IconCache;

import org.apache.commons.lang.StringUtils;
import org.rococoa.cocoa.foundation.NSInteger;
import org.rococoa.cocoa.foundation.NSRect;

import java.util.Comparator;

/**
 * @version $Id: GotoController.java 10058 2012-10-11 16:31:59Z dkocher $
 */
public class GotoController extends AlertController {

    private NSComboBox folderCombobox;
    private ProxyController folderComboboxModel = new FolderComboboxModel();

    private class FolderComboboxModel extends ProxyController implements NSComboBox.DataSource {

        private final Comparator<Path> comparator = new NullComparator<Path>();

        private final PathFilter<Path> filter = new PathFilter<Path>() {
            @Override
            public boolean accept(Path p) {
                return p.attributes().isDirectory();
            }
        };

        @Override
        public NSInteger numberOfItemsInComboBox(NSComboBox combo) {
            if(!((BrowserController) parent).isMounted()) {
                return new NSInteger(0);
            }
            return new NSInteger(((BrowserController) parent).workdir().children(comparator, filter).size());
        }

        @Override
        public NSObject comboBox_objectValueForItemAtIndex(final NSComboBox sender, final NSInteger row) {
            return (NSObject) ((BrowserController) parent).workdir().children(comparator, filter).get(row.intValue()).getReference().unique();
        }
    }

    public GotoController(final WindowController parent) {
        super(parent, NSAlert.alert(
                Locale.localizedString("Go to folder", "Goto"),
                Locale.localizedString("Enter the pathname to list:", "Goto"),
                Locale.localizedString("Go", "Goto"),
                null,
                Locale.localizedString("Cancel", "Goto")
        ));
        alert.setIcon(IconCache.folderIcon(64));
        folderCombobox = NSComboBox.textfieldWithFrame(new NSRect(0, 26));
        folderCombobox.setCompletes(true);
        folderCombobox.setUsesDataSource(true);
        folderCombobox.setDataSource(folderComboboxModel.id());
        folderCombobox.setStringValue(((BrowserController) this.parent).workdir().getAbsolute());
        this.setAccessoryView(folderCombobox);
    }

    @Override
    protected void focus() {
        // Focus accessory view.
        folderCombobox.selectText(null);
        this.window().makeFirstResponder(folderCombobox);
    }

    @Override
    protected void invalidate() {
        folderCombobox.setDelegate(null);
        folderCombobox.setDataSource(null);
        super.invalidate();
    }

    @Override
    public void callback(final int returncode) {
        if(returncode == DEFAULT_OPTION) {
            this.gotoFolder(((BrowserController) parent).workdir(), folderCombobox.stringValue());
        }
    }

    @Override
    protected boolean validateInput() {
        return StringUtils.isNotBlank(folderCombobox.stringValue());
    }

    protected Session getSession() {
        return ((BrowserController) parent).getSession();
    }

    protected void gotoFolder(final Path workdir, final String filename) {
        final BrowserController c = (BrowserController) parent;
        final Path dir;
        if(!filename.startsWith(String.valueOf(Path.DELIMITER))) {
            dir = PathFactory.createPath(this.getSession(), workdir.getAbsolute(), filename, Path.DIRECTORY_TYPE);
        }
        else {
            dir = PathFactory.createPath(this.getSession(), filename, Path.DIRECTORY_TYPE);
        }
        if(workdir.getParent().equals(dir)) {
            c.setWorkdir(dir, workdir);
        }
        else {
            c.setWorkdir(dir);
        }
    }
}