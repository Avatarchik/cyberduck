package ch.cyberduck.core.transfer.symlink;

import ch.cyberduck.core.Path;

/**
 * @version $Id: SymlinkResolver.java 10292 2012-10-16 09:36:55Z dkocher $
 */
public interface SymlinkResolver {

    /**
     * @param file Symbolic link
     * @return True if the symbolic link target can be resolved on transfer target
     */
    boolean resolve(Path file);

    /**
     * @param file Symbolic link
     * @return False if symlink target is already included as a child in the root files
     */
    boolean include(Path file);

    String relativize(String base, String name);
}
