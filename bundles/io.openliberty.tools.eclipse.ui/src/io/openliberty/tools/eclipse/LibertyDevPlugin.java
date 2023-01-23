/*******************************************************************************
* Copyright (c) 2022 IBM Corporation and others.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v. 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     IBM Corporation - initial implementation
*******************************************************************************/
package io.openliberty.tools.eclipse;

import java.util.Hashtable;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.osgi.service.debug.DebugOptions;
import org.eclipse.osgi.service.debug.DebugOptionsListener;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Listener;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.tm.terminal.view.ui.interfaces.ITerminalsView;
import org.eclipse.tm.terminal.view.ui.interfaces.IUIConstants;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.terminal.ProjectTab;
import io.openliberty.tools.eclipse.ui.terminal.ProjectTabController;

/**
 * The activator class controls the plug-in life cycle
 */
public class LibertyDevPlugin extends AbstractUIPlugin {

    // The plug-in ID
    public static final String PLUGIN_ID = "io.openliberty.tools.eclipse.ui";

    public static final String DEBUG_OPTIONS_ID = "io.openliberty.tools.eclipse";

    // The shared instance
    private static LibertyDevPlugin plugin;

    private IResourceChangeListener resourceChangeListener;

    private IWorkbenchListener workbenchListener;

    private IPartListener2 viewPartListener;

    private CTabFolder2Listener tabFolderListener;

    /**
     * Constructor.
     */
    public LibertyDevPlugin() {
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;

        // Register the trace listener.
        Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(DebugOptions.LISTENER_SYMBOLICNAME, LibertyDevPlugin.DEBUG_OPTIONS_ID);
        context.registerService(DebugOptionsListener.class.getName(), new Trace(), props);

        // Register a workspace listener for cleanup.
        registerListeners();

        // Classify all projects in the workspace.
        DevModeOperations.getInstance().getProjectModel().createNewCompleteWorkspaceModelWithClassify();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);

    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static LibertyDevPlugin getDefault() {
        return plugin;
    }

    /**
     * Register a workbench listener for Liberty dev mode process cleanup when the workbench exits or fails while there are active
     * terminals running dev mode. This cleanup required mainly for windows platforms because dev mode sub process shutdown hooks do
     * not get processed as they do on other platforms.
     */
    private void registerListeners() {
        // Register a resource change listener to listen for project updates.
        resourceChangeListener = new LibertyResourceChangeListener();
        ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceChangeListener, IResourceChangeEvent.PRE_BUILD);

        // Register a workbench listener to handle workbench termination cleanup.
        ProjectTabController ptc = ProjectTabController.getInstance();
        IWorkbench iwb = PlatformUI.getWorkbench();
        registerWorkbenchListener(iwb, ptc);

        IWorkbenchWindow activeWindow = iwb.getActiveWorkbenchWindow();
        if (activeWindow != null) {
            IWorkbenchPage activePage = activeWindow.getActivePage();
            if (activePage != null) {
                // Register a part listener to handle Terminal tab view termination cleanup.
                registerPartListener(activePage, ptc);

                IViewPart viewPart = activePage.findView(IUIConstants.ID);
                if (viewPart != null || (viewPart instanceof ITerminalsView)) {

                    // register a CTabFolder listener to handle terminal tab view item termination cleanup.
                    registerCTabFolderListener(viewPart, ptc);

                }
            }
        }
    }

    private void deregisterListeners() {
        // Remove the resource change listener.
        System.out.println("@ed: DEregistering a resource change listener ...");
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceChangeListener);

        // Remove the workbench listener
        IWorkbench iwb = PlatformUI.getWorkbench();
        System.out.println("@ed: DEregistering a workbench listener ...");
        iwb.removeWorkbenchListener(workbenchListener);

        IWorkbenchWindow activeWindow = iwb.getActiveWorkbenchWindow();
        if (activeWindow != null) {
            IWorkbenchPage activePage = activeWindow.getActivePage();
            if (activePage != null) {
                // Remove Terminal tab view cleanup listener.
                System.out.println("@ed: DEregistering a viewpart listener ...");
                activePage.removePartListener(viewPartListener);

                IViewPart iViewPart = activePage.findView(IUIConstants.ID);
                if (iViewPart != null || (iViewPart instanceof ITerminalsView)) {
                    ITerminalsView terminalView = (ITerminalsView) iViewPart;
                    CTabFolder tabFolder = terminalView.getAdapter(CTabFolder.class);
                    System.out.println("@ed: DEregistering a ctabfolder2 listener ...");
                    tabFolder.removeCTabFolder2Listener(tabFolderListener);
                }
            }
        }
    }

    public void registerWorkbenchListener(IWorkbench iWorkbench, ProjectTabController tabController) {
        System.out.println("@ed: registering a workbench listener ...");
        workbenchListener = new IWorkbenchListener() {

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean preShutdown(IWorkbench iwb, boolean arg1) {
                tabController.cleanupTerminalView();

                return true;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void postShutdown(IWorkbench iwb) {
                // iwb.removeWorkbenchListener(this);
                System.out.println("@ed: postShutdown: deregistering  listeners ...");
                deregisterListeners();
            }
        };

        iWorkbench.addWorkbenchListener(workbenchListener);
    }

    public void registerCTabFolderListener(IViewPart iViewPart, ProjectTabController tabController) {
        // Manages individual terminal tab item cleanup.
        System.out.println("@ed: registering CTabFolder listener ...");
        tabFolderListener = new CTabFolder2Listener() {

            /**
             * {@inheritDoc}
             */
            public void close(CTabFolderEvent event) {
                System.out.println("addCTabFolder2Listener close called. Event: " + event);
                CTabItem item = (CTabItem) event.item;
                if (item != null && !item.isDisposed()) {
                    try {
                        System.out.println("addCTabFolder2Listener close called. tooltip text tabitemt: " + item.getToolTipText());
                        String projectName = (String) item.getText();
                        ProjectTab projectTab = tabController.getProjectTab(projectName);
                        String cmd = "exit" + System.lineSeparator();

                        projectTab.writeToStream(cmd.getBytes());
                        System.out.println("addCTabFolder2Listener close called. successfully called exit");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("addCTabFolder2Listener close called but item is already disabled. Event: " + event);
                }
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void maximize(CTabFolderEvent arg0) {
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void minimize(CTabFolderEvent arg0) {
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void restore(CTabFolderEvent arg0) {
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void showList(CTabFolderEvent arg0) {
            }
        };

        ITerminalsView terminalView = (ITerminalsView) iViewPart;
        CTabFolder tabFolder = terminalView.getAdapter(CTabFolder.class);
        System.out.println("@ed: registering tab folder listener ...");
        tabFolder.addCTabFolder2Listener(tabFolderListener);
    }

    public void registerPartListener(IWorkbenchPage iworkbenchPage, ProjectTabController tabController) {
        System.out.println("@ed: registering part listener ...");
        viewPartListener = new IPartListener2() {

            /**
             * {@inheritDoc}
             */
            @Override
            public void partClosed(IWorkbenchPartReference partRef) {
                System.out.println("IPartListener close called. title: " + partRef.getTitle() + ", part id: " + partRef.getId());
                if (IUIConstants.ID.equals(partRef.getId())) {
                    tabController.cleanupTerminalView();
                }
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void partClosed(IWorkbenchPartReference partRef) {
                System.out.println("IPartListener close called. title: " + partRef.getTitle() + ", part id: " + partRef.getId());
                if (IUIConstants.ID.equals(partRef.getId())) {
                    tabController.cleanupTerminalView();
                }
            }
        };

        // IWorkbenchPartSite iwbPartsite = iViewPart.getViewSite();
        // IWorkbenchPage iwbPage = iwbPartsite.getPage();
        iworkbenchPage.addPartListener(viewPartListener);

    }
}
