package liberty.tools.utils;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jem.util.emf.workbench.ProjectUtilities;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public class Project {

    public static final String LIBERTY_MAVEN_PLUGIN_CONTAINER_VERSION = "3.3-M1";
    public static final String LIBERTY_GRADLE_PLUGIN_CONTAINER_VERSION = "3.1-M1";

    /**
     * Retrieves the project currently selected.
     * 
     * @return The project currently selected or null if one was not found.
     */
    public static IProject getSelected() {
        IProject project = null;
        IWorkbenchWindow w = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        ISelectionService selectionService = w.getSelectionService();
        ISelection selection = selectionService.getSelection();

        if (selection instanceof IStructuredSelection) {
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            Object firstElement = structuredSelection.getFirstElement();
            project = ProjectUtilities.getProject(firstElement);
            if (project == null && (firstElement instanceof String)) {
                project = getByName((String) firstElement);
            }
            if (project == null && (firstElement instanceof IProject)) {
                project = ((IProject) firstElement);
            }
            if (firstElement instanceof IResource) {
                project = ((IResource) firstElement).getProject();
            }
        }

        return project;
    }

    /**
     * Gets all open projects currently in the workspace.
     * 
     * @return All open projects currently in the workspace.
     */
    public static List<String> getOpenWokspaceProjects() {
        List<String> jProjects = new ArrayList<String>();

        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] projects = workspaceRoot.getProjects();
        for (int i = 0; i < projects.length; i++) {
            IProject project = projects[i];

            if (project.isOpen()) {
                jProjects.add(project.getName());
            }
        }

        return jProjects;
    }

    /**
     * Retrieves the IProject object associated with the input name.
     * 
     * @param name The name of the project.
     * 
     * @return The IProject object associated with the input name.
     */
    public static IProject getByName(String name) {

        try {
            IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();

            IProject[] projects = workspaceRoot.getProjects();
            for (int i = 0; i < projects.length; i++) {
                IProject project = projects[i];
                if (project.isOpen() && (project.getName().equals(name))) {
                    return project;
                }
            }
        } catch (Exception ce) {

        }
        return null;
    }

    /**
     * Retrieves the absolute path of the currently selected project.
     *
     * @param selectedProject The project object
     * 
     * @return The absolute path of the currently selected project or null if the path could not be obtained.
     */
    public static String getPath(IProject project) {
        if (project != null) {
            IPath path = project.getLocation();
            if (path != null) {
                return path.toPortableString();
            }
        }

        return null;
    }

    /**
     * Returns true if the input project is a Maven project. False otherwise.
     * 
     * @param project The project to check.
     * 
     * @return True if the input project is a Maven project. False, otherwise.
     */
    public static boolean isMaven(IProject project) {
        // TODO: Handle cases where pom.xml is not in the root dir.
        IFile file = project.getFile("pom.xml");
        return file.exists();
    }

    /**
     * Returns true if the input project is a Gradle project. False, otherwise.
     * 
     * @param project The project to check.
     * 
     * @return True if the input project is a Gradle project. False otherwise.
     */
    public static boolean isGradle(IProject project) {
        // TODO: Handle cases where build.gradle is not in the root dir.
        IFile file = project.getFile("build.gradle");
        return file.exists();
    }

    /**
     * Returns true if the Maven project's pom.xml file is configured to use Liberty development mode. False, otherwise.
     * 
     * @param project The Maven project.
     * 
     * @return True if the Maven project's pom.xml file is configured to use Liberty development mode. False, otherwise.
     */
    public static boolean isMavenBuildFileValid(IProject project) throws ParserConfigurationException, SAXException, IOException, CoreException {
        IFile file = project.getFile("pom.xml");
		
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document doc = builder.parse(file.getContents());

        doc.getDocumentElement().normalize();
        Node root = doc.getDocumentElement();

        NodeList nList = root.getChildNodes();
        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node nNode = nList.item(temp);

            // Check for liberty maven plugin in profiles
            if (nNode.getNodeName().equals("profiles")) {
                NodeList profiles = nNode.getChildNodes();
                for (int i = 0; i < profiles.getLength(); i++) {
                    Node profile = profiles.item(i);
                    if (profile.getNodeName().equals("profile")) {
                        NodeList profileList = profile.getChildNodes();
                        for (int j = 0; j < profileList.getLength(); j++) {
                            if (profileList.item(j).getNodeName().equals("build")) {
                                NodeList buildNodeList = profileList.item(j).getChildNodes();
                                return isLibertyMavenPluginDetected(buildNodeList);
                            }
                        }
                    }
                }
            }

            // Check for liberty maven plugin in plugins
            if (nNode.getNodeName().equals("build")) {
                NodeList buildNodeList = nNode.getChildNodes();
                if (isLibertyMavenPluginDetected(buildNodeList)) {
                    return true;
                }

                // Check for liberty maven plugin in plugin management
                // indicates this is a parent pom, list in the Liberty Dev Dashboard
                for (int i = 0; i < buildNodeList.getLength(); i++) {
                    Node buildNode = buildNodeList.item(i);
                    if (buildNode.getNodeName().equals("pluginManagement")) {
                        NodeList pluginManagementList = buildNode.getChildNodes();
                        return isLibertyMavenPluginDetected(buildNodeList);
                    }
                }
            }
        }
		
		return false;
	}
	
	private static boolean isLibertyMavenPluginDetected(NodeList buildList) {
        for (int i = 0; i < buildList.getLength(); i++) {
            Node buildNode = buildList.item(i);
            if (buildNode.getNodeName().equals("plugins")) {
                NodeList plugins = buildNode.getChildNodes();
                if (buildNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element pluginsElem = (Element) buildNode;
                    NodeList pluginsList = pluginsElem.getElementsByTagName("plugin");
                    for (int j = 0; j < pluginsList.getLength(); j++) {
                        Node pluginNode = pluginsList.item(j);
                        if (pluginNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element pluginElem = (Element) pluginNode;
                            String groupId = "";
                            String artifactId = "";
                            String version = "";
                            if (pluginElem.getElementsByTagName("groupId").getLength() != 0) {
                                groupId = pluginElem.getElementsByTagName("groupId").item(0).getTextContent();
                            }
                            if (pluginElem.getElementsByTagName("artifactId").getLength() != 0) {
                                artifactId = pluginElem.getElementsByTagName("artifactId").item(0).getTextContent();
                            }
                            if (pluginElem.getElementsByTagName("version").getLength() != 0) {
                                version = pluginElem.getElementsByTagName("version").item(0).getTextContent();
                            }
                            if (groupId.equals("io.openliberty.tools") && artifactId.equals("liberty-maven-plugin")){
                                if (containerVersion(version)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * Given liberty-maven-plugin version, determine if it is compatible for dev mode with containers
     *
     * @param version plugin version
     * @return true if valid for dev mode in contianers
     */
    private static boolean containerVersion(String version){
        try {
            if (version.isEmpty()) {
                return true;
            }
            ComparableVersion pluginVersion = new ComparableVersion(version);
            ComparableVersion containerVersion = new ComparableVersion(LIBERTY_MAVEN_PLUGIN_CONTAINER_VERSION);
            if (pluginVersion.compareTo(containerVersion) >= 0) {
                return true;
            }
            return false;
        } catch (NullPointerException | ClassCastException e) {
            return false;
        }
    }
	
	public static boolean isGradleBuildFileValid(IProject project) throws CoreException, IOException {
		IFile file = project.getFile("build.gradle");
		
		// Read build.gradle file to String
		BufferedInputStream bis = new BufferedInputStream(file.getContents());
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		for (int result = bis.read(); result != -1; result = bis.read()) {
		    buf.write((byte) result);
		}
		
		String buildFileText = buf.toString("UTF-8");

         if (buildFileText.isEmpty()) { 
        	 return false; 
         }
         
         // Filter commented out lines in build.gradle
         String partialFiltered = buildFileText.replaceAll("/\\*.*\\*/", "");
         String buildFileTextFiltered = partialFiltered.replaceAll("//.*(?=\\n)", "");

         // Check if "apply plugin: 'liberty'" is specified in the build.gradle
         boolean libertyPlugin = false;
        		 
         String applyPluginRegex = "(?<=apply plugin:)(\\s*)('|\")liberty";
         Pattern applyPluginPattern = Pattern.compile(applyPluginRegex);
         Matcher applyPluginMatcher = applyPluginPattern.matcher(buildFileTextFiltered);
         while (applyPluginMatcher.find()) {
             libertyPlugin = true;
         }
         
         // Check if liberty is in the plugins block
         if (libertyPlugin) {
             // check if group matches io.openliberty.tools and name matches liberty-gradle-plugin
             String regex = "(?<=dependencies)(\\s*\\{)([^\\}]+)(?=\\})";
             String regex2 = "(.*\\bio\\.openliberty\\.tools\\b.*)(.*\\bliberty-gradle-plugin\\b.*)";

             Pattern pattern = Pattern.compile(regex);
             Matcher matcher = pattern.matcher(buildFileTextFiltered);

             while (matcher.find()) {
                 String sub = buildFileTextFiltered.substring(matcher.start(), matcher.end());
                 Pattern pattern2 = Pattern.compile(regex2);
                 Matcher matcher2 = pattern2.matcher(sub);
                 while (matcher2.find()) {
                     String plugin = sub.substring(matcher2.start(), matcher2.end());
                     return containerVersion(plugin);
                 }
             }
         }
         
		return false;
	}
}

}
