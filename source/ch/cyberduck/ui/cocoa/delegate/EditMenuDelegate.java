package ch.cyberduck.ui.cocoa.delegate;

/*
 *  Copyright (c) 2006 David Kocher. All rights reserved.
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
import ch.cyberduck.core.editor.EditorFactory;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.local.Application;
import ch.cyberduck.ui.cocoa.application.NSEvent;
import ch.cyberduck.ui.cocoa.application.NSMenu;
import ch.cyberduck.ui.cocoa.application.NSMenuItem;
import ch.cyberduck.ui.cocoa.resources.IconCache;

import org.apache.commons.lang.ObjectUtils;
import org.rococoa.Foundation;
import org.rococoa.Selector;
import org.rococoa.cocoa.foundation.NSInteger;

import java.util.List;

/**
 * @version $Id: EditMenuDelegate.java 10490 2012-10-19 16:16:09Z dkocher $
 */
public abstract class EditMenuDelegate extends AbstractMenuDelegate {

    /**
     * Last selected extension
     */
    private String extension = null;

    @Override
    public NSInteger numberOfItemsInMenu(NSMenu menu) {
        if(this.isPopulated()) {
            // If you return a negative value, the number of items is left unchanged
            // and menu:updateItem:atIndex:shouldCancel: is not called.
            return new NSInteger(-1);
        }
        final Path file = this.getEditable();
        final int count;
        if(null == file) {
            count = EditorFactory.instance().getEditors().size();
        }
        else {
            count = EditorFactory.instance().getEditors(file.getName()).size();
        }
        if(0 == count) {
            return new NSInteger(1);
        }
        return new NSInteger(count);
    }

    protected abstract Path getEditable();

    /**
     * Caching last selected extension to build menu.
     *
     * @return True if menu is built.
     */
    @Override
    protected boolean isPopulated() {
        final Path selected = this.getEditable();
        if(selected != null && ObjectUtils.equals(extension, selected.getExtension())) {
            return true;
        }
        if(selected != null) {
            extension = selected.getExtension();
        }
        else {
            extension = null;
        }
        return false;
    }

    @Override
    public boolean menuUpdateItemAtIndex(NSMenu menu, NSMenuItem item, NSInteger index, boolean cancel) {
        final Path selected = this.getEditable();
        final List<Application> editors;
        if(null == selected) {
            editors = EditorFactory.instance().getEditors();
        }
        else {
            editors = EditorFactory.instance().getEditors(selected.getName());
        }
        if(editors.size() == 0) {
            item.setTitle(Locale.localizedString("No external editor available"));
            return false;
        }
        final String identifier = editors.get(index.intValue()).getIdentifier();
        item.setRepresentedObject(identifier);
        final String editor = editors.get(index.intValue()).getName();
        item.setTitle(editor);
        if(null != selected && identifier.equalsIgnoreCase(EditorFactory.instance().getEditor(selected.getName()).getIdentifier())) {
            setShortcut(item, this.getKeyEquivalent(), this.getModifierMask());
        }
        else {
            this.clearShortcut(item);
        }
        item.setImage(IconCache.instance().iconForApplication(identifier, 16));
        item.setAction(Foundation.selector("editMenuClicked:"));
        return super.menuUpdateItemAtIndex(menu, item, index, cancel);
    }

    @Override
    protected String getKeyEquivalent() {
        return "k";
    }

    @Override
    protected int getModifierMask() {
        return NSEvent.NSCommandKeyMask;
    }

    @Override
    protected Selector getDefaultAction() {
        return Foundation.selector("editButtonClicked:");
    }
}