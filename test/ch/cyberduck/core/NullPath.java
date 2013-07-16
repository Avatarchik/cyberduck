package ch.cyberduck.core;

import ch.cyberduck.core.io.BandwidthThrottle;
import ch.cyberduck.core.transfer.TransferStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @version $Id: NullPath.java 10983 2013-05-02 10:26:52Z dkocher $
 */
public class NullPath extends Path {

    public NullPath(final String path, final int type) {
        super(path, type);
    }

    @Override
    protected AttributedList<Path> list(final AttributedList<Path> children) {
        return AttributedList.emptyList();
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public Path getParent() {
        return new NullPath(Path.getParent(this.getAbsolute(), Path.DELIMITER), Path.DIRECTORY_TYPE);
    }

    @Override
    public AbstractPath getSymlinkTarget() {
        if(this.attributes().isSymbolicLink()) {
            return new NullPath(symlink, this.attributes().isDirectory() ? Path.DIRECTORY_TYPE : Path.FILE_TYPE);
        }
        return null;
    }

    private final NullSession session = new NullSession(new Host("test"));

    @Override
    public Session getSession() {
        return session;
    }

    @Override
    public InputStream read(final TransferStatus status) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void download(final BandwidthThrottle throttle, final StreamListener listener, final TransferStatus status) {
        throw new UnsupportedOperationException();
    }

    @Override
    public OutputStream write(final TransferStatus status) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void upload(final BandwidthThrottle throttle, final StreamListener listener, final TransferStatus status) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void mkdir() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rename(final AbstractPath renamed) {
        //
    }
}
