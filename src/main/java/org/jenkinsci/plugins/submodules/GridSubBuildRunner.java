package org.jenkinsci.plugins.submodules;


import org.jenkinsci.plugins.submodules.GridSubProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Environment;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.model.Result;
import static hudson.model.Result.FAILURE;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.Visitor;
import org.dom4j.io.SAXReader;

/**
 *
 * @author berserker
 */
public class GridSubBuildRunner extends Build<GridSubProject,GridSubBuildRunner>{
    
    public GridSubBuildRunner(GridSubProject job) throws IOException {
        super(job);
    }

    public GridSubBuildRunner(GridSubProject project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    @Override
    public void run() {
        this.execute(new GridSubBuildRunner.Execution());
    }
    
protected class Execution extends AbstractBuild.AbstractBuildExecution {
        protected Result doRun(BuildListener listener) throws Exception {
            listener.getLogger().print("This is submodule build! YEAH! Unstable!");
            
            return Result.UNSTABLE;
        }

        public void post2(BuildListener listener) throws 
                IOException, InterruptedException {
            if (!performAllBuildSteps(listener, project.getPublishersList(), true))
                setResult(FAILURE);
            if (!performAllBuildSteps(listener, project.getProperties(), true))
                setResult(FAILURE);
        }
        
        private boolean build(BuildListener listener, Collection<Builder> steps) 
                throws IOException, InterruptedException {
            for( BuildStep bs : steps )
                if(!perform(bs,listener)) {
                    LOGGER.fine(MessageFormat.format
                            ("{0} : {1} failed", GridSubBuildRunner.this.toString(), bs));
                    return false;
                }
            return true;
        }        

         
    }

    private static final Logger LOGGER = Logger.getLogger(Build.class.getName());

    

}
