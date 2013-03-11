package org.jenkinsci.plugins.berserkers;


import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.*;
import hudson.tasks.*;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
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
public class GridProject extends Project<GridProject,GridBuildRunner> implements TopLevelItem,Saveable{

    //private static final Logger LOGGER = Logger.getLogger(AbstractBuild.class.getName());
    
    private String goals;
    
    private String rootPOM;

    private DescribableList<Builder,Descriptor<Builder>> buildersvar;
    
      
        
    public GridProject(ItemGroup itemGroup, String name) {
        super(itemGroup, name);
        this.buildersvar = new DescribableList<Builder,Descriptor<Builder>>(this);
        try {
            buildersvar.add(new GridBuilder());
        } catch (IOException ex) {
            Logger.getLogger(GridProject.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public List<Builder> getBuilders() {
        return buildersvar.toList();
    }

    public DescribableList<Builder,Descriptor<Builder>> getBuildersList() {
        return buildersvar;
    }
    
    public String getGoals() {
        return goals;
    }
    
    public String getRootPOM() {
        return rootPOM;
    }
    
    @Override
    protected void submit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, Descriptor.FormException {
        super.submit(req,rsp);
        rootPOM = Util.fixEmpty(req.getParameter("rootPOM").trim());
        if(rootPOM!=null && rootPOM.equals("pom.xml"))   rootPOM=null;   // normalization
        goals = Util.fixEmpty(req.getParameter("goals").trim());
    }
    
    public TopLevelItemDescriptor getDescriptor() {
        return new DescriptorImpl(); 
    }
   
    @Override
    protected Class<GridBuildRunner> getBuildClass() {
        return GridBuildRunner.class;
    }

    public FormValidation doCheckFileInWorkspace(@QueryParameter String value) throws IOException, ServletException {
        return FormValidation.ok();
    }
    
    
    public List<Descriptor<Builder>> getBuildersForGrid() {
        List<Descriptor<Builder>> list = new ArrayList<Descriptor<Builder>>();
        list.add(buildersvar.get(0).getDescriptor());
        return list;
    }    
   

//    @Override
//    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
//        super.onLoad(parent, name);
//        System.out.println("LOADED FROM DISK Builderu tady:"+super.getBuildersList().size());
//    }
    
//    @Override
//    public Label getAssignedLabel() {
//        return new LabelAtom("suse1");
//    }    
    
    @Extension
    public static final class DescriptorImpl extends AbstractProjectDescriptor {
        public String getDisplayName() {
            System.out.println("START");
            return "Diploma thesis entry in development!";
        }
        @Override
        public String getConfigPage() {
            return super.getConfigPage();
        }

        @Override
        public TopLevelItem newInstance(ItemGroup paramItemGroup, String paramString) {
            return new GridProject(Hudson.getInstance(), paramString);
        }
    }

    
}
