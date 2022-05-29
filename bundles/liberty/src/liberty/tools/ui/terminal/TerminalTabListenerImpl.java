package liberty.tools.ui.terminal;

import org.eclipse.tm.terminal.view.core.interfaces.ITerminalTabListener;

/**
 * Listens for terminal tab termination.
 */
public class TerminalTabListenerImpl implements ITerminalTabListener {

    /**
     * The name of the project being processed.
     */
    String projectName;

    /**
     * Constructor.
     *
     * @param projectName The name of the project to be associated with this listener.
     */
    public TerminalTabListenerImpl(String projectName) {
        this.projectName = projectName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void terminalTabDisposed(Object source, Object data) {
        // Perform cleanup if the project name associated with the disposed tab matches the project name associated with this
        // listener. Note that, input "data" is the custom data (ITerminalsConnectorConstants.PROP_DATA = project name) provided
        // for opening the console. This is a bit more reliable and easier to use than using the "source" input (CTabItem) on
        // Windows specially due to potential tab title overrides.
        if (data instanceof String) {
            String disposedProjectName = (String) data;
            if (disposedProjectName.equals(projectName)) {
                ProjectTabController.getInstance().cleanupTerminal(projectName);
            }
        }
    }
}