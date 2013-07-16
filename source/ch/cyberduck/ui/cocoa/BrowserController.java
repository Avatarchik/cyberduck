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
import ch.cyberduck.core.aquaticprime.LicenseFactory;
import ch.cyberduck.core.editor.Editor;
import ch.cyberduck.core.editor.EditorFactory;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.io.BandwidthThrottle;
import ch.cyberduck.core.local.Application;
import ch.cyberduck.core.local.Local;
import ch.cyberduck.core.local.LocalFactory;
import ch.cyberduck.core.sftp.SFTPSession;
import ch.cyberduck.core.ssl.SSLSession;
import ch.cyberduck.core.threading.AbstractBackgroundAction;
import ch.cyberduck.core.threading.BackgroundAction;
import ch.cyberduck.core.threading.DefaultMainAction;
import ch.cyberduck.core.threading.MainAction;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.core.transfer.TransferAction;
import ch.cyberduck.core.transfer.TransferAdapter;
import ch.cyberduck.core.transfer.TransferOptions;
import ch.cyberduck.core.transfer.TransferPrompt;
import ch.cyberduck.core.transfer.TransferSpeedometer;
import ch.cyberduck.core.transfer.copy.CopyTransfer;
import ch.cyberduck.core.transfer.download.DownloadTransfer;
import ch.cyberduck.core.transfer.move.MoveTransfer;
import ch.cyberduck.core.transfer.synchronisation.SyncTransfer;
import ch.cyberduck.core.transfer.upload.UploadTransfer;
import ch.cyberduck.core.urlhandler.SchemeHandlerFactory;
import ch.cyberduck.ui.PathPasteboard;
import ch.cyberduck.ui.action.DeleteWorker;
import ch.cyberduck.ui.cocoa.application.*;
import ch.cyberduck.ui.cocoa.delegate.ArchiveMenuDelegate;
import ch.cyberduck.ui.cocoa.delegate.CopyURLMenuDelegate;
import ch.cyberduck.ui.cocoa.delegate.EditMenuDelegate;
import ch.cyberduck.ui.cocoa.delegate.OpenURLMenuDelegate;
import ch.cyberduck.ui.cocoa.delegate.URLMenuDelegate;
import ch.cyberduck.ui.cocoa.foundation.*;
import ch.cyberduck.ui.cocoa.quicklook.QLPreviewPanel;
import ch.cyberduck.ui.cocoa.quicklook.QLPreviewPanelController;
import ch.cyberduck.ui.cocoa.quicklook.QuickLook;
import ch.cyberduck.ui.cocoa.quicklook.QuickLookFactory;
import ch.cyberduck.ui.cocoa.resources.IconCache;
import ch.cyberduck.ui.cocoa.threading.BrowserBackgroundAction;
import ch.cyberduck.ui.cocoa.threading.WindowMainAction;
import ch.cyberduck.ui.cocoa.threading.WorkerBackgroundAction;
import ch.cyberduck.ui.cocoa.view.BookmarkCell;
import ch.cyberduck.ui.cocoa.view.OutlineCell;
import ch.cyberduck.ui.growl.Growl;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.rococoa.Foundation;
import org.rococoa.ID;
import org.rococoa.Rococoa;
import org.rococoa.Selector;
import org.rococoa.cocoa.CGFloat;
import org.rococoa.cocoa.foundation.NSInteger;
import org.rococoa.cocoa.foundation.NSPoint;
import org.rococoa.cocoa.foundation.NSRect;
import org.rococoa.cocoa.foundation.NSSize;
import org.rococoa.cocoa.foundation.NSUInteger;

import java.io.File;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @version $Id: BrowserController.java 10889 2013-04-19 15:42:22Z dkocher $
 */
public class BrowserController extends WindowController implements NSToolbar.Delegate, QLPreviewPanelController {
    private static Logger log = Logger.getLogger(BrowserController.class);

    public BrowserController() {
        this.loadBundle();
    }

    @Override
    protected String getBundleName() {
        return "Browser";
    }

    public static void validateToolbarItems() {
        for(BrowserController controller : MainController.getBrowsers()) {
            controller.validateToolbar();
        }
    }

    protected void validateToolbar() {
        this.window().toolbar().validateVisibleItems();
    }

    public static void updateBookmarkTableRowHeight() {
        for(BrowserController controller : MainController.getBrowsers()) {
            controller._updateBookmarkCell();
        }
    }

    public static void updateBrowserTableAttributes() {
        for(BrowserController controller : MainController.getBrowsers()) {
            controller._updateBrowserAttributes(controller.browserListView);
            controller._updateBrowserAttributes(controller.browserOutlineView);
        }
    }

    public static void updateBrowserTableColumns() {
        for(BrowserController controller : MainController.getBrowsers()) {
            controller._updateBrowserColumns(controller.browserListView);
            controller._updateBrowserColumns(controller.browserOutlineView);
        }
    }

    private QuickLook quicklook = QuickLookFactory.get();

    private NSToolbar toolbar;

    @Override
    public void awakeFromNib() {
        // Configure Toolbar
        this.toolbar = NSToolbar.toolbarWithIdentifier("Cyberduck Toolbar");
        this.toolbar.setDelegate((this.id()));
        this.toolbar.setAllowsUserCustomization(true);
        this.toolbar.setAutosavesConfiguration(true);
        this.window().setToolbar(toolbar);
        this.window().makeFirstResponder(this.quickConnectPopup);
        this._updateBrowserColumns(this.browserListView);
        this._updateBrowserColumns(this.browserOutlineView);
        if(Preferences.instance().getBoolean("browser.logDrawer.isOpen")) {
            this.logDrawer.open();
        }
        if(LicenseFactory.find().equals(LicenseFactory.EMPTY_LICENSE)) {
            this.addDonateWindowTitle();
        }
        this.toggleBookmarks(true);
        super.awakeFromNib();
    }

    protected Comparator<Path> getComparator() {
        return this.getSelectedBrowserDelegate().getSortingComparator();
    }

    /**
     * Hide files beginning with '.'
     *
     * @see ch.cyberduck.core.HiddenFilesPathFilter#accept(ch.cyberduck.core.AbstractPath)
     */
    private boolean showHiddenFiles;

    private PathFilter<Path> filenameFilter;

    {
        if(Preferences.instance().getBoolean("browser.showHidden")) {
            this.filenameFilter = new NullPathFilter<Path>();
            this.showHiddenFiles = true;
        }
        else {
            this.filenameFilter = new HiddenFilesPathFilter<Path>();
            this.showHiddenFiles = false;
        }
    }

    /**
     * No file filter.
     */
    private static final PathFilter<Path> NULL_FILTER = new NullPathFilter<Path>();

    /**
     * Filter hidden files.
     */
    private static final PathFilter<Path> HIDDEN_FILTER = new HiddenFilesPathFilter<Path>();

    protected PathFilter<Path> getFileFilter() {
        return this.filenameFilter;
    }

    protected void setPathFilter(final String search) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Set path filter to %s", search));
        }
        if(StringUtils.isBlank(search)) {
            this.searchField.setStringValue(StringUtils.EMPTY);
            // Revert to the last used default filter
            if(this.isShowHiddenFiles()) {
                this.filenameFilter = NULL_FILTER;
            }
            else {
                this.filenameFilter = HIDDEN_FILTER;
            }
        }
        else {
            // Setting up a custom filter for the directory listing
            this.filenameFilter = new PathFilter<Path>() {
                @Override
                public boolean accept(Path file) {
                    if(file.getName().toLowerCase(java.util.Locale.ENGLISH).contains(search.toLowerCase(java.util.Locale.ENGLISH))) {
                        // Matching filename
                        return true;
                    }
                    if(file.attributes().isDirectory() && getSelectedBrowserView() == browserOutlineView) {
                        // #471. Expanded item children may match search string
                        return getSession().cache().isCached(file.getReference());
                    }
                    return false;
                }
            };
        }
    }

    public void setShowHiddenFiles(boolean showHidden) {
        if(showHidden) {
            this.filenameFilter = NULL_FILTER;
            this.showHiddenFiles = true;
        }
        else {
            this.filenameFilter = HIDDEN_FILTER;
            this.showHiddenFiles = false;
        }
    }

    public boolean isShowHiddenFiles() {
        return this.showHiddenFiles;
    }

    /**
     * Marks the current browser as the first responder
     */
    private void getFocus() {
        NSView view;
        if(this.getSelectedTabView() == TAB_BOOKMARKS) {
            view = bookmarkTable;
        }
        else {
            if(this.isMounted()) {
                view = this.getSelectedBrowserView();
            }
            else {
                view = quickConnectPopup;
            }
        }
        this.updateStatusLabel();
        this.window().makeFirstResponder(view);
    }

    /**
     * @param preserveSelection All selected files should be reselected after reloading the view
     */
    public void reloadData(boolean preserveSelection) {
        this.reloadData(preserveSelection, true);
    }

    /**
     * @param preserveSelection All selected files should be reselected after reloading the view
     * @param scroll            Scroll to current selection
     */
    public void reloadData(boolean preserveSelection, boolean scroll) {
        this.reloadData(Collections.<Path>emptyList(), preserveSelection, scroll);
    }

    /**
     * @param changed           Modified files. Invalidate its parents
     * @param preserveSelection All selected files should be reselected after reloading the view
     */
    public void reloadData(final List<Path> changed, boolean preserveSelection) {
        this.reloadData(changed, preserveSelection, true);
    }

    /**
     * @param preserveSelection All selected files should be reselected after reloading the view
     * @param scroll            Scroll to current selection
     */
    public void reloadData(final List<Path> changed, boolean preserveSelection, boolean scroll) {
        if(preserveSelection) {
            //Remember the previously selected paths
            this.reloadData(changed, this.getSelectedPaths(), scroll);
        }
        else {
            this.reloadData(changed, Collections.<Path>emptyList(), scroll);
        }
    }

    /**
     * Make the broser reload its content. Will make use of the cache.
     *
     * @param selected The items to be selected
     * @see #setSelectedPaths(java.util.List)
     */
    protected void reloadData(final List<Path> selected) {
        this.reloadData(Collections.<Path>emptyList(), selected);
    }

    /**
     * Make the broser reload its content. Will make use of the cache.
     *
     * @param selected The items to be selected
     * @see #setSelectedPaths(java.util.List)
     */
    protected void reloadData(final List<Path> changed, final List<Path> selected) {
        this.reloadData(changed, selected, true);
    }

    protected void reloadData(final List<Path> changed, final List<Path> selected, boolean scroll) {
        this.reloadBrowserImpl(changed, selected, scroll);
    }

    private void reloadBrowserImpl(final List<Path> changed, final List<Path> selected, boolean scroll) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Reload data with selected files %s", selected));
        }
        for(Path p : changed) {
            session.cache().invalidate(p.getParent().getReference());
        }
        // Tell the browser view to reload the data. This will request all paths from the browser model
        // which will refetch paths from the server marked as invalid.
        final NSTableView browser = this.getSelectedBrowserView();
        browser.reloadData();
        this.deselectAll();
        for(Path path : selected) {
            this.selectRow(path.getReference(), true, scroll);
            // Only scroll to the first in the list
            scroll = false;
        }
        this.setSelectedPaths(selected);
        this.updateStatusLabel();
        // Update path navigation
        this.validateNavigationButtons();
    }

    private void selectRow(PathReference reference, boolean expand, boolean scroll) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Select row with reference %s", reference));
        }
        final NSTableView browser = this.getSelectedBrowserView();
        int row = this.getSelectedBrowserModel().indexOf(browser, reference);
        if(log.isDebugEnabled()) {
            log.debug(String.format("Select row at index :%d", row));
        }
        if(-1 == row) {
            return;
        }
        final NSInteger index = new NSInteger(row);
        browser.selectRowIndexes(NSIndexSet.indexSetWithIndex(index), expand);
        if(scroll) {
            browser.scrollRowToVisible(index);
        }
    }

    private List<Path> selected = Collections.emptyList();

    protected void setSelectedPaths(final List<Path> selected) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Set selected paths to %s", selected));
        }
        this.selected = new ArrayList<Path>();
        for(Path s : selected) {
            this.selected.add(PathFactory.createPath(session, s.getAsDictionary()));
        }
        this.validateToolbar();
    }

    /**
     * @return The first selected path found or null if there is no selection
     */
    protected Path getSelectedPath() {
        final List<Path> s = this.getSelectedPaths();
        if(s.size() > 0) {
            return s.get(0);
        }
        return null;
    }

    /**
     * @return All selected paths or an empty list if there is no selection
     */
    protected List<Path> getSelectedPaths() {
        return selected;
    }

    protected int getSelectionCount() {
        return this.getSelectedBrowserView().numberOfSelectedRows().intValue();
    }

    private void deselectAll() {
        if(log.isDebugEnabled()) {
            log.debug("Deselect all files in browser");
        }
        final NSTableView browser = this.getSelectedBrowserView();
        if(null == browser) {
            return;
        }
        browser.deselectAll(null);
    }

    @Override
    public void setWindow(NSWindow window) {
        window.setTitle(Preferences.instance().getProperty("application.name"));
        window.setMiniwindowImage(IconCache.iconNamed("cyberduck-document.icns"));
        window.setMovableByWindowBackground(true);
        window.setDelegate(this.id());
        super.setWindow(window);
    }

    private TranscriptController transcript;

    @Outlet
    private NSDrawer logDrawer;

    public void drawerWillOpen(NSNotification notification) {
        logDrawer.setContentSize(new NSSize(
                logDrawer.contentSize().width.doubleValue(),
                Preferences.instance().getDouble("browser.logDrawer.size.height")
        ));
    }

    public void drawerDidOpen(NSNotification notification) {
        Preferences.instance().setProperty("browser.logDrawer.isOpen", true);
    }

    public void drawerWillClose(NSNotification notification) {
        Preferences.instance().setProperty("browser.logDrawer.size.height",
                logDrawer.contentSize().height.intValue());
    }

    public void drawerDidClose(NSNotification notification) {
        Preferences.instance().setProperty("browser.logDrawer.isOpen", false);
    }

    public void setLogDrawer(NSDrawer logDrawer) {
        this.logDrawer = logDrawer;
        this.transcript = new TranscriptController();
        this.logDrawer.setContentView(this.transcript.getLogView());
        NSNotificationCenter.defaultCenter().addObserver(this.id(),
                Foundation.selector("drawerWillOpen:"),
                NSDrawer.DrawerWillOpenNotification,
                this.logDrawer);
        NSNotificationCenter.defaultCenter().addObserver(this.id(),
                Foundation.selector("drawerDidOpen:"),
                NSDrawer.DrawerDidOpenNotification,
                this.logDrawer);
        NSNotificationCenter.defaultCenter().addObserver(this.id(),
                Foundation.selector("drawerWillClose:"),
                NSDrawer.DrawerWillCloseNotification,
                this.logDrawer);
        NSNotificationCenter.defaultCenter().addObserver(this.id(),
                Foundation.selector("drawerDidClose:"),
                NSDrawer.DrawerDidCloseNotification,
                this.logDrawer);
    }

    private NSButton donateButton;

    public void setDonateButton(NSButton donateButton) {
        this.donateButton = donateButton;
        this.donateButton.setTitle(Locale.localizedString("Get a donation key!", "License"));
        this.donateButton.setAction(Foundation.selector("donateMenuClicked:"));
        this.donateButton.sizeToFit();
    }

    public NSButton getDonateButton() {
        return donateButton;
    }

    private void addDonateWindowTitle() {
        NSView view = this.window().contentView().superview();
        NSSize bounds = view.frame().size;
        NSSize size = donateButton.frame().size;
        donateButton.setFrame(new NSRect(
                new NSPoint(
                        bounds.width.intValue() - size.width.intValue() - 40,
                        bounds.height.intValue() - size.height.intValue() + 3),
                new NSSize(
                        size.width.intValue(),
                        size.height.intValue()))
        );
        view.addSubview(donateButton);
    }

    public void removeDonateWindowTitle() {
        donateButton.removeFromSuperview();
    }

    private static final int TAB_BOOKMARKS = 0;
    private static final int TAB_LIST_VIEW = 1;
    private static final int TAB_OUTLINE_VIEW = 2;

    private int getSelectedTabView() {
        return browserTabView.indexOfTabViewItem(browserTabView.selectedTabViewItem());
    }

    private NSTabView browserTabView;

    public void setBrowserTabView(NSTabView browserTabView) {
        this.browserTabView = browserTabView;
    }

    /**
     * @return The currently selected browser view (which is either an outlineview or a plain tableview)
     */
    public NSTableView getSelectedBrowserView() {
        switch(this.browserSwitchView.selectedSegment()) {
            case SWITCH_LIST_VIEW: {
                return browserListView;
            }
            case SWITCH_OUTLINE_VIEW: {
                return browserOutlineView;
            }
        }
        log.fatal("No selected brower view");
        return null;
    }

    /**
     * @return The datasource of the currently selected browser view
     */
    public BrowserTableDataSource getSelectedBrowserModel() {
        switch(this.browserSwitchView.selectedSegment()) {
            case SWITCH_LIST_VIEW: {
                return browserListModel;
            }
            case SWITCH_OUTLINE_VIEW: {
                return browserOutlineModel;
            }
        }
        log.fatal("No selected brower view");
        return null;
    }

    public AbstractBrowserTableDelegate<Path> getSelectedBrowserDelegate() {
        switch(this.browserSwitchView.selectedSegment()) {
            case SWITCH_LIST_VIEW: {
                return browserListViewDelegate;
            }
            case SWITCH_OUTLINE_VIEW: {
                return browserOutlineViewDelegate;
            }
        }
        log.fatal("No selected brower view");
        return null;
    }

    @Outlet
    private NSMenu editMenu;
    private EditMenuDelegate editMenuDelegate;

    public void setEditMenu(NSMenu editMenu) {
        this.editMenu = editMenu;
        this.editMenuDelegate = new EditMenuDelegate() {
            @Override
            protected Path getEditable() {
                final Path selected = BrowserController.this.getSelectedPath();
                if(null == selected) {
                    return null;
                }
                if(isEditable(selected)) {
                    return selected;
                }
                return null;
            }

            @Override
            protected ID getTarget() {
                return BrowserController.this.id();
            }
        };
        this.editMenu.setDelegate(editMenuDelegate.id());
    }

    @Outlet
    private NSMenu urlMenu;
    private URLMenuDelegate urlMenuDelegate;

    public void setUrlMenu(NSMenu urlMenu) {
        this.urlMenu = urlMenu;
        this.urlMenuDelegate = new CopyURLMenuDelegate() {
            @Override
            protected List<Path> getSelected() {
                final List<Path> s = BrowserController.this.getSelectedPaths();
                if(s.isEmpty()) {
                    if(BrowserController.this.isMounted()) {
                        return Collections.singletonList(BrowserController.this.workdir());
                    }
                }
                return s;
            }
        };
        this.urlMenu.setDelegate(urlMenuDelegate.id());
    }

    @Outlet
    private NSMenu openUrlMenu;
    private URLMenuDelegate openUrlMenuDelegate;

    public void setOpenUrlMenu(NSMenu openUrlMenu) {
        this.openUrlMenu = openUrlMenu;
        this.openUrlMenuDelegate = new OpenURLMenuDelegate() {
            @Override
            protected List<Path> getSelected() {
                final List<Path> s = BrowserController.this.getSelectedPaths();
                if(s.isEmpty()) {
                    if(BrowserController.this.isMounted()) {
                        return Collections.singletonList(BrowserController.this.workdir());
                    }
                }
                return s;
            }
        };
        this.openUrlMenu.setDelegate(openUrlMenuDelegate.id());
    }

    @Outlet
    private NSMenu archiveMenu;
    private ArchiveMenuDelegate archiveMenuDelegate;

    public void setArchiveMenu(NSMenu archiveMenu) {
        this.archiveMenu = archiveMenu;
        this.archiveMenuDelegate = new ArchiveMenuDelegate();
        this.archiveMenu.setDelegate(archiveMenuDelegate.id());
    }

    @Outlet
    private NSButton bonjourButton;

    public void setBonjourButton(NSButton bonjourButton) {
        this.bonjourButton = bonjourButton;
        NSImage img = IconCache.iconNamed("rendezvous.tiff", 16);
        img.setTemplate(false);
        this.bonjourButton.setImage(img);
        this.setRecessedBezelStyle(this.bonjourButton);
        this.bonjourButton.setTarget(this.id());
        this.bonjourButton.setAction(Foundation.selector("bookmarkButtonClicked:"));
    }

    @Outlet
    private NSButton historyButton;

    public void setHistoryButton(NSButton historyButton) {
        this.historyButton = historyButton;
        NSImage img = IconCache.iconNamed("history.tiff", 16);
        img.setTemplate(false);
        this.historyButton.setImage(img);
        this.setRecessedBezelStyle(this.historyButton);
        this.historyButton.setTarget(this.id());
        this.historyButton.setAction(Foundation.selector("bookmarkButtonClicked:"));
    }

    @Outlet
    private NSButton bookmarkButton;

    public void setBookmarkButton(NSButton bookmarkButton) {
        this.bookmarkButton = bookmarkButton;
        NSImage img = IconCache.iconNamed("bookmarks.tiff", 16);
        img.setTemplate(false);
        this.bookmarkButton.setImage(img);
        this.setRecessedBezelStyle(this.bookmarkButton);
        this.bookmarkButton.setTarget(this.id());
        this.bookmarkButton.setAction(Foundation.selector("bookmarkButtonClicked:"));
        this.bookmarkButton.setState(NSCell.NSOnState); // Set as default selected bookmark source
    }

    public void bookmarkButtonClicked(final NSButton sender) {
        if(sender != bonjourButton) {
            bonjourButton.setState(NSCell.NSOffState);
        }
        if(sender != historyButton) {
            historyButton.setState(NSCell.NSOffState);
        }
        if(sender != bookmarkButton) {
            bookmarkButton.setState(NSCell.NSOffState);
        }
        sender.setState(NSCell.NSOnState);

        this.updateBookmarkSource();
    }

    private void setRecessedBezelStyle(final NSButton b) {
        b.setBezelStyle(NSButton.NSRecessedBezelStyle);
        b.setButtonType(NSButton.NSMomentaryPushButtonButton);
        b.setImagePosition(NSCell.NSImageLeft);
        b.setFont(NSFont.boldSystemFontOfSize(11f));
        b.setShowsBorderOnlyWhileMouseInside(true);
    }

    private void updateBookmarkSource() {
        AbstractHostCollection source = BookmarkCollection.defaultCollection();
        if(bonjourButton.state() == NSCell.NSOnState) {
            source = RendezvousCollection.defaultCollection();
        }
        else if(historyButton.state() == NSCell.NSOnState) {
            source = HistoryCollection.defaultCollection();
        }
        bookmarkModel.setSource(source);
        if(!source.isLoaded()) {
            browserSpinner.startAnimation(null);
        }
        source.addListener(new AbstractCollectionListener<Host>() {
            @Override
            public void collectionLoaded() {
                invoke(new WindowMainAction(BrowserController.this) {
                    @Override
                    public void run() {
                        browserSpinner.stopAnimation(null);
                        bookmarkTable.setGridStyleMask(NSTableView.NSTableViewSolidHorizontalGridLineMask);
                    }
                });
            }
        });
        if(source.isLoaded()) {
            browserSpinner.stopAnimation(null);
            bookmarkTable.setGridStyleMask(NSTableView.NSTableViewSolidHorizontalGridLineMask);
        }
        this.setBookmarkFilter(null);
        this.reloadBookmarks();
        this.getFocus();
    }

    public void sortBookmarksByNickame(final ID sender) {
        BookmarkCollection.defaultCollection().sortByNickname();
        this.reloadBookmarks();
    }

    public void sortBookmarksByHostname(final ID sender) {
        BookmarkCollection.defaultCollection().sortByHostname();
        this.reloadBookmarks();
    }

    public void sortBookmarksByProtocol(final ID sender) {
        BookmarkCollection.defaultCollection().sortByProtocol();
        this.reloadBookmarks();
    }

    /**
     * Reload bookmark table from currently selected model
     */
    public void reloadBookmarks() {
        bookmarkTable.reloadData();
        this.updateStatusLabel();
    }

    private NSSegmentedControl bookmarkSwitchView;

    private static final int SWITCH_BOOKMARK_VIEW = 0;

    public void setBookmarkSwitchView(NSSegmentedControl bookmarkSwitchView) {
        this.bookmarkSwitchView = bookmarkSwitchView;
        this.bookmarkSwitchView.setSegmentCount(1);
        this.bookmarkSwitchView.setToolTip(Locale.localizedString("Bookmarks"));
        final NSImage image = IconCache.iconNamed("book.tiff");
        this.bookmarkSwitchView.setImage_forSegment(image, SWITCH_BOOKMARK_VIEW);
        final NSSegmentedCell cell = Rococoa.cast(this.bookmarkSwitchView.cell(), NSSegmentedCell.class);
        cell.setTrackingMode(NSSegmentedCell.NSSegmentSwitchTrackingSelectAny);
        cell.setControlSize(NSCell.NSRegularControlSize);
        this.bookmarkSwitchView.setTarget(this.id());
        this.bookmarkSwitchView.setAction(Foundation.selector("bookmarkSwitchClicked:"));
        this.bookmarkSwitchView.setSelectedSegment(SWITCH_BOOKMARK_VIEW);
    }

    @Action
    public void bookmarkSwitchClicked(final ID sender) {
        this.toggleBookmarks(this.getSelectedTabView() != TAB_BOOKMARKS);
    }

    /**
     * @param open Should open the bookmarks
     */
    public void toggleBookmarks(final boolean open) {
        this.bookmarkSwitchView.setSelected_forSegment(open, SWITCH_BOOKMARK_VIEW);
        if(open) {
            // Display bookmarks
            this.browserTabView.selectTabViewItemAtIndex(TAB_BOOKMARKS);
            this.updateBookmarkSource();
            if(this.isMounted()) {
                int row = this.bookmarkModel.getSource().indexOf(this.getSession().getHost());
                if(row != -1) {
                    this.bookmarkTable.selectRowIndexes(NSIndexSet.indexSetWithIndex(new NSInteger(row)), false);
                    this.bookmarkTable.scrollRowToVisible(new NSInteger(row));
                }
            }
        }
        else {
            this.setBookmarkFilter(null);
            this.selectBrowser(Preferences.instance().getInteger("browser.view"));
        }
        this.getFocus();
        this.validateNavigationButtons();
    }

    private NSSegmentedControl browserSwitchView;

    private static final int SWITCH_LIST_VIEW = 0;
    private static final int SWITCH_OUTLINE_VIEW = 1;

    public void setBrowserSwitchView(NSSegmentedControl browserSwitchView) {
        this.browserSwitchView = browserSwitchView;
        this.browserSwitchView.setSegmentCount(2); // list, outline
        final NSImage list = IconCache.iconNamed("list.tiff");
        list.setTemplate(true);
        this.browserSwitchView.setImage_forSegment(list, SWITCH_LIST_VIEW);
        final NSImage outline = IconCache.iconNamed("outline.tiff");
        outline.setTemplate(true);
        this.browserSwitchView.setImage_forSegment(outline, SWITCH_OUTLINE_VIEW);
        this.browserSwitchView.setTarget(this.id());
        this.browserSwitchView.setAction(Foundation.selector("browserSwitchButtonClicked:"));
        final NSSegmentedCell cell = Rococoa.cast(this.browserSwitchView.cell(), NSSegmentedCell.class);
        cell.setTrackingMode(NSSegmentedCell.NSSegmentSwitchTrackingSelectOne);
        cell.setControlSize(NSCell.NSRegularControlSize);
        this.browserSwitchView.setSelectedSegment(Preferences.instance().getInteger("browser.view"));
    }

    @Action
    public void browserSwitchButtonClicked(final NSSegmentedControl sender) {
        this.browserSwitchClicked(sender.selectedSegment(), this.getSelectedPaths());
    }

    @Action
    public void browserSwitchMenuClicked(final NSMenuItem sender) {
        this.browserSwitchView.setSelectedSegment(sender.tag());
        this.browserSwitchClicked(sender.tag(), this.getSelectedPaths());
    }

    private void browserSwitchClicked(final int view, final List<Path> selected) {
        // Close bookmarks
        this.toggleBookmarks(false);
        // Highlight selected browser view
        this.selectBrowser(view);
        // Remove any custom file filter
        setPathFilter(null);
        // Update from model
        this.reloadData(selected);
        // Focus on browser view
        this.getFocus();
        // Save selected browser view
        Preferences.instance().setProperty("browser.view", view);
    }

    private void selectBrowser(int selected) {
        this.browserSwitchView.setSelectedSegment(selected);
        switch(selected) {
            case SWITCH_LIST_VIEW:
                this.browserTabView.selectTabViewItemAtIndex(TAB_LIST_VIEW);
                break;
            case SWITCH_OUTLINE_VIEW:
                this.browserTabView.selectTabViewItemAtIndex(TAB_OUTLINE_VIEW);
                break;
        }
    }

    private abstract class AbstractBrowserOutlineViewDelegate<E> extends AbstractBrowserTableDelegate<E>
            implements NSOutlineView.Delegate {

        public String outlineView_toolTipForCell_rect_tableColumn_item_mouseLocation(NSOutlineView t, NSCell cell,
                                                                                     ID rect, NSTableColumn c,
                                                                                     NSObject item, NSPoint mouseLocation) {
            return this.tooltip(lookup(new NSObjectPathReference(item)));
        }

        @Override
        protected void setBrowserColumnSortingIndicator(NSImage image, String columnIdentifier) {
            browserOutlineView.setIndicatorImage_inTableColumn(image,
                    browserOutlineView.tableColumnWithIdentifier(columnIdentifier));
        }

        @Override
        protected Path pathAtRow(int row) {
            if(row < browserOutlineView.numberOfRows().intValue()) {
                return lookup(new NSObjectPathReference(browserOutlineView.itemAtRow(new NSInteger(row))));
            }
            log.warn("No item at row:" + row);
            return null;
        }
    }

    private abstract class AbstractBrowserListViewDelegate<E> extends AbstractBrowserTableDelegate<E>
            implements NSTableView.Delegate {

        public String tableView_toolTipForCell_rect_tableColumn_row_mouseLocation(NSTableView t, NSCell cell,
                                                                                  ID rect, NSTableColumn c,
                                                                                  NSInteger row, NSPoint mouseLocation) {
            return this.tooltip(browserListModel.children(workdir()).get(row.intValue()));
        }

        @Override
        protected void setBrowserColumnSortingIndicator(NSImage image, String columnIdentifier) {
            browserListView.setIndicatorImage_inTableColumn(image,
                    browserListView.tableColumnWithIdentifier(columnIdentifier));
        }

        @Override
        protected Path pathAtRow(int row) {
            final AttributedList<Path> children = browserListModel.children(workdir());
            if(row < children.size()) {
                return children.get(row);
            }
            log.warn("No item at row:" + row);
            return null;
        }
    }

    private abstract class AbstractBrowserTableDelegate<E> extends AbstractPathTableDelegate {

        public AbstractBrowserTableDelegate() {
            BrowserController.this.addListener(new WindowListener() {
                @Override
                public void windowWillClose() {
                    if(quicklook.isAvailable()) {
                        if(quicklook.isOpen()) {
                            quicklook.close();
                        }
                    }
                }
            });
        }

        @Override
        public boolean isColumnRowEditable(NSTableColumn column, int row) {
            if(Preferences.instance().getBoolean("browser.editable")) {
                return column.identifier().equals(BrowserTableDataSource.FILENAME_COLUMN);
            }
            return false;
        }

        @Override
        public void tableRowDoubleClicked(final ID sender) {
            BrowserController.this.insideButtonClicked(sender);
        }

        public void spaceKeyPressed(final ID sender) {
            if(quicklook.isAvailable()) {
                if(quicklook.isOpen()) {
                    quicklook.close();
                }
                else {
                    this.updateQuickLookSelection(BrowserController.this.getSelectedPaths());
                }
            }
        }

        private void updateQuickLookSelection(final List<Path> selected) {
            if(quicklook.isAvailable()) {
                final Collection<Path> downloads = new Collection<Path>();
                for(Path path : selected) {
                    if(!path.attributes().isFile()) {
                        continue;
                    }
                    final Local folder = LocalFactory.createLocal(new File(Preferences.instance().getProperty("tmp.dir"),
                            path.getSession().getHost().getUuid() + String.valueOf(Path.DELIMITER) + path.getParent().getAbsolute()));
                    path.setLocal(LocalFactory.createLocal(folder, path.getName()));
                    downloads.add(path);
                }
                if(downloads.size() > 0) {
                    background(new BrowserBackgroundAction(BrowserController.this) {
                        @Override
                        public void run() {
                            Transfer transfer = new DownloadTransfer(downloads);
                            TransferOptions options = new TransferOptions();
                            options.closeSession = false;
                            transfer.start(new TransferPrompt() {
                                @Override
                                public TransferAction prompt() {
                                    return TransferAction.ACTION_COMPARISON;
                                }
                            }, options);
                        }

                        @Override
                        public void cleanup() {
                            final Collection<Local> previews = new Collection<Local>();
                            for(Path download : downloads) {
                                previews.add(download.getLocal());
                            }
                            // Change files in Quick Look
                            quicklook.select(previews);
                            // Open Quick Look Preview Panel
                            quicklook.open();
                            // Revert status label
                            BrowserController.this.updateStatusLabel();
                            // Restore the focus to our window to demo the selection changing, scrolling
                            // (left/right) and closing (space) functionality
                            BrowserController.this.window().makeKeyWindow();
                        }

                        @Override
                        public String getActivity() {
                            return Locale.localizedString("Quick Look", "Status");
                        }
                    });
                }
            }
        }

        @Override
        public void deleteKeyPressed(final ID sender) {
            BrowserController.this.deleteFileButtonClicked(sender);
        }

        @Override
        public void tableColumnClicked(NSTableView view, NSTableColumn tableColumn) {
            final List<Path> s = BrowserController.this.getSelectedPaths();
            if(this.selectedColumnIdentifier().equals(tableColumn.identifier())) {
                this.setSortedAscending(!this.isSortedAscending());
            }
            else {
                // Remove sorting indicator on previously selected column
                this.setBrowserColumnSortingIndicator(null, this.selectedColumnIdentifier());
                // Set the newly selected column
                this.setSelectedColumn(tableColumn);
            }
            this.setBrowserColumnSortingIndicator(
                    this.isSortedAscending() ?
                            IconCache.iconNamed("NSAscendingSortIndicator") :
                            IconCache.iconNamed("NSDescendingSortIndicator"),
                    tableColumn.identifier());
            reloadData(s);
        }

        @Override
        public void selectionDidChange(NSNotification notification) {
            List<Path> selected = new ArrayList<Path>();
            NSIndexSet iterator = getSelectedBrowserView().selectedRowIndexes();
            for(NSUInteger index = iterator.firstIndex(); !index.equals(NSIndexSet.NSNotFound); index = iterator.indexGreaterThanIndex(index)) {
                Path file = this.pathAtRow(index.intValue());
                if(null == file) {
                    break;
                }
                selected.add(file);
            }
            setSelectedPaths(selected);
            if(quicklook.isOpen()) {
                this.updateQuickLookSelection(BrowserController.this.selected);
            }
            if(Preferences.instance().getBoolean("browser.info.isInspector")) {
                InfoController c = InfoController.Factory.get(BrowserController.this);
                if(null == c) {
                    return;
                }
                c.setFiles(BrowserController.this.selected);
            }
        }

        protected abstract Path pathAtRow(int row);

        protected abstract void setBrowserColumnSortingIndicator(NSImage image, String columnIdentifier);

        private static final double kSwipeGestureLeft = 1.000000;
        private static final double kSwipeGestureRight = -1.000000;
        private static final double kSwipeGestureUp = 1.000000;
        private static final double kSwipeGestureDown = -1.000000;

        /**
         * Available in Mac OS X v10.6 and later.
         *
         * @param event Swipe event
         */
        @Action
        public void swipeWithEvent(NSEvent event) {
            if(event.deltaX().doubleValue() == kSwipeGestureLeft) {
                BrowserController.this.backButtonClicked(event.id());
            }
            else if(event.deltaX().doubleValue() == kSwipeGestureRight) {
                BrowserController.this.forwardButtonClicked(event.id());
            }
            else if(event.deltaY().doubleValue() == kSwipeGestureUp) {
                NSInteger row = getSelectedBrowserView().selectedRow();
                NSInteger next;
                if(-1 == row.intValue()) {
                    // No current selection
                    next = new NSInteger(0);
                }
                else {
                    next = new NSInteger(row.longValue() - 1);
                }
                BrowserController.this.getSelectedBrowserView().selectRowIndexes(
                        NSIndexSet.indexSetWithIndex(next), false);
            }
            else if(event.deltaY().doubleValue() == kSwipeGestureDown) {
                NSInteger row = getSelectedBrowserView().selectedRow();
                NSInteger next;
                if(-1 == row.intValue()) {
                    // No current selection
                    next = new NSInteger(0);
                }
                else {
                    next = new NSInteger(row.longValue() + 1);
                }
                BrowserController.this.getSelectedBrowserView().selectRowIndexes(
                        NSIndexSet.indexSetWithIndex(next), false);
            }
        }
    }

    /**
     * QuickLook support for 10.6+
     *
     * @param panel The Preview Panel looking for a controller.
     * @return
     * @ Sent to each object in the responder chain to find a controller.
     */
    @Override
    public boolean acceptsPreviewPanelControl(QLPreviewPanel panel) {
        return true;
    }

    /**
     * QuickLook support for 10.6+
     * The receiver should setup the preview panel (data source, delegate, binding, etc.) here.
     *
     * @param panel The Preview Panel the receiver will control.
     * @ Sent to the object taking control of the Preview Panel.
     */
    @Override
    public void beginPreviewPanelControl(QLPreviewPanel panel) {
        quicklook.willBeginQuickLook();
    }

    /**
     * QuickLook support for 10.6+
     * The receiver should unsetup the preview panel (data source, delegate, binding, etc.) here.
     *
     * @param panel The Preview Panel that the receiver will stop controlling.
     * @ Sent to the object in control of the Preview Panel just before stopping its control.
     */
    @Override
    public void endPreviewPanelControl(QLPreviewPanel panel) {
        quicklook.didEndQuickLook();
    }

    // setting appearance attributes()
    final NSLayoutManager layoutManager = NSLayoutManager.layoutManager();

    private BrowserOutlineViewModel browserOutlineModel;
    @Outlet
    private NSOutlineView browserOutlineView;
    private AbstractBrowserTableDelegate<Path> browserOutlineViewDelegate;

    public void setBrowserOutlineView(NSOutlineView view) {
        browserOutlineView = view;
        // receive drag events from types
        browserOutlineView.registerForDraggedTypes(NSArray.arrayWithObjects(
                NSPasteboard.URLPboardType,
                NSPasteboard.FilenamesPboardType, //accept files dragged from the Finder for uploading
                NSPasteboard.FilesPromisePboardType //accept file promises made myself but then interpret them as TransferPasteboardType
        ));
        // setting appearance attributes()
        this._updateBrowserAttributes(browserOutlineView);
        // selection properties
        browserOutlineView.setAllowsMultipleSelection(true);
        browserOutlineView.setAllowsEmptySelection(true);
        browserOutlineView.setAllowsColumnResizing(true);
        browserOutlineView.setAllowsColumnSelection(false);
        browserOutlineView.setAllowsColumnReordering(true);

        browserOutlineView.setRowHeight(new CGFloat(layoutManager.defaultLineHeightForFont(
                NSFont.systemFontOfSize(Preferences.instance().getFloat("browser.font.size"))).intValue() + 2));

        browserOutlineView.setDataSource((browserOutlineModel = new BrowserOutlineViewModel(this)).id());
        browserOutlineView.setDelegate((browserOutlineViewDelegate = new AbstractBrowserOutlineViewDelegate<Path>() {
            @Override
            public void enterKeyPressed(final ID sender) {
                if(Preferences.instance().getBoolean("browser.enterkey.rename")) {
                    if(browserOutlineView.numberOfSelectedRows().intValue() == 1) {
                        renameFileButtonClicked(sender);
                    }
                }
                else {
                    this.tableRowDoubleClicked(sender);
                }
            }

            /**
             * @see NSOutlineView.Delegate
             */
            @Override
            public void outlineView_willDisplayCell_forTableColumn_item(NSOutlineView view, NSTextFieldCell cell,
                                                                        NSTableColumn tableColumn, NSObject item) {
                if(null == item) {
                    return;
                }
                final Path path = lookup(new NSObjectPathReference(item));
                if(null == path) {
                    return;
                }
                if(tableColumn.identifier().equals(BrowserTableDataSource.FILENAME_COLUMN)) {
                    cell.setEditable(getSession().isRenameSupported(path));
                    (Rococoa.cast(cell, OutlineCell.class)).setIcon(browserOutlineModel.iconForPath(path));
                }
                if(!BrowserController.this.isConnected() || !HIDDEN_FILTER.accept(path)) {
                    cell.setTextColor(NSColor.disabledControlTextColor());
                }
                else {
                    cell.setTextColor(NSColor.controlTextColor());
                }
            }

            /**
             * @see NSOutlineView.Delegate
             */
            @Override
            public boolean outlineView_shouldExpandItem(final NSOutlineView view, final NSObject item) {
                NSEvent event = NSApplication.sharedApplication().currentEvent();
                if(event != null) {
                    if(NSEvent.NSLeftMouseDragged == event.type()) {
                        if(!Preferences.instance().getBoolean("browser.view.autoexpand")) {
                            if(log.isDebugEnabled()) {
                                log.debug("Returning false to #outlineViewShouldExpandItem while dragging because browser.view.autoexpand == false");
                            }
                            // See tickets #98 and #633
                            return false;
                        }
                        final NSInteger draggingColumn = view.columnAtPoint(view.convertPoint_fromView(event.locationInWindow(), null));
                        if(draggingColumn.intValue() != 0) {
                            if(log.isDebugEnabled()) {
                                log.debug("Returning false to #outlineViewShouldExpandItem for column:" + draggingColumn);
                            }
                            // See ticket #60
                            return false;
                        }
                    }
                }
                return true;
            }

            /**
             * @see NSOutlineView.Delegate
             */
            @Override
            public void outlineViewItemDidExpand(NSNotification notification) {
                updateStatusLabel();
            }

            /**
             * @see NSOutlineView.Delegate
             */
            @Override
            public void outlineViewItemDidCollapse(NSNotification notification) {
                updateStatusLabel();
            }

            @Override
            protected boolean isTypeSelectSupported() {
                return true;
            }

        }).id());
        {
            NSTableColumn c = browserOutlineColumnsFactory.create(BrowserTableDataSource.FILENAME_COLUMN);
            c.headerCell().setStringValue(Locale.localizedString("Filename"));
            c.setMinWidth(new CGFloat(100));
            c.setWidth(new CGFloat(250));
            c.setMaxWidth(new CGFloat(1000));
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask | NSTableColumn.NSTableColumnUserResizingMask);
            c.setDataCell(outlineCellPrototype);
            this.browserOutlineView.addTableColumn(c);
            this.browserOutlineView.setOutlineTableColumn(c);
        }
    }

    private BrowserListViewModel browserListModel;
    @Outlet
    private NSTableView browserListView;
    private AbstractBrowserTableDelegate<Path> browserListViewDelegate;

    public void setBrowserListView(NSTableView view) {
        browserListView = view;
        // receive drag events from types
        browserListView.registerForDraggedTypes(NSArray.arrayWithObjects(
                NSPasteboard.URLPboardType,
                NSPasteboard.FilenamesPboardType, //accept files dragged from the Finder for uploading
                NSPasteboard.FilesPromisePboardType //accept file promises made myself but then interpret them as TransferPasteboardType
        ));
        // setting appearance attributes()
        this._updateBrowserAttributes(browserListView);
        // selection properties
        browserListView.setAllowsMultipleSelection(true);
        browserListView.setAllowsEmptySelection(true);
        browserListView.setAllowsColumnResizing(true);
        browserListView.setAllowsColumnSelection(false);
        browserListView.setAllowsColumnReordering(true);

        browserListView.setRowHeight(new CGFloat(layoutManager.defaultLineHeightForFont(
                NSFont.systemFontOfSize(Preferences.instance().getFloat("browser.font.size"))).intValue() + 2));

        browserListView.setDataSource((browserListModel = new BrowserListViewModel(this)).id());
        browserListView.setDelegate((browserListViewDelegate = new AbstractBrowserListViewDelegate<Path>() {
            @Override
            public void enterKeyPressed(final ID sender) {
                if(Preferences.instance().getBoolean("browser.enterkey.rename")) {
                    if(browserListView.numberOfSelectedRows().intValue() == 1) {
                        renameFileButtonClicked(sender);
                    }
                }
                else {
                    this.tableRowDoubleClicked(sender);
                }
            }

            @Override
            public void tableView_willDisplayCell_forTableColumn_row(NSTableView view, NSTextFieldCell cell, NSTableColumn tableColumn, NSInteger row) {
                final String identifier = tableColumn.identifier();
                final Path path = browserListModel.children(BrowserController.this.workdir()).get(row.intValue());
                if(identifier.equals(BrowserTableDataSource.FILENAME_COLUMN)) {
                    cell.setEditable(getSession().isRenameSupported(path));
                }
                if(cell.isKindOfClass(Foundation.getClass(NSTextFieldCell.class.getSimpleName()))) {
                    if(!BrowserController.this.isConnected() || !HIDDEN_FILTER.accept(path)) {
                        cell.setTextColor(NSColor.disabledControlTextColor());
                    }
                    else {
                        cell.setTextColor(NSColor.controlTextColor());
                    }
                }
            }

            @Override
            protected boolean isTypeSelectSupported() {
                return true;
            }
        }).id());
        {
            NSTableColumn c = browserListColumnsFactory.create(BrowserTableDataSource.ICON_COLUMN);
            c.headerCell().setStringValue(StringUtils.EMPTY);
            c.setMinWidth((20));
            c.setWidth((20));
            c.setMaxWidth((20));
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask);
            c.setDataCell(imageCellPrototype);
            c.dataCell().setAlignment(NSText.NSCenterTextAlignment);
            browserListView.addTableColumn(c);
        }
        {
            NSTableColumn c = browserListColumnsFactory.create(BrowserTableDataSource.FILENAME_COLUMN);
            c.headerCell().setStringValue(Locale.localizedString("Filename"));
            c.setMinWidth((100));
            c.setWidth((250));
            c.setMaxWidth((1000));
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask | NSTableColumn.NSTableColumnUserResizingMask);
            c.setDataCell(filenameCellPrototype);
            this.browserListView.addTableColumn(c);
        }
    }

    protected void _updateBrowserAttributes(NSTableView tableView) {
        tableView.setUsesAlternatingRowBackgroundColors(Preferences.instance().getBoolean("browser.alternatingRows"));
        if(Preferences.instance().getBoolean("browser.horizontalLines") && Preferences.instance().getBoolean("browser.verticalLines")) {
            tableView.setGridStyleMask(new NSUInteger(NSTableView.NSTableViewSolidHorizontalGridLineMask.intValue() | NSTableView.NSTableViewSolidVerticalGridLineMask.intValue()));
        }
        else if(Preferences.instance().getBoolean("browser.verticalLines")) {
            tableView.setGridStyleMask(NSTableView.NSTableViewSolidVerticalGridLineMask);
        }
        else if(Preferences.instance().getBoolean("browser.horizontalLines")) {
            tableView.setGridStyleMask(NSTableView.NSTableViewSolidHorizontalGridLineMask);
        }
        else {
            tableView.setGridStyleMask(NSTableView.NSTableViewGridNone);
        }
    }

    protected void _updateBookmarkCell() {
        final int size = Preferences.instance().getInteger("bookmark.icon.size");
        final double width = size * 1.5;
        final NSTableColumn c = bookmarkTable.tableColumnWithIdentifier(BookmarkTableDataSource.ICON_COLUMN);
        c.setMinWidth(width);
        c.setMaxWidth(width);
        c.setWidth(width);
        // Notify the table about the changed row height.
        bookmarkTable.noteHeightOfRowsWithIndexesChanged(
                NSIndexSet.indexSetWithIndexesInRange(NSRange.NSMakeRange(new NSUInteger(0), new NSUInteger(bookmarkTable.numberOfRows()))));
    }

    private final NSTextFieldCell outlineCellPrototype = OutlineCell.outlineCell();
    private final NSImageCell imageCellPrototype = NSImageCell.imageCell();
    private final NSTextFieldCell textCellPrototype = NSTextFieldCell.textFieldCell();
    private final NSTextFieldCell filenameCellPrototype = NSTextFieldCell.textFieldCell();

    private final TableColumnFactory browserListColumnsFactory = new TableColumnFactory();
    private final TableColumnFactory browserOutlineColumnsFactory = new TableColumnFactory();
    private final TableColumnFactory bookmarkTableColumnFactory = new TableColumnFactory();

    protected void _updateBrowserColumns(NSTableView table) {
        table.removeTableColumn(table.tableColumnWithIdentifier(BrowserTableDataSource.SIZE_COLUMN));
        if(Preferences.instance().getBoolean("browser.columnSize")) {
            NSTableColumn c = browserListColumnsFactory.create(BrowserTableDataSource.SIZE_COLUMN);
            c.headerCell().setStringValue(Locale.localizedString("Size"));
            c.setMinWidth(50f);
            c.setWidth(80f);
            c.setMaxWidth(150f);
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask | NSTableColumn.NSTableColumnUserResizingMask);
            c.setDataCell(textCellPrototype);
            table.addTableColumn(c);
        }
        table.removeTableColumn(table.tableColumnWithIdentifier(BrowserTableDataSource.MODIFIED_COLUMN));
        if(Preferences.instance().getBoolean("browser.columnModification")) {
            NSTableColumn c = browserListColumnsFactory.create(BrowserTableDataSource.MODIFIED_COLUMN);
            c.headerCell().setStringValue(Locale.localizedString("Modified"));
            c.setMinWidth(100f);
            c.setWidth(150);
            c.setMaxWidth(500);
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask | NSTableColumn.NSTableColumnUserResizingMask);
            c.setDataCell(textCellPrototype);
            table.addTableColumn(c);
        }
        table.removeTableColumn(table.tableColumnWithIdentifier(BrowserTableDataSource.OWNER_COLUMN));
        if(Preferences.instance().getBoolean("browser.columnOwner")) {
            NSTableColumn c = browserListColumnsFactory.create(BrowserTableDataSource.OWNER_COLUMN);
            c.headerCell().setStringValue(Locale.localizedString("Owner"));
            c.setMinWidth(50);
            c.setWidth(80);
            c.setMaxWidth(500);
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask | NSTableColumn.NSTableColumnUserResizingMask);
            c.setDataCell(textCellPrototype);
            table.addTableColumn(c);
        }
        table.removeTableColumn(table.tableColumnWithIdentifier(BrowserTableDataSource.GROUP_COLUMN));
        if(Preferences.instance().getBoolean("browser.columnGroup")) {
            NSTableColumn c = browserListColumnsFactory.create(BrowserTableDataSource.GROUP_COLUMN);
            c.headerCell().setStringValue(Locale.localizedString("Group"));
            c.setMinWidth(50);
            c.setWidth(80);
            c.setMaxWidth(500);
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask | NSTableColumn.NSTableColumnUserResizingMask);
            c.setDataCell(textCellPrototype);
            table.addTableColumn(c);
        }
        table.removeTableColumn(table.tableColumnWithIdentifier(BrowserTableDataSource.PERMISSIONS_COLUMN));
        if(Preferences.instance().getBoolean("browser.columnPermissions")) {
            NSTableColumn c = browserListColumnsFactory.create(BrowserTableDataSource.PERMISSIONS_COLUMN);
            c.headerCell().setStringValue(Locale.localizedString("Permissions"));
            c.setMinWidth(100);
            c.setWidth(100);
            c.setMaxWidth(800);
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask | NSTableColumn.NSTableColumnUserResizingMask);
            c.setDataCell(textCellPrototype);
            table.addTableColumn(c);
        }
        table.removeTableColumn(table.tableColumnWithIdentifier(BrowserTableDataSource.KIND_COLUMN));
        if(Preferences.instance().getBoolean("browser.columnKind")) {
            NSTableColumn c = browserListColumnsFactory.create(BrowserTableDataSource.KIND_COLUMN);
            c.headerCell().setStringValue(Locale.localizedString("Kind"));
            c.setMinWidth(50);
            c.setWidth(80);
            c.setMaxWidth(500);
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask | NSTableColumn.NSTableColumnUserResizingMask);
            c.setDataCell(textCellPrototype);
            table.addTableColumn(c);
        }
        table.removeTableColumn(table.tableColumnWithIdentifier(BrowserTableDataSource.EXTENSION_COLUMN));
        if(Preferences.instance().getBoolean("browser.columnExtension")) {
            NSTableColumn c = browserListColumnsFactory.create(BrowserTableDataSource.EXTENSION_COLUMN);
            c.headerCell().setStringValue(Locale.localizedString("Extension"));
            c.setMinWidth(50);
            c.setWidth(80);
            c.setMaxWidth(500);
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask | NSTableColumn.NSTableColumnUserResizingMask);
            c.setDataCell(textCellPrototype);
            table.addTableColumn(c);
        }
        table.setIndicatorImage_inTableColumn((browserListViewDelegate).isSortedAscending() ?
                IconCache.iconNamed("NSAscendingSortIndicator") :
                IconCache.iconNamed("NSDescendingSortIndicator"),
                table.tableColumnWithIdentifier(Preferences.instance().getProperty("browser.sort.column")));
        // Sets whether the order and width of this table view’s columns are automatically saved.
        table.setAutosaveTableColumns(true);
        table.sizeToFit();
        this.reloadData(false);
    }

    private BookmarkTableDataSource bookmarkModel;

    private NSTableView bookmarkTable;
    private AbstractTableDelegate<Host> bookmarkTableDelegate;

    public void setBookmarkTable(NSTableView view) {
        this.bookmarkTable = view;
        this.bookmarkTable.setSelectionHighlightStyle(NSTableView.NSTableViewSelectionHighlightStyleSourceList);
        this.bookmarkTable.setDataSource((this.bookmarkModel = new BookmarkTableDataSource(
                this, BookmarkCollection.defaultCollection())
        ).id());
        this.bookmarkTable.setDelegate((this.bookmarkTableDelegate = new AbstractTableDelegate<Host>() {
            @Override
            public String tooltip(Host bookmark) {
                return bookmark.toURL();
            }

            @Override
            public void tableRowDoubleClicked(final ID sender) {
                BrowserController.this.connectBookmarkButtonClicked(sender);
            }

            @Override
            public void enterKeyPressed(final ID sender) {
                this.tableRowDoubleClicked(sender);
            }

            @Override
            public void deleteKeyPressed(final ID sender) {
                if(bookmarkModel.getSource().allowsDelete()) {
                    BrowserController.this.deleteBookmarkButtonClicked(sender);
                }
            }

            @Override
            public void tableColumnClicked(NSTableView view, NSTableColumn tableColumn) {

            }

            @Override
            public void selectionDidChange(NSNotification notification) {
                addBookmarkButton.setEnabled(bookmarkModel.getSource().allowsAdd());
                final int selected = bookmarkTable.numberOfSelectedRows().intValue();
                editBookmarkButton.setEnabled(bookmarkModel.getSource().allowsEdit() && selected == 1);
                deleteBookmarkButton.setEnabled(bookmarkModel.getSource().allowsDelete() && selected > 0);
            }

            public CGFloat tableView_heightOfRow(NSTableView view, NSInteger row) {
                final int size = Preferences.instance().getInteger("bookmark.icon.size");
                if(BookmarkCell.SMALL_BOOKMARK_SIZE == size) {
                    return new CGFloat(18);
                }
                if(BookmarkCell.MEDIUM_BOOKMARK_SIZE == size) {
                    return new CGFloat(45);
                }
                return new CGFloat(70);
            }

            @Override
            public boolean isTypeSelectSupported() {
                return true;
            }

            public String tableView_typeSelectStringForTableColumn_row(NSTableView view,
                                                                       NSTableColumn tableColumn,
                                                                       NSInteger row) {
                return bookmarkModel.getSource().get(row.intValue()).getNickname();
            }

            public boolean tableView_isGroupRow(NSTableView view, NSInteger row) {
                return false;
            }

            private static final double kSwipeGestureLeft = 1.000000;
            private static final double kSwipeGestureRight = -1.000000;
            private static final double kSwipeGestureUp = 1.000000;
            private static final double kSwipeGestureDown = -1.000000;

            /**
             * Available in Mac OS X v10.6 and later.
             *
             * @param event Swipe event
             */
            @Action
            public void swipeWithEvent(NSEvent event) {
                if(event.deltaY().doubleValue() == kSwipeGestureUp) {
                    NSInteger row = bookmarkTable.selectedRow();
                    NSInteger next;
                    if(-1 == row.intValue()) {
                        // No current selection
                        next = new NSInteger(0);
                    }
                    else {
                        next = new NSInteger(row.longValue() - 1);
                    }
                    bookmarkTable.selectRowIndexes(
                            NSIndexSet.indexSetWithIndex(next), false);
                }
                else if(event.deltaY().doubleValue() == kSwipeGestureDown) {
                    NSInteger row = bookmarkTable.selectedRow();
                    NSInteger next;
                    if(-1 == row.intValue()) {
                        // No current selection
                        next = new NSInteger(0);
                    }
                    else {
                        next = new NSInteger(row.longValue() + 1);
                    }
                    bookmarkTable.selectRowIndexes(
                            NSIndexSet.indexSetWithIndex(next), false);
                }
            }
        }).id());
        // receive drag events from types
        this.bookmarkTable.registerForDraggedTypes(NSArray.arrayWithObjects(
                NSPasteboard.URLPboardType,
                NSPasteboard.StringPboardType,
                NSPasteboard.FilenamesPboardType, //accept bookmark files dragged from the Finder
                NSPasteboard.FilesPromisePboardType,
                "HostPBoardType" //moving bookmarks
        ));

        {
            NSTableColumn c = bookmarkTableColumnFactory.create(BookmarkTableDataSource.ICON_COLUMN);
            c.headerCell().setStringValue(StringUtils.EMPTY);
            c.setResizingMask(NSTableColumn.NSTableColumnNoResizing);
            c.setDataCell(imageCellPrototype);
            this.bookmarkTable.addTableColumn(c);
        }
        {
            NSTableColumn c = bookmarkTableColumnFactory.create(BookmarkTableDataSource.BOOKMARK_COLUMN);
            c.headerCell().setStringValue(Locale.localizedString("Bookmarks"));
            c.setMinWidth(150);
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask);
            c.setDataCell(BookmarkCell.bookmarkCell());
            this.bookmarkTable.addTableColumn(c);
        }
        {
            NSTableColumn c = bookmarkTableColumnFactory.create(BookmarkTableDataSource.STATUS_COLUMN);
            c.headerCell().setStringValue(StringUtils.EMPTY);
            c.setMinWidth(40);
            c.setWidth(40);
            c.setMaxWidth(40);
            c.setResizingMask(NSTableColumn.NSTableColumnAutoresizingMask);
            c.setDataCell(imageCellPrototype);
            c.dataCell().setAlignment(NSText.NSCenterTextAlignment);
            this.bookmarkTable.addTableColumn(c);
        }

        this._updateBookmarkCell();

        final int size = Preferences.instance().getInteger("bookmark.icon.size");
        if(BookmarkCell.SMALL_BOOKMARK_SIZE == size) {
            this.bookmarkTable.setRowHeight(new CGFloat(18));
        }
        else if(BookmarkCell.MEDIUM_BOOKMARK_SIZE == size) {
            this.bookmarkTable.setRowHeight(new CGFloat(45));
        }
        else {
            this.bookmarkTable.setRowHeight(new CGFloat(70));
        }

        // setting appearance attributes()
        this.bookmarkTable.setUsesAlternatingRowBackgroundColors(Preferences.instance().getBoolean("browser.alternatingRows"));
        this.bookmarkTable.setGridStyleMask(NSTableView.NSTableViewGridNone);

        // selection properties
        this.bookmarkTable.setAllowsMultipleSelection(true);
        this.bookmarkTable.setAllowsEmptySelection(true);
        this.bookmarkTable.setAllowsColumnResizing(false);
        this.bookmarkTable.setAllowsColumnSelection(false);
        this.bookmarkTable.setAllowsColumnReordering(false);
        this.bookmarkTable.sizeToFit();
    }

    @Outlet
    private NSPopUpButton actionPopupButton;

    public void setActionPopupButton(NSPopUpButton actionPopupButton) {
        this.actionPopupButton = actionPopupButton;
        this.actionPopupButton.setPullsDown(true);
        this.actionPopupButton.setAutoenablesItems(true);
        final NSInteger index = new NSInteger(0);
        this.actionPopupButton.insertItemWithTitle_atIndex(StringUtils.EMPTY, index);
        this.actionPopupButton.itemAtIndex(index).setImage(IconCache.iconNamed("gear.tiff"));
    }

    @Outlet
    private NSComboBox quickConnectPopup;

    private ProxyController quickConnectPopupModel = new QuickConnectModel();

    public void setQuickConnectPopup(NSComboBox quickConnectPopup) {
        this.quickConnectPopup = quickConnectPopup;
        this.quickConnectPopup.setTarget(this.id());
        this.quickConnectPopup.setCompletes(true);
        this.quickConnectPopup.setAction(Foundation.selector("quickConnectSelectionChanged:"));
        // Make sure action is not sent twice.
        this.quickConnectPopup.cell().setSendsActionOnEndEditing(false);
        this.quickConnectPopup.setUsesDataSource(true);
        this.quickConnectPopup.setDataSource(quickConnectPopupModel.id());
        NSNotificationCenter.defaultCenter().addObserver(this.id(),
                Foundation.selector("quickConnectWillPopUp:"),
                NSComboBox.ComboBoxWillPopUpNotification,
                this.quickConnectPopup);
        this.quickConnectWillPopUp(null);
    }

    private static class QuickConnectModel extends ProxyController implements NSComboBox.DataSource {
        @Override
        public NSInteger numberOfItemsInComboBox(final NSComboBox combo) {
            return new NSInteger(BookmarkCollection.defaultCollection().size());
        }

        @Override
        public NSObject comboBox_objectValueForItemAtIndex(final NSComboBox sender, final NSInteger row) {
            return NSString.stringWithString(BookmarkCollection.defaultCollection().get(row.intValue()).getNickname());
        }
    }

    public void quickConnectWillPopUp(NSNotification notification) {
        int size = BookmarkCollection.defaultCollection().size();
        quickConnectPopup.setNumberOfVisibleItems(size > 10 ? new NSInteger(10) : new NSInteger(size));
    }

    @Action
    public void quickConnectSelectionChanged(final ID sender) {
        String input = quickConnectPopup.stringValue();
        if(StringUtils.isBlank(input)) {
            return;
        }
        input = input.trim();
        // First look for equivalent bookmarks
        for(Host h : BookmarkCollection.defaultCollection()) {
            if(h.getNickname().equals(input)) {
                this.mount(h);
                return;
            }
        }
        // Try to parse the input as a URL and extract protocol, hostname, username and password if any.
        this.mount(Host.parse(input));
    }

    @Outlet
    private NSTextField searchField;

    public void setSearchField(NSTextField searchField) {
        this.searchField = searchField;
        this.searchField.setEnabled(false);
        NSNotificationCenter.defaultCenter().addObserver(this.id(),
                Foundation.selector("searchFieldTextDidChange:"),
                NSControl.NSControlTextDidChangeNotification,
                this.searchField);
    }

    @Action
    public void searchButtonClicked(final ID sender) {
        this.window().makeFirstResponder(searchField);
    }

    public void searchFieldTextDidChange(NSNotification notification) {
        if(this.getSelectedTabView() == TAB_BOOKMARKS) {
            this.setBookmarkFilter(searchField.stringValue());
        }
        else { // TAB_LIST_VIEW || TAB_OUTLINE_VIEW
            this.setPathFilter(searchField.stringValue());
            this.reloadData(true);
        }
    }

    private void setBookmarkFilter(final String searchString) {
        if(StringUtils.isBlank(searchString)) {
            this.searchField.setStringValue(StringUtils.EMPTY);
            this.bookmarkModel.setFilter(null);
        }
        else {
            this.bookmarkModel.setFilter(new HostFilter() {
                @Override
                public boolean accept(Host host) {
                    return StringUtils.lowerCase(host.getNickname()).contains(searchString.toLowerCase(java.util.Locale.ENGLISH))
                            || ((null != host.getComment()) && StringUtils.lowerCase(host.getComment()).contains(searchString.toLowerCase(java.util.Locale.ENGLISH)))
                            || StringUtils.lowerCase(host.getHostname()).contains(searchString.toLowerCase(java.util.Locale.ENGLISH));
                }
            });
        }
        this.reloadBookmarks();
    }

    // ----------------------------------------------------------
    // Manage Bookmarks
    // ----------------------------------------------------------

    @Action
    public void connectBookmarkButtonClicked(final ID sender) {
        if(bookmarkTable.numberOfSelectedRows().intValue() == 1) {
            final Host selected = bookmarkModel.getSource().get(bookmarkTable.selectedRow().intValue());
            this.mount(selected);
        }
    }

    @Outlet
    private NSButton editBookmarkButton;

    public void setEditBookmarkButton(NSButton editBookmarkButton) {
        this.editBookmarkButton = editBookmarkButton;
        this.editBookmarkButton.setEnabled(false);
        this.editBookmarkButton.setTarget(this.id());
        this.editBookmarkButton.setAction(Foundation.selector("editBookmarkButtonClicked:"));
    }

    @Action
    public void editBookmarkButtonClicked(final ID sender) {
        BookmarkController c = BookmarkController.Factory.create(
                bookmarkModel.getSource().get(bookmarkTable.selectedRow().intValue())
        );
        c.window().makeKeyAndOrderFront(null);
    }

    @Action
    public void duplicateBookmarkButtonClicked(final ID sender) {
        final Host selected = bookmarkModel.getSource().get(bookmarkTable.selectedRow().intValue());
        this.toggleBookmarks(true);
        final Host duplicate = new Host(selected.getAsDictionary());
        // Make sure a new UUID is asssigned for duplicate
        duplicate.setUuid(null);
        this.addBookmark(duplicate);
    }

    @Outlet
    private NSButton addBookmarkButton;

    public void setAddBookmarkButton(NSButton addBookmarkButton) {
        this.addBookmarkButton = addBookmarkButton;
        this.addBookmarkButton.setTarget(this.id());
        this.addBookmarkButton.setAction(Foundation.selector("addBookmarkButtonClicked:"));
    }

    @Action
    public void addBookmarkButtonClicked(final ID sender) {
        final Host bookmark;
        if(this.isMounted()) {
            Path selected = this.getSelectedPath();
            if(null == selected || !selected.attributes().isDirectory()) {
                selected = this.workdir();
            }
            bookmark = new Host(this.session.getHost().getAsDictionary());
            // Make sure a new UUID is asssigned for duplicate
            bookmark.setUuid(null);
            bookmark.setDefaultPath(selected.getAbsolute());
        }
        else {
            bookmark = new Host(ProtocolFactory.forName(Preferences.instance().getProperty("connection.protocol.default")),
                    Preferences.instance().getProperty("connection.hostname.default"),
                    Preferences.instance().getInteger("connection.port.default"));
        }
        this.toggleBookmarks(true);
        this.addBookmark(bookmark);
    }

    public void addBookmark(Host item) {
        bookmarkModel.setFilter(null);
        bookmarkModel.getSource().add(item);
        final int row = bookmarkModel.getSource().lastIndexOf(item);
        final NSInteger index = new NSInteger(row);
        bookmarkTable.selectRowIndexes(NSIndexSet.indexSetWithIndex(index), false);
        bookmarkTable.scrollRowToVisible(index);
        BookmarkController c = BookmarkController.Factory.create(item);
        c.window().makeKeyAndOrderFront(null);
    }

    @Outlet
    private NSButton deleteBookmarkButton;

    public void setDeleteBookmarkButton(NSButton deleteBookmarkButton) {
        this.deleteBookmarkButton = deleteBookmarkButton;
        this.deleteBookmarkButton.setEnabled(false);
        this.deleteBookmarkButton.setTarget(this.id());
        this.deleteBookmarkButton.setAction(Foundation.selector("deleteBookmarkButtonClicked:"));
    }

    @Action
    public void deleteBookmarkButtonClicked(final ID sender) {
        NSIndexSet iterator = bookmarkTable.selectedRowIndexes();
        final List<Host> selected = new ArrayList<Host>();
        for(NSUInteger index = iterator.firstIndex(); !index.equals(NSIndexSet.NSNotFound); index = iterator.indexGreaterThanIndex(index)) {
            selected.add(bookmarkModel.getSource().get(index.intValue()));
        }
        StringBuilder alertText = new StringBuilder(
                Locale.localizedString("Do you want to delete the selected bookmark?"));
        int i = 0;
        Iterator<Host> iter = selected.iterator();
        while(i < 10 && iter.hasNext()) {
            alertText.append("\n").append(Character.toString('\u2022')).append(" ").append(iter.next().getNickname());
            i++;
        }
        if(iter.hasNext()) {
            alertText.append("\n").append(Character.toString('\u2022')).append(" " + "…");
        }
        final NSAlert alert = NSAlert.alert(Locale.localizedString("Delete Bookmark"),
                alertText.toString(),
                Locale.localizedString("Delete"),
                Locale.localizedString("Cancel"),
                null);
        this.alert(alert, new SheetCallback() {
            @Override
            public void callback(int returncode) {
                if(returncode == DEFAULT_OPTION) {
                    bookmarkTable.deselectAll(null);
                    bookmarkModel.getSource().removeAll(selected);
                }
            }
        });
    }

    // ----------------------------------------------------------
    // Browser navigation
    // ----------------------------------------------------------

    private static final int NAVIGATION_LEFT_SEGMENT_BUTTON = 0;
    private static final int NAVIGATION_RIGHT_SEGMENT_BUTTON = 1;

    private static final int NAVIGATION_UP_SEGMENT_BUTTON = 0;

    private NSSegmentedControl navigationButton;

    public void setNavigationButton(NSSegmentedControl navigationButton) {
        this.navigationButton = navigationButton;
        this.navigationButton.setTarget(this.id());
        this.navigationButton.setAction(Foundation.selector("navigationButtonClicked:"));
        this.navigationButton.setImage_forSegment(IconCache.iconNamed("nav-backward.tiff"),
                NAVIGATION_LEFT_SEGMENT_BUTTON);
        this.navigationButton.setImage_forSegment(IconCache.iconNamed("nav-forward.tiff"),
                NAVIGATION_RIGHT_SEGMENT_BUTTON);
    }

    @Action
    public void navigationButtonClicked(NSSegmentedControl sender) {
        switch(sender.selectedSegment()) {
            case NAVIGATION_LEFT_SEGMENT_BUTTON: {
                this.backButtonClicked(sender.id());
                break;
            }
            case NAVIGATION_RIGHT_SEGMENT_BUTTON: {
                this.forwardButtonClicked(sender.id());
                break;
            }
        }
    }

    @Action
    public void backButtonClicked(final ID sender) {
        final Path selected = this.getPreviousPath();
        if(selected != null) {
            final Path previous = this.workdir();
            if(previous.getParent().equals(selected)) {
                this.setWorkdir(selected, previous);
            }
            else {
                this.setWorkdir(selected);
            }
        }
    }

    @Action
    public void forwardButtonClicked(final ID sender) {
        final Path selected = this.getForwardPath();
        if(selected != null) {
            this.setWorkdir(selected);
        }
    }

    @Outlet
    private NSSegmentedControl upButton;

    public void setUpButton(NSSegmentedControl upButton) {
        this.upButton = upButton;
        this.upButton.setTarget(this.id());
        this.upButton.setAction(Foundation.selector("upButtonClicked:"));
        this.upButton.setImage_forSegment(IconCache.iconNamed("nav-up.tiff"),
                NAVIGATION_UP_SEGMENT_BUTTON);
    }

    public void upButtonClicked(final ID sender) {
        final Path previous = this.workdir();
        this.setWorkdir(previous.getParent(), previous);
    }

    private Path workdir;

    @Outlet
    private NSPopUpButton pathPopupButton;

    public void setPathPopup(NSPopUpButton pathPopupButton) {
        this.pathPopupButton = pathPopupButton;
        this.pathPopupButton.setTarget(this.id());
        this.pathPopupButton.setAction(Foundation.selector("pathPopupSelectionChanged:"));
    }

    private void addPathToNavigation(final Path p) {
        pathPopupButton.addItemWithTitle(p.getAbsolute());
        pathPopupButton.lastItem().setRepresentedObject(p.getAbsolute());
        pathPopupButton.lastItem().setImage(IconCache.instance().iconForPath(p, 16));
    }

    /**
     * Update navigation toolbar.
     */
    private void validateNavigationButtons() {
        if(!this.isMounted()) {
            pathPopupButton.removeAllItems();
        }
        else {
            pathPopupButton.removeAllItems();
            final Path workdir = this.workdir();
            this.addPathToNavigation(workdir);
            Path p = workdir;
            while(!p.getParent().equals(p)) {
                this.addPathToNavigation(p);
                p = p.getParent();
            }
            this.addPathToNavigation(p);
        }

        this.navigationButton.setEnabled_forSegment(this.isMounted() && this.getBackHistory().size() > 1,
                NAVIGATION_LEFT_SEGMENT_BUTTON);
        this.navigationButton.setEnabled_forSegment(this.isMounted() && this.getForwardHistory().size() > 0,
                NAVIGATION_RIGHT_SEGMENT_BUTTON);
        this.upButton.setEnabled_forSegment(this.isMounted() && !this.workdir().isRoot(),
                NAVIGATION_UP_SEGMENT_BUTTON);

        this.pathPopupButton.setEnabled(this.isMounted());
        final boolean enabled = this.isMounted() || this.getSelectedTabView() == TAB_BOOKMARKS;
        this.searchField.setEnabled(enabled);
        if(!enabled) {
            this.searchField.setStringValue(StringUtils.EMPTY);
        }
    }

    @Action
    public void pathPopupSelectionChanged(final NSPopUpButton sender) {
        final String selected = sender.selectedItem().representedObject();
        final Path previous = this.workdir();
        if(selected != null) {
            final Path path = PathFactory.createPath(session, selected, Path.DIRECTORY_TYPE);
            this.setWorkdir(path);
            if(previous.getParent().equals(path)) {
                this.setWorkdir(path, previous);
            }
            else {
                this.setWorkdir(path);
            }
        }
    }

    @Outlet
    private NSPopUpButton encodingPopup;

    public void setEncodingPopup(NSPopUpButton encodingPopup) {
        this.encodingPopup = encodingPopup;
        this.encodingPopup.setTarget(this.id());
        this.encodingPopup.setAction(Foundation.selector("encodingButtonClicked:"));
        this.encodingPopup.removeAllItems();
        this.encodingPopup.addItemsWithTitles(NSArray.arrayWithObjects(MainController.availableCharsets()));
        this.encodingPopup.selectItemWithTitle(Preferences.instance().getProperty("browser.charset.encoding"));
    }

    @Action
    public void encodingButtonClicked(final NSPopUpButton sender) {
        this.encodingChanged(sender.titleOfSelectedItem());
    }

    @Action
    public void encodingMenuClicked(final NSMenuItem sender) {
        this.encodingChanged(sender.title());
    }

    public void encodingChanged(final String encoding) {
        if(null == encoding) {
            return;
        }
        this.setEncoding(encoding);
        if(this.isMounted()) {
            if(this.session.getEncoding().equals(encoding)) {
                return;
            }
            this.background(new BrowserBackgroundAction(this) {
                @Override
                public void run() {
                    session.close();
                }

                @Override
                public void cleanup() {
                    session.getHost().setEncoding(encoding);
                    reloadButtonClicked(null);
                }

                @Override
                public String getActivity() {
                    return MessageFormat.format(Locale.localizedString("Disconnecting {0}", "Status"),
                            session.getHost().getHostname());
                }
            });
        }
    }

    /**
     * @param encoding Character encoding
     */
    private void setEncoding(final String encoding) {
        this.encodingPopup.selectItemWithTitle(encoding);
    }

    // ----------------------------------------------------------
    // Drawers
    // ----------------------------------------------------------

    @Action
    public void toggleLogDrawer(final ID sender) {
        this.logDrawer.toggle(this.id());
    }

    // ----------------------------------------------------------
    // Status
    // ----------------------------------------------------------

    @Outlet
    protected NSProgressIndicator statusSpinner;

    public void setStatusSpinner(NSProgressIndicator statusSpinner) {
        this.statusSpinner = statusSpinner;
        this.statusSpinner.setDisplayedWhenStopped(false);
        this.statusSpinner.setIndeterminate(true);
    }

    public NSProgressIndicator getStatusSpinner() {
        return statusSpinner;
    }

    @Outlet
    protected NSProgressIndicator browserSpinner;

    public void setBrowserSpinner(NSProgressIndicator browserSpinner) {
        this.browserSpinner = browserSpinner;
    }

    public NSProgressIndicator getBrowserSpinner() {
        return browserSpinner;
    }

    @Outlet
    private NSTextField statusLabel;

    public void setStatusLabel(NSTextField statusLabel) {
        this.statusLabel = statusLabel;
    }

    public void updateStatusLabel() {
        String label;
        if(this.getSelectedTabView() == TAB_BOOKMARKS) {
            label = this.bookmarkTable.numberOfRows() + " " + Locale.localizedString("Bookmarks");
        }
        else {
            final BackgroundAction current = this.getActions().getCurrent();
            if(null == current) {
                if(this.isConnected()) {
                    label = MessageFormat.format(Locale.localizedString("{0} Files"),
                            String.valueOf(this.getSelectedBrowserView().numberOfRows()));
                }
                else {
                    label = Locale.localizedString("Disconnected", "Status");
                }
            }
            else {
                if(StringUtils.isNotBlank(laststatus)) {
                    label = laststatus;
                }
                else {
                    label = current.getActivity();
                }
            }
        }
        this.updateStatusLabel(label);
    }

    /**
     * @param label Status message
     */
    public void updateStatusLabel(String label) {
        if(StringUtils.isNotBlank(label)) {
            // Update the status label at the bottom of the browser window
            statusLabel.setAttributedStringValue(NSAttributedString.attributedStringWithAttributes(label, TRUNCATE_MIDDLE_ATTRIBUTES));
        }
        else {
            statusLabel.setStringValue(StringUtils.EMPTY);
        }
    }

    @Outlet
    private NSButton securityLabel;

    public void setSecurityLabel(NSButton securityLabel) {
        this.securityLabel = securityLabel;
        this.securityLabel.setEnabled(false);
        this.securityLabel.setTarget(this.id());
        this.securityLabel.setAction(Foundation.selector("securityLabelClicked:"));
    }

    @Action
    public void securityLabelClicked(final ID sender) {
        if(session instanceof SSLSession) {
            List<X509Certificate> certificates = ((SSLSession) this.session).getAcceptedIssuers();
            if(0 == certificates.size()) {
                log.warn("No accepted certificates found");
                return;
            }
            KeychainFactory.get().displayCertificates(
                    certificates.toArray(new X509Certificate[certificates.size()]));
        }
    }

    // ----------------------------------------------------------
    // Selector methods for the toolbar items
    // ----------------------------------------------------------

    public void quicklookButtonClicked(final ID sender) {
        if(quicklook.isOpen()) {
            quicklook.close();
        }
        else {
            final AbstractBrowserTableDelegate delegate = this.getSelectedBrowserDelegate();
            delegate.updateQuickLookSelection(this.getSelectedPaths());
        }
    }

    /**
     * Marks all expanded directories as invalid and tells the
     * browser table to reload its data
     *
     * @param sender Toolbar button
     */
    @Action
    public void reloadButtonClicked(final ID sender) {
        if(this.isMounted()) {
            final List<Path> s = this.getSelectedPaths();
            final Session session = this.getSession();session.cache().invalidate(this.workdir().getReference());
            session.cdn().clear();
            switch(this.browserSwitchView.selectedSegment()) {
                case SWITCH_OUTLINE_VIEW: {
                    for(int i = 0; i < browserOutlineView.numberOfRows().intValue(); i++) {
                        final NSObject item = browserOutlineView.itemAtRow(new NSInteger(i));
                        if(browserOutlineView.isItemExpanded(item)) {
                            final NSObjectPathReference reference = new NSObjectPathReference(item);
                            session.cache().invalidate(reference);
                        }
                    }
                    break;
                }
            }
            this.reloadData(s);
        }
    }

    /**
     * Open a new browser with the current selected folder as the working directory
     *
     * @param sender Toolbar button
     */
    @Action
    public void newBrowserButtonClicked(final ID sender) {
        Path selected = this.getSelectedPath();
        if(null == selected || !selected.attributes().isDirectory()) {
            selected = this.workdir();
        }
        BrowserController c = MainController.newDocument(true);
        final Host host = new Host(this.getSession().getHost().<NSDictionary>getAsDictionary());
        host.setDefaultPath(selected.getAbsolute());
        c.mount(host);
    }

    /**
     * @param source      The original file to duplicate
     * @param destination The destination of the duplicated file
     */
    protected void duplicatePath(final Path source, final Path destination) {
        this.duplicatePaths(Collections.singletonMap(source, destination), true);
    }

    /**
     * @param selected A map with the original files as the key and the destination
     *                 files as the value
     * @param browser  Transfer in browser session
     */
    protected void duplicatePaths(final Map<Path, Path> selected, final boolean browser) {
        this.checkOverwrite(selected.values(), new DefaultMainAction() {
            @Override
            public void run() {
                transfer(new CopyTransfer(selected), new ArrayList<Path>(selected.values()), browser);
            }
        });
    }

    /**
     * @param path    The existing file
     * @param renamed The renamed file
     */
    protected void renamePath(final Path path, final Path renamed) {
        this.renamePaths(Collections.singletonMap(path, renamed));
    }

    /**
     * @param selected A map with the original files as the key and the destination
     *                 files as the value
     */
    protected void renamePaths(final Map<Path, Path> selected) {
        this.checkMove(selected.values(), new DefaultMainAction() {
            @Override
            public void run() {
                final ArrayList<Path> changed = new ArrayList<Path>();
                changed.addAll(selected.keySet());
                changed.addAll(selected.values());
                transfer(new MoveTransfer(selected), changed, true);
            }
        });
    }

    /**
     * Displays a warning dialog about already existing files
     *
     * @param selected The files to check for existance
     */
    private void checkOverwrite(final java.util.Collection<Path> selected, final MainAction action) {
        if(selected.size() > 0) {
            StringBuilder alertText = new StringBuilder(
                    Locale.localizedString("A file with the same name already exists. Do you want to replace the existing file?"));
            int i = 0;
            Iterator<Path> iter;
            boolean shouldWarn = false;
            for(iter = selected.iterator(); iter.hasNext(); ) {
                Path item = iter.next();
                if(item.exists()) {
                    if(i < 10) {
                        alertText.append("\n").append(Character.toString('\u2022')).append(" ").append(item.getName());
                    }
                    shouldWarn = true;
                }
                i++;
            }
            if(i >= 10) {
                alertText.append("\n").append(Character.toString('\u2022')).append(" ...)");
            }
            if(shouldWarn) {
                NSAlert alert = NSAlert.alert(
                        Locale.localizedString("Overwrite"), //title
                        alertText.toString(),
                        Locale.localizedString("Overwrite"), // defaultbutton
                        Locale.localizedString("Cancel"), //alternative button
                        null //other button
                );
                this.alert(alert, new SheetCallback() {
                    @Override
                    public void callback(final int returncode) {
                        if(returncode == DEFAULT_OPTION) {
                            action.run();
                        }
                    }
                });
            }
            else {
                action.run();
            }
        }
    }

    /**
     * Displays a warning dialog about files to be moved
     *
     * @param selected The files to check for existance
     */
    private void checkMove(final java.util.Collection<Path> selected, final MainAction action) {
        if(selected.size() > 0) {
            if(Preferences.instance().getBoolean("browser.confirmMove")) {
                StringBuilder alertText = new StringBuilder(
                        Locale.localizedString("Do you want to move the selected files?"));
                int i = 0;
                Iterator<Path> iter;
                for(iter = selected.iterator(); i < 10 && iter.hasNext(); ) {
                    Path item = iter.next();
                    alertText.append(String.format("\n%s %s", Character.toString('\u2022'), item.getName()));
                    i++;
                }
                if(iter.hasNext()) {
                    alertText.append(String.format("\n%s ...)", Character.toString('\u2022')));
                }
                final NSAlert alert = NSAlert.alert(
                        Locale.localizedString("Move"), //title
                        alertText.toString(),
                        Locale.localizedString("Move"), // defaultbutton
                        Locale.localizedString("Cancel"), //alternative button
                        null //other button
                );
                this.alert(alert, new SheetCallback() {
                    @Override
                    public void callback(final int returncode) {
                        if(returncode == DEFAULT_OPTION) {
                            checkOverwrite(selected, action);
                        }
                    }
                });
            }
            else {
                this.checkOverwrite(selected, action);
            }
        }
    }

    /**
     * Prunes the list of selected files. Files which are a child of an already included directory
     * are removed from the returned list.
     *
     * @param selected Selected files for transfer
     * @return Normalized
     */
    protected List<Path> checkHierarchy(final List<Path> selected) {
        final List<Path> normalized = new Collection<Path>();
        for(Path f : selected) {
            boolean duplicate = false;
            for(Path n : normalized) {
                if(f.isChild(n)) {
                    // The selected file is a child of a directory already included for deletion
                    duplicate = true;
                    break;
                }
            }
            if(!duplicate) {
                normalized.add(f);
            }
        }
        return normalized;
    }

    /**
     * Recursively deletes the file
     *
     * @param file File or directory
     */
    public void deletePath(final Path file) {
        this.deletePaths(Collections.singletonList(file));
    }

    /**
     * Recursively deletes the files
     *
     * @param selected The files selected in the browser to delete
     */
    public void deletePaths(final List<Path> selected) {
        final List<Path> normalized = this.checkHierarchy(selected);
        if(normalized.isEmpty()) {
            return;
        }
        StringBuilder alertText =
                new StringBuilder(Locale.localizedString("Really delete the following files? This cannot be undone."));
        int i = 0;
        Iterator<Path> iter;
        for(iter = normalized.iterator(); i < 10 && iter.hasNext(); ) {
            alertText.append("\n").append(Character.toString('\u2022')).append(" ").append(iter.next().getName());
            i++;
        }
        if(iter.hasNext()) {
            alertText.append("\n").append(Character.toString('\u2022')).append(" " + "…");
        }
        NSAlert alert = NSAlert.alert(Locale.localizedString("Delete"), //title
                alertText.toString(),
                Locale.localizedString("Delete"), // defaultbutton
                Locale.localizedString("Cancel"), //alternative button
                null //other button
        );
        this.alert(alert, new SheetCallback() {
            @Override
            public void callback(final int returncode) {
                if(returncode == DEFAULT_OPTION) {
                    BrowserController.this.deletePathsImpl(normalized);
                }
            }
        });
    }

    private void deletePathsImpl(final List<Path> files) {
        this.background(new WorkerBackgroundAction<Boolean>(this,
                new DeleteWorker(files) {
                    @Override
                    public void cleanup(final Boolean result) {
                        if(result) {
                            reloadData(files, false);
                        }
                    }
                })
        );
    }

    /**
     * @param selected File
     */
    public void revertPath(final Path selected) {
        this.background(new BrowserBackgroundAction(this) {
            @Override
            public void run() {
                if(this.isCanceled()) {
                    return;
                }
                selected.revert();
            }

            @Override
            public String getActivity() {
                return MessageFormat.format(Locale.localizedString("Reverting {0}", "Status"),
                        selected.getName());
            }

            @Override
            public void cleanup() {
                reloadData(Collections.singletonList(selected), false);
            }
        });
    }

    /**
     * @param selected File
     * @return True if the selected path is editable (not a directory and no known binary file)
     */
    protected boolean isEditable(final Path selected) {
        if(this.getSession().getHost().getCredentials().isAnonymousLogin()) {
            return false;
        }
        return selected.attributes().isFile();
    }

    @Action
    public void gotoButtonClicked(final ID sender) {
        SheetController sheet = new GotoController(this);
        sheet.beginSheet();
    }

    @Action
    public void createFileButtonClicked(final ID sender) {
        SheetController sheet = new CreateFileController(this);
        sheet.beginSheet();
    }

    @Action
    public void createSymlinkButtonClicked(final ID sender) {
        SheetController sheet = new CreateSymlinkController(this);
        sheet.beginSheet();
    }

    @Action
    public void duplicateFileButtonClicked(final ID sender) {
        SheetController sheet = new DuplicateFileController(this);
        sheet.beginSheet();
    }

    @Action
    public void createFolderButtonClicked(final ID sender) {
        SheetController sheet = new FolderController(this);
        sheet.beginSheet();
    }

    @Action
    public void renameFileButtonClicked(final ID sender) {
        final NSTableView browser = this.getSelectedBrowserView();
        browser.editRow(browser.columnWithIdentifier(BrowserTableDataSource.FILENAME_COLUMN),
                browser.selectedRow(), true);
        final Path selected = this.getSelectedPath();
        if(StringUtils.isNotBlank(selected.getExtension())) {
            NSText view = browser.currentEditor();
            int index = selected.getName().indexOf(selected.getExtension()) - 1;
            if(index > 0) {
                view.setSelectedRange(NSRange.NSMakeRange(new NSUInteger(0), new NSUInteger(index)));
            }
        }
    }

    @Action
    public void sendCustomCommandClicked(final ID sender) {
        SheetController sheet = new CommandController(this, this.session);
        sheet.beginSheet();
    }

    @Action
    public void editMenuClicked(final NSMenuItem sender) {
        for(Path selected : this.getSelectedPaths()) {
            final Editor editor = EditorFactory.instance().create(this,
                    new Application(sender.representedObject(), null), selected);
            editor.open();
        }
    }

    @Action
    public void editButtonClicked(final ID sender) {
        for(Path selected : this.getSelectedPaths()) {
            final Editor editor = EditorFactory.instance().create(this, selected);
            editor.open();
        }
    }

    @Action
    public void openBrowserButtonClicked(final ID sender) {
        if(this.getSelectionCount() == 1) {
            openUrl(this.getSelectedPath().toHttpURL());
        }
        else {
            openUrl(this.workdir().toHttpURL());
        }
    }

    @Action
    public void infoButtonClicked(final ID sender) {
        if(this.getSelectionCount() > 0) {
            InfoController c = InfoController.Factory.create(BrowserController.this, this.getSelectedPaths());
            c.window().makeKeyAndOrderFront(null);
        }
    }

    @Action
    public void revertFileButtonClicked(final ID sender) {
        this.revertPath(this.getSelectedPath());
    }

    @Action
    public void deleteFileButtonClicked(final ID sender) {
        this.deletePaths(this.getSelectedPaths());
    }

    private static String lastSelectedDownloadDirectory =
            Preferences.instance().getProperty("queue.download.folder");

    private NSOpenPanel downloadToPanel;

    @Action
    public void downloadToButtonClicked(final ID sender) {
        downloadToPanel = NSOpenPanel.openPanel();
        downloadToPanel.setCanChooseDirectories(true);
        downloadToPanel.setCanCreateDirectories(true);
        downloadToPanel.setCanChooseFiles(false);
        downloadToPanel.setAllowsMultipleSelection(false);
        downloadToPanel.setPrompt(Locale.localizedString("Choose"));
        downloadToPanel.beginSheetForDirectory(
                lastSelectedDownloadDirectory, //trying to be smart
                null, this.window, this.id(),
                Foundation.selector("downloadToPanelDidEnd:returnCode:contextInfo:"),
                null);
    }

    public void downloadToPanelDidEnd_returnCode_contextInfo(NSOpenPanel sheet, int returncode, final ID contextInfo) {
        sheet.close();
        if(returncode == SheetCallback.DEFAULT_OPTION) {
            final Local downloadfolder = LocalFactory.createLocal(sheet.filename());
            this.download(this.getSelectedPaths(), downloadfolder);
        }
        lastSelectedDownloadDirectory = sheet.filename();
        downloadToPanel = null;
    }

    private NSSavePanel downloadAsPanel;

    @Action
    public void downloadAsButtonClicked(final ID sender) {
        downloadAsPanel = NSSavePanel.savePanel();
        downloadAsPanel.setMessage(Locale.localizedString("Download the selected file to…"));
        downloadAsPanel.setNameFieldLabel(Locale.localizedString("Download As:"));
        downloadAsPanel.setPrompt(Locale.localizedString("Download"));
        downloadAsPanel.setCanCreateDirectories(true);
        downloadAsPanel.beginSheetForDirectory(null, this.getSelectedPath().getDisplayName(), this.window, this.id(),
                Foundation.selector("downloadAsPanelDidEnd:returnCode:contextInfo:"),
                null);
    }

    public void downloadAsPanelDidEnd_returnCode_contextInfo(NSSavePanel sheet, int returncode, final ID contextInfo) {
        sheet.close();
        if(returncode == SheetCallback.DEFAULT_OPTION) {
            String filename;
            if((filename = sheet.filename()) != null) {
                this.download(Collections.singletonList(this.getSelectedPath()), LocalFactory.createLocal(filename));
            }
        }
    }

    private NSOpenPanel syncPanel;

    @Action
    public void syncButtonClicked(final ID sender) {
        final Path selection;
        if(this.getSelectionCount() == 1 &&
                this.getSelectedPath().attributes().isDirectory()) {
            selection = this.getSelectedPath();
        }
        else {
            selection = this.workdir();
        }
        syncPanel = NSOpenPanel.openPanel();
        syncPanel.setCanChooseDirectories(selection.attributes().isDirectory());
        syncPanel.setTreatsFilePackagesAsDirectories(true);
        syncPanel.setCanChooseFiles(selection.attributes().isFile());
        syncPanel.setCanCreateDirectories(true);
        syncPanel.setAllowsMultipleSelection(false);
        syncPanel.setMessage(MessageFormat.format(Locale.localizedString("Synchronize {0} with"),
                selection.getName()));
        syncPanel.setPrompt(Locale.localizedString("Choose"));
        syncPanel.beginSheetForDirectory(this.getSession().getHost().getDownloadFolder().getAbsolute(), null, this.window, this.id(),
                Foundation.selector("syncPanelDidEnd:returnCode:contextInfo:"), null //context info
        );
    }

    public void syncPanelDidEnd_returnCode_contextInfo(NSOpenPanel sheet, int returncode, final ID contextInfo) {
        sheet.close();
        if(returncode == SheetCallback.DEFAULT_OPTION) {
            if(sheet.filenames().count().intValue() > 0) {
                final Path selected;
                if(this.getSelectionCount() == 1 && this.getSelectedPath().attributes().isDirectory()) {
                    selected = this.getSelectedPath();
                }
                else {
                    selected = this.workdir();
                }
                Path root = PathFactory.createPath(getTransferSession(true), selected.getAsDictionary());
                root.setLocal(LocalFactory.createLocal(sheet.filenames().lastObject().toString()));
                this.transfer(new SyncTransfer(root));
            }
        }
    }

    @Action
    public void downloadButtonClicked(final ID sender) {
        this.download(this.getSelectedPaths());
    }

    /**
     * Download to default download directory.
     *
     * @param downloads Paths to transfer
     */
    public void download(List<Path> downloads) {
        this.download(downloads, session.getHost().getDownloadFolder());
    }

    /**
     * @param downloads      Paths to transfer
     * @param downloadfolder Destination folder
     */
    public void download(final List<Path> downloads, final Local downloadfolder) {
        final Session session = this.getTransferSession();
        final List<Path> roots = new Collection<Path>();
        for(Path selected : downloads) {
            Path path = PathFactory.createPath(session, selected.getAsDictionary());
            path.setLocal(LocalFactory.createLocal(downloadfolder, path.getName()));
            roots.add(path);
        }
        final Transfer transfer = new DownloadTransfer(roots);
        this.transfer(transfer, Collections.<Path>emptyList());
    }

    private static String lastSelectedUploadDirectory = null;

    private NSOpenPanel uploadPanel;

    private NSButton uploadPanelHiddenFilesCheckbox;

    @Action
    public void uploadButtonClicked(final ID sender) {
        uploadPanel = NSOpenPanel.openPanel();
        uploadPanel.setCanChooseDirectories(true);
        uploadPanel.setCanCreateDirectories(false);
        uploadPanel.setTreatsFilePackagesAsDirectories(true);
        uploadPanel.setCanChooseFiles(true);
        uploadPanel.setAllowsMultipleSelection(true);
        uploadPanel.setPrompt(Locale.localizedString("Upload"));
        if(uploadPanel.respondsToSelector(Foundation.selector("setShowsHiddenFiles:"))) {
            uploadPanelHiddenFilesCheckbox = NSButton.buttonWithFrame(new NSRect(0, 0));
            uploadPanelHiddenFilesCheckbox.setTitle(Locale.localizedString("Show Hidden Files"));
            uploadPanelHiddenFilesCheckbox.setTarget(this.id());
            uploadPanelHiddenFilesCheckbox.setAction(Foundation.selector("uploadPanelSetShowHiddenFiles:"));
            uploadPanelHiddenFilesCheckbox.setButtonType(NSButton.NSSwitchButton);
            uploadPanelHiddenFilesCheckbox.setState(NSCell.NSOffState);
            uploadPanelHiddenFilesCheckbox.sizeToFit();
            uploadPanel.setAccessoryView(uploadPanelHiddenFilesCheckbox);
        }
        uploadPanel.beginSheetForDirectory(lastSelectedUploadDirectory, //trying to be smart
                null, this.window,
                this.id(),
                Foundation.selector("uploadPanelDidEnd:returnCode:contextInfo:"),
                null);
    }

    public void uploadPanelSetShowHiddenFiles(ID sender) {
        uploadPanel.setShowsHiddenFiles(uploadPanelHiddenFilesCheckbox.state() == NSCell.NSOnState);
    }

    public void uploadPanelDidEnd_returnCode_contextInfo(NSOpenPanel sheet, int returncode, ID contextInfo) {
        sheet.close();
        if(returncode == SheetCallback.DEFAULT_OPTION) {
            Path destination = getSelectedPath();
            if(null == destination) {
                destination = workdir();
            }
            else if(!destination.attributes().isDirectory()) {
                destination = destination.getParent();
            }
            // selected files on the local filesystem
            NSArray selected = sheet.filenames();
            NSEnumerator iterator = selected.objectEnumerator();
            final Session session = this.getTransferSession();
            final List<Path> roots = new Collection<Path>();
            NSObject next;
            while((next = iterator.nextObject()) != null) {
                roots.add(PathFactory.createPath(session,
                        destination.getAbsolute(), LocalFactory.createLocal(next.toString())));
            }
            transfer(new UploadTransfer(roots));
        }
        lastSelectedUploadDirectory = new File(sheet.filename()).getParent();
        uploadPanel = null;
        uploadPanelHiddenFilesCheckbox = null;
    }

    /**
     * @return The session to be used for file transfers. Null if not mounted
     */
    protected Session getTransferSession() {
        return this.getTransferSession(false);
    }

    /**
     * @param force Force to create a new session and not reuse the browser session
     * @return The session to be used for file transfers. Null if not mounted
     */
    protected Session getTransferSession(boolean force) {
        if(!this.isMounted()) {
            return null;
        }
        if(!force) {
            if(this.session.getMaxConnections() == 1) {
                return this.session;
            }
        }
        return SessionFactory.createSession(session.getHost());
    }

    protected void transfer(final Transfer transfer) {
        this.transfer(transfer, transfer.getRoots());
    }

    /**
     * Transfers the files either using the queue or using the browser session if #connection.pool.max is 1
     *
     * @param transfer Transfer Operation
     */
    protected void transfer(final Transfer transfer, final List<Path> selected) {
        this.transfer(transfer, selected, this.getSession().getMaxConnections() == 1,
                new TransferPrompt() {
                    @Override
                    public TransferAction prompt() {
                        return TransferPromptController.create(BrowserController.this, transfer).prompt();
                    }
                }
        );
    }

    /**
     * @param transfer Transfer Operation
     * @param browser  Transfer in browser window
     */
    protected void transfer(final Transfer transfer, final List<Path> selected, final boolean browser) {
        this.transfer(transfer, selected, browser, new TransferPrompt() {
            @Override
            public TransferAction prompt() {
                return TransferPromptController.create(BrowserController.this, transfer).prompt();
            }
        });
    }

    protected void transfer(final Transfer transfer, final List<Path> selected, boolean browser, final TransferPrompt prompt) {
        if(!selected.isEmpty()) {
            transfer.addListener(new TransferAdapter() {
                @Override
                public void transferDidEnd() {
                    if(!transfer.isCanceled()) {
                        invoke(new WindowMainAction(BrowserController.this) {
                            @Override
                            public void run() {
                                reloadData(selected, selected, true);
                            }

                            @Override
                            public boolean isValid() {
                                return super.isValid() && BrowserController.this.isConnected();
                            }
                        });
                    }
                    transfer.removeListener(this);
                }
            });
        }
        if(browser) {
            transfer.addListener(new TransferAdapter() {
                private TransferSpeedometer meter = new TransferSpeedometer(transfer);

                /**
                 * Timer to update the progress indicator
                 */
                private ScheduledFuture<?> progressTimer;

                @Override
                public void willTransferPath(Path path) {
                    meter.reset();
                    progressTimer = getTimerPool().scheduleAtFixedRate(new Runnable() {
                        @Override
                        public void run() {
                            invoke(new WindowMainAction(BrowserController.this) {
                                @Override
                                public void run() {
                                    BrowserController.this.updateStatusLabel(meter.getProgress());
                                }
                            });
                        }
                    }, 0, 500, TimeUnit.MILLISECONDS);
                }

                @Override
                public void didTransferPath(Path path) {
                    boolean canceled = false;
                    while(!canceled) {
                        canceled = progressTimer.cancel(false);
                    }
                    meter.reset();
                }

                @Override
                public void bandwidthChanged(BandwidthThrottle bandwidth) {
                    meter.reset();
                }

                @Override
                public void transferDidEnd() {
                    transfer.removeListener(this);
                }
            });
            this.background(new BrowserBackgroundAction(this) {
                @Override
                public void run() {
                    TransferOptions options = new TransferOptions();
                    options.closeSession = false;
                    transfer.start(prompt, options);
                }

                @Override
                public void cancel() {
                    transfer.cancel();
                    super.cancel();
                }

                @Override
                public void cleanup() {
                    updateStatusLabel();
                    super.cleanup();
                }

                @Override
                public String getActivity() {
                    return transfer.getName();
                }
            });
        }
        else {
            TransferController.instance().startTransfer(transfer);
        }
    }

    @Action
    public void insideButtonClicked(final ID sender) {
        final Path selected = this.getSelectedPath(); //first row selected
        if(null == selected) {
            return;
        }
        if(selected.attributes().isDirectory()) {
            this.setWorkdir(selected);
        }
        else if(selected.attributes().isFile() || this.getSelectionCount() > 1) {
            if(Preferences.instance().getBoolean("browser.doubleclick.edit")) {
                this.editButtonClicked(null);
            }
            else {
                this.downloadButtonClicked(null);
            }
        }
    }

    @Action
    public void connectButtonClicked(final ID sender) {
        final SheetController controller = ConnectionController.instance(this);
        this.addListener(new WindowListener() {
            @Override
            public void windowWillClose() {
                controller.invalidate();
            }
        });
        controller.beginSheet();
    }

    @Action
    public void interruptButtonClicked(final ID sender) {
        // Remove all pending actions
        for(BackgroundAction action : this.getActions().toArray(
                new BackgroundAction[this.getActions().size()])) {
            action.cancel();
        }
        // Interrupt any pending operation by forcefully closing the socket
        this.interrupt();
    }

    @Action
    public void disconnectButtonClicked(final ID sender) {
        if(this.isActivityRunning()) {
            this.interruptButtonClicked(sender);
        }
        else {
            this.disconnect();
        }
    }

    @Action
    public void showHiddenFilesClicked(final NSMenuItem sender) {
        if(sender.state() == NSCell.NSOnState) {
            this.setShowHiddenFiles(false);
            sender.setState(NSCell.NSOffState);
        }
        else if(sender.state() == NSCell.NSOffState) {
            this.setShowHiddenFiles(true);
            sender.setState(NSCell.NSOnState);
        }
        if(this.isMounted()) {
            this.reloadData(true);
        }
    }

    /**
     * @return true if a connection is being opened or is already initialized
     */
    public boolean hasSession() {
        return this.session != null;
    }

    /**
     * @return This browser's session or null if not mounted
     */
    public Session getSession() {
        return this.session;
    }

    /**
     * @return true if the remote file system has been mounted
     */
    public boolean isMounted() {
        return this.hasSession() && this.workdir() != null;
    }

    /**
     * @return true if mounted and the connection to the server is alive
     */
    public boolean isConnected() {
        if(this.isMounted()) {
            return this.session.isConnected();
        }
        return false;
    }

    /**
     * NSService
     * <p/>
     * Indicates whether the receiver can send and receive the specified pasteboard types.
     * <p/>
     * Either sendType or returnType—but not both—may be empty. If sendType is empty,
     * the service doesn’t require input from the application requesting the service.
     * If returnType is empty, the service doesn’t return data.
     *
     * @param sendType   The pasteboard type the application needs to send.
     * @param returnType The pasteboard type the application needs to receive.
     * @return The object that can send and receive the specified types or nil
     *         if the receiver knows of no object that can send and receive data of that type.
     */
    public ID validRequestorForSendType_returnType(String sendType, String returnType) {
        log.debug("validRequestorForSendType_returnType:" + sendType + "," + returnType);
        if(StringUtils.isNotEmpty(sendType)) {
            // Cannot send any data type
            return null;
        }
        if(StringUtils.isNotEmpty(returnType)) {
            // Can receive filenames
            if(NSPasteboard.FilenamesPboardType.equals(sendType)) {
                return this.id();
            }
        }
        return null;
    }

    /**
     * NSService
     * <p/>
     * Reads data from the pasteboard and uses it to replace the current selection.
     *
     * @param pboard Pasteboard
     * @return YES if your implementation was able to read the pasteboard data successfully; otherwise, NO.
     */
    public boolean readSelectionFromPasteboard(NSPasteboard pboard) {
        return this.upload(pboard);
    }

    /**
     * NSService
     * <p/>
     * Writes the current selection to the pasteboard.
     *
     * @param pboard Pasteboard
     * @param types  Types in pasteboard
     * @return YES if your implementation was able to write one or more types to the pasteboard; otherwise, NO.
     */
    public boolean writeSelectionToPasteboard_types(NSPasteboard pboard, NSArray types) {
        return false;
    }

    @Action
    public void copy(final ID sender) {
        PathPasteboard pasteboard = PathPasteboard.getPasteboard(this.getSession());
        pasteboard.clear();
        pasteboard.setCopy(true);
        final List<Path> s = this.getSelectedPaths();
        for(Path p : s) {
            // Writing data for private use when the item gets dragged to the transfer queue.
            pasteboard.add(p);
        }
        final NSPasteboard clipboard = NSPasteboard.generalPasteboard();
        if(s.size() == 0) {
            s.add(this.workdir());
        }
        clipboard.declareTypes(NSArray.arrayWithObject(
                NSString.stringWithString(NSPasteboard.StringPboardType)), null);
        StringBuilder copy = new StringBuilder();
        for(Iterator<Path> i = s.iterator(); i.hasNext(); ) {
            copy.append(i.next().getAbsolute());
            if(i.hasNext()) {
                copy.append("\n");
            }
        }
        if(!clipboard.setStringForType(copy.toString(), NSPasteboard.StringPboardType)) {
            log.error("Error writing to NSPasteboard.StringPboardType.");
        }
    }

    @Action
    public void cut(final ID sender) {
        PathPasteboard pasteboard = PathPasteboard.getPasteboard(this.getSession());
        pasteboard.clear();
        pasteboard.setCut(true);
        for(Path s : this.getSelectedPaths()) {
            // Writing data for private use when the item gets dragged to the transfer queue.
            pasteboard.add(s);
        }
        final NSPasteboard clipboard = NSPasteboard.generalPasteboard();
        clipboard.declareTypes(NSArray.arrayWithObject(NSString.stringWithString(NSPasteboard.StringPboardType)), null);
        if(!clipboard.setStringForType(this.getSelectedPath().getAbsolute(), NSPasteboard.StringPboardType)) {
            log.error("Error writing to NSPasteboard.StringPboardType.");
        }
    }

    @Action
    public void paste(final ID sender) {
        final PathPasteboard pasteboard = PathPasteboard.getPasteboard(this.getSession());
        if(pasteboard.isEmpty()) {
            NSPasteboard pboard = NSPasteboard.generalPasteboard();
            this.upload(pboard);
        }
        else {
            final Map<Path, Path> files = new HashMap<Path, Path>();
            Path parent = this.workdir();
            if(this.getSelectionCount() == 1) {
                Path selected = this.getSelectedPath();
                if(selected.attributes().isDirectory()) {
                    parent = selected;
                }
                else {
                    parent = selected.getParent();
                }
            }
            for(final Path next : pasteboard) {
                Path current = PathFactory.createPath(this.getSession(),
                        next.getAbsolute(), next.attributes().getType());
                Path renamed = PathFactory.createPath(this.getSession(),
                        parent.getAbsolute(), current.getName(), next.attributes().getType());
                files.put(current, renamed);
            }
            pasteboard.clear();
            if(pasteboard.isCut()) {
                this.renamePaths(files);
            }
            if(pasteboard.isCopy()) {
                this.duplicatePaths(files, true);
            }
        }
    }

    /**
     * @param pboard Pasteboard with filenames
     * @return True if filenames are found in pasteboard and upload has started
     */
    private boolean upload(NSPasteboard pboard) {
        if(!this.isMounted()) {
            return false;
        }
        if(pboard.availableTypeFromArray(NSArray.arrayWithObject(NSPasteboard.FilenamesPboardType)) != null) {
            NSObject o = pboard.propertyListForType(NSPasteboard.FilenamesPboardType);
            if(o != null) {
                final NSArray elements = Rococoa.cast(o, NSArray.class);
                final Path workdir = this.workdir();
                final Session session = this.getTransferSession();
                final List<Path> roots = new Collection<Path>();
                for(int i = 0; i < elements.count().intValue(); i++) {
                    Path p = PathFactory.createPath(session,
                            workdir.getAbsolute(),
                            LocalFactory.createLocal(elements.objectAtIndex(new NSUInteger(i)).toString()));
                    roots.add(p);
                }
                final Transfer t = new UploadTransfer(roots);
                if(t.numberOfRoots() > 0) {
                    this.transfer(t);
                    return true;
                }
            }
        }
        return false;
    }

    @Action
    public void openTerminalButtonClicked(final ID sender) {
        final Host host = this.getSession().getHost();
        final boolean identity = host.getCredentials().isPublicKeyAuthentication();
        String workdir = null;
        if(this.getSelectionCount() == 1) {
            Path selected = this.getSelectedPath();
            if(selected.attributes().isDirectory()) {
                workdir = selected.getAbsolute();
            }
        }
        if(null == workdir) {
            workdir = this.workdir().getAbsolute();
        }
        final String app = NSWorkspace.sharedWorkspace().absolutePathForAppBundleWithIdentifier(
                Preferences.instance().getProperty("terminal.bundle.identifier"));
        if(StringUtils.isEmpty(app)) {
            log.error(String.format("Application with bundle identifier %s is not installed",
                    Preferences.instance().getProperty("terminal.bundle.identifier")));
            return;
        }
        String ssh = MessageFormat.format(Preferences.instance().getProperty("terminal.command.ssh"),
                identity ? "-i " + host.getCredentials().getIdentity().getAbsolute() : StringUtils.EMPTY,
                host.getCredentials().getUsername(),
                host.getHostname(),
                String.valueOf(host.getPort()), workdir);
        log.info("SSH Command:" + ssh);
        // Escape
        ssh = StringUtils.replace(ssh, "\\", "\\\\");
        // Escape all " for do script command
        ssh = StringUtils.replace(ssh, "\"", "\\\"");
        log.info("Escaped SSH Command for Applescript:" + ssh);
        String command
                = "tell application \"" + LocalFactory.createLocal(app).getDisplayName() + "\""
                + "\n"
                + "activate"
                + "\n"
                + MessageFormat.format(Preferences.instance().getProperty("terminal.command"), ssh)
                + "\n"
                + "end tell";
        log.info("Excecuting AppleScript:" + command);
        final NSAppleScript as = NSAppleScript.createWithSource(command);
        as.executeAndReturnError(null);
    }

    @Action
    public void archiveMenuClicked(final NSMenuItem sender) {
        final Archive archive = Archive.forName(sender.representedObject());
        this.archiveClicked(archive);
    }

    @Action
    public void archiveButtonClicked(final NSToolbarItem sender) {
        this.archiveClicked(Archive.TARGZ);
    }

    /**
     * @param archive Archive format
     */
    private void archiveClicked(final Archive archive) {
        final List<Path> changed = this.getSelectedPaths();
        this.checkOverwrite(Collections.singletonList(archive.getArchive(changed)), new DefaultMainAction() {
            @Override
            public void run() {
                background(new BrowserBackgroundAction(BrowserController.this) {
                    @Override
                    public void run() {
                        session.archive(archive, changed);
                    }

                    @Override
                    public void cleanup() {
                        // Update Selection
                        reloadData(changed, Collections.singletonList(archive.getArchive(changed)));
                    }

                    @Override
                    public String getActivity() {
                        return archive.getCompressCommand(changed);
                    }
                });
            }
        });
    }

    @Action
    public void unarchiveButtonClicked(final ID sender) {
        final List<Path> expanded = new ArrayList<Path>();
        final List<Path> selected = this.getSelectedPaths();
        for(final Path s : selected) {
            final Archive archive = Archive.forName(s.getName());
            if(null == archive) {
                continue;
            }
            this.checkOverwrite(archive.getExpanded(Collections.singletonList(s)), new DefaultMainAction() {
                @Override
                public void run() {
                    background(new BrowserBackgroundAction(BrowserController.this) {
                        @Override
                        public void run() {
                            session.unarchive(archive, s);
                        }

                        @Override
                        public void cleanup() {
                            expanded.addAll(archive.getExpanded(Collections.singletonList(s)));
                            // Update Selection
                            reloadData(selected, expanded);
                        }

                        @Override
                        public String getActivity() {
                            return archive.getDecompressCommand(s);
                        }
                    });
                }
            });
        }
    }

    /**
     * @return true if there is any network activity running in the background
     */
    public boolean isActivityRunning() {
        final BackgroundAction current = this.getActions().getCurrent();
        return null != current;
    }

    /**
     * @param reference Reference for path
     * @return Null if not mounted or lookup fails
     */
    public Path lookup(final PathReference reference) {
        if(this.isMounted()) {
            return this.getSession().cache().lookup(reference);
        }
        return null;
    }

    /**
     * Accessor to the working directory
     *
     * @return The current working directory or null if no file system is mounted
     */
    protected Path workdir() {
        return this.workdir;
    }

    public void setWorkdir(final Path directory) {
        this.setWorkdir(directory, Collections.<Path>emptyList());
    }

    public void setWorkdir(final Path directory, Path selected) {
        this.setWorkdir(directory, Collections.singletonList(selected));
    }

    /**
     * Sets the current working directory. This will udpate the path selection dropdown button
     * and also add this path to the browsing history. If the path cannot be a working directory (e.g. permission
     * issues trying to enter the directory), reloading the browser view is canceled and the working directory
     * not changed.
     *
     * @param directory The new working directory to display or null to detach any working directory from the browser
     * @param selected  Selected files in browser
     */
    public void setWorkdir(final Path directory, final List<Path> selected) {
        if(null == directory) {
            // Clear the browser view if no working directory is given
            this.workdir = null;
            this.reloadData(false);
            return;
        }
        if(log.isDebugEnabled()) {
            log.debug(String.format("Set working directory to %s", directory.getAbsolute()));
        }
        final NSTableView browser = this.getSelectedBrowserView();
        window.endEditingFor(browser);
        this.background(new BrowserBackgroundAction(this) {
            @Override
            public String getActivity() {
                return MessageFormat.format(Locale.localizedString("Listing directory {0}", "Status"),
                        directory.getName());
            }

            @Override
            public void run() {
                if(getSession().cache().isCached(directory.getReference())) {
                    // Reset the readable attribute
                    directory.children().attributes().setReadable(true);
                }
                // Get the directory listing in the background
                AttributedList children = directory.children();
                if(children.attributes().isReadable() || !children.isEmpty()) {
                    // Update the working directory if listing is successful
                    workdir = directory;
                    // Update the current working directory
                    addPathToHistory(workdir());
                }
            }

            @Override
            public void cleanup() {
                // Change to last selected browser view
                browserSwitchClicked(Preferences.instance().getInteger("browser.view"), selected);
            }
        });
    }


    /**
     * Keeps a ordered backward history of previously visited paths
     */
    private List<Path> backHistory = new Collection<Path>();

    /**
     * Keeps a ordered forward history of previously visited paths
     */
    private List<Path> forwardHistory = new Collection<Path>();

    /**
     * @param p Directory
     */
    public void addPathToHistory(final Path p) {
        if(backHistory.size() > 0) {
            // Do not add if this was a reload
            if(p.equals(backHistory.get(backHistory.size() - 1))) {
                return;
            }
        }
        backHistory.add(p);
    }

    /**
     * Returns the prevously browsed path and moves it to the forward history
     *
     * @return The previously browsed path or null if there is none
     */
    public Path getPreviousPath() {
        int size = backHistory.size();
        if(size > 1) {
            forwardHistory.add(backHistory.get(size - 1));
            Path p = backHistory.get(size - 2);
            //delete the fetched path - otherwise we produce a loop
            backHistory.remove(size - 1);
            backHistory.remove(size - 2);
            return p;
        }
        else if(1 == size) {
            forwardHistory.add(backHistory.get(size - 1));
            return backHistory.get(size - 1);
        }
        return null;
    }

    /**
     * @return The last path browsed before #getPreviousPath was called
     * @see #getPreviousPath()
     */
    public Path getForwardPath() {
        int size = forwardHistory.size();
        if(size > 0) {
            Path p = forwardHistory.get(size - 1);
            forwardHistory.remove(size - 1);
            return p;
        }
        return null;
    }

    /**
     * @return The ordered array of previously visited directories
     */
    public List<Path> getBackHistory() {
        return backHistory;
    }

    /**
     * Remove all entries from the back path history
     */
    public void clearBackHistory() {
        backHistory.clear();
    }

    /**
     * @return The ordered array of previously visited directories
     */
    public List<Path> getForwardHistory() {
        return forwardHistory;
    }

    /**
     * Remove all entries from the forward path history
     */
    public void clearForwardHistory() {
        forwardHistory.clear();
    }

    /**
     *
     */
    private ConnectionListener listener = null;

    private String laststatus = null;

    /**
     * Initializes a session for the passed host. Setting up the listeners and adding any callback
     * controllers needed for login, trust management and hostkey verification.
     *
     * @param host Bookmark
     * @return A session object bound to this browser controller
     */
    private Session init(final Host host) {
        if(this.hasSession()) {
            PathPasteboard.getPasteboard(session).delete();
            session.removeConnectionListener(listener);
        }
        session = SessionFactory.createSession(host);
        this.setWorkdir(null);
        this.setEncoding(session.getEncoding());
        session.addProgressListener(new ProgressListener() {
            @Override
            public void message(final String message) {
                invoke(new WindowMainAction(BrowserController.this) {
                    @Override
                    public void run() {
                        laststatus = message;
                        updateStatusLabel(message);
                    }
                });
            }
        });
        session.addConnectionListener(listener = new ConnectionAdapter() {
            @Override
            public void connectionWillOpen() {
                invoke(new WindowMainAction(BrowserController.this) {
                    @Override
                    public void run() {
                        // Update status icon
                        bookmarkTable.setNeedsDisplay();
                        window.setTitle(host.getNickname());
                        window.setRepresentedFilename(StringUtils.EMPTY);
                    }
                });
            }

            @Override
            public void connectionDidOpen() {
                invoke(new WindowMainAction(BrowserController.this) {
                    @Override
                    public void run() {
                        // Update status icon
                        bookmarkTable.setNeedsDisplay();

                        Growl.instance().notify("Connection opened", host.getHostname());

                        // Set the window title
                        window.setRepresentedFilename(
                                HistoryCollection.defaultCollection().getFile(host).getAbsolute());

                        if(Preferences.instance().getBoolean("browser.confirmDisconnect")) {
                            window.setDocumentEdited(true);
                        }
                        securityLabel.setImage(session.isSecure() ? IconCache.iconNamed("locked.tiff")
                                : IconCache.iconNamed("unlocked.tiff"));
                        securityLabel.setEnabled(session instanceof SSLSession);
                    }
                });
            }

            @Override
            public void connectionDidClose() {
                invoke(new WindowMainAction(BrowserController.this) {
                    @Override
                    public void run() {
                        // Update status icon
                        bookmarkTable.setNeedsDisplay();

                        if(!isMounted()) {
                            window.setTitle(Preferences.instance().getProperty("application.name"));
                            window.setRepresentedFilename(StringUtils.EMPTY);
                        }
                        window.setDocumentEdited(false);

                        securityLabel.setImage(null);
                        securityLabel.setEnabled(false);

                        updateStatusLabel();
                    }
                });
            }
        });
        transcript.clear();
        backHistory.clear();
        forwardHistory.clear();
        session.addTranscriptListener(new TranscriptListener() {
            @Override
            public void log(final boolean request, final String message) {
                if(logDrawer.state() == NSDrawer.OpenState) {
                    invoke(new WindowMainAction(BrowserController.this) {
                        @Override
                        public void run() {
                            transcript.log(request, message);
                        }
                    });
                }
            }
        });
        return session;
    }

    /**
     *
     */
    private Session session;

    /**
     * Open connection in browser
     *
     * @param host Bookmark
     */
    public void mount(final Host host) {
        if(log.isDebugEnabled()) {
            log.debug("mount:" + host);
        }
        this.unmount(new Runnable() {
            @Override
            public void run() {
                // The browser has no session, we are allowed to proceed
                // Initialize the browser with the new session attaching all listeners
                final Session session = init(host);

                background(new BrowserBackgroundAction(BrowserController.this) {
                    private Path mount;

                    @Override
                    public void run() {
                        // Mount this session
                        mount = session.mount();
                    }

                    @Override
                    public void cleanup() {
                        browserListModel.clear();
                        browserOutlineModel.clear();
                        if(session.isConnected()) {
                            // Set the working directory
                            setWorkdir(mount);
                        }
                        else {
                            // Connection attempt failed
                            log.warn("Mount failed:" + host);
                        }
                    }

                    @Override
                    public String getActivity() {
                        return MessageFormat.format(Locale.localizedString("Mounting {0}", "Status"),
                                host.getHostname());
                    }
                });
            }
        });
    }

    /**
     * Close connection
     *
     * @return True if succeeded
     */
    public boolean unmount() {
        return this.unmount(new Runnable() {
            @Override
            public void run() {
                //
            }
        });
    }

    /**
     * @param disconnected Callback after the session has been disconnected
     * @return True if the unmount process has finished, false if the user has to agree first
     *         to close the connection
     */
    public boolean unmount(final Runnable disconnected) {
        return this.unmount(new SheetCallback() {
            @Override
            public void callback(int returncode) {
                if(returncode == DEFAULT_OPTION) {
                    unmountImpl(disconnected);
                }
            }
        }, disconnected);
    }

    /**
     * @param callback     Confirmation callback
     * @param disconnected Action to run after disconnected
     * @return True if succeeded
     */
    public boolean unmount(final SheetCallback callback, final Runnable disconnected) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Unmount session %s", session));
        }
        if(this.isConnected() || this.isActivityRunning()) {
            if(Preferences.instance().getBoolean("browser.confirmDisconnect")) {
                // Defer the unmount to the callback function
                final NSAlert alert = NSAlert.alert(
                        MessageFormat.format(Locale.localizedString("Disconnect from {0}"), this.session.getHost().getHostname()), //title
                        Locale.localizedString("The connection will be closed."), // message
                        Locale.localizedString("Disconnect"), // defaultbutton
                        Locale.localizedString("Cancel"), // alternate button
                        null //other button
                );
                alert.setShowsSuppressionButton(true);
                alert.suppressionButton().setTitle(Locale.localizedString("Don't ask again", "Configuration"));
                this.alert(alert, new SheetCallback() {
                    @Override
                    public void callback(int returncode) {
                        if(alert.suppressionButton().state() == NSCell.NSOnState) {
                            // Never show again.
                            Preferences.instance().setProperty("browser.confirmDisconnect", false);
                        }
                        callback.callback(returncode);
                    }
                });
                // No unmount yet
                return false;
            }
            this.unmountImpl(disconnected);
            // Unmount in progress
            return true;
        }
        disconnected.run();
        // Unmount succeeded
        return true;
    }

    /**
     * @param disconnected Action to run after disconnected
     */
    private void unmountImpl(final Runnable disconnected) {
        if(this.isActivityRunning()) {
            this.interrupt();
        }
        final Session session = this.getSession();
        this.background(new AbstractBackgroundAction<Void>() {
            @Override
            public void run() {
                session.close();
            }

            @Override
            public void cleanup() {
                // Clear the cache on the main thread to make sure the browser model is not in an invalid state
                session.cache().clear();
                session.getHost().getCredentials().setPassword(null);

                disconnected.run();
            }

            @Override
            public String getActivity() {
                return MessageFormat.format(Locale.localizedString("Disconnecting {0}", "Status"),
                        session.getHost().getHostname());
            }
        });
    }

    /**
     * Interrupt any operation in progress;
     * just closes the socket without any quit message sent to the server
     */
    private void interrupt() {
        if(this.hasSession()) {
            if(this.isActivityRunning()) {
                final BackgroundAction current = this.getActions().getCurrent();
                if(null != current) {
                    current.cancel();
                }
            }
            this.background(new BrowserBackgroundAction(this) {
                @Override
                public void run() {
                    if(hasSession()) {
                        // Aggressively close the connection to interrupt the current task
                        session.interrupt();
                    }
                }

                @Override
                public String getActivity() {
                    return MessageFormat.format(Locale.localizedString("Disconnecting {0}", "Status"),
                            session.getHost().getHostname());
                }

                @Override
                public int retry() {
                    return 0;
                }

                private final Object lock = new Object();

                @Override
                public Object lock() {
                    // No synchronization with other tasks
                    return lock;
                }
            });
        }
    }

    /**
     * Unmount this session
     */
    private void disconnect() {
        this.background(new BrowserBackgroundAction(this) {
            @Override
            public void run() {
                session.close();
            }

            @Override
            public void cleanup() {
                if(Preferences.instance().getBoolean("browser.disconnect.showBookmarks")) {
                    BrowserController.this.toggleBookmarks(true);
                }
            }

            @Override
            public String getActivity() {
                return MessageFormat.format(Locale.localizedString("Disconnecting {0}", "Status"),
                        session.getHost().getHostname());
            }
        });
    }

    @Action
    public void printDocument(final ID sender) {
        this.print(this.getSelectedBrowserView());
    }

    /**
     * @param app Singleton
     * @return NSApplication.TerminateLater if the application should not yet be terminated
     */
    public static NSUInteger applicationShouldTerminate(final NSApplication app) {
        // Determine if there are any open connections
        for(final BrowserController controller : MainController.getBrowsers()) {
            if(!controller.unmount(new SheetCallback() {
                                       @Override
                                       public void callback(final int returncode) {
                                           if(returncode == DEFAULT_OPTION) { //Disconnect
                                               controller.window().close();
                                               if(NSApplication.NSTerminateNow.equals(BrowserController.applicationShouldTerminate(app))) {
                                                   app.terminate(null);
                                               }
                                           }
                                       }
                                   }, new Runnable() {
                                       @Override
                                       public void run() {
                                           //
                                       }
                                   }
            )) {
                return NSApplication.NSTerminateCancel;
            }
        }
        return NSApplication.NSTerminateNow;
    }

    @Override
    public boolean windowShouldClose(final NSWindow sender) {
        return this.unmount(new Runnable() {
            @Override
            public void run() {
                sender.close();
            }
        });
    }

    /**
     * @param item Menu item
     * @return True if the menu should be enabled
     */
    public boolean validateMenuItem(NSMenuItem item) {
        final Selector action = item.action();
        if(action.equals(Foundation.selector("paste:"))) {
            final String title = "Paste {0}";
            item.setTitle(MessageFormat.format(Locale.localizedString(title), StringUtils.EMPTY).trim());
            if(this.isMounted()) {
                final PathPasteboard pasteboard = PathPasteboard.getPasteboard(this.getSession());
                if(pasteboard.isEmpty()) {
                    if(NSPasteboard.generalPasteboard().availableTypeFromArray(NSArray.arrayWithObject(NSPasteboard.FilenamesPboardType)) != null) {
                        NSObject o = NSPasteboard.generalPasteboard().propertyListForType(NSPasteboard.FilenamesPboardType);
                        if(o != null) {
                            final NSArray elements = Rococoa.cast(o, NSArray.class);
                            if(elements.count().intValue() == 1) {
                                item.setTitle(MessageFormat.format(Locale.localizedString(title),
                                        "\"" + elements.objectAtIndex(new NSUInteger(0)) + "\"").trim());
                            }
                            else {
                                item.setTitle(MessageFormat.format(Locale.localizedString(title),
                                        MessageFormat.format(Locale.localizedString("{0} Files"),
                                                String.valueOf(elements.count().intValue()))).trim());
                            }
                        }
                    }
                }
                else {
                    if(pasteboard.size() == 1) {
                        item.setTitle(MessageFormat.format(Locale.localizedString(title),
                                "\"" + pasteboard.get(0).getName() + "\"").trim());
                    }
                    else {
                        item.setTitle(MessageFormat.format(Locale.localizedString(title),
                                MessageFormat.format(Locale.localizedString("{0} Files"), String.valueOf(pasteboard.size()))).trim());
                    }
                }
            }
        }
        else if(action.equals(Foundation.selector("cut:")) || action.equals(Foundation.selector("copy:"))) {
            String title = null;
            if(action.equals(Foundation.selector("cut:"))) {
                title = "Cut {0}";
            }
            else if(action.equals(Foundation.selector("copy:"))) {
                title = "Copy {0}";
            }
            if(this.isMounted()) {
                int count = this.getSelectionCount();
                if(0 == count) {
                    item.setTitle(MessageFormat.format(Locale.localizedString(title), StringUtils.EMPTY).trim());
                }
                else if(1 == count) {
                    item.setTitle(MessageFormat.format(Locale.localizedString(title),
                            "\"" + this.getSelectedPath().getName() + "\"").trim());
                }
                else {
                    item.setTitle(MessageFormat.format(Locale.localizedString(title),
                            MessageFormat.format(Locale.localizedString("{0} Files"), String.valueOf(this.getSelectionCount()))).trim());
                }
            }
            else {
                item.setTitle(MessageFormat.format(Locale.localizedString(title), StringUtils.EMPTY).trim());
            }
        }
        else if(action.equals(Foundation.selector("showHiddenFilesClicked:"))) {
            item.setState(this.getFileFilter() instanceof NullPathFilter ? NSCell.NSOnState : NSCell.NSOffState);
        }
        else if(action.equals(Foundation.selector("encodingMenuClicked:"))) {
            if(this.isMounted()) {
                item.setState(this.session.getEncoding().equalsIgnoreCase(
                        item.title()) ? NSCell.NSOnState : NSCell.NSOffState);
            }
            else {
                item.setState(Preferences.instance().getProperty("browser.charset.encoding").equalsIgnoreCase(
                        item.title()) ? NSCell.NSOnState : NSCell.NSOffState);
            }
        }
        else if(action.equals(Foundation.selector("browserSwitchMenuClicked:"))) {
            if(item.tag() == Preferences.instance().getInteger("browser.view")) {
                item.setState(NSCell.NSOnState);
            }
            else {
                item.setState(NSCell.NSOffState);
            }
        }
        else if(action.equals(Foundation.selector("archiveMenuClicked:"))) {
            final Archive archive = Archive.forName(item.representedObject());
            item.setTitle(archive.getTitle(this.getSelectedPaths()));
        }
        else if(action.equals(Foundation.selector("quicklookButtonClicked:"))) {
            item.setKeyEquivalent(" ");
            item.setKeyEquivalentModifierMask(0);
        }
        return this.validateItem(action);
    }

    /**
     * @return Browser tab active
     */
    private boolean isBrowser() {
        return this.getSelectedTabView() == TAB_LIST_VIEW
                || this.getSelectedTabView() == TAB_OUTLINE_VIEW;
    }

    /**
     * @return Bookmarks tab active
     */
    private boolean isBookmarks() {
        return this.getSelectedTabView() == TAB_BOOKMARKS;
    }

    /**
     * @param action the method selector
     * @return true if the item by that identifier should be enabled
     */
    private boolean validateItem(final Selector action) {
        if(action.equals(Foundation.selector("cut:"))) {
            return this.isBrowser() && this.isMounted() && this.getSelectionCount() > 0;
        }
        else if(action.equals(Foundation.selector("copy:"))) {
            return this.isBrowser() && this.isMounted() && this.getSelectionCount() > 0;
        }
        else if(action.equals(Foundation.selector("paste:"))) {
            if(this.isBrowser() && this.isMounted()) {
                PathPasteboard pasteboard = PathPasteboard.getPasteboard(this.getSession());
                if(pasteboard.isEmpty()) {
                    NSPasteboard pboard = NSPasteboard.generalPasteboard();
                    if(pboard.availableTypeFromArray(NSArray.arrayWithObject(NSPasteboard.FilenamesPboardType)) != null) {
                        Object o = pboard.propertyListForType(NSPasteboard.FilenamesPboardType);
                        if(o != null) {
                            return true;
                        }
                    }
                    return false;
                }
                return true;
            }
            return false;
        }
        else if(action.equals(Foundation.selector("encodingMenuClicked:"))) {
            return this.isBrowser() && !isActivityRunning();
        }
        else if(action.equals(Foundation.selector("connectBookmarkButtonClicked:"))) {
            if(this.isBookmarks()) {
                return bookmarkTable.numberOfSelectedRows().intValue() == 1;
            }
            return false;
        }
        else if(action.equals(Foundation.selector("addBookmarkButtonClicked:"))) {
            if(this.isBookmarks()) {
                return bookmarkModel.getSource().allowsAdd();
            }
            return true;
        }
        else if(action.equals(Foundation.selector("deleteBookmarkButtonClicked:"))) {
            if(this.isBookmarks()) {
                return bookmarkModel.getSource().allowsDelete() && bookmarkTable.selectedRow().intValue() != -1;
            }
            return false;
        }
        else if(action.equals(Foundation.selector("duplicateBookmarkButtonClicked:"))) {
            if(this.isBookmarks()) {
                return bookmarkModel.getSource().allowsEdit() && bookmarkTable.numberOfSelectedRows().intValue() == 1;
            }
            return false;
        }
        else if(action.equals(Foundation.selector("editBookmarkButtonClicked:"))) {
            if(this.isBookmarks()) {
                return bookmarkModel.getSource().allowsEdit() && bookmarkTable.numberOfSelectedRows().intValue() == 1;
            }
            return false;
        }
        else if(action.equals(Foundation.selector("editButtonClicked:"))) {
            if(this.isBrowser() && this.isMounted() && this.getSelectionCount() > 0) {
                for(Path s : this.getSelectedPaths()) {
                    if(!this.isEditable(s)) {
                        return false;
                    }
                    // Choose editor for selected file
                    if(null == EditorFactory.instance().getEditor(s.getName())) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
        else if(action.equals(Foundation.selector("editMenuClicked:"))) {
            if(this.isBrowser() && this.isMounted() && this.getSelectionCount() > 0) {
                for(Path s : this.getSelectedPaths()) {
                    if(!this.isEditable(s)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
        else if(action.equals(Foundation.selector("searchButtonClicked:"))) {
            return this.isMounted() || this.isBookmarks();
        }
        else if(action.equals(Foundation.selector("quicklookButtonClicked:"))) {
            return this.isBrowser() && this.isMounted() && quicklook.isAvailable() && this.getSelectionCount() > 0;
        }
        else if(action.equals(Foundation.selector("openBrowserButtonClicked:"))) {
            return this.isMounted();
        }
        else if(action.equals(Foundation.selector("sendCustomCommandClicked:"))) {
            return this.isBrowser() && this.isMounted() && this.getSession().isSendCommandSupported();
        }
        else if(action.equals(Foundation.selector("gotoButtonClicked:"))) {
            return this.isBrowser() && this.isMounted();
        }
        else if(action.equals(Foundation.selector("infoButtonClicked:"))) {
            return this.isBrowser() && this.isMounted() && this.getSelectionCount() > 0;
        }
        else if(action.equals(Foundation.selector("createFolderButtonClicked:"))) {
            return this.isBrowser() && this.isMounted() && this.getSession().isCreateFolderSupported(this.workdir());
        }
        else if(action.equals(Foundation.selector("createFileButtonClicked:"))) {
            return this.isBrowser() && this.isMounted() && this.getSession().isCreateFileSupported(this.workdir());
        }
        else if(action.equals(Foundation.selector("createSymlinkButtonClicked:"))) {
            return this.isBrowser() && this.isMounted() && this.getSession().isCreateSymlinkSupported() && this.getSelectionCount() == 1;
        }
        else if(action.equals(Foundation.selector("duplicateFileButtonClicked:"))) {
            return this.isBrowser() && this.isMounted() && this.getSelectionCount() == 1;
        }
        else if(action.equals(Foundation.selector("renameFileButtonClicked:"))) {
            if(this.isBrowser() && this.isMounted() && this.getSelectionCount() == 1) {
                final Path selected = this.getSelectedPath();
                if(null == selected) {
                    return false;
                }
                return getSession().isRenameSupported(selected);
            }
            return false;
        }
        else if(action.equals(Foundation.selector("deleteFileButtonClicked:"))) {
            return this.isBrowser() && this.isMounted() && this.getSelectionCount() > 0;
        }
        else if(action.equals(Foundation.selector("revertFileButtonClicked:"))) {
            if(this.isBrowser() && this.isMounted() && this.getSelectionCount() == 1) {
                return this.getSession().isRevertSupported();
            }
            return false;
        }
        else if(action.equals(Foundation.selector("reloadButtonClicked:"))) {
            return this.isBrowser() && this.isMounted();
        }
        else if(action.equals(Foundation.selector("newBrowserButtonClicked:"))) {
            return this.isMounted();
        }
        else if(action.equals(Foundation.selector("uploadButtonClicked:"))) {
            return this.isBrowser() && this.isMounted();
        }
        else if(action.equals(Foundation.selector("syncButtonClicked:"))) {
            return this.isBrowser() && this.isMounted();
        }
        else if(action.equals(Foundation.selector("downloadAsButtonClicked:"))) {
            return this.isBrowser() && this.isMounted() && this.getSelectionCount() == 1;
        }
        else if(action.equals(Foundation.selector("downloadToButtonClicked:")) || action.equals(Foundation.selector("downloadButtonClicked:"))) {
            return this.isBrowser() && this.isMounted() && this.getSelectionCount() > 0;
        }
        else if(action.equals(Foundation.selector("insideButtonClicked:"))) {
            return this.isBrowser() && this.isMounted() && this.getSelectionCount() > 0;
        }
        else if(action.equals(Foundation.selector("upButtonClicked:"))) {
            return this.isBrowser() && this.isMounted() && !this.workdir().isRoot();
        }
        else if(action.equals(Foundation.selector("backButtonClicked:"))) {
            return this.isBrowser() && this.isMounted() && this.getBackHistory().size() > 1;
        }
        else if(action.equals(Foundation.selector("forwardButtonClicked:"))) {
            return this.isBrowser() && this.isMounted() && this.getForwardHistory().size() > 0;
        }
        else if(action.equals(Foundation.selector("printDocument:"))) {
            return this.isBrowser() && this.isMounted();
        }
        else if(action.equals(Foundation.selector("disconnectButtonClicked:"))) {
            if(this.isBrowser()) {
                if(!this.isConnected()) {
                    return this.isActivityRunning();
                }
                return this.isConnected();
            }
        }
        else if(action.equals(Foundation.selector("interruptButtonClicked:"))) {
            return this.isBrowser() && this.isActivityRunning();
        }
        else if(action.equals(Foundation.selector("gotofolderButtonClicked:"))) {
            return this.isBrowser() && this.isMounted();
        }
        else if(action.equals(Foundation.selector("openTerminalButtonClicked:"))) {
            return this.isBrowser() && this.isMounted() && this.getSession() instanceof SFTPSession;
        }
        else if(action.equals(Foundation.selector("archiveButtonClicked:")) || action.equals(Foundation.selector("archiveMenuClicked:"))) {
            if(this.isBrowser() && this.isMounted()) {
                if(!this.getSession().isArchiveSupported()) {
                    return false;
                }
                if(this.getSelectionCount() > 0) {
                    for(Path s : this.getSelectedPaths()) {
                        if(s.attributes().isFile() && Archive.isArchive(s.getName())) {
                            // At least one file selected is already an archive. No distinct action possible
                            return false;
                        }
                    }
                    return true;
                }
            }
            return false;
        }
        else if(action.equals(Foundation.selector("unarchiveButtonClicked:"))) {
            if(this.isBrowser() && this.isMounted()) {
                if(!this.getSession().isUnarchiveSupported()) {
                    return false;
                }
                if(this.getSelectionCount() > 0) {
                    for(Path s : this.getSelectedPaths()) {
                        if(s.attributes().isDirectory()) {
                            return false;
                        }
                        if(!Archive.isArchive(s.getName())) {
                            return false;
                        }
                    }
                    return true;
                }
            }
            return false;
        }
        return true; // by default everything is enabled
    }

    private static final String TOOLBAR_NEW_CONNECTION = "New Connection";
    private static final String TOOLBAR_BROWSER_VIEW = "Browser View";
    private static final String TOOLBAR_TRANSFERS = "Transfers";
    private static final String TOOLBAR_QUICK_CONNECT = "Quick Connect";
    private static final String TOOLBAR_TOOLS = "Tools";
    private static final String TOOLBAR_REFRESH = "Refresh";
    private static final String TOOLBAR_ENCODING = "Encoding";
    private static final String TOOLBAR_SYNCHRONIZE = "Synchronize";
    private static final String TOOLBAR_DOWNLOAD = "Download";
    private static final String TOOLBAR_UPLOAD = "Upload";
    private static final String TOOLBAR_EDIT = "Edit";
    private static final String TOOLBAR_DELETE = "Delete";
    private static final String TOOLBAR_NEW_FOLDER = "New Folder";
    private static final String TOOLBAR_NEW_BOOKMARK = "New Bookmark";
    private static final String TOOLBAR_GET_INFO = "Get Info";
    private static final String TOOLBAR_WEBVIEW = "Open";
    private static final String TOOLBAR_DISCONNECT = "Disconnect";
    private static final String TOOLBAR_TERMINAL = "Terminal";
    private static final String TOOLBAR_ARCHIVE = "Archive";
    private static final String TOOLBAR_QUICKLOOK = "Quick Look";
    private static final String TOOLBAR_LOG = "Log";

    @Override
    public boolean validateToolbarItem(final NSToolbarItem item) {
        final String identifier = item.itemIdentifier();
        if(identifier.equals(TOOLBAR_EDIT)) {
            Application editor = null;
            final Path selected = this.getSelectedPath();
            if(null != selected) {
                if(this.isEditable(selected)) {
                    // Choose editor for selected file
                    editor = EditorFactory.instance().getEditor(selected.getName());
                }
            }
            if(null == editor) {
                // No editor found
                item.setImage(IconCache.iconNamed("pencil.tiff", 32));
            }
            else {
                item.setImage(IconCache.instance().iconForApplication(editor.getIdentifier(), 32));
            }
        }
        else if(identifier.equals(TOOLBAR_DISCONNECT)) {
            if(isActivityRunning()) {
                item.setLabel(Locale.localizedString("Stop"));
                item.setPaletteLabel(Locale.localizedString("Stop"));
                item.setToolTip(Locale.localizedString("Cancel current operation in progress"));
                item.setImage(IconCache.iconNamed("stop", 32));
            }
            else {
                item.setLabel(Locale.localizedString(TOOLBAR_DISCONNECT));
                item.setPaletteLabel(Locale.localizedString(TOOLBAR_DISCONNECT));
                item.setToolTip(Locale.localizedString("Disconnect from server"));
                item.setImage(IconCache.iconNamed("eject.tiff", 32));
            }
        }
        else if(identifier.equals(TOOLBAR_ARCHIVE)) {
            final Path selected = getSelectedPath();
            if(null != selected) {
                if(Archive.isArchive(selected.getName())) {
                    item.setLabel(Locale.localizedString("Unarchive", "Archive"));
                    item.setPaletteLabel(Locale.localizedString("Unarchive"));
                    item.setAction(Foundation.selector("unarchiveButtonClicked:"));
                }
                else {
                    item.setLabel(Locale.localizedString("Archive", "Archive"));
                    item.setPaletteLabel(Locale.localizedString("Archive"));
                    item.setAction(Foundation.selector("archiveButtonClicked:"));
                }
            }
        }
        return validateItem(item.action());
    }

    /**
     * Keep reference to weak toolbar items. A toolbar may ask again for a kind of toolbar
     * item already supplied to it, in which case this method may return the same toolbar
     * item it returned before
     */
    private Map<String, NSToolbarItem> toolbarItems
            = new HashMap<String, NSToolbarItem>();

    @Override
    public NSToolbarItem toolbar_itemForItemIdentifier_willBeInsertedIntoToolbar(NSToolbar toolbar, final String itemIdentifier, boolean inserted) {
        if(log.isDebugEnabled()) {
            log.debug("toolbar_itemForItemIdentifier_willBeInsertedIntoToolbar:" + itemIdentifier);
        }
        if(!toolbarItems.containsKey(itemIdentifier)) {
            toolbarItems.put(itemIdentifier, NSToolbarItem.itemWithIdentifier(itemIdentifier));
        }
        final NSToolbarItem item = toolbarItems.get(itemIdentifier);
        if(itemIdentifier.equals(TOOLBAR_BROWSER_VIEW)) {
            item.setLabel(Locale.localizedString("View"));
            item.setPaletteLabel(Locale.localizedString("View"));
            item.setToolTip(Locale.localizedString("Switch Browser View"));
            item.setView(browserSwitchView);
            // Add a menu representation for text mode of toolbar
            NSMenuItem viewMenu = NSMenuItem.itemWithTitle(Locale.localizedString("View"), null, StringUtils.EMPTY);
            NSMenu viewSubmenu = NSMenu.menu();
            viewSubmenu.addItemWithTitle_action_keyEquivalent(Locale.localizedString("List"),
                    Foundation.selector("browserSwitchMenuClicked:"), StringUtils.EMPTY);
            viewSubmenu.itemWithTitle(Locale.localizedString("List")).setTag(0);
            viewSubmenu.addItemWithTitle_action_keyEquivalent(Locale.localizedString("Outline"),
                    Foundation.selector("browserSwitchMenuClicked:"), StringUtils.EMPTY);
            viewSubmenu.itemWithTitle(Locale.localizedString("Outline")).setTag(1);
            viewMenu.setSubmenu(viewSubmenu);
            item.setMenuFormRepresentation(viewMenu);
            item.setMinSize(this.browserSwitchView.frame().size);
            item.setMaxSize(this.browserSwitchView.frame().size);
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_NEW_CONNECTION)) {
            item.setLabel(Locale.localizedString(TOOLBAR_NEW_CONNECTION));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_NEW_CONNECTION));
            item.setToolTip(Locale.localizedString("Connect to server"));
            item.setImage(IconCache.iconNamed("connect.tiff", 32));
            item.setTarget(this.id());
            item.setAction(Foundation.selector("connectButtonClicked:"));
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_TRANSFERS)) {
            item.setLabel(Locale.localizedString(TOOLBAR_TRANSFERS));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_TRANSFERS));
            item.setToolTip(Locale.localizedString("Show Transfers window"));
            item.setImage(IconCache.iconNamed("queue.tiff", 32));
            item.setAction(Foundation.selector("showTransferQueueClicked:"));
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_TOOLS)) {
            item.setLabel(Locale.localizedString("Action"));
            item.setPaletteLabel(Locale.localizedString("Action"));
            if(inserted || !Factory.VERSION_PLATFORM.matches("10\\.5.*")) {
                item.setView(this.actionPopupButton);
                // Add a menu representation for text mode of toolbar
                NSMenuItem toolMenu = NSMenuItem.itemWithTitle(Locale.localizedString("Action"), null, StringUtils.EMPTY);
                NSMenu toolSubmenu = NSMenu.menu();
                for(int i = 1; i < this.actionPopupButton.menu().numberOfItems().intValue(); i++) {
                    NSMenuItem template = this.actionPopupButton.menu().itemAtIndex(new NSInteger(i));
                    toolSubmenu.addItem(NSMenuItem.itemWithTitle(template.title(),
                            template.action(),
                            template.keyEquivalent()));
                }
                toolMenu.setSubmenu(toolSubmenu);
                item.setMenuFormRepresentation(toolMenu);
                item.setMinSize(this.actionPopupButton.frame().size);
                item.setMaxSize(this.actionPopupButton.frame().size);
            }
            else {
                NSToolbarItem temporary = NSToolbarItem.itemWithIdentifier(itemIdentifier);
                temporary.setPaletteLabel(Locale.localizedString("Action"));
                temporary.setImage(IconCache.iconNamed("advanced.tiff", 32));
                return temporary;
            }
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_QUICK_CONNECT)) {
            item.setLabel(Locale.localizedString(TOOLBAR_QUICK_CONNECT));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_QUICK_CONNECT));
            item.setToolTip(Locale.localizedString("Connect to server"));
            item.setView(quickConnectPopup);
            item.setMinSize(this.quickConnectPopup.frame().size);
            item.setMaxSize(this.quickConnectPopup.frame().size);
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_ENCODING)) {
            item.setLabel(Locale.localizedString(TOOLBAR_ENCODING));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_ENCODING));
            item.setToolTip(Locale.localizedString("Character Encoding"));
            item.setView(this.encodingPopup);
            // Add a menu representation for text mode of toolbar
            NSMenuItem encodingMenu = NSMenuItem.itemWithTitle(Locale.localizedString(TOOLBAR_ENCODING),
                    Foundation.selector("encodingMenuClicked:"), StringUtils.EMPTY);
            String[] charsets = MainController.availableCharsets();
            NSMenu charsetMenu = NSMenu.menu();
            for(String charset : charsets) {
                charsetMenu.addItemWithTitle_action_keyEquivalent(charset, Foundation.selector("encodingMenuClicked:"), StringUtils.EMPTY);
            }
            encodingMenu.setSubmenu(charsetMenu);
            item.setMenuFormRepresentation(encodingMenu);
            item.setMinSize(this.encodingPopup.frame().size);
            item.setMaxSize(this.encodingPopup.frame().size);
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_REFRESH)) {
            item.setLabel(Locale.localizedString(TOOLBAR_REFRESH));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_REFRESH));
            item.setToolTip(Locale.localizedString("Refresh directory listing"));
            item.setImage(IconCache.iconNamed("reload.tiff", 32));
            item.setTarget(this.id());
            item.setAction(Foundation.selector("reloadButtonClicked:"));
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_DOWNLOAD)) {
            item.setLabel(Locale.localizedString(TOOLBAR_DOWNLOAD));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_DOWNLOAD));
            item.setToolTip(Locale.localizedString("Download file"));
            item.setImage(IconCache.iconNamed("download.tiff", 32));
            item.setTarget(this.id());
            item.setAction(Foundation.selector("downloadButtonClicked:"));
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_UPLOAD)) {
            item.setLabel(Locale.localizedString(TOOLBAR_UPLOAD));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_UPLOAD));
            item.setToolTip(Locale.localizedString("Upload local file to the remote host"));
            item.setImage(IconCache.iconNamed("upload.tiff", 32));
            item.setTarget(this.id());
            item.setAction(Foundation.selector("uploadButtonClicked:"));
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_SYNCHRONIZE)) {
            item.setLabel(Locale.localizedString(TOOLBAR_SYNCHRONIZE));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_SYNCHRONIZE));
            item.setToolTip(Locale.localizedString("Synchronize files"));
            item.setImage(IconCache.iconNamed("sync.tiff", 32));
            item.setTarget(this.id());
            item.setAction(Foundation.selector("syncButtonClicked:"));
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_GET_INFO)) {
            item.setLabel(Locale.localizedString(TOOLBAR_GET_INFO));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_GET_INFO));
            item.setToolTip(Locale.localizedString("Show file attributes"));
            item.setImage(IconCache.iconNamed("info.tiff", 32));
            item.setTarget(this.id());
            item.setAction(Foundation.selector("infoButtonClicked:"));
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_WEBVIEW)) {
            item.setLabel(Locale.localizedString(TOOLBAR_WEBVIEW));
            item.setPaletteLabel(Locale.localizedString("Open in Web Browser"));
            item.setToolTip(Locale.localizedString("Open in Web Browser"));
            final Application browser = SchemeHandlerFactory.get().getDefaultHandler(Scheme.http);
            if(null == browser) {
                item.setEnabled(false);
                item.setImage(IconCache.iconNamed("notfound.tiff", 32));
            }
            else {
                item.setImage(IconCache.instance().iconForApplication(browser.getIdentifier(), 32));
            }
            item.setTarget(this.id());
            item.setAction(Foundation.selector("openBrowserButtonClicked:"));
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_EDIT)) {
            item.setLabel(Locale.localizedString(TOOLBAR_EDIT));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_EDIT));
            item.setToolTip(Locale.localizedString("Edit file in external editor"));
            item.setImage(IconCache.iconNamed("pencil.tiff"));
            item.setTarget(this.id());
            item.setAction(Foundation.selector("editButtonClicked:"));
            // Add a menu representation for text mode of toolbar
            NSMenuItem toolbarMenu = NSMenuItem.itemWithTitle(Locale.localizedString(TOOLBAR_EDIT),
                    Foundation.selector("editButtonClicked:"), StringUtils.EMPTY);
            NSMenu editMenu = NSMenu.menu();
            editMenu.setAutoenablesItems(true);
            editMenu.setDelegate(editMenuDelegate.id());
            toolbarMenu.setSubmenu(editMenu);
            item.setMenuFormRepresentation(toolbarMenu);
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_DELETE)) {
            item.setLabel(Locale.localizedString(TOOLBAR_DELETE));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_DELETE));
            item.setToolTip(Locale.localizedString("Delete file"));
            item.setImage(IconCache.iconNamed("delete.tiff", 32));
            item.setTarget(this.id());
            item.setAction(Foundation.selector("deleteFileButtonClicked:"));
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_NEW_FOLDER)) {
            item.setLabel(Locale.localizedString(TOOLBAR_NEW_FOLDER));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_NEW_FOLDER));
            item.setToolTip(Locale.localizedString("Create New Folder"));
            item.setImage(IconCache.iconNamed("newfolder.tiff", 32));
            item.setTarget(this.id());
            item.setAction(Foundation.selector("createFolderButtonClicked:"));
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_NEW_BOOKMARK)) {
            item.setLabel(Locale.localizedString(TOOLBAR_NEW_BOOKMARK));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_NEW_BOOKMARK));
            item.setToolTip(Locale.localizedString("New Bookmark"));
            item.setImage(IconCache.iconNamed("bookmark", 32));
            item.setTarget(this.id());
            item.setAction(Foundation.selector("addBookmarkButtonClicked:"));
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_DISCONNECT)) {
            item.setLabel(Locale.localizedString(TOOLBAR_DISCONNECT));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_DISCONNECT));
            item.setToolTip(Locale.localizedString("Disconnect from server"));
            item.setImage(IconCache.iconNamed("eject.tiff", 32));
            item.setTarget(this.id());
            item.setAction(Foundation.selector("disconnectButtonClicked:"));
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_TERMINAL)) {
            String app = NSWorkspace.sharedWorkspace().absolutePathForAppBundleWithIdentifier(
                    Preferences.instance().getProperty("terminal.bundle.identifier"));
            if(StringUtils.isEmpty(app)) {
                log.error(String.format("Application with bundle identifier %s is not installed",
                        Preferences.instance().getProperty("terminal.bundle.identifier")));
            }
            else {
                final Local terminal = LocalFactory.createLocal(app);
                item.setLabel(terminal.getDisplayName());
                item.setPaletteLabel(terminal.getDisplayName());
                item.setImage(IconCache.instance().iconForPath(terminal, 32));
            }
            item.setTarget(this.id());
            item.setAction(Foundation.selector("openTerminalButtonClicked:"));
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_ARCHIVE)) {
            item.setLabel(Locale.localizedString("Archive", "Archive"));
            item.setPaletteLabel(Locale.localizedString("Archive", "Archive"));
            item.setImage(IconCache.instance().iconForApplication("com.apple.archiveutility", 32));
            item.setTarget(this.id());
            item.setAction(Foundation.selector("archiveButtonClicked:"));
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_QUICKLOOK)) {
            item.setLabel(Locale.localizedString(TOOLBAR_QUICKLOOK));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_QUICKLOOK));
            if(quicklook.isAvailable()) {
                quicklookButton = NSButton.buttonWithFrame(new NSRect(29, 23));
                quicklookButton.setBezelStyle(NSButtonCell.NSTexturedRoundedBezelStyle);
                quicklookButton.setImage(IconCache.iconNamed("NSQuickLookTemplate"));
                quicklookButton.sizeToFit();
                quicklookButton.setTarget(this.id());
                quicklookButton.setAction(Foundation.selector("quicklookButtonClicked:"));
                item.setView(quicklookButton);
                item.setMinSize(quicklookButton.frame().size);
                item.setMaxSize(quicklookButton.frame().size);
            }
            else {
                item.setEnabled(false);
                item.setImage(IconCache.iconNamed("notfound.tiff", 32));
            }
            return item;
        }
        else if(itemIdentifier.equals(TOOLBAR_LOG)) {
            item.setLabel(Locale.localizedString(TOOLBAR_LOG));
            item.setPaletteLabel(Locale.localizedString(TOOLBAR_LOG));
            item.setToolTip(Locale.localizedString("Toggle Log Drawer"));
            item.setImage(IconCache.iconNamed("log", 32));
            item.setTarget(this.id());
            item.setAction(Foundation.selector("toggleLogDrawer:"));
            return item;
        }
        // Returning null will inform the toolbar this kind of item is not supported.
        return null;
    }

    @Outlet
    private NSButton quicklookButton;

    /**
     * @param toolbar Window toolbar
     * @return The default configuration of toolbar items
     */
    @Override
    public NSArray toolbarDefaultItemIdentifiers(NSToolbar toolbar) {
        return NSArray.arrayWithObjects(
                TOOLBAR_NEW_CONNECTION,
                NSToolbarItem.NSToolbarSeparatorItemIdentifier,
                TOOLBAR_QUICK_CONNECT,
                TOOLBAR_TOOLS,
                NSToolbarItem.NSToolbarSeparatorItemIdentifier,
                TOOLBAR_REFRESH,
                TOOLBAR_EDIT,
                NSToolbarItem.NSToolbarFlexibleSpaceItemIdentifier,
                TOOLBAR_DISCONNECT
        );
    }

    /**
     * @param toolbar Window toolbar
     * @return All available toolbar items
     */
    @Override
    public NSArray toolbarAllowedItemIdentifiers(NSToolbar toolbar) {
        return NSArray.arrayWithObjects(
                TOOLBAR_NEW_CONNECTION,
                TOOLBAR_BROWSER_VIEW,
                TOOLBAR_TRANSFERS,
                TOOLBAR_QUICK_CONNECT,
                TOOLBAR_TOOLS,
                TOOLBAR_REFRESH,
                TOOLBAR_ENCODING,
                TOOLBAR_SYNCHRONIZE,
                TOOLBAR_DOWNLOAD,
                TOOLBAR_UPLOAD,
                TOOLBAR_EDIT,
                TOOLBAR_DELETE,
                TOOLBAR_NEW_FOLDER,
                TOOLBAR_NEW_BOOKMARK,
                TOOLBAR_GET_INFO,
                TOOLBAR_WEBVIEW,
                TOOLBAR_TERMINAL,
                TOOLBAR_ARCHIVE,
                TOOLBAR_QUICKLOOK,
                TOOLBAR_LOG,
                TOOLBAR_DISCONNECT,
                NSToolbarItem.NSToolbarCustomizeToolbarItemIdentifier,
                NSToolbarItem.NSToolbarSpaceItemIdentifier,
                NSToolbarItem.NSToolbarSeparatorItemIdentifier,
                NSToolbarItem.NSToolbarFlexibleSpaceItemIdentifier
        );
    }

    @Override
    public NSArray toolbarSelectableItemIdentifiers(NSToolbar toolbar) {
        return NSArray.array();
    }

    /**
     * Overrriden to remove any listeners from the session
     */
    @Override
    protected void invalidate() {
        if(this.hasSession()) {
            this.session.removeConnectionListener(this.listener);
        }

        bookmarkTable.setDelegate(null);
        bookmarkTable.setDataSource(null);
        bookmarkModel.invalidate();

        browserListView.setDelegate(null);
        browserListView.setDataSource(null);
        browserListModel.invalidate();

        browserOutlineView.setDelegate(null);
        browserOutlineView.setDataSource(null);
        browserOutlineModel.invalidate();

        toolbar.setDelegate(null);
        toolbarItems.clear();

        browserListColumnsFactory.clear();
        browserOutlineColumnsFactory.clear();
        bookmarkTableColumnFactory.clear();

        quickConnectPopup.setDelegate(null);
        quickConnectPopup.setDataSource(null);

        archiveMenu.setDelegate(null);
        editMenu.setDelegate(null);

        super.invalidate();
    }
}
