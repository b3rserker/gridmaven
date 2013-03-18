/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.berserkers;

import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.slaves.Cloud;
import hudson.tasks.BatchFile;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Messages;
import hudson.tasks.Shell;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author berserker
 */
public class GridBuilder extends Builder {

    private String goals;
    
    private String rootPOM;
    public void setRootPOM(String a){
        rootPOM = a;
    }
    
    public void setGoals(String a){
        goals = a;
    }

    public String getRootPOM(){
        return rootPOM;
    }
    
    public String getGoals(){
        return goals;
    }    
    
    @DataBoundConstructor
    public GridBuilder(String rootPOM, String goals) {
        this.rootPOM = rootPOM;
        this.goals = goals;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) 
        throws InterruptedException {
        return perform(build,launcher,(TaskListener)listener);
    }    
    
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, TaskListener listener) 
        throws InterruptedException {
        listener.getLogger().println("This is diploma thesis run");
        FilePath ws = build.getWorkspace();
        FilePath script=null;
        try {
            try {
                script = createScriptFile(ws);
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace(listener.fatalError(Messages.CommandInterpreter_UnableToProduceScript()));
                return false;
            }

            int r;
            try {
                EnvVars envVars = build.getEnvironment(listener);
                // on Windows environment variables are converted to all upper case,
                // but no such conversions are done on Unix, so to make this cross-platform,
                // convert variables to all upper cases.
                for(Map.Entry<String,String> e : build.getBuildVariables().entrySet())
                    envVars.put(e.getKey(),e.getValue());

                r = launcher.launch().cmds(buildCommandLine(script)).envs(envVars).
                        stdout(listener).pwd(ws).join();
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace(listener.fatalError(Messages.CommandInterpreter_CommandFailed()));
                r = -1;
            }
            return r==0;
        } finally {
            try {
                if(script!=null)
                script.delete();
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace( listener.fatalError(Messages.CommandInterpreter_UnableToDelete(script)) );
            }
        }
    }       
    
    /**
     * Creates a script file in a temporary name in the specified directory.
     */
    public FilePath createScriptFile(FilePath dir) throws IOException, InterruptedException {
        return dir.createTextTempFile("hudson", getFileExtension(), getContents(), false);
    }
    
    public String[] buildCommandLine(FilePath script) {
        if(goals.startsWith("#!")) {
            // interpreter override
            int end = goals.indexOf('\n');
            if(end<0)   end=goals.length();
            List<String> args = new ArrayList<String>();
            args.addAll(Arrays.asList(Util.tokenize(goals.substring(0,end).trim())));
            args.add(script.getRemote());
            args.set(0,args.get(0).substring(2));   // trim off "#!"
            return args.toArray(new String[args.size()]);
        } else 
            return new String[] { getDescriptor().getShellOrDefault(script.getChannel()),
                "-xe", script.getRemote()};
    }

    protected String getContents() {
        return addCrForNonASCII(fixCrLf(goals));
    }

    protected String getFileExtension() {
        return ".sh";
    }

    private static String addCrForNonASCII(String s) {
        if(!s.startsWith("#!")) {
            if (s.indexOf('\n')!=0) {
                return "\n" + s;
            }
        }

        return s;
    }    
    
    private static String fixCrLf(String s) {
        // eliminate CR
        int idx;
        while((idx=s.indexOf("\r\n"))!=-1)
            s = s.substring(0,idx)+s.substring(idx+1);
        return s;
    }    

    @Override
    public GridBuilder.DescriptorImpl getDescriptor() {
        return (GridBuilder.DescriptorImpl)super.getDescriptor();
    }
    
    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public String getDisplayName() {
            return "HERE AM I!";
        }
        
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
        
        /**
         * Shell executable, or null to default.
         */
        private String shell;

        public DescriptorImpl() {
            load();
        }

        public String getShell() {
            return shell;
        }

        /**
         *  @deprecated 1.403
         *      Use {@link #getShellOrDefault(hudson.remoting.VirtualChannel) }.
         */
        public String getShellOrDefault() {
            if(shell==null)
                return Functions.isWindows() ?"sh":"/bin/sh";
            return shell;
        }

        public String getShellOrDefault(VirtualChannel channel) {
            if (shell != null) 
                return shell;

            String interpreter = null;
            try {
                interpreter = channel.call(new GridBuilder.DescriptorImpl.Shellinterpreter());
            } catch (IOException e) {
                LOGGER.warning(e.getMessage());
            } catch (InterruptedException e) {
                LOGGER.warning(e.getMessage());
            }
            if (interpreter == null) {
                interpreter = getShellOrDefault();
            }

            return interpreter;
        }
        
        public void setShell(String shell) {
            this.shell = Util.fixEmptyAndTrim(shell);
            save();
        }

        @Override
        public GridBuilder newInstance(StaplerRequest req, JSONObject data) {
            return new GridBuilder(data.getString("goals"),data.getString("rootPOM"));
            //return new GridBuilder();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject data) {
            setShell(req.getParameter("shell"));
            return true;
        }

        /**
         * Check the existence of sh in the given location.
         */
        public FormValidation doCheck(@QueryParameter String value) {
            // Executable requires admin permission
            return FormValidation.validateExecutable(value); 
        }
        
        private static final class Shellinterpreter implements Callable<String, IOException> {

            private static final long serialVersionUID = 1L;

            public String call() throws IOException {
                return Functions.isWindows() ? "sh" : "/bin/sh";
            }
        }
    }
    private static final Logger LOGGER = Logger.getLogger(Shell.class.getName());
}
