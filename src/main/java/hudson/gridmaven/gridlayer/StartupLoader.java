/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.gridmaven.gridlayer;

import hudson.Extension;
import hudson.model.Hudson;
import hudson.model.Computer;
import hudson.model.listeners.ItemListener;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.URL;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URLClassLoader;
import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.classworlds.*;

/**
 * After all the projects have loaded, start Hadoop name node.
 */
@Extension
public class StartupLoader extends ItemListener {

    private NameNodeStartTask masterNameNode;
    
    //Afrer all jobs loaded, start hadoop
    @Override
    public void onLoaded() {
        try { 
            PluginImpl p = PluginImpl.get();
            p.postInit();
            String hdfsUrl = p.getHdfsUrl();
            if(hdfsUrl!=null) {
                // start Hadoop namenode and data node
                StreamTaskListener listener = new StreamTaskListener(System.out);
                File root = Hudson.getInstance().getRootDir();
                p.channel = PluginImpl.createHadoopVM(root, listener);
                masterNameNode = new NameNodeStartTask(root, hdfsUrl, p.getHdfsAddress().getPort());
                p.channel.call(masterNameNode);
  
                Computer c = Hudson.getInstance().toComputer();
                String masterName = c.getHostName();
                if(masterName ==null)
                    listener.getLogger().println("Unable to determine the hostname/IP address of the master. Skipping Hadoop deployment");
                p.channel.callAsync(new SlaveStartTask(c, listener, hdfsUrl, masterName));
            } else {
                LOGGER.info("Skipping Hadoop initialization because we don't know the root URL.");
                p.page.pendingConfiguration = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.log(Level.WARNING, "Failed to start Hadoop on master",e);
        }
    }

    public static PluginImpl get() {
        return Hudson.getInstance().getPlugin(PluginImpl.class);
    }

    private static final Logger LOGGER = Logger.getLogger(StartupLoader.class.getName());
}
