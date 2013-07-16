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

import ch.cyberduck.core.AttributedList;
import ch.cyberduck.core.NSObjectPathReference;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathReference;
import ch.cyberduck.core.Preferences;
import ch.cyberduck.ui.PathPasteboard;
import ch.cyberduck.ui.cocoa.application.NSApplication;
import ch.cyberduck.ui.cocoa.application.NSDraggingInfo;
import ch.cyberduck.ui.cocoa.application.NSEvent;
import ch.cyberduck.ui.cocoa.application.NSOutlineView;
import ch.cyberduck.ui.cocoa.application.NSPasteboard;
import ch.cyberduck.ui.cocoa.application.NSTableColumn;
import ch.cyberduck.ui.cocoa.application.NSTableView;
import ch.cyberduck.ui.cocoa.foundation.NSArray;
import ch.cyberduck.ui.cocoa.foundation.NSObject;
import ch.cyberduck.ui.cocoa.foundation.NSURL;

import org.apache.log4j.Logger;
import org.rococoa.cocoa.foundation.NSInteger;
import org.rococoa.cocoa.foundation.NSUInteger;

/**
 * @version $Id: BrowserOutlineViewModel.java 10084 2012-10-12 13:48:23Z dkocher $
 */
public class BrowserOutlineViewModel extends BrowserTableDataSource implements NSOutlineView.DataSource {
    private static final Logger log = Logger.getLogger(BrowserOutlineViewModel.class);

    public BrowserOutlineViewModel(BrowserController controller) {
        super(controller);
    }

    @Override
    public int indexOf(NSTableView view, PathReference reference) {
        return ((NSOutlineView) view).rowForItem(((NSObjectPathReference) reference).unique()).intValue();
    }

    protected AttributedList<Path> children(final NSObjectPathReference path) {
        final Path lookup = controller.lookup(path);
        if(null == lookup) {
            return AttributedList.emptyList();
        }
        return super.children(lookup);
    }

    /**
     * @see NSOutlineView.DataSource
     */
    @Override
    public boolean outlineView_isItemExpandable(final NSOutlineView view, final NSObject item) {
        if(log.isDebugEnabled()) {
            log.debug("outlineViewIsItemExpandable:" + item);
        }
        if(null == item) {
            return false;
        }
        final Path path = controller.lookup(new NSObjectPathReference(item));
        if(null == path) {
            return false;
        }
        return path.attributes().isDirectory();
    }

    /**
     * @see NSOutlineView.DataSource
     */
    @Override
    public NSInteger outlineView_numberOfChildrenOfItem(final NSOutlineView view, NSObject item) {
        if(log.isDebugEnabled()) {
            log.debug("outlineView_numberOfChildrenOfItem:" + item);
        }
        if(controller.isMounted()) {
            if(null == item) {
                return new NSInteger(this.children(controller.workdir()).size());
            }
            NSEvent event = NSApplication.sharedApplication().currentEvent();
            if(event != null) {
                log.debug("Event:" + event.type());
                if(NSEvent.NSLeftMouseDragged == event.type()) {
                    final int draggingColumn = view.columnAtPoint(view.convertPoint_fromView(event.locationInWindow(), null)).intValue();
                    if(draggingColumn != 0) {
                        log.debug("Returning 0 to #outlineViewNumberOfChildrenOfItem for column:" + draggingColumn);
                        // See ticket #60
                        return new NSInteger(0);
                    }
                    if(!Preferences.instance().getBoolean("browser.view.autoexpand")) {
                        log.debug("Returning 0 to #outlineViewNumberOfChildrenOfItem while dragging because browser.view.autoexpand == false");
                        // See tickets #98 and #633
                        return new NSInteger(0);
                    }
                }
            }
            return new NSInteger(this.children(new NSObjectPathReference(item)).size());
        }
        return new NSInteger(0);
    }

    /**
     * @see NSOutlineView.DataSource
     *      Invoked by outlineView, and returns the child item at the specified index. Children
     *      of a given parent item are accessed sequentially. If item is null, this method should
     *      return the appropriate child item of the root object
     */
    @Override
    public NSObject outlineView_child_ofItem(final NSOutlineView outlineView, NSInteger index, NSObject item) {
        if(log.isDebugEnabled()) {
            log.debug("outlineView_child_ofItem:" + item);
        }
        final Path path;
        if(null == item) {
            path = controller.workdir();
        }
        else {
            path = controller.lookup(new NSObjectPathReference(item));
        }
        if(null == path) {
            return null;
        }
        final AttributedList<Path> children = this.children(path);
        if(index.intValue() >= children.size()) {
            log.warn(String.format("Index %s out of bound for %s", index, item));
            return null;
        }
        return (NSObject) children.get(index.intValue()).getReference().unique();
    }

    @Override
    public void outlineView_setObjectValue_forTableColumn_byItem(final NSOutlineView outlineView, NSObject value,
                                                                 final NSTableColumn tableColumn, NSObject item) {
        super.setObjectValueForItem(controller.lookup(new NSObjectPathReference(item)), value, tableColumn.identifier());
    }

    @Override
    public NSObject outlineView_objectValueForTableColumn_byItem(final NSOutlineView view, final NSTableColumn tableColumn, NSObject item) {
        if(null == item) {
            return null;
        }
        return super.objectValueForItem(controller.lookup(new NSObjectPathReference(item)), tableColumn.identifier());
    }

    @Override
    public NSUInteger outlineView_validateDrop_proposedItem_proposedChildIndex(final NSOutlineView view, final NSDraggingInfo draggingInfo, NSObject item, NSInteger row) {
        if(controller.isMounted()) {
            Path destination = null;
            if(null != item) {
                destination = controller.lookup(new NSObjectPathReference(item));
            }
            if(null == destination) {
                // Dragging over empty rows
                view.setDropItem(null, NSOutlineView.NSOutlineViewDropOnItemIndex);
                return super.validateDrop(view, controller.workdir(), row, draggingInfo);
            }
            else {
                // Dragging over file or folder
                final int draggingColumn = view.columnAtPoint(draggingInfo.draggingLocation()).intValue();
                if(-1 == draggingColumn || 0 == draggingColumn) {
                    if(destination.attributes().isDirectory()) {
                        // Drop target is directory
                        view.setDropItem((NSObject) destination.getReference().unique(), NSOutlineView.NSOutlineViewDropOnItemIndex);
                        return super.validateDrop(view, destination, row, draggingInfo);
                    }
                }
                for(Path next : PathPasteboard.getPasteboard(controller.getSession())) {
                    if(destination.equals(next)) {
                        // Do not allow dragging onto myself. Fix #4320
                        return NSDraggingInfo.NSDragOperationNone;
                    }
                }
                view.setDropItem(null, NSOutlineView.NSOutlineViewDropOnItemIndex);
                return super.validateDrop(view, controller.workdir(), row, draggingInfo);
            }
        }
        // Passing to super to look for URLs to mount
        if(draggingInfo.draggingPasteboard().availableTypeFromArray(NSArray.arrayWithObject(NSPasteboard.URLPboardType)) != null) {
            view.setDropItem(null, NSOutlineView.NSOutlineViewDropOnItemIndex);
        }
        return super.validateDrop(view, null, row, draggingInfo);
    }

    @Override
    public boolean outlineView_acceptDrop_item_childIndex(final NSOutlineView outlineView, final NSDraggingInfo info, NSObject item, NSInteger row) {
        Path destination = null;
        if(controller.isMounted()) {
            if(null == item) {
                destination = controller.workdir();
            }
            else {
                destination = controller.lookup(new NSObjectPathReference(item));
            }
        }
        return super.acceptDrop(outlineView, destination, info);
    }

    @Override
    public NSArray outlineView_namesOfPromisedFilesDroppedAtDestination_forDraggedItems(NSURL dropDestination, NSArray items) {
        return this.namesOfPromisedFilesDroppedAtDestination(dropDestination);
    }

    @Override
    public boolean outlineView_writeItems_toPasteboard(final NSOutlineView outlineView, final NSArray items, final NSPasteboard pboard) {
        return super.writeItemsToPasteBoard(outlineView, items, pboard);
    }
}