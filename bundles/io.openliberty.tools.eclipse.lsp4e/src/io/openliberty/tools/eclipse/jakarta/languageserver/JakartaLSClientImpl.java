/******************************************************************************* 
 * Copyright (c) 2019 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/
/**
 * This class is a copy/paste of jbosstools-quarkus language server plugin
 * https://github.com/jbosstools/jbosstools-quarkus/blob/main/plugins/org.jboss.tools.quarkus.lsp4e/src/org/jboss/tools/quarkus/lsp4e/QuarkusLanguageClient.java
 * with modifications made for the Liberty Tools Microprofile LS plugin
 *
 */
package io.openliberty.tools.eclipse.jakarta.languageserver;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.lsp4e.LanguageClientImpl;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4jakarta.jdt.core.JakartaPropertiesManagerForJava;
import org.eclipse.lsp4jakarta.ls.api.JakartaLanguageClientAPI;
import org.eclipse.lsp4jdt.commons.JavaCodeActionParams;
import org.eclipse.lsp4jdt.commons.JavaCompletionParams;
import org.eclipse.lsp4jdt.commons.JavaCompletionResult;
import org.eclipse.lsp4jdt.commons.JavaCursorContextResult;
import org.eclipse.lsp4jdt.commons.JavaDiagnosticsParams;
import org.eclipse.lsp4jdt.commons.JavaFileInfo;
import org.eclipse.lsp4jdt.commons.JavaFileInfoParams;
import org.eclipse.lsp4jdt.commons.JavaProjectLabelsParams;
import org.eclipse.lsp4jdt.commons.ProjectLabelInfoEntry;
import org.eclipse.lsp4jdt.commons.codeaction.CodeActionResolveData;
import org.eclipse.lsp4jdt.commons.utils.JSONUtility;
import org.eclipse.lsp4jdt.core.ProjectLabelManager;
import org.eclipse.lsp4jdt.participants.core.ls.JDTUtilsLSImpl;

import io.openliberty.tools.eclipse.ls.plugin.LibertyToolsLSPlugin;

/**
 * Liberty Devex MicroProfile language client.
 * 
 * @author
 */
public class JakartaLSClientImpl extends LanguageClientImpl implements JakartaLanguageClientAPI {

    /**
     * {@inheritDoc}
     */
    private IProgressMonitor getProgressMonitor(CancelChecker cancelChecker) {
        IProgressMonitor monitor = (IProgressMonitor) new NullProgressMonitor() {
            public boolean isCanceled() {
                cancelChecker.checkCanceled();
                return false;
            };
        };

        return monitor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<JavaCompletionResult> getJavaCompletion(JavaCompletionParams javaParams) {
        return CompletableFutures.computeAsync(cancelChecker -> {
            IProgressMonitor monitor = getProgressMonitor(cancelChecker);
            CompletionList completionList;
            try {
                completionList = JakartaPropertiesManagerForJava.getInstance().completion(javaParams, JDTUtilsLSImpl.getInstance(), monitor);
                JavaCursorContextResult javaCursorContext = JakartaPropertiesManagerForJava.getInstance().javaCursorContext(javaParams,
                        JDTUtilsLSImpl.getInstance(), monitor);
                return new JavaCompletionResult(completionList, javaCursorContext);
            } catch (JavaModelException e) {
                LibertyToolsLSPlugin.logException(e.getLocalizedMessage(), e);
                return null;
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<ProjectLabelInfoEntry>> getAllJavaProjectLabels() {
        return CompletableFutures.computeAsync((cancelChecker) -> {
        	String pluginId = JakartaPropertiesManagerForJava.getInstance().getPluginId();
            return ProjectLabelManager.getInstance().getProjectLabelInfo(pluginId);
        });
    }

    /**
     * {@inheritDoc}
     */
    public CompletableFuture<ProjectLabelInfoEntry> getJavaProjectLabels(JavaProjectLabelsParams javaParams) {
        return CompletableFutures.computeAsync((cancelChecker) -> {
            IProgressMonitor monitor = getProgressMonitor(cancelChecker);
            String pluginId = JakartaPropertiesManagerForJava.getInstance().getPluginId();
            return ProjectLabelManager.getInstance().getProjectLabelInfo(javaParams, pluginId, JDTUtilsLSImpl.getInstance(), monitor);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<JavaFileInfo> getJavaFileInfo(JavaFileInfoParams javaParams) {
        return CompletableFutures.computeAsync(cancelChecker -> {
            IProgressMonitor monitor = getProgressMonitor(cancelChecker);
            return JakartaPropertiesManagerForJava.getInstance().fileInfo(javaParams, JDTUtilsLSImpl.getInstance());
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<PublishDiagnosticsParams>> getJavaDiagnostics(JavaDiagnosticsParams javaParams) {
        return CompletableFutures.computeAsync((cancelChecker) -> {
            IProgressMonitor monitor = getProgressMonitor(cancelChecker);
            try {
                return JakartaPropertiesManagerForJava.getInstance().diagnostics(javaParams, JDTUtilsLSImpl.getInstance(), monitor);
            } catch (JavaModelException e) {
                LibertyToolsLSPlugin.logException(e.getLocalizedMessage(), e);
                return Collections.emptyList();
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public CompletableFuture<List<CodeAction>> getJavaCodeAction(JavaCodeActionParams javaParams) {
        return CompletableFutures.computeAsync((cancelChecker) -> {
            IProgressMonitor monitor = getProgressMonitor(cancelChecker);
            try {
                return (List<CodeAction>) JakartaPropertiesManagerForJava.getInstance().codeAction(javaParams, JDTUtilsLSImpl.getInstance(),
                        monitor);
            } catch (JavaModelException e) {
                LibertyToolsLSPlugin.logException(e.getLocalizedMessage(), e);
                return Collections.emptyList();
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<CodeAction> resolveCodeAction(CodeAction unresolved) {
        return CompletableFutures.computeAsync((cancelChecker) -> {
            IProgressMonitor monitor = getProgressMonitor(cancelChecker);
            try {
                CodeActionResolveData resolveData = JSONUtility.toModel(unresolved.getData(), CodeActionResolveData.class);
                unresolved.setData(resolveData);
                return (CodeAction) JakartaPropertiesManagerForJava.getInstance().resolveCodeAction(unresolved, JDTUtilsLSImpl.getInstance(),
                        monitor);
            } catch (JavaModelException e) {
                LibertyToolsLSPlugin.logException(e.getLocalizedMessage(), e);
                return null;
            }
        });
    }
}
