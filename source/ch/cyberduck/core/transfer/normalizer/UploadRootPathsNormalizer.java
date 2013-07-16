package ch.cyberduck.core.transfer.normalizer;

import ch.cyberduck.core.Collection;
import ch.cyberduck.core.Path;

import org.apache.log4j.Logger;

import java.util.Iterator;
import java.util.List;

/**
 * @version $Id: UploadRootPathsNormalizer.java 10522 2012-10-22 09:45:57Z dkocher $
 */
public class UploadRootPathsNormalizer implements RootPathsNormalizer<List<Path>> {
    private static final Logger log = Logger.getLogger(UploadRootPathsNormalizer.class);

    @Override
    public List<Path> normalize(final List<Path> roots) {
        final List<Path> normalized = new Collection<Path>();
        for(final Path upload : roots) {
            boolean duplicate = false;
            for(Iterator<Path> iter = normalized.iterator(); iter.hasNext(); ) {
                Path n = iter.next();
                if(upload.getLocal().isChild(n.getLocal())) {
                    // The selected file is a child of a directory already included
                    duplicate = true;
                    break;
                }
                if(n.getLocal().isChild(upload.getLocal())) {
                    iter.remove();
                }
                if(upload.equals(n)) {
                    // The selected file has the same name; if uploaded as a root element
                    // it would overwrite the earlier
                    final String parent = upload.getParent().getAbsolute();
                    final String filename = upload.getName();
                    String proposal;
                    int no = 0;
                    int index = filename.lastIndexOf('.');
                    do {
                        no++;
                        if(index != -1 && index != 0) {
                            proposal = String.format("%s-%d%s", filename.substring(0, index), no, filename.substring(index));
                        }
                        else {
                            proposal = String.format("%s-%d", filename, no);
                        }
                        upload.setPath(parent, proposal);
                    }
                    while(false);//(upload.exists());
                    if(log.isInfoEnabled()) {
                        log.info(String.format("Changed name from %s to %s", filename, upload.getName()));
                    }
                }
            }
            // Prunes the list of selected files. Files which are a child of an already included directory
            // are removed from the returned list.
            if(!duplicate) {
                normalized.add(upload);
            }
        }
        return normalized;
    }
}