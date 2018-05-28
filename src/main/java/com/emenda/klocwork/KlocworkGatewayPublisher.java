package com.emenda.klocwork;

import com.emenda.klocwork.config.KlocworkGatewayConfig;
import com.emenda.klocwork.config.KlocworkGatewayServerConfig;
import com.emenda.klocwork.services.KlocworkApiConnection;
import com.emenda.klocwork.util.KlocworkUtil;
import com.emenda.klocwork.util.KlocworkXMLReportParser;

import org.apache.commons.lang3.StringUtils;

import hudson.AbortException;
import hudson.Launcher;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;

import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.InterruptedException;
import java.net.URLEncoder;


public class KlocworkGatewayPublisher extends Publisher implements SimpleBuildStep {

    private final KlocworkGatewayConfig gatewayConfig;
    private int totalIssuesDesktop;
    private int thresholdDesktop;

    @DataBoundConstructor
    public KlocworkGatewayPublisher(KlocworkGatewayConfig gatewayConfig) {
        this.gatewayConfig = gatewayConfig;
        this.totalIssuesDesktop = 0;
        this.thresholdDesktop = 0;
    }

    public KlocworkGatewayConfig getGatewayConfig() {
        return gatewayConfig;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
    throws AbortException {
        EnvVars envVars = null;
        try {
            envVars = build.getEnvironment(listener);
        } catch (IOException | InterruptedException ex) {
            throw new AbortException(ex.getMessage());
        }
        perform(build, envVars, workspace, launcher, listener);
    }


    public void perform(Run<?, ?> build, EnvVars envVars, FilePath workspace, Launcher launcher, TaskListener listener)
    throws AbortException {
        KlocworkLogger logger = new KlocworkLogger("KlocworkGatewayPublisher", listener.getLogger());
        if (gatewayConfig.getEnableServerGateway()) {
            logger.logMessage("Performing Klocwork Server Gateway");
            for (KlocworkGatewayServerConfig pfConfig : gatewayConfig.getGatewayServerConfigs()) {
                String request = "action=search&project=" + envVars.get(KlocworkConstants.KLOCWORK_PROJECT);
                if (!StringUtils.isEmpty(pfConfig.getQuery())) {
                    try {
                        request += "&query=";
                        if(!pfConfig.getQuery().toLowerCase().contains("grouping:off")
                                && !pfConfig.getQuery().toLowerCase().contains("grouping:on")){
                            request += "grouping:off ";
                        }
                        request += URLEncoder.encode(pfConfig.getQuery(), "UTF-8");
                    } catch (UnsupportedEncodingException ex) {
                        throw new AbortException(ex.getMessage());
                    }

                }
                logger.logMessage("Condition Name : " + pfConfig.getConditionName());
                logger.logMessage("Using query: " + request);
                JSONArray response;

                try {
                    String[] ltokenLine = KlocworkUtil.getLtokenValues(envVars, launcher);
                    KlocworkApiConnection kwService = new KlocworkApiConnection(
                                    envVars.get(KlocworkConstants.KLOCWORK_URL),
                                    ltokenLine[KlocworkConstants.LTOKEN_USER_INDEX],
                                    ltokenLine[KlocworkConstants.LTOKEN_HASH_INDEX]);
                    response = kwService.sendRequest(request);
                } catch (IOException ex) {
                    throw new AbortException("Error: failed to connect to the Klocwork" +
                        " web API.\nCause: " + ex.getMessage());
                }

                logger.logMessage("Number of issues returned : " + Integer.toString(response.size()));
                logger.logMessage("Configured Threshold : " + pfConfig.getThreshold());
                if (response.size() >= Integer.parseInt(pfConfig.getThreshold())) {
                    logger.logMessage("Threshold exceeded. Marking build as failed.");
                    build.setResult(pfConfig.getResultValue());
                }
                for (int i = 0; i < response.size(); i++) {
                      JSONObject jObj = response.getJSONObject(i);
                      logger.logMessage(jObj.toString());
                }
            }
        }


        if (gatewayConfig.getEnableDesktopGateway()) {
			logger.logMessage("Performing Klocwork Desktop Gateway");

            String xmlReport = envVars.expand(KlocworkUtil.getDefaultKwciagentReportFile(
                gatewayConfig.getGatewayDesktopConfig().getReportFile()));
			logger.logMessage("Working with report file: " + xmlReport);

            try {
                totalIssuesDesktop = launcher.getChannel().call(
                    new KlocworkXMLReportParser(
                    workspace.getRemote(), xmlReport));
                logger.logMessage("Total Desktop Issues : " +
                    Integer.toString(totalIssuesDesktop));
                logger.logMessage("Configured Threshold : " +
                    gatewayConfig.getGatewayDesktopConfig().getThreshold());
                final String threshold = gatewayConfig.getGatewayDesktopConfig().getThreshold();
                thresholdDesktop = StringUtils.isNotEmpty(threshold) ? Integer.parseInt(threshold): 0;
                if (totalIssuesDesktop >= thresholdDesktop) {
                    logger.logMessage("Threshold exceeded. Marking build as failed.");
                    build.setResult(Result.FAILURE);
                }

            } catch (InterruptedException | IOException ex) {
                throw new AbortException(ex.getMessage());
            }
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public String getDisplayName() {
            return KlocworkConstants.KLOCWORK_QUALITY_GATEWAY_DISPLAY_NAME;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req,formData);
        }
    }
}
