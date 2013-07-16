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

import ch.cyberduck.ui.cocoa.application.NSWorkspace;
import ch.cyberduck.ui.cocoa.foundation.NSDistributedNotificationCenter;
import ch.cyberduck.ui.cocoa.foundation.NSNotification;

import org.apache.log4j.Logger;

/**
 * @version $Id: WorkspaceApplicationLauncher.java 10556 2012-10-22 17:22:43Z dkocher $
 */
public final class WorkspaceApplicationLauncher implements ApplicationLauncher {
    private static final Logger log = Logger.getLogger(WorkspaceApplicationLauncher.class);

    public static void register() {
        ApplicationLauncherFactory.addFactory(Factory.NATIVE_PLATFORM, new Factory());
    }

    private static class Factory extends ApplicationLauncherFactory {
        @Override
        protected ApplicationLauncher create() {
            return new WorkspaceApplicationLauncher();
        }
    }

    private WorkspaceApplicationLauncher() {
        //
    }

    @Override
    public boolean open(final Local file) {
        synchronized(NSWorkspace.class) {
            if(!NSWorkspace.sharedWorkspace().openFile(file.getAbsolute())) {
                log.warn(String.format("Error opening file %s", file.getAbsolute()));
                return false;
            }
            return true;
        }
    }

    @Override
    public boolean open(final Local file, final Application application) {
        synchronized(NSWorkspace.class) {
            if(!NSWorkspace.sharedWorkspace().openFile(file.getAbsolute(),
                    NSWorkspace.sharedWorkspace().absolutePathForAppBundleWithIdentifier(application.getIdentifier()))) {
                log.warn(String.format("Error opening file %s with application %s", file.getAbsolute(), application));
                return false;
            }
            return true;
        }
    }

    @Override
    public boolean open(final Application application, final String args) {
        throw new UnsupportedOperationException();
    }

    /**
     * Post a download finished notification to the distributed notification center. Will cause the
     * download folder to bounce just once.
     */
    @Override
    public void bounce(final Local file) {
        synchronized(NSWorkspace.class) {
            NSDistributedNotificationCenter.defaultCenter().postNotification(
                    NSNotification.notificationWithName("com.apple.DownloadFileFinished", file.getAbsolute())
            );
        }
    }
}
