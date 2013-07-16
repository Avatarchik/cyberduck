package ch.cyberduck.core.local;

import ch.cyberduck.core.Factory;
import ch.cyberduck.ui.cocoa.application.NSWorkspace;

/**
 * @version $Id: WorkspaceRevealService.java 10556 2012-10-22 17:22:43Z dkocher $
 */
public class WorkspaceRevealService implements RevealService {

    public static void register() {
        RevealServiceFactory.addFactory(Factory.NATIVE_PLATFORM, new RevealServiceFactory() {
            @Override
            protected RevealService create() {
                return new WorkspaceRevealService();
            }
        });
    }

    @Override
    public void reveal(final Local file) {
        synchronized(NSWorkspace.class) {
            // If a second path argument is specified, a new file viewer is opened. If you specify an
            // empty string (@"") for this parameter, the file is selected in the main viewer.
            NSWorkspace.sharedWorkspace().selectFile(file.getAbsolute(), file.getParent().getAbsolute());
        }
    }
}
