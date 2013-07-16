package ch.cyberduck.ui.cocoa.quicklook;

/*
 * Copyright (c) 2002-2009 David Kocher. All rights reserved.
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

import ch.cyberduck.core.library.Native;
import ch.cyberduck.core.local.Local;

import java.util.ArrayList;
import java.util.List;

/**
 * @version $Id: DeprecatedQuickLook.java 10530 2012-10-22 12:03:11Z dkocher $
 */
public final class DeprecatedQuickLook extends AbstractQuickLook {

    public static void register() {
        if(Factory.VERSION_PLATFORM.matches("10\\.5.*")) {
            QuickLookFactory.addFactory(Factory.VERSION_PLATFORM, new Factory());
        }
    }

    private static class Factory extends QuickLookFactory {
        @Override
        protected QuickLook create() {
            return new DeprecatedQuickLook();
        }
    }

    private DeprecatedQuickLook() {
        Native.load("QuickLook");
    }

    private native void selectNative(String[] files);

    /**
     * Add this files to the Quick Look Preview shared window
     *
     * @param files Selected files
     */
    @Override
    public void select(List<Local> files) {
        final List<String> paths = new ArrayList<String>();
        for(Local file : files) {
            paths.add(file.getAbsolute());
        }
        this.selectNative(paths.toArray(new String[paths.size()]));
    }

    @Override
    public boolean isAvailable() {
        return this.isAvailableNative();
    }

    public native boolean isAvailableNative();

    @Override
    public boolean isOpen() {
        return this.isOpenNative();
    }

    public native boolean isOpenNative();

    @Override
    public void open() {
        this.willBeginQuickLook();
        this.openNative();
    }

    public native void openNative();

    @Override
    public void close() {
        this.closeNative();
        this.didEndQuickLook();
    }

    public native void closeNative();
}
