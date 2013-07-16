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

import ch.cyberduck.ui.cocoa.application.NSApplication;

/**
 * @version $Id: WorkspaceApplicationBadgeLabeler.java 10913 2013-04-23 12:58:24Z dkocher $
 */
public class WorkspaceApplicationBadgeLabeler implements ApplicationBadgeLabeler {

    public static void register() {
        ApplicationBadgeLabelerFactory.addFactory(Factory.NATIVE_PLATFORM, new Factory());
    }

    private static class Factory extends ApplicationBadgeLabelerFactory {
        @Override
        protected ApplicationBadgeLabeler create() {
            return new WorkspaceApplicationBadgeLabeler();
        }
    }

    @Override
    public void badge(String label) {
        NSApplication.sharedApplication().dockTile().setBadgeLabel(label);
    }
}
