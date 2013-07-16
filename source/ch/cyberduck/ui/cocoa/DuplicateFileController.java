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
import ch.cyberduck.core.Preferences;
import ch.cyberduck.core.date.UserDateFormatterFactory;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.ui.cocoa.application.NSAlert;
import ch.cyberduck.ui.cocoa.resources.IconCache;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;

import java.text.MessageFormat;

/**
 * @version $Id: DuplicateFileController.java 10608 2012-11-27 10:16:01Z dkocher $
 */
public class DuplicateFileController extends FileController {

    public DuplicateFileController(final WindowController parent) {
        super(parent, NSAlert.alert(
                Locale.localizedString("Duplicate File", "Duplicate"),
                Locale.localizedString("Enter the name for the new file:", "Duplicate"),
                Locale.localizedString("Duplicate", "Duplicate"),
                null,
                Locale.localizedString("Cancel", "Duplicate")
        ));
        alert.setIcon(IconCache.instance().iconForExtension(this.getSelected().getExtension(), 64));
        final Path selected = this.getSelected();
        String proposal = MessageFormat.format(Preferences.instance().getProperty("browser.duplicate.format"),
                FilenameUtils.getBaseName(selected.getName()),
                UserDateFormatterFactory.get().getShortFormat(System.currentTimeMillis(), false).replace(Path.DELIMITER, ':'),
                StringUtils.isNotEmpty(selected.getExtension()) ? "." + selected.getExtension() : StringUtils.EMPTY);
        this.filenameField.setStringValue(proposal);
    }

    @Override
    public void callback(final int returncode) {
        if(returncode == DEFAULT_OPTION) {
            this.duplicateFile(this.getSelected(), filenameField.stringValue());
        }
    }

    @Override
    protected Path getWorkdir() {
        return this.getSelected().getParent();
    }

    private void duplicateFile(final Path selected, final String filename) {
        final Path duplicate = PathFactory.createPath(this.getSession(),
                selected.getParent().getAbsolute(), filename, selected.attributes().getType());
        ((BrowserController) parent).duplicatePath(selected, duplicate);
    }
}