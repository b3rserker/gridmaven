/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Olivier Lamy
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.gridmaven;


import hudson.EnvVars;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.gridmaven.gridlayer.HadoopSlaveRequestInfo;
import hudson.gridmaven.gridlayer.HadoopSlaveRequestInfo.UpStreamDep;
import hudson.maven.agent.AbortException;
import hudson.maven.agent.Main;
import hudson.maven.agent.PluginManagerListener;
import hudson.gridmaven.reporters.SurefireArchiver;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.DelegatingCallable;
import hudson.remoting.VirtualChannel;
import hudson.util.IOException2;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.maven.BuildFailureException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.LifecycleExecutorListener;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReport;
import org.codehaus.classworlds.NoSuchRealmException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;

/**
 * {@link Callable} that invokes Maven CLI (in process) and drives a build.
 *
 * <p>
 * As a callable, this function returns the build result.
 *
 * <p>
 * This class defines a series of event callbacks, which are invoked during the build.
 * This allows subclass to monitor the progress of a build.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.133
 */
@SuppressWarnings("deprecation") // as we're restricted to Maven 2.x API here, but compile against Maven 3.x we cannot avoid deprecations
public abstract class MavenBuilder extends AbstractMavenBuilder implements DelegatingCallable<Result,IOException> {


    /**
     * Flag needs to be set at the constructor, so that this reflects
     * the setting at master.
     */
    private final boolean profile = MavenProcessFactory.profile;
    
    // Hadoop builpath of this specific module
    private final String buildPath;
    private final HadoopSlaveRequestInfo info;
    FileSystem fs;

    protected MavenBuilder(BuildListener listener, Collection<MavenModule> modules,
            List<String> goals, Map<String, String> systemProps, String buildPath, HadoopSlaveRequestInfo hadoopData) {
        super( listener, modules, goals, systemProps);
        this.buildPath = buildPath;
        this.info = hadoopData;
    }

    /**
     * Called before the whole build.
     */
    abstract void preBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException;

    /**
     * Called after the build has completed fully.
     */
    abstract void postBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException;

    /**
     * Called when a build enter another module.
     */
    abstract void preModule(MavenProject project) throws InterruptedException, IOException, AbortException;

    /**
     * Called when a build leaves a module.
     */
    abstract void postModule(MavenProject project) throws InterruptedException, IOException, AbortException;

    /**
     * Called before a mojo is executed
     */
    abstract void preExecute(MavenProject project, MojoInfo mojoInfo) throws IOException, InterruptedException, AbortException;

    /**
     * Called after a mojo has finished executing.
     */
    abstract void postExecute(MavenProject project, MojoInfo mojoInfo, Exception exception) throws IOException, InterruptedException, AbortException;

    /**
     * Called after a {@link MavenReport} is successfully generated.
     */
    abstract void onReportGenerated(MavenProject project, MavenReportInfo report) throws IOException, InterruptedException, AbortException;

    private Class<?> pluginManagerInterceptorClazz;
    private Class[] pluginManagerInterceptorListenerClazz;
    private Class<?> lifecycleInterceptorClazz;
    private Class[] lifecycleInterceptorListenerClazz;
    
    /**
     * This code is executed inside the maven jail process.
     */
    public Result call() throws IOException {
        
        // hold a ref on correct classloader for finally call as something is changing tccl 
        // and not restore it !
        ClassLoader mavenJailProcessClassLoader = Thread.currentThread().getContextClassLoader();        
        
        try {
            PrintStream logger = listener.getLogger();
            initializeAsynchronousExecutions();
            Adapter a = new Adapter(this);
            callSetListenerWithReflectOnInterceptors( a, mavenJailProcessClassLoader );
            
            /*
            PluginManagerInterceptor.setListener(a);
            LifecycleExecutorInterceptor.setListener(a);
            */
            
            markAsSuccess = false;

            registerSystemProperties();

            // ####### Hadoop stuff start
            Configuration conf = new Configuration();
            conf.set("fs.default.name", info.hdfsUrl);
            conf.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
            conf.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem");
            String moduleTar = info.mArtifact + "-" + info.mVersion + ".tar";
            String hdfsSource = "/tar/" + info.jobName + "/" + info.rName + "/" + moduleTar;
            fs = FileSystem.get(conf);

            String installCommand = "";

            // Untar
            logger.println("Untaring sources for artifact: "+info.mArtifact 
                    + "-" + info.mVersion + "." + info.mPackaging);
            try {
                getAndUntar(fs, hdfsSource, buildPath);
            } catch (Exception fe) {
                logger.println("Source data for this module not found in hdfs repository or hdfs error. Please try rebuild main project.");
                //fe.printStackTrace();
                return Result.FAILURE;
            }
            logger.println("Untared file: " + hdfsSource + " to " + buildPath + "\n");

            // Check if repository exists + HDFS working?
            Path repo = new Path("/repository");
            FileStatus[] status = fs.listStatus(repo);
            if (status != null) {
                if (status.length < 1) {
                    logger.println("Zero files stored in HDFS");
                }
//                // Print files stored in hdfs for debug
//                for (int i = 0; i < status.length; i++) {
//                    logger.println("Reading file: " + status[i].getPath());
//                }
            }
            else{
                logger.println("Creating hdfs repository.");
                if (!fs.mkdirs(repo)){
                    logger.println("Cannot create hdfs repository");
                    return Result.FAILURE;
                }
            }

            // Install artifacts
            if (info.upStreamDeps.size()>0)
                logger.println("Preinstalling artifacts:");
            for (UpStreamDep dep : info.upStreamDeps) {

                // Fetch deps from hdfs repository
                String artifactName = dep.art + "-" + dep.ver
                        + "." + dep.pkg;
                Path hdfsPath = new Path("/repository/" + artifactName);
                Path absPath = new Path(buildPath + "/deps");

                boolean success = (new File(buildPath + "/deps")).mkdirs();
                if (!success) {
                    //IOException e = new IOException();
                    //e.printStackTrace();
                }

                logger.println("Copying from hadoop path: " + hdfsPath + " to local path:" + absPath);
                try{
                    fs.copyToLocalFile(hdfsPath, absPath);
                }
                catch(IOException e){
                    logger.println("Prerequisite artifact needed for module build missing: "+artifactName);
                    return Result.FAILURE;
                }
                //goals.add("package");
                String s = "install:install-file -Dfile=./deps/"
                        + dep.art + "-" + dep.ver + "." + dep.pkg
                        + " -DgroupId=" + dep.group
                        + " -DartifactId=" + dep.art
                        + " -Dversion=" + dep.ver
                        + " -Dpackaging=" + dep.pkg;
                logger.println("Preinstalling artifact: " + s + "\n");
                //Shell b = new Shell(mvn.getExecutable(launcher) + s);
                //b.perform(MavenBuild.this, launcher, listener);
                installCommand += info.mavenExePath + " " + s + ";";
            }

            //logger.println("Executing: " + installCommand);
            try {
                if (!performWrapper(installCommand)) {
                    logger.println("Artifact installation failed!");
                }
            } catch (Exception e) {
                logger.println("Execute process of installing artifacts to local repository failed: "+installCommand);
                //logger.println("If you have shared NFS repository...TODO: "+installCommand);
                e.printStackTrace();
                return Result.FAILURE;
            }
            logger.println("Artifact installation finished\n");

            // ####### EOF Hadoop stuff, maven plugin continues
            
            logger.println("Executing main goal");
            // Lauch MAIN maven process
            logger.println(formatArgs(goals));
            int r = Main.launch(goals.toArray(new String[goals.size()]));           
            
            // now check the completion status of async ops
            long startTime = System.nanoTime();
            
            Result waitForAsyncExecutionsResult = waitForAsynchronousExecutions();
            if (waitForAsyncExecutionsResult != null) {
                return waitForAsyncExecutionsResult;
            }
            
            a.overheadTime += System.nanoTime()-startTime;

            if(profile) {
                NumberFormat n = NumberFormat.getInstance();
                logger.println("Total overhead was "+format(n,a.overheadTime)+"ms");
                Channel ch = Channel.current();
                logger.println("Class loading "   +format(n,ch.classLoadingTime.get())   +"ms, "+ch.classLoadingCount+" classes");
                logger.println("Resource loading "+format(n,ch.resourceLoadingTime.get())+"ms, "+ch.resourceLoadingCount+" times");                
            }
            
            // Building successfully finished?
            if(r!=0)    return Result.FAILURE;
            
            // ####### Hadoop stuff
            // Package artifact
            logger.println("Packaging...");
            try {
                performWrapper(info.mavenExePath + " -N -B package -Dmaven.test.skip=true -Dmaven.test.failure.ignore=true");
            } catch (InterruptedException ex) {
                logger.println("Artifact packaging failed!");
                Logger.getLogger(MavenBuilder.class.getName()).log(Level.SEVERE, null, ex);
                return Result.FAILURE;
            }
            logger.println("Package created\n");

            // Insert compiled artifact to hdfs repository
            String absolute = buildPath;
            //String relative = absolute.substring(absolute.lastIndexOf('/'), absolute.length());
            String artPath = absolute + "/target/" + artifact + "-" + version + "." + packaging;
            File normalPom = new File(artPath);
            if (!normalPom.exists()){
                artPath = absolute + "/target/" + artifact + "." + "jar";
                File specialJar = new File(artPath);
                if (!specialJar.exists()){
                    artPath = absolute + "/target/" + artifact + "-tests." + "jar";
                    File specialTestJar = new File(artPath);
                    if (!specialTestJar.exists()){
                        artPath = "";
                    }
                }
            }
            
            
            
            try {
                // Copy produced archives
                if (upStreamDeps.size() > 0 && !artPath.equals("")) {
                    String destName = "/repository/" + artifact + "-" + version + "." + packaging;
                    Path absArtifactPath = new Path(artPath);
                    logger.println("\nCopying from local path:"
                            + absArtifactPath + " to hadoop:" +destName);
                    fs.copyFromLocalFile(absArtifactPath, new Path(destName));
                
                // Copy only parent pom
                } else {
                    Path pomPath = new Path(absolute + "/pom.xml");
                    logger.println("\nCopying from local path:" + pomPath
                            + " to hadoop: /repository/" + artifact + "-" + version + "." + packaging);
                    String name = "/repository/" + artifact + "-" + version + "." + packaging;
                    
                    Path nameP = new Path(name);
                    FileStatus[] status2 = fs.listStatus(nameP);
                    if (status2 == null) 
                        try{
                        fs.copyFromLocalFile(pomPath, new Path(name));
                        }catch (Exception e){
                            logger.println("Exception");
                        }
                }
            } catch (Exception e) {
                logger.println("Failed to insert packaged artifact to hdfs repository! Maybe artifact only exists and this is not error.");
                //e.printStackTrace();
                //return Result.FAILURE;
            }
            
            logger.println("Inserting to hadoop finished"); 
            // ####### EOF Hadoop stuff, maven plugin continues      

            if(r==0)    return Result.SUCCESS;

            if(markAsSuccess) {
                logger.println(Messages.MavenBuilder_Failed());
                return Result.SUCCESS;
            }
            return Result.FAILURE;
        } catch (NoSuchMethodException e) {
            throw new IOException2(e);
        } catch (IllegalAccessException e) {
            throw new IOException2(e);
        } catch (RuntimeException e) {
            throw new IOException2(e);
        } catch (InvocationTargetException e) {
            throw new IOException2(e);
        } catch (ClassNotFoundException e) {
            throw new IOException2(e);
        } catch (NoSuchRealmException ex) {
            throw new IOException2(ex);
        }
        finally {
            
            //PluginManagerInterceptor.setListener(null);
            //LifecycleExecutorInterceptor.setListener(null);
            callSetListenerWithReflectOnInterceptorsQuietly( null, mavenJailProcessClassLoader );
        }

    }

    private void callSetListenerWithReflectOnInterceptors( PluginManagerListener pluginManagerListener, ClassLoader cl )
        throws ClassNotFoundException, SecurityException, NoSuchMethodException, IllegalArgumentException,
        IllegalAccessException, InvocationTargetException
    {
        if (pluginManagerInterceptorClazz == null)
            pluginManagerInterceptorClazz = cl.loadClass( "hudson.maven.agent.PluginManagerInterceptor" );
        if (pluginManagerInterceptorListenerClazz == null)    
            pluginManagerInterceptorListenerClazz = new Class[] { 
                cl.loadClass( "hudson.maven.agent.PluginManagerListener" ) };

        Method setListenerMethod =
            pluginManagerInterceptorClazz.getMethod( "setListener", pluginManagerInterceptorListenerClazz);
        setListenerMethod.invoke( null, new Object[] { pluginManagerListener } );

        if (lifecycleInterceptorClazz == null)
            lifecycleInterceptorClazz = cl.loadClass( "org.apache.maven.lifecycle.LifecycleExecutorInterceptor" );
        if (lifecycleInterceptorListenerClazz == null)   
            lifecycleInterceptorListenerClazz = new Class[] { 
                cl.loadClass( "org.apache.maven.lifecycle.LifecycleExecutorListener" ) };

        setListenerMethod =
            lifecycleInterceptorClazz.getMethod( "setListener", lifecycleInterceptorListenerClazz);

        setListenerMethod.invoke( null, new Object[] { pluginManagerListener } );
    }
    
    private void callSetListenerWithReflectOnInterceptorsQuietly( PluginManagerListener pluginManagerListener, ClassLoader cl )
    {
        try
        {
            callSetListenerWithReflectOnInterceptors(pluginManagerListener, cl);
        }
        catch ( SecurityException e )
        {
            throw new RuntimeException( e.getMessage(), e );
        }
        catch ( IllegalArgumentException e )
        {
            throw new RuntimeException( e.getMessage(), e );
        }
        catch ( ClassNotFoundException e )
        {
            throw new RuntimeException( e.getMessage(), e );
        }
        catch ( NoSuchMethodException e )
        {
            throw new RuntimeException( e.getMessage(), e );
        }
        catch ( IllegalAccessException e )
        {
            throw new RuntimeException( e.getMessage(), e );
        }
        catch ( InvocationTargetException e )
        {
            throw new RuntimeException( e.getMessage(), e );
        }
    }  


    /**
     * Receives {@link PluginManagerListener} and {@link LifecycleExecutorListener} events
     * and converts them to {@link MavenBuilder} events.
     */
    private static final class Adapter implements PluginManagerListener, LifecycleExecutorListener {
        /**
         * Used to detect when to fire {@link MavenReporter#enterModule}
         */
        private MavenProject lastModule;

        private final MavenBuilder listener;

        /**
         * Number of total nanoseconds {@link MavenBuilder} spent.
         */
        long overheadTime;

        public Adapter(MavenBuilder listener) {
            this.listener = listener;
        }

        public void preBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException {
            long startTime = System.nanoTime();
            listener.preBuild(session, rm, dispatcher);
            overheadTime += System.nanoTime()-startTime;
        }

        public void postBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException {
            long startTime = System.nanoTime();
            fireLeaveModule();
            listener.postBuild(session, rm, dispatcher);
            overheadTime += System.nanoTime()-startTime;
        }

        public void endModule() throws InterruptedException, IOException {
            long startTime = System.nanoTime();
            fireLeaveModule();
            overheadTime += System.nanoTime()-startTime;
        }

        public void preExecute(MavenProject project, MojoExecution exec, Mojo mojo, PlexusConfiguration mergedConfig, ExpressionEvaluator eval) throws IOException, InterruptedException {
            long startTime = System.nanoTime();
            if(lastModule!=project) {
                // module change
                fireLeaveModule();
                fireEnterModule(project);
            }

            listener.preExecute(project, new MojoInfo(exec, mojo, mergedConfig, eval));
            overheadTime += System.nanoTime()-startTime;
        }

        public void postExecute(MavenProject project, MojoExecution exec, Mojo mojo, PlexusConfiguration mergedConfig, ExpressionEvaluator eval, Exception exception) throws IOException, InterruptedException {
            long startTime = System.nanoTime();
            listener.postExecute(project, new MojoInfo(exec, mojo, mergedConfig, eval),exception);
            overheadTime += System.nanoTime()-startTime;
        }

        public void onReportGenerated(MavenReport report, MojoExecution mojoExecution, PlexusConfiguration mergedConfig, ExpressionEvaluator eval) throws IOException, InterruptedException {
            long startTime = System.nanoTime();
            listener.onReportGenerated(lastModule,new MavenReportInfo(mojoExecution,report,mergedConfig,eval));
            overheadTime += System.nanoTime()-startTime;
        }

        private void fireEnterModule(MavenProject project) throws InterruptedException, IOException {
            lastModule = project;
            listener.preModule(project);
        }

        private void fireLeaveModule() throws InterruptedException, IOException {
            if(lastModule!=null) {
                listener.postModule(lastModule);
                lastModule = null;
            }
        }
    }
    
    public void getAndUntar(FileSystem fs, String src, String targetPath) throws FileNotFoundException, IOException {
        BufferedOutputStream dest = null;
        InputStream tarArchiveStream = new FSDataInputStream(fs.open(new Path(src)));
        TarArchiveInputStream tis = new TarArchiveInputStream(new BufferedInputStream(tarArchiveStream));
        TarArchiveEntry entry = null;
        try {
            while ((entry = tis.getNextTarEntry()) != null) {
                int count;
                File outputFile = new File(targetPath, entry.getName());

                if (entry.isDirectory()) { // entry is a directory
                    if (!outputFile.exists()) {
                        outputFile.mkdirs();
                    }
                } else { // entry is a file
                    byte[] data = new byte[BUFFER_MAX];
                    FileOutputStream fos = new FileOutputStream(outputFile);
                    dest = new BufferedOutputStream(fos, BUFFER_MAX);
                    while ((count = tis.read(data, 0, BUFFER_MAX)) != -1) {
                        dest.write(data, 0, count);
                    }
                    dest.flush();
                    dest.close();
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            if (dest != null) {
                dest.flush();
                dest.close();
            }
            tis.close();
        }
    }
    public static final int BUFFER_MAX = 2048;
    
    
    // Methods from Shell.class, used for script that installs and packages artifacts
    public boolean performWrapper(String command) throws InterruptedException {
        FilePath ws = new FilePath(new File(buildPath));
//        String command = "mkdir hello";
        command = fixCrLf(command);
        FilePath script = null;
        try {
            script = ws.createTextTempFile("hudson", ".sh", addCrForNonASCII(fixCrLf(command)), false);
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError(hudson.tasks.Messages.CommandInterpreter_UnableToProduceScript()));
            return false;
        }
        int r;
        try {
            EnvVars envVars = new EnvVars();
            
            Iterator<Map.Entry<String, String>> entries = info.entrySet.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<String, String> entry = entries.next();
                envVars.put(entry.getKey(), entry.getValue());
            }
            
            Launcher launcher = ws.createLauncher(listener);
            //r = launcher.launch().cmds("echo").envs(envVars).stdout(logger).pwd(ws).join();
            r = launcher.launch().cmds(buildCommandLine(script, command)).envs(envVars).stdout(listener).pwd(ws).join();
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError(hudson.tasks.Messages.CommandInterpreter_CommandFailed()));
            r = -1;
        }
        return r == 0;
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

    public String[] buildCommandLine(FilePath script, String command) {
        return new String[]{getShellOrDefault(script.getChannel()), "-xe", script.getRemote()};
    }

    public String getShellOrDefault(VirtualChannel channel) {
        String interpreter = null;
        try {
            interpreter = channel.call(new Shellinterpreter());
        } catch (IOException ex) {
            Logger.getLogger(MavenBuilder.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(MavenBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (interpreter == null) {
            interpreter = getShellOrDefault();
        }
        return interpreter;
    }

    public String getShellOrDefault() {
        return Functions.isWindows() ? "sh" : "/bin/sh";
    }

    private static final class Shellinterpreter implements Callable<String, IOException> {
        private static final long serialVersionUID = 1L;
        public String call() throws IOException {
            return Functions.isWindows() ? "sh" : "/bin/sh";
        }
    }

    /**
     * Used by selected {@link MavenReporter}s to notify the maven build agent
     * that even though Maven is going to fail, we should report the build as
     * success.
     *
     * <p>
     * This rather ugly hook is necessary to mark builds as unstable, since
     * maven considers a test failure to be a build failure, which will otherwise
     * mark the build as FAILED.
     *
     * <p>
     * It's OK for this field to be static, because the JVM where this is actually
     * used is in the Maven JVM, so only one build is going on for the whole JVM.
     *
     * <p>
     * Even though this field is public, please consider this field reserved
     * for {@link SurefireArchiver}. Subject to change without notice.
     */
    public static boolean markAsSuccess;

    private static final long serialVersionUID = 1L;
}
