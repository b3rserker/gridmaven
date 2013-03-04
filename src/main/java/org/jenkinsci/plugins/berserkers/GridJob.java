package org.jenkinsci.plugins.berserkers;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.AbstractProject.AbstractProjectDescriptor;
import hudson.model.BuildListener;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Project;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import java.io.IOException;
import java.util.SortedMap;
import javax.servlet.ServletException;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link HelloWorldBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class GridJob extends AbstractProject<GridJob,GridRun> implements TopLevelItem{

    /**
     * List of active {@link Builder}s configured for this project.
     */
    private DescribableList<Builder,Descriptor<Builder>> builders =
            new DescribableList<Builder,Descriptor<Builder>>(this);

    /**
     * List of active {@link Publisher}s configured for this project.
     */
    private DescribableList<Publisher,Descriptor<Publisher>> publishers =
            new DescribableList<Publisher,Descriptor<Publisher>>(this);

    /**
     * List of active {@link BuildWrapper}s configured for this project.
     */
    private DescribableList<BuildWrapper,Descriptor<BuildWrapper>> buildWrappers =
            new DescribableList<BuildWrapper,Descriptor<BuildWrapper>>(this);    
    
    public GridJob(ItemGroup itemGroup, String name) {
        super(itemGroup, name);
    }

    
    public TopLevelItemDescriptor getDescriptor() {
        return new DescriptorImpl(); 
    }

    @Override
    public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected Class<GridRun> getBuildClass() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isFingerprintConfigured() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void buildDependencyGraph(DependencyGraph graph) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Extension
    public static final class DescriptorImpl extends AbstractProjectDescriptor {
        public String getDisplayName() {
            return "Diploma thesis entry in development!";
        }
        @Override
        public String getConfigPage() {
            return super.getConfigPage();
        }

        @Override
        public TopLevelItem newInstance(ItemGroup paramItemGroup, String paramString) {
            return new GridJob(Hudson.getInstance(), paramString);
        }
    }

    
}