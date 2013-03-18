package org.jenkinsci.plugins.submodules;


import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.tasks.*;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.*;

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
public class GridSubProject extends Project<GridSubProject,GridSubBuildRunner> implements TopLevelItem,Saveable{

    //private static final Logger LOGGER = Logger.getLogger(AbstractBuild.class.getName());
      
    public GridSubProject(ItemGroup itemGroup, String name) {
        super(itemGroup, name);
    }

    @Override
    public String getDisplayName() {
        return "SPECIALJOB";
        
    }
    public TopLevelItemDescriptor getDescriptor() {
        return new DescriptorImpl(); 
    }
   
    @Override
    protected Class<GridSubBuildRunner> getBuildClass() {
        return GridSubBuildRunner.class;
    }

    @Override
    public Label getAssignedLabel() {
        return new LabelAtom("master");
    }    
    
    public static final class DescriptorImpl extends AbstractProjectDescriptor {
        public String getDisplayName() {
            System.out.println("SUBMODULE START DESTCRIMPL");
            return "Submodule project in development!";
        }

        @Override
        public TopLevelItem newInstance(ItemGroup paramItemGroup, String paramString) {
            return new GridSubProject(Hudson.getInstance(), paramString);
        }
    }

    
}
