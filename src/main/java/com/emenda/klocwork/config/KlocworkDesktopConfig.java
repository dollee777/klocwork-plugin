
package com.emenda.klocwork.config;

import com.emenda.klocwork.KlocworkConstants;
import com.emenda.klocwork.util.KlocworkBuildSpecParser;
import com.emenda.klocwork.util.KlocworkUtil;

import org.apache.commons.lang3.StringUtils;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.lang.InterruptedException;
import java.net.URL;
import java.util.List;
import java.lang.String;



public class KlocworkDesktopConfig extends AbstractDescribableImpl<KlocworkDesktopConfig> {

    private final String buildSpec;
    private final String projectDir;
    private final boolean cleanupProject;
    private final String reportFile;
    private final String additionalOpts;
    // private final boolean setupKwdtagent;
    // private final String kwdtagentPort;
    private final boolean incrementalAnalysis;
    private final KlocworkDiffAnalysisConfig diffAnalysisConfig;

    @DataBoundConstructor
    public KlocworkDesktopConfig(String buildSpec, String projectDir, boolean cleanupProject, String reportFile, String additionalOpts,
    // boolean setupKwdtagent, String kwdtagentPort,
    boolean incrementalAnalysis, KlocworkDiffAnalysisConfig diffAnalysisConfig) {
        this.buildSpec = buildSpec;
        this.projectDir = projectDir;
        this.cleanupProject = cleanupProject;
        this.reportFile = reportFile;
        this.additionalOpts = additionalOpts;
        // this.setupKwdtagent = setupKwdtagent;
        // this.kwdtagentPort = kwdtagentPort;
        this.incrementalAnalysis = incrementalAnalysis;
        this.diffAnalysisConfig = diffAnalysisConfig;
    }

    public ArgumentListBuilder getVersionCmd()
                                        throws IOException, InterruptedException {
        //ArgumentListBuilder versionCmd = new ArgumentListBuilder("kwciagent");
        ArgumentListBuilder versionCmd = new ArgumentListBuilder("kwciagent");
        versionCmd.add("--version");
        return versionCmd;
    }

    public ArgumentListBuilder getKwciagentCreateCmd(EnvVars envVars, FilePath workspace)
                                        throws IOException, InterruptedException {

        validateParentProjectDir(getKwlpDir(workspace, envVars).getParent());

        ArgumentListBuilder kwciagentCreateCmd = new ArgumentListBuilder("kwciagent", "create");
        String projectUrl = KlocworkUtil.getKlocworkProjectUrl(envVars);
        if (!StringUtils.isEmpty(projectUrl)) {
            kwciagentCreateCmd.add("--url", projectUrl);
        }
        kwciagentCreateCmd.add("--project-dir", getKwlpDir(workspace, envVars).getRemote());
        kwciagentCreateCmd.add("--settings-dir", getKwpsDir(workspace, envVars).getRemote());
        kwciagentCreateCmd.add("--build-spec", envVars.expand(KlocworkUtil.getDefaultBuildSpec(buildSpec)));
        return kwciagentCreateCmd;
    }

    public ArgumentListBuilder getKwciagentSetCmd(EnvVars envVars, FilePath workspace)
                                        throws IOException, InterruptedException {

        validateParentProjectDir(getKwlpDir(workspace, envVars).getParent());

        ArgumentListBuilder kwciagentSetCmd = new ArgumentListBuilder("kwciagent", "set");
        kwciagentSetCmd.add("--project-dir", getKwlpDir(workspace, envVars).getRemote());
        String serverUrl = envVars.get(KlocworkConstants.KLOCWORK_URL);
        if (!StringUtils.isEmpty(serverUrl)) {
            URL url = new URL(serverUrl);
            kwciagentSetCmd.add("klocwork.host=" + url.getHost());
            kwciagentSetCmd.add("klocwork.port=" + Integer.toString(url.getPort()));
            kwciagentSetCmd.add("klocwork.project=" + envVars.get(KlocworkConstants.KLOCWORK_PROJECT));
        }
        return kwciagentSetCmd;
    }

    public ArgumentListBuilder getKwciagentListCmd(EnvVars envVars, FilePath workspace,
                                                   String diffList)
                                        throws IOException, InterruptedException {
        ArgumentListBuilder kwciagentRunCmd =
            new ArgumentListBuilder("kwciagent", "list");
        kwciagentRunCmd.add("--project-dir", getKwlpDir(workspace, envVars).getRemote());
        String licenseHost = envVars.get(KlocworkConstants.KLOCWORK_LICENSE_HOST);
        if (!StringUtils.isEmpty(licenseHost)) {
            kwciagentRunCmd.add("--license-host", licenseHost);
        }

        String licensePort = envVars.get(KlocworkConstants.KLOCWORK_LICENSE_PORT);
        if (!StringUtils.isEmpty(licensePort)) {
            kwciagentRunCmd.add("--license-port", licensePort);
        }

        kwciagentRunCmd.add("-F", "xml");

        if (!StringUtils.isEmpty(additionalOpts)) {
            kwciagentRunCmd.addTokenized(envVars.expand(additionalOpts));
        }

        // add list of changed files to end of kwciagent run command
        kwciagentRunCmd.addTokenized(diffList);

        return kwciagentRunCmd;
    }

    public ArgumentListBuilder getKwciagentRunCmd(EnvVars envVars, FilePath workspace,
                                                  String diffList)
                                        throws IOException, InterruptedException {
        ArgumentListBuilder kwciagentRunCmd =
            new ArgumentListBuilder("kwciagent", "run");
        kwciagentRunCmd.add("--project-dir", getKwlpDir(workspace, envVars).getRemote());

        if (!StringUtils.isEmpty(envVars.get(KlocworkConstants.KLOCWORK_LICENSE_HOST))) {
            kwciagentRunCmd.add("--license-host", envVars.get(KlocworkConstants.KLOCWORK_LICENSE_HOST));
            if (!StringUtils.isEmpty(envVars.get(KlocworkConstants.KLOCWORK_LICENSE_PORT))) {
                kwciagentRunCmd.add("--license-port", envVars.get(KlocworkConstants.KLOCWORK_LICENSE_PORT));
            }
        }

        //TODO: Clean up here
//        String xmlReport = envVars.expand(KlocworkUtil.getDefaultKwciagentReportFile(reportFile));
//        kwciagentRunCmd.add("-F", "xml", "--report", xmlReport);
        kwciagentRunCmd.add("-Y", "-L"); // Report nothing

        kwciagentRunCmd.add("--build-spec", envVars.expand(KlocworkUtil.getDefaultBuildSpec(buildSpec)));
        if (!StringUtils.isEmpty(additionalOpts)) {
            kwciagentRunCmd.addTokenized(envVars.expand(additionalOpts));
        }

        // add list of changed files to end of kwciagent run command
        kwciagentRunCmd.addTokenized(diffList);

        return kwciagentRunCmd;
    }

    // public ArgumentListBuilder getKwdtagentCmd(EnvVars envVars, FilePath workspace)
    //                                     throws IOException, InterruptedException {
    //     ArgumentListBuilder kwdtagentCmd =
    //         new ArgumentListBuilder("kwdtagent");
    //     kwdtagentCmd.add("--project-dir", getKwlpDir(workspace, envVars).getRemote());
    //     kwdtagentCmd.add("--port", kwdtagentPort);
    //     return kwdtagentCmd;
    // }

    public ArgumentListBuilder getGitDiffCmd(EnvVars envVars) {
        ArgumentListBuilder gitDiffCmd = new ArgumentListBuilder("git");
        gitDiffCmd.add("diff", "--name-only", envVars.expand(diffAnalysisConfig.getGitPreviousCommit()));
        gitDiffCmd.add(">", getDiffFileList(envVars));
        return gitDiffCmd;
    }

    /*
    function to check if a local project already exists.
    If the creation of a project went wrong before, there may be some left over .kwlp or .kwps directories
    so we need to make sure to clean these up.
    If both .kwlp and .kwps exist then we reuse them
     */
    public boolean hasExistingProject(FilePath workspace, EnvVars envVars)
        throws IOException, InterruptedException {
        FilePath kwlp = getKwlpDir(workspace, envVars);
        FilePath kwps = getKwpsDir(workspace, envVars);

        if (cleanupProject) {
            // cleanup is forced
            cleanupExistingProject(kwlp, kwps);
        }
        // else check if a cleanup is needed because a kwciagent create command
        // failed and left some things lying around that will make it fail
        // next time...
        if (kwlp.exists()) {
            if (kwps.exists()) {
                // both directories exist
                return true;
            } else {
                // clean up directories because something has gone wrong
                cleanupExistingProject(kwlp, kwps);
                return false;
            }
        } else if (kwps.exists()) {
            // clean up directories because something has gone wrong
            cleanupExistingProject(kwlp, kwps);
            return false;
        } else {
            // no existing project
            return false;
        }
    }

    private void validateParentProjectDir(FilePath dir) throws IOException, InterruptedException {
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private FilePath getKwlpDir(FilePath workspace, EnvVars envVars) {
        return new FilePath(
            workspace.child(envVars.expand(projectDir)), ".kwlp");
    }

    private FilePath getKwpsDir(FilePath workspace, EnvVars envVars) {
        return new FilePath(
            workspace.child(envVars.expand(projectDir)), ".kwps");
    }

    private void cleanupExistingProject(FilePath kwlp, FilePath kwps)
        throws IOException, InterruptedException {
        if (kwlp.exists()) {
            kwlp.deleteRecursive();
        }
        if (kwps.exists()) {
            kwps.deleteRecursive();
        }
    }

    public String getKwciagentDiffList(EnvVars envVars, FilePath workspace, Launcher launcher) throws AbortException {
        try {
            List<String> fileList = launcher.getChannel().call(
                new KlocworkBuildSpecParser(workspace.getRemote(),
                    envVars.expand(getDiffFileList(envVars)),
                    envVars.expand(KlocworkUtil.getBuildSpecPath(buildSpec, workspace))));
            return String.join(" ", fileList); // TODO: is Java 8 OK?
        } catch (IOException | InterruptedException ex) {
            throw new AbortException(ex.getMessage());
        }

    }

    public String getDiffFileList(EnvVars envVars) {
        String diffFileList = envVars.expand(diffAnalysisConfig.getDiffFileList());
        return diffFileList;
    }

    public boolean isGitDiffType() {
        return diffAnalysisConfig.isGitDiffType();
    }

    public String getBuildSpec() { return buildSpec; }
    public String getProjectDir() { return projectDir; }
    public boolean getCleanupProject() { return cleanupProject; }
    public String getReportFile() { return reportFile; }
    public String getAdditionalOpts() { return additionalOpts; }
    // public boolean getSetupKwdtagent() { return setupKwdtagent; }
    // public String getKwdtagentPort() { return kwdtagentPort; }
    public boolean getIncrementalAnalysis() { return incrementalAnalysis; }
    public KlocworkDiffAnalysisConfig getDiffAnalysisConfig() { return diffAnalysisConfig; }

    @Extension
    public static class DescriptorImpl extends Descriptor<KlocworkDesktopConfig> {
        public String getDisplayName() { return null; }
    }


}
