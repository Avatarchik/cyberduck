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

import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathFactory;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.ui.cocoa.application.NSAlert;
import ch.cyberduck.ui.cocoa.resources.IconCache;
import ch.cyberduck.ui.cocoa.threading.BrowserBackgroundAction;

import java.text.MessageFormat;
import java.util.Collections;

/**
 * @version $Id: FolderController.java 10385 2012-10-18 08:12:17Z dkocher $
 */
public class FolderController extends FileController {

    public FolderController(final WindowController parent) {
        super(parent, NSAlert.alert(
                Locale.localizedString("Create new folder", "Folder"),
                Locale.localizedString("Enter the name for the new folder:", "Folder"),
                Locale.localizedString("Create", "Folder"),
                null,
                Locale.localizedString("Cancel", "Folder")
        ));
        alert.setIcon(IconCache.iconNamed("newfolder.tiff", 64));
    }

    @Override
    public void callback(int returncode) {
        if(returncode == DEFAULT_OPTION) {
            this.createFolder(this.getWorkdir(), filenameField.stringValue());
        }
    }

    protected void createFolder(final Path workdir, final String filename) {
        final BrowserController c = (BrowserController) parent;
        c.background(new BrowserBackgroundAction(c) {
            final Path folder = PathFactory.createPath(c.getSession(), workdir.getAbsolute(),
                    filename, Path.DIRECTORY_TYPE);

            @Override
            public void run() {
                folder.mkdir();
            }

            @Override
            public String getActivity() {
                return MessageFormat.format(Locale.localizedString("Making directory {0}", "Status"),
                        folder.getName());
            }

            @Override
            public void cleanup() {
                if(filename.charAt(0) == '.') {
                    c.setShowHiddenFiles(true);
                }
                c.reloadData(Collections.singletonList(folder), Collections.singletonList(folder));
            }
        });
    }
}
