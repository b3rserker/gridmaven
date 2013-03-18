package org.jenkinsci.plugins.berserkers;


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
public class GridBuildRunner extends Build<GridProject,GridBuildRunner>{
    
    public GridBuildRunner(GridProject job) throws IOException {
        super(job);
    }

    public GridBuildRunner(GridProject project, File buildDir) throws IOException {
        super(project, buildDir);
    } 

    @Override
    public void run() {
        this.execute(new Execution());
    }
    
    
    
protected class Execution extends AbstractBuildExecution {
        protected Result doRun(BuildListener listener) throws Exception {
            if(!preBuild(listener,project.getBuilders()))
                return FAILURE;
            if(!preBuild(listener,project.getPublishersList()))
                return FAILURE;

            Result r = null;
            try {
                List<BuildWrapper> wrappers = 
                        new ArrayList<BuildWrapper>(project.getBuildWrappers().values());
                
                ParametersAction parameters = getAction(ParametersAction.class);
                if (parameters != null)
                    parameters.createBuildWrappers(GridBuildRunner.this,wrappers);

                for( BuildWrapper w : wrappers ) {
                    Environment e = w.setUp((AbstractBuild<?,?>)GridBuildRunner.this, launcher, listener);
                    if(e==null)
                        return (r = FAILURE);
                    buildEnvironments.add(e);
                }
                GridSubProject b;
                b = new GridSubProject(this.getProject().getParent(), "");
                b.scheduleBuild(new Cause.UpstreamCause((Run<?,?>)GridBuildRunner.this));
                if(!build(listener,project.getBuilders()))
                    r = FAILURE;
            } catch (InterruptedException e) {
                r = Executor.currentExecutor().abortResult();
                // not calling Executor.
                // recordCauseOfInterruption here. We do that where this exception is consumed.
                throw e;
            } finally {
                if (r != null) setResult(r);
                // tear down in reverse order
                boolean failed=false;
                for( int i=buildEnvironments.size()-1; i>=0; i-- ) {
                    if (!buildEnvironments.get(i).tearDown(GridBuildRunner.this,listener)) {
                        failed=true;
                    }                    
                }
                // WARNING The return in the finally clause will trump any return before
                if (failed) return FAILURE;
            }

            return r;
        }

        public void post2(BuildListener listener) throws 
                IOException, InterruptedException {
            if (!performAllBuildSteps(listener, project.getPublishersList(), true))
                setResult(FAILURE);
            if (!performAllBuildSteps(listener, project.getProperties(), true))
                setResult(FAILURE);
        }

        @Override
        public void cleanUp(BuildListener listener) throws Exception {
            // at this point it's too late to mark the build as a failure, so ignore return value.
            performAllBuildSteps(listener, project.getPublishersList(), false);
            performAllBuildSteps(listener, project.getProperties(), false);
            super.cleanUp(listener);
        }

        private boolean build(BuildListener listener, Collection<Builder> steps) 
                throws IOException, InterruptedException {
            for( BuildStep bs : steps )
                if(!perform(bs,listener)) {
                    LOGGER.fine(MessageFormat.format
                            ("{0} : {1} failed", GridBuildRunner.this.toString(), bs));
                    return false;
                }
            return true;
        }        

         
    }

    private static final Logger LOGGER = Logger.getLogger(Build.class.getName());

    

}
