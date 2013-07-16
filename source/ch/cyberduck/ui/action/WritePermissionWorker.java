package ch.cyberduck.ui.action;

/*
 * Copyright (c) 2002-2010 David Kocher. All rights reserved.
 *
 * http://cyberduck.ch/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to:
 * dkocher@cyberduck.ch
 */

import ch.cyberduck.core.Path;
import ch.cyberduck.core.Permission;
import ch.cyberduck.core.i18n.Locale;

import java.text.MessageFormat;
import java.util.List;

/**
 * @version $Id: WritePermissionWorker.java 10989 2013-05-02 12:27:37Z dkocher $
 */
public abstract class WritePermissionWorker extends Worker<Permission> {

    /**
     * Selected files.
     */
    private List<Path> files;

    /**
     * Permissions to apply to files.
     */
    private Permission permission;

    /**
     * Descend into directories
     */
    private boolean recursive;

    public WritePermissionWorker(final List<Path> files, final Permission permission, final boolean recursive) {
        this.files = files;
        this.permission = permission;
        this.recursive = recursive;
    }

    @Override
    public Permission run() {
        for(Path next : files) {
            this.write(next);
        }
        return permission;
    }

    private void write(final Path file) {
        if(!file.getSession().isConnected()) {
            return;
        }
        if(recursive && file.attributes().isFile()) {
            // Do not write executable bit for files if not already set when recursively updating directory.
            // See #1787
            Permission modified = new Permission(permission);
            if(!file.attributes().getPermission().getOwnerPermissions()[Permission.EXECUTE]) {
                modified.getOwnerPermissions()[Permission.EXECUTE] = false;
            }
            if(!file.attributes().getPermission().getGroupPermissions()[Permission.EXECUTE]) {
                modified.getGroupPermissions()[Permission.EXECUTE] = false;
            }
            if(!file.attributes().getPermission().getOtherPermissions()[Permission.EXECUTE]) {
                modified.getOtherPermissions()[Permission.EXECUTE] = false;
            }
            if(!file.attributes().getPermission().equals(modified)) {
                file.writeUnixPermission(modified);
            }
        }
        else {
            if(!file.attributes().getPermission().equals(permission)) {
                file.writeUnixPermission(permission);
            }
        }
        if(recursive) {
            if(file.attributes().isDirectory()) {
                for(Path child : file.children()) {
                    this.write(child);
                }
            }
        }
    }

    @Override
    public String getActivity() {
        return MessageFormat.format(Locale.localizedString("Changing permission of {0} to {1}", "Status"),
                this.toString(files), permission);
    }
}
