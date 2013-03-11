/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.berserkers;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Saveable;
import hudson.tasks.BatchFile;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Messages;
import hudson.tasks.Shell;
import hudson.util.DescribableList;
import java.io.IOException;
import java.util.List;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author berserker
 */
public class GridBuilder extends Builder {

    private final String task;

    @DataBoundConstructor
    public GridBuilder(String task) {
        this.task = task;
    }
    

    public String getTask() {
        return task;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, 
    BuildListener listener) throws InterruptedException, IOException {
    
        return true;
    }  

    public void save() throws IOException {
        save();
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public String getDisplayName() {
            return "HERE AM I!";
        }
        
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}