package ch.cyberduck.core.local;

/*
 * Copyright (c) 2012 David Kocher. All rights reserved.
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

import ch.cyberduck.core.AbstractPath;
import ch.cyberduck.core.AttributedList;
import ch.cyberduck.core.Permission;
import ch.cyberduck.core.Preferences;
import ch.cyberduck.core.library.Native;
import ch.cyberduck.ui.cocoa.application.NSWorkspace;
import ch.cyberduck.ui.cocoa.foundation.NSArray;
import ch.cyberduck.ui.cocoa.foundation.NSDate;
import ch.cyberduck.ui.cocoa.foundation.NSDictionary;
import ch.cyberduck.ui.cocoa.foundation.NSEnumerator;
import ch.cyberduck.ui.cocoa.foundation.NSFileManager;
import ch.cyberduck.ui.cocoa.foundation.NSNumber;
import ch.cyberduck.ui.cocoa.foundation.NSObject;
import ch.cyberduck.ui.cocoa.foundation.NSString;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.io.File;

/**
 * @version $Id: FinderLocal.java 10851 2013-04-15 09:21:33Z dkocher $
 */
public class FinderLocal extends Local {
    private static final Logger log = Logger.getLogger(FinderLocal.class);

    public FinderLocal(Local parent, String name) {
        super(parent, name);
    }

    public FinderLocal(String parent, String name) {
        super(parent, name);
    }

    public FinderLocal(String path) {
        super(path);
    }

    public FinderLocal(File path) {
        super(path);
    }

    public static void register() {
        LocalFactory.addFactory(Factory.NATIVE_PLATFORM, new Factory());
    }

    static {
        Native.load("Local");
    }

    private static class Factory extends LocalFactory {
        @Override
        protected Local create() {
            return new FinderLocal(System.getProperty("user.home"));
        }

        @Override
        protected Local create(final Local parent, final String name) {
            return new FinderLocal(parent, name);
        }

        @Override
        protected Local create(final String parent, final String name) {
            return new FinderLocal(parent, name);
        }

        @Override
        protected Local create(final String path) {
            return new FinderLocal(path);
        }

        @Override
        protected Local create(final File path) {
            return new FinderLocal(path);
        }
    }

    @Override
    protected void setPath(final String name) {
        super.setPath(this.resolveAlias(stringByExpandingTildeInPath(name)));
    }

    @Override
    public void setPath(final String parent, final String name) {
        super.setPath(stringByExpandingTildeInPath(parent), name);
    }

    /**
     * @return Name of the file as displayed in the Finder. E.g. a ':' is replaced with '/'.
     */
    @Override
    public String getDisplayName() {
        return NSFileManager.defaultManager().displayNameAtPath(this.getName());
    }

    /**
     * @return Path relative to the home directory denoted with a tilde.
     */
    @Override
    public String getAbbreviatedPath() {
        return stringByAbbreviatingWithTildeInPath(this.getAbsolute());
    }

    @Override
    public Local getVolume() {
        for(AbstractPath parent = this.getParent(); !parent.isRoot(); parent = parent.getParent()) {
            if(parent.getParent().getAbsolute().equals("/Volumes")) {
                return (Local) parent;
            }
        }
        return super.getVolume();
    }

    @Override
    public AttributedList<Local> list() {
        if(Preferences.instance().getBoolean("local.list.native")) {
            final AttributedList<Local> children = new AttributedList<Local>();
            final NSArray files = NSFileManager.defaultManager().contentsOfDirectoryAtPath_error(this.getAbsolute(), null);
            if(null == files) {
                log.error(String.format("Error listing children for folder %s", getAbsolute()));
                return children;
            }
            final NSEnumerator i = files.objectEnumerator();
            NSObject next;
            while(((next = i.nextObject()) != null)) {
                children.add(new FinderLocal(this.getAbsolute(), next.toString()));
            }
            return children;
        }
        else {
            return super.list();
        }
    }

    @Override
    public boolean exists() {
        return NSFileManager.defaultManager().fileExistsAtPath(this.getAbsolute());
    }

    @Override
    public void symlink(String target) {
        final boolean success = NSFileManager.defaultManager().createSymbolicLinkAtPath_pathContent(
                this.getAbsolute(), target);
        if(!success) {
            log.error(String.format("File attribute changed failed for file %s", getAbsolute()));
        }
    }

    /**
     * @param absolute The absolute path of the alias file.
     * @return The absolute path this alias is pointing to.
     */
    private native String resolveAlias(String absolute);

    @Override
    public FinderLocalAttributes attributes() {
        return new FinderLocalAttributes(this.getAbsolute());
    }

    @Override
    public AbstractPath getSymlinkTarget() {
        return new FinderLocal(this.getParent().getAbsolute(),
                NSFileManager.defaultManager().destinationOfSymbolicLinkAtPath_error(this.getAbsolute(), null));
    }

    private static final Object workspace = new Object();

    @Override
    public void writeUnixPermission(final Permission permission) {
        synchronized(workspace) {
            boolean success = NSFileManager.defaultManager().setAttributes_ofItemAtPath_error(
                    NSDictionary.dictionaryWithObjectsForKeys(
                            NSArray.arrayWithObject(NSNumber.numberWithInt(Integer.valueOf(permission.getOctalString(), 8))),
                            NSArray.arrayWithObject(NSFileManager.NSFilePosixPermissions)),
                    getAbsolute(), null);
            if(!success) {
                log.error(String.format("File attribute changed failed for file %s", getAbsolute()));
            }
        }
    }

    /**
     * Write <code>NSFileModificationDate</code>.
     *
     * @param created  Milliseconds
     * @param modified Milliseconds
     * @param accessed Milliseconds
     */
    @Override
    public void writeTimestamp(final long created, final long modified, final long accessed) {
        synchronized(workspace) {
            boolean success = NSFileManager.defaultManager().setAttributes_ofItemAtPath_error(
                    NSDictionary.dictionaryWithObjectsForKeys(
                            NSArray.arrayWithObject(NSDate.dateWithTimeIntervalSince1970(modified / 1000d)),
                            NSArray.arrayWithObject(NSFileManager.NSFileModificationDate)),
                    getAbsolute(), null);
            if(!success) {
                log.error(String.format("File attribute changed failed for file %s", getAbsolute()));
            }
        }
    }

    /**
     * Move file to trash on main interface thread using <code>NSWorkspace.RecycleOperation</code>.
     */
    @Override
    public void trash() {
        if(this.exists()) {
            synchronized(workspace) {
                if(log.isDebugEnabled()) {
                    log.debug(String.format("Move %s to Trash", this.getAbsolute()));
                }
                if(!NSWorkspace.sharedWorkspace().performFileOperation(
                        NSWorkspace.RecycleOperation,
                        this.getParent().getAbsolute(), StringUtils.EMPTY,
                        NSArray.arrayWithObject(this.getName()))) {
                    log.warn(String.format("Failed to move %s to Trash", this.getAbsolute()));
                }
            }
        }
    }

    /**
     * Comparing by inode if the file exists.
     *
     * @param o Other file
     * @return True if Inode is same
     */
    @Override
    public boolean equals(Object o) {
        // Case insensitive compare returned
        if(super.equals(o)) {
            // Now test with inode for case sensitive volumes
            if(!this.exists()) {
                return super.equals(o);
            }
            FinderLocal other = (FinderLocal) o;
            if(!other.exists()) {
                return super.equals(o);
            }
            return this.attributes().getInode() == other.attributes().getInode();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Long.valueOf(this.attributes().getInode()).hashCode();
    }

    private static String stringByAbbreviatingWithTildeInPath(final String path) {
        return NSString.stringByAbbreviatingWithTildeInPath(path);
    }

    private static String stringByExpandingTildeInPath(final String path) {
        return NSString.stringByExpandingTildeInPath(path);
    }
}