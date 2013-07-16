package ch.cyberduck.core.transfer.upload;

import ch.cyberduck.core.Path;
import ch.cyberduck.core.transfer.symlink.SymlinkResolver;

/**
 * @version $Id: SkipFilter.java 10292 2012-10-16 09:36:55Z dkocher $
 */
public class SkipFilter extends AbstractUploadFilter {

    public SkipFilter(final SymlinkResolver symlinkResolver) {
        super(symlinkResolver);
    }

    /**
     * Skip files that already exist on the server.
     */
    @Override
    public boolean accept(final Path file) {
        if(file.exists()) {
            return false;
        }
        return super.accept(file);
    }
}
