package ch.cyberduck.core.transfer.upload;

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

import ch.cyberduck.core.AbstractStreamListener;
import ch.cyberduck.core.AttributedList;
import ch.cyberduck.core.Cache;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathFactory;
import ch.cyberduck.core.Preferences;
import ch.cyberduck.core.Session;
import ch.cyberduck.core.filter.UploadRegexFilter;
import ch.cyberduck.core.io.BandwidthThrottle;
import ch.cyberduck.core.local.Local;
import ch.cyberduck.core.serializer.Serializer;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.core.transfer.TransferAction;
import ch.cyberduck.core.transfer.TransferOptions;
import ch.cyberduck.core.transfer.TransferPathFilter;
import ch.cyberduck.core.transfer.TransferPrompt;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.core.transfer.normalizer.UploadRootPathsNormalizer;
import ch.cyberduck.core.transfer.symlink.SymlinkResolver;
import ch.cyberduck.core.transfer.symlink.UploadSymlinkResolver;

import org.apache.log4j.Logger;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * @version $Id: UploadTransfer.java 10907 2013-04-23 12:23:32Z dkocher $
 */
public class UploadTransfer extends Transfer {
    private static final Logger log = Logger.getLogger(UploadTransfer.class);

    private UploadRegexFilter filter = new UploadRegexFilter();

    public UploadTransfer(final Path root) {
        this(Collections.singletonList(root));
    }

    public UploadTransfer(final List<Path> roots) {
        super(new UploadRootPathsNormalizer().normalize(roots), new BandwidthThrottle(
                Preferences.instance().getFloat("queue.upload.bandwidth.bytes")));
    }

    public <T> UploadTransfer(final T dict, final Session s) {
        super(dict, s, new BandwidthThrottle(
                Preferences.instance().getFloat("queue.upload.bandwidth.bytes")));
    }

    @Override
    public <T> T getAsDictionary() {
        final Serializer dict = super.getSerializer();
        dict.setStringForKey(String.valueOf(KIND_UPLOAD), "Kind");
        return dict.getSerialized();
    }

    /**
     * File listing cache for children of the root paths not part of the session cache because
     * they only exist on the local file system.
     */
    private final Cache cache = new Cache() {
        @Override
        public void clear() {
            super.clear();
            session.cache().clear();
        }
    };

    @Override
    public Cache cache() {
        return cache;
    }

    @Override
    public AttributedList<Path> children(final Path parent) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Children for %s", parent));
        }
        if(parent.getLocal().attributes().isSymbolicLink()
                && new UploadSymlinkResolver(this.getRoots()).resolve(parent)) {
            if(log.isDebugEnabled()) {
                log.debug("Do not list children for symbolic link:" + parent);
            }
            this.cache().put(parent.getReference(), AttributedList.<Path>emptyList());
        }
        else if(!this.cache().containsKey(parent.getReference())) {
            if(!parent.getLocal().exists()) {
                // Cannot fetch file listing of non existent file
                this.cache().put(parent.getReference(), AttributedList.<Path>emptyList());
            }
            else {
                final AttributedList<Path> children = new AttributedList<Path>();
                for(Local local : parent.getLocal().children(filter)) {
                    children.add(PathFactory.createPath(session, parent.getAbsolute(), local));
                }
                this.cache().put(parent.getReference(), children);
            }
        }
        return this.cache().get(parent.getReference());
    }

    @Override
    public boolean isResumable() {
        return session.isUploadResumable();
    }

    @Override
    public boolean isReloadable() {
        return true;
    }

    @Override
    public TransferPathFilter filter(final TransferPrompt prompt, final TransferAction action) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Filter transfer with action %s", action.toString()));
        }
        final SymlinkResolver resolver = new UploadSymlinkResolver(this.getRoots());
        if(action.equals(TransferAction.ACTION_OVERWRITE)) {
            return new OverwriteFilter(resolver);
        }
        if(action.equals(TransferAction.ACTION_RESUME)) {
            return new ResumeFilter(resolver);
        }
        if(action.equals(TransferAction.ACTION_RENAME)) {
            return new RenameFilter(resolver);
        }
        if(action.equals(TransferAction.ACTION_RENAME_EXISTING)) {
            return new RenameExistingFilter(resolver);
        }
        if(action.equals(TransferAction.ACTION_SKIP)) {
            return new SkipFilter(resolver);
        }
        if(action.equals(TransferAction.ACTION_COMPARISON)) {
            return new CompareFilter(resolver);
        }
        if(action.equals(TransferAction.ACTION_CALLBACK)) {
            for(Path upload : this.getRoots()) {
                if(!this.check()) {
                    return null;
                }
                if(upload.exists()) {
                    if(upload.getLocal().attributes().isDirectory()) {
                        if(this.children(upload).isEmpty()) {
                            // Do not prompt for existing empty directories
                            continue;
                        }
                    }
                    // Prompt user to choose a filter
                    TransferAction result = prompt.prompt();
                    return this.filter(prompt, result);
                }
            }
            // No files exist yet therefore it is most straightforward to use the overwrite action
            return this.filter(prompt, TransferAction.ACTION_OVERWRITE);
        }
        return super.filter(prompt, action);
    }

    @Override
    protected TransferAction action(final boolean resumeRequested, final boolean reloadRequested) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Find transfer action for Resume=%s,Reload=%s", resumeRequested, reloadRequested));
        }
        if(resumeRequested) {
            // Force resume
            return TransferAction.ACTION_RESUME;
        }
        if(reloadRequested) {
            return TransferAction.forName(
                    Preferences.instance().getProperty("queue.upload.reload.fileExists")
            );
        }
        // Use default
        return TransferAction.forName(
                Preferences.instance().getProperty("queue.upload.fileExists")
        );
    }


    @Override
    public void transfer(final Path file, final TransferOptions options, final TransferStatus status) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Transfer file %s with options %s", file, options));
        }
        final SymlinkResolver symlinkResolver = new UploadSymlinkResolver(this.getRoots());
        if(file.getLocal().attributes().isSymbolicLink() && symlinkResolver.resolve(file)) {
            // Make relative symbolic link
            final String target = symlinkResolver.relativize(file.getLocal().getAbsolute(),
                    file.getLocal().getSymlinkTarget().getAbsolute());
            if(log.isDebugEnabled()) {
                log.debug(String.format("Create symbolic link from %s to %s", file, target));
            }
            file.symlink(target);
            status.setComplete();
        }
        else if(file.attributes().isFile()) {
            String original = file.getName();
            final boolean temporary = Preferences.instance().getBoolean("queue.upload.file.temporary")
                    && file.getSession().isRenameSupported(file);
            if(temporary) {
                file.setPath(file.getParent(), MessageFormat.format(Preferences.instance().getProperty("queue.upload.file.temporary.format"),
                        file.getName(), UUID.randomUUID().toString()));
            }
            // Transfer
            file.upload(bandwidth, new AbstractStreamListener() {
                @Override
                public void bytesSent(long bytes) {
                    addTransferred(bytes);
                }
            }, status);
            if(status.isComplete()) {
                if(temporary) {
                    file.rename(PathFactory.createPath(file.getSession(), file.getParent().getAbsolute(),
                            original, file.attributes().getType()));
                    file.setPath(file.getParent(), original);
                }
            }
        }
        else if(file.attributes().isDirectory()) {
            if(file.getSession().isCreateFolderSupported(file)) {
                file.mkdir();
            }
        }
    }

    @Override
    public String getStatus() {
        return this.isComplete() ? "Upload complete" : "Transfer incomplete";
    }

    @Override
    public String getImage() {
        return "transfer-upload.tiff";
    }
}
