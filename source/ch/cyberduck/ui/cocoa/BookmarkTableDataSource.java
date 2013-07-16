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

import ch.cyberduck.core.*;
import ch.cyberduck.core.local.Local;
import ch.cyberduck.core.local.LocalFactory;
import ch.cyberduck.core.serializer.HostReaderFactory;
import ch.cyberduck.core.serializer.HostWriterFactory;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.core.transfer.upload.UploadTransfer;
import ch.cyberduck.ui.cocoa.application.NSApplication;
import ch.cyberduck.ui.cocoa.application.NSDraggingInfo;
import ch.cyberduck.ui.cocoa.application.NSDraggingSource;
import ch.cyberduck.ui.cocoa.application.NSEvent;
import ch.cyberduck.ui.cocoa.application.NSImage;
import ch.cyberduck.ui.cocoa.application.NSPasteboard;
import ch.cyberduck.ui.cocoa.application.NSTableColumn;
import ch.cyberduck.ui.cocoa.application.NSTableView;
import ch.cyberduck.ui.cocoa.foundation.NSArray;
import ch.cyberduck.ui.cocoa.foundation.NSDictionary;
import ch.cyberduck.ui.cocoa.foundation.NSIndexSet;
import ch.cyberduck.ui.cocoa.foundation.NSMutableArray;
import ch.cyberduck.ui.cocoa.foundation.NSMutableDictionary;
import ch.cyberduck.ui.cocoa.foundation.NSObject;
import ch.cyberduck.ui.cocoa.foundation.NSString;
import ch.cyberduck.ui.cocoa.foundation.NSURL;
import ch.cyberduck.ui.cocoa.resources.IconCache;
import ch.cyberduck.ui.cocoa.threading.WindowMainAction;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.rococoa.Rococoa;
import org.rococoa.cocoa.foundation.NSInteger;
import org.rococoa.cocoa.foundation.NSPoint;
import org.rococoa.cocoa.foundation.NSRect;
import org.rococoa.cocoa.foundation.NSSize;
import org.rococoa.cocoa.foundation.NSUInteger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @version $Id: BookmarkTableDataSource.java 10709 2012-12-22 19:26:49Z dkocher $
 */
public class BookmarkTableDataSource extends ListDataSource {
    private static Logger log = Logger.getLogger(BookmarkTableDataSource.class);

    public static final String ICON_COLUMN = "ICON";
    public static final String BOOKMARK_COLUMN = "BOOKMARK";
    public static final String STATUS_COLUMN = "STATUS";
    // virtual column to implement keyboard selection
    protected static final String TYPEAHEAD_COLUMN = "TYPEAHEAD";

    protected BrowserController controller;

    public BookmarkTableDataSource(final BrowserController controller,
                                   final AbstractHostCollection source) {
        this.controller = controller;
        this.setSource(source);
    }

    private AbstractHostCollection source = AbstractHostCollection.empty();

    private CollectionListener<Host> listener;

    public void setSource(final AbstractHostCollection source) {
        this.source.removeListener(listener); //Remove previous listener
        this.source = source;
        this.source.addListener(listener = new CollectionListener<Host>() {
            private ScheduledFuture<?> delayed = null;

            @Override
            public void collectionLoaded() {
                cache.clear();
                invoke(new WindowMainAction(controller) {
                    @Override
                    public void run() {
                        controller.reloadBookmarks();
                    }
                });
            }

            @Override
            public void collectionItemAdded(Host item) {
                cache.remove(item);
                invoke(new WindowMainAction(controller) {
                    @Override
                    public void run() {
                        controller.reloadBookmarks();
                    }
                });
            }

            @Override
            public void collectionItemRemoved(Host item) {
                cache.remove(item);
                invoke(new WindowMainAction(controller) {
                    @Override
                    public void run() {
                        controller.reloadBookmarks();
                    }
                });
            }

            @Override
            public void collectionItemChanged(Host item) {
                cache.remove(item);
                if(null != delayed) {
                    delayed.cancel(false);
                }
                // Delay to 1 second. When typing changes we don't have to save every iteration.
                delayed = getTimerPool().schedule(new Runnable() {
                    public void run() {
                        controller.invoke(new WindowMainAction(controller) {
                            @Override
                            public void run() {
                                controller.reloadBookmarks();
                            }
                        });
                    }
                }, 1, TimeUnit.SECONDS);
            }
        });
        this.setFilter(null);
        cache.clear();
    }

    @Override
    protected void invalidate() {
        cache.clear();
        source.removeListener(listener);
        super.invalidate();
    }

    private HostFilter filter;

    /**
     * Display only a subset of all bookmarks
     *
     * @param filter Filter for bookmarks
     */
    public void setFilter(HostFilter filter) {
        this.filter = filter;
        this.filtered = null;
    }

    /**
     * Subset of the original source
     */
    private AbstractHostCollection filtered;

    /**
     * @return The filtered collection currently to be displayed within the constraints
     *         given by the comparision with the HostFilter
     * @see HostFilter
     */
    protected AbstractHostCollection getSource() {
        if(null == filter) {
            return source;
        }
        if(null == filtered) {
            filtered = new AbstractHostCollection() {
                private static final long serialVersionUID = -2154002477046004380L;

                @Override
                public String getName() {
                    return source.getName();
                }

                @Override
                public boolean allowsAdd() {
                    return source.allowsAdd();
                }

                @Override
                public boolean allowsDelete() {
                    return source.allowsDelete();
                }

                @Override
                public boolean allowsEdit() {
                    return source.allowsEdit();
                }

                @Override
                public void save() {
                    source.save();
                }

                @Override
                public void load() {
                    source.load();
                }
            };
            for(final Host bookmark : source) {
                if(filter.accept(bookmark)) {
                    filtered.add(bookmark);
                }
            }
            filtered.addListener(new CollectionListener<Host>() {
                @Override
                public void collectionLoaded() {
                    source.collectionLoaded();
                }

                @Override
                public void collectionItemAdded(Host item) {
                    source.add(item);
                }

                @Override
                public void collectionItemRemoved(Host item) {
                    source.remove(item);
                }

                @Override
                public void collectionItemChanged(Host item) {
                    source.collectionItemChanged(item);
                }
            });
        }
        return filtered;
    }

    @Override
    public NSInteger numberOfRowsInTableView(NSTableView view) {
        return new NSInteger(this.getSource().size());
    }

    /**
     * Second cache because it is expensive to create proxy instances
     */
    private AttributeCache<Host> cache = new AttributeCache<Host>(
            Preferences.instance().getInteger("bookmark.model.cache.size")
    );

    @Override
    public NSObject tableView_objectValueForTableColumn_row(NSTableView view, NSTableColumn tableColumn, NSInteger row) {
        if(row.intValue() >= this.numberOfRowsInTableView(view).intValue()) {
            return null;
        }
        final String identifier = tableColumn.identifier();
        final Host host = this.getSource().get(row.intValue());
        final NSObject cached = cache.get(host, identifier);
        if(null == cached) {
            if(identifier.equals(ICON_COLUMN)) {
                return IconCache.iconNamed(host.getProtocol().disk(),
                        Preferences.instance().getInteger("bookmark.icon.size"));
            }
            if(identifier.equals(BOOKMARK_COLUMN)) {
                NSMutableDictionary dict = NSMutableDictionary.dictionaryWithDictionary(host.<NSDictionary>getAsDictionary());
                dict.setObjectForKey(host.toURL() + Path.normalize(host.getDefaultPath()), "URL");
                String comment = this.getSource().getComment(host);
                if(StringUtils.isNotBlank(comment)) {
                    dict.setObjectForKey(comment, "Comment");
                }
                return cache.put(host, identifier, dict);
            }
            if(identifier.equals(STATUS_COLUMN)) {
                if(controller.hasSession()) {
                    final Session session = controller.getSession();
                    if(host.equals(session.getHost())) {
                        if(session.isConnected()) {
                            return IconCache.iconNamed("statusGreen.tiff", 16);
                        }
                        if(session.isOpening()) {
                            return IconCache.iconNamed("statusYellow.tiff", 16);
                        }
                    }
                }
                return null;
            }
            if(identifier.equals(TYPEAHEAD_COLUMN)) {
                return cache.put(host, identifier, NSString.stringWithString(host.getNickname()));
            }
            throw new IllegalArgumentException("Unknown identifier: " + identifier);
        }
        return cached;
    }

    /**
     * Sets whether the use of modifier keys should have an effect on the type of operation performed.
     *
     * @return Always false
     * @see NSDraggingSource
     */
    @Override
    public boolean ignoreModifierKeysWhileDragging() {
        // If this method is not implemented or returns false, the user can tailor the drag operation by
        // holding down a modifier key during the drag.
        return false;
    }

    @Override
    public NSUInteger tableView_validateDrop_proposedRow_proposedDropOperation(NSTableView view,
                                                                               NSDraggingInfo info,
                                                                               NSInteger row, NSUInteger operation) {
        NSPasteboard draggingPasteboard = info.draggingPasteboard();
        if(!this.getSource().allowsEdit()) {
            // Do not allow drags for non writable collections
            return NSDraggingInfo.NSDragOperationNone;
        }
        else if(draggingPasteboard.availableTypeFromArray(NSArray.arrayWithObject(NSPasteboard.StringPboardType)) != null) {
            String o = draggingPasteboard.stringForType(NSPasteboard.StringPboardType);
            if(o != null) {
                if(ProtocolFactory.isURL(o)) {
                    view.setDropRow(row, NSTableView.NSTableViewDropAbove);
                    return NSDraggingInfo.NSDragOperationCopy;
                }
            }
            return NSDraggingInfo.NSDragOperationNone;
        }
        else if(draggingPasteboard.availableTypeFromArray(NSArray.arrayWithObject(NSPasteboard.FilenamesPboardType)) != null) {
            NSObject o = draggingPasteboard.propertyListForType(NSPasteboard.FilenamesPboardType);
            if(o != null) {
                NSArray elements = Rococoa.cast(o, NSArray.class);
                for(int i = 0; i < elements.count().intValue(); i++) {
                    String file = elements.objectAtIndex(new NSUInteger(i)).toString();
                    if(file.endsWith(".duck")) {
                        // Allow drag if at least one file is a serialized bookmark
                        view.setDropRow(row, NSTableView.NSTableViewDropAbove);
                        return NSDraggingInfo.NSDragOperationCopy;
                    }
                }
                //only allow other files if there is at least one bookmark
                view.setDropRow(row, NSTableView.NSTableViewDropOn);
                return NSDraggingInfo.NSDragOperationCopy;
            }
            return NSDraggingInfo.NSDragOperationNone;
        }
        else if(draggingPasteboard.availableTypeFromArray(NSArray.arrayWithObject(NSPasteboard.URLPboardType)) != null) {
            NSObject o = draggingPasteboard.propertyListForType(NSPasteboard.URLPboardType);
            if(o != null) {
                NSArray elements = Rococoa.cast(o, NSArray.class);
                for(int i = 0; i < elements.count().intValue(); i++) {
                    if(ProtocolFactory.isURL(elements.objectAtIndex(new NSUInteger(i)).toString())) {
                        view.setDropRow(row, NSTableView.NSTableViewDropAbove);
                        return NSDraggingInfo.NSDragOperationCopy;
                    }
                }
            }
            return NSDraggingInfo.NSDragOperationNone;
        }
        else if(!HostPasteboard.getPasteboard().isEmpty()) {
            view.setDropRow(row, NSTableView.NSTableViewDropAbove);
            // We accept any file promise within the bounds
            if(info.draggingSourceOperationMask().intValue() == NSDraggingInfo.NSDragOperationCopy.intValue()) {
                return NSDraggingInfo.NSDragOperationCopy;
            }
            return NSDraggingInfo.NSDragOperationMove;
        }
        return NSDraggingInfo.NSDragOperationNone;
    }

    /**
     * @param info contains details on this dragging operation.
     * @param row  The proposed location is row and action is operation.
     *             The data source should incorporate the data from the dragging pasteboard at this time.
     * @see NSTableView.DataSource
     *      Invoked by view when the mouse button is released over a table view that previously decided to allow a drop.
     */
    @Override
    public boolean tableView_acceptDrop_row_dropOperation(NSTableView view, NSDraggingInfo info,
                                                          NSInteger row, NSUInteger operation) {
        NSPasteboard draggingPasteboard = info.draggingPasteboard();
        if(log.isDebugEnabled()) {
            log.debug(String.format("Accept drop at row %s", row));
        }
        view.deselectAll(null);
        final AbstractHostCollection source = this.getSource();
        if(draggingPasteboard.availableTypeFromArray(NSArray.arrayWithObject(NSPasteboard.StringPboardType)) != null) {
            String o = draggingPasteboard.stringForType(NSPasteboard.StringPboardType);
            if(null == o) {
                return false;
            }
            final Host h = Host.parse(o);
            source.add(row.intValue(), h);
            view.selectRowIndexes(NSIndexSet.indexSetWithIndex(row), false);
            view.scrollRowToVisible(row);
            return true;
        }
        else if(draggingPasteboard.availableTypeFromArray(NSArray.arrayWithObject(NSPasteboard.FilenamesPboardType)) != null) {
            // We get a drag from another application e.g. Finder.app proposing some files
            NSArray filesList = Rococoa.cast(draggingPasteboard.propertyListForType(NSPasteboard.FilenamesPboardType), NSArray.class);// get the filenames from pasteboard
            // If regular files are dropped, these will be uploaded to the dropped bookmark location
            final List<Path> roots = new Collection<Path>();
            Session session = null;
            for(int i = 0; i < filesList.count().intValue(); i++) {
                String filename = filesList.objectAtIndex(new NSUInteger(i)).toString();
                if(filename.endsWith(".duck")) {
                    // Adding a previously exported bookmark file from the Finder
                    Host bookmark = HostReaderFactory.get().read(LocalFactory.createLocal(filename));
                    if(null == bookmark) {
                        continue;
                    }
                    source.add(row.intValue(), bookmark);
                    view.selectRowIndexes(NSIndexSet.indexSetWithIndex(row), true);
                    view.scrollRowToVisible(row);
                }
                else {
                    // The bookmark this file has been dropped onto
                    Host h = source.get(row.intValue());
                    if(null == session) {
                        session = SessionFactory.createSession(h);
                    }
                    // Upload to the remote host this bookmark points to
                    roots.add(PathFactory.createPath(session,
                            h.getDefaultPath(),
                            LocalFactory.createLocal(filename)));
                }
            }
            if(!roots.isEmpty()) {
                final Transfer t = new UploadTransfer(roots);
                // If anything has been added to the queue, then process the queue
                if(t.numberOfRoots() > 0) {
                    TransferController.instance().startTransfer(t);
                }
            }
            return true;
        }
        else if(draggingPasteboard.availableTypeFromArray(NSArray.arrayWithObject(NSPasteboard.URLPboardType)) != null) {
            NSObject o = draggingPasteboard.propertyListForType(NSPasteboard.URLPboardType);
            if(o != null) {
                NSArray elements = Rococoa.cast(o, NSArray.class);
                for(int i = 0; i < elements.count().intValue(); i++) {
                    final String url = elements.objectAtIndex(new NSUInteger(i)).toString();
                    if(StringUtils.isNotBlank(url)) {
                        final Host h = Host.parse(url);
                        source.add(row.intValue(), h);
                        view.selectRowIndexes(NSIndexSet.indexSetWithIndex(row), true);
                        view.scrollRowToVisible(row);
                    }
                }
                return true;
            }
            return false;
        }
        else if(!HostPasteboard.getPasteboard().isEmpty()) {
            if(info.draggingSourceOperationMask().intValue() == NSDraggingInfo.NSDragOperationCopy.intValue()) {
                List<Host> duplicates = new ArrayList<Host>();
                for(Host bookmark : HostPasteboard.getPasteboard()) {
                    final Host duplicate = new Host(bookmark.getAsDictionary());
                    // Make sure a new UUID is asssigned for duplicate
                    duplicate.setUuid(null);
                    source.add(row.intValue(), duplicate);
                    duplicates.add(duplicate);
                }
                for(Host bookmark : duplicates) {
                    int index = source.indexOf(bookmark);
                    view.selectRowIndexes(NSIndexSet.indexSetWithIndex(new NSInteger(index)), true);
                    view.scrollRowToVisible(new NSInteger(index));
                }
            }
            else {
                int insert = row.intValue();
                for(Host bookmark : HostPasteboard.getPasteboard()) {
                    int previous = source.indexOf(bookmark);
                    if(previous == insert) {
                        // No need to move
                        continue;
                    }
                    source.remove(previous);
                    int moved;
                    if(previous < insert) {
                        moved = insert - 1;
                    }
                    else {
                        moved = insert;
                    }
                    source.add(moved, bookmark);
                }
                for(Host bookmark : HostPasteboard.getPasteboard()) {
                    int index = source.indexOf(bookmark);
                    view.selectRowIndexes(NSIndexSet.indexSetWithIndex(new NSInteger(index)), true);
                    view.scrollRowToVisible(new NSInteger(index));
                }
            }
            return true;
        }
        return false;
    }

    /**
     * @see NSDraggingSource
     * @see "http://www.cocoabuilder.com/archive/message/2005/10/5/118857"
     */
    @Override
    public void draggedImage_endedAt_operation(NSImage image, NSPoint point, NSUInteger operation) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Drop finished with operation %s", operation));
        }
        if(NSDraggingInfo.NSDragOperationDelete.intValue() == operation.intValue()) {
            controller.deleteBookmarkButtonClicked(null);
        }
        NSPasteboard.pasteboardWithName(NSPasteboard.DragPboard).declareTypes_owner(null, null);
        HostPasteboard.getPasteboard().clear();
    }

    /**
     * @param local indicates that the candidate destination object (the window or view over which the dragged
     *              image is currently poised) is in the same application as the source, while a NO value indicates that
     *              the destination object is in a different application
     * @return A mask, created by combining the dragging operations listed in the NSDragOperation section of
     *         NSDraggingInfo protocol reference using the C bitwise OR operator.If the source does not permit
     *         any dragging operations, it should return NSDragOperationNone.
     * @see NSDraggingSource
     */
    @Override
    public NSUInteger draggingSourceOperationMaskForLocal(boolean local) {
        if(local) {
            return new NSUInteger(NSDraggingInfo.NSDragOperationMove.intValue() | NSDraggingInfo.NSDragOperationCopy.intValue());
        }
        return new NSUInteger(NSDraggingInfo.NSDragOperationCopy.intValue() | NSDraggingInfo.NSDragOperationDelete.intValue());
    }

    /**
     * @param rowIndexes is the list of row numbers that will be participating in the drag.
     * @return To refuse the drag, return false. To start a drag, return true and place
     *         the drag data onto pboard (data, owner, and so on).
     * @see NSTableView.DataSource
     *      Invoked by view after it has been determined that a drag should begin, but before the drag has been started.
     *      The drag image and other drag-related information will be set up and provided by the table view once this call
     *      returns with true.
     */
    @Override
    public boolean tableView_writeRowsWithIndexes_toPasteboard(NSTableView view, NSIndexSet rowIndexes, NSPasteboard pboard) {
        for(NSUInteger index = rowIndexes.firstIndex(); !index.equals(NSIndexSet.NSNotFound); index = rowIndexes.indexGreaterThanIndex(index)) {
            HostPasteboard.getPasteboard().add(this.getSource().get(index.intValue()));
        }
        NSEvent event = NSApplication.sharedApplication().currentEvent();
        if(event != null) {
            NSPoint dragPosition = view.convertPoint_fromView(event.locationInWindow(), null);
            NSRect imageRect = new NSRect(new NSPoint(dragPosition.x.doubleValue() - 16, dragPosition.y.doubleValue() - 16), new NSSize(32, 32));
            // Writing a promised file of the host as a bookmark file to the clipboard
            view.dragPromisedFilesOfTypes(NSArray.arrayWithObject("duck"), imageRect, this.id(), true, event);
            return true;
        }
        return false;
    }

    /**
     * @return the names (not full paths) of the files that the receiver promises to create at dropDestination.
     *         This method is invoked when the drop has been accepted by the destination and the destination,
     *         in the case of another Cocoa application, invokes the NSDraggingInfo method
     *         namesOfPromisedFilesDroppedAtDestination.
     *         For long operations, you can cache dropDestination and defer the creation of the files until the
     *         finishedDraggingImage method to avoid blocking the destination application.
     * @see NSTableView.DataSource
     */
    @Override
    public NSArray namesOfPromisedFilesDroppedAtDestination(final NSURL dropDestination) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Query promised files dropped dat destination %s", dropDestination.path()));
        }
        final NSMutableArray promisedDragNames = NSMutableArray.array();
        if(null != dropDestination) {
            final HostPasteboard pasteboard = HostPasteboard.getPasteboard();
            for(Host promisedDragBookmark : pasteboard) {
                final Local file = LocalFactory.createLocal(dropDestination.path(), promisedDragBookmark.getNickname(true) + ".duck");
                HostWriterFactory.get().write(promisedDragBookmark, file);
                // Adding the filename that is promised to be created at the dropDestination
                promisedDragNames.addObject(NSString.stringWithString(file.getName()));
            }
            pasteboard.clear();
        }
        return promisedDragNames;
    }
}
