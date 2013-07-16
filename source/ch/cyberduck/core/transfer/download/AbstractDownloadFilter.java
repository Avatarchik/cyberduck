package ch.cyberduck.core.transfer.download;

import ch.cyberduck.core.Path;
import ch.cyberduck.core.Permission;
import ch.cyberduck.core.Preferences;
import ch.cyberduck.core.local.ApplicationLauncher;
import ch.cyberduck.core.local.ApplicationLauncherFactory;
import ch.cyberduck.core.local.Local;
import ch.cyberduck.core.local.QuarantineService;
import ch.cyberduck.core.local.QuarantineServiceFactory;
import ch.cyberduck.core.transfer.TransferOptions;
import ch.cyberduck.core.transfer.TransferPathFilter;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.core.transfer.symlink.SymlinkResolver;

import org.apache.log4j.Logger;

/**
 * @version $Id: AbstractDownloadFilter.java 10538 2012-10-22 13:45:19Z dkocher $
 */
public abstract class AbstractDownloadFilter extends TransferPathFilter {
    private static final Logger log = Logger.getLogger(AbstractDownloadFilter.class);

    private SymlinkResolver symlinkResolver;

    private final QuarantineService quarantine
            = QuarantineServiceFactory.get();

    private final ApplicationLauncher launcher
            = ApplicationLauncherFactory.get();

    public AbstractDownloadFilter(final SymlinkResolver symlinkResolver) {
        this.symlinkResolver = symlinkResolver;
    }

    @Override
    public boolean accept(final Path file) {
        if(file.attributes().isDirectory()) {
            if(file.getLocal().exists()) {
                return false;
            }
        }
        if(file.attributes().isSymbolicLink()) {
            if(!symlinkResolver.resolve(file)) {
                return symlinkResolver.include(file);
            }
        }
        final Local volume = file.getLocal().getVolume();
        if(!volume.exists()) {
            log.error(String.format("Volume %s not mounted", volume.getAbsolute()));
            return false;
        }
        return true;
    }

    @Override
    public TransferStatus prepare(final Path file) {
        final TransferStatus status = new TransferStatus();
        if(file.attributes().getSize() == -1) {
            file.readSize();
        }
        if(file.getSession().isReadTimestampSupported()) {
            if(file.attributes().getModificationDate() == -1) {
                if(Preferences.instance().getBoolean("queue.download.preserveDate")) {
                    file.readTimestamp();
                }
            }
        }
        if(file.getSession().isUnixPermissionsSupported()) {
            if(Preferences.instance().getBoolean("queue.download.changePermissions")) {
                if(file.attributes().getPermission().equals(Permission.EMPTY)) {
                    file.readUnixPermission();
                }
            }
        }
        if(file.attributes().isFile()) {
            if(file.attributes().isSymbolicLink()) {
                if(symlinkResolver.resolve(file)) {
                    // No file size increase for symbolic link to be created locally
                }
                else {
                    // A server will resolve the symbolic link when the file is requested.
                    final Path target = (Path) file.getSymlinkTarget();
                    if(target.attributes().getSize() == -1) {
                        target.readSize();
                    }
                    status.setLength(target.attributes().getSize());
                }
            }
            else {
                // Read file size
                status.setLength(file.attributes().getSize());
            }
        }
        if(!file.getLocal().getParent().exists()) {
            // Create download folder if missing
            file.getLocal().getParent().mkdir();
        }
        return status;
    }

    /**
     * Update timestamp and permission
     */
    @Override
    public void complete(final Path file, final TransferOptions options, final TransferStatus status) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Complete %s with status %s", file.getAbsolute(), status));
        }
        if(status.isComplete()) {
            if(options.quarantine) {
                // Set quarantine attributes
                quarantine.setQuarantine(file.getLocal(), file.getHost().toURL(), file.toURL());
            }
            if(Preferences.instance().getBoolean("queue.download.wherefrom")) {
                // Set quarantine attributes
                quarantine.setWhereFrom(file.getLocal(), file.toURL());
            }
            if(options.open) {
                launcher.open(file.getLocal());
            }
            launcher.bounce(file.getLocal());
        }
        if(!status.isCanceled()) {
            if(Preferences.instance().getBoolean("queue.download.changePermissions")) {
                Permission permission = Permission.EMPTY;
                if(Preferences.instance().getBoolean("queue.download.permissions.useDefault")) {
                    if(file.attributes().isFile()) {
                        permission = new Permission(
                                Preferences.instance().getInteger("queue.download.permissions.file.default"));
                    }
                    if(file.attributes().isDirectory()) {
                        permission = new Permission(
                                Preferences.instance().getInteger("queue.download.permissions.folder.default"));
                    }
                }
                else {
                    permission = file.attributes().getPermission();
                }
                if(!Permission.EMPTY.equals(permission)) {
                    if(file.attributes().isDirectory()) {
                        // Make sure we can read & write files to directory created.
                        permission.getOwnerPermissions()[Permission.READ] = true;
                        permission.getOwnerPermissions()[Permission.WRITE] = true;
                        permission.getOwnerPermissions()[Permission.EXECUTE] = true;
                    }
                    if(file.attributes().isFile()) {
                        // Make sure the owner can always read and write.
                        permission.getOwnerPermissions()[Permission.READ] = true;
                        permission.getOwnerPermissions()[Permission.WRITE] = true;
                    }
                    if(log.isInfoEnabled()) {
                        log.info(String.format("Updating permissions of %s to %s", file.getLocal(), permission));
                    }
                    file.getLocal().writeUnixPermission(permission);
                }
            }
            if(Preferences.instance().getBoolean("queue.download.preserveDate")) {
                if(file.attributes().getModificationDate() != -1) {
                    long timestamp = file.attributes().getModificationDate();
                    if(log.isInfoEnabled()) {
                        log.info(String.format("Updating timestamp of %s to %d", file.getLocal(), timestamp));
                    }
                    file.getLocal().writeTimestamp(-1, timestamp, -1);
                }
            }
        }
    }
}
