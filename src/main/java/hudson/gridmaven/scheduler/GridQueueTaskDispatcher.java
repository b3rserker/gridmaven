/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Red Hat, Inc., Victor Glushenkov, Alan Harder, Olivier Lamy, Dominik Bartholdi
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
package hudson.gridmaven.scheduler;

import hudson.Extension;
import hudson.gridmaven.MavenModule;
import hudson.gridmaven.MavenModuleSet;
import hudson.gridmaven.MavenModuleSet.DescriptorImpl;
import hudson.model.AbstractProject;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.BuildableItem;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.model.queue.CauseOfBlockage;
import hudson.tasks.Maven;
import jenkins.model.Jenkins;

/**
 * This object provides extension of Jenkins scheduling policy. 
 * Only affects Grid Projects.
 * 
 * @author Filip Hubik
 */
@Extension
public class GridQueueTaskDispatcher extends QueueTaskDispatcher {
	private static final Maven.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class);

        
    /** 
     * Every executor on every node is passed thru this method as part
     * of schedule decision process. It this method doesn't return null,
     * is asociated task blocked.
    */        
    @Override
    public CauseOfBlockage canTake(Node node, BuildableItem item) {
        Queue.Task t = item.task;
        String n = node.getNodeName();
        Label l = item.getAssignedLabel();

        // If it's gridproject, we will schedule
        if (t instanceof hudson.gridmaven.MavenModuleSet || t instanceof hudson.gridmaven.MavenModule){
            if (t instanceof hudson.gridmaven.MavenModuleSet && !(node instanceof Jenkins))
                return new BecauseOfGridMaven();
    //                else if (item.task instanceof MavenModule && l.getName() == ((MavenModuleSet.DescriptorImpl)descriptor).getGridJobsLabel()) {
    //                    return null;
    //                }
    //                return new BecauseOfGridMaven();
        }

        return null;

        // Not a grid project
        // But we override all jobs to master, slaves are slower for devel
    //            if (node instanceof Jenkins)
    //                return null;
    //            else
    //               return new BecauseOfSmartJenkinsSchedule(); 
    }

    private static class BecauseOfGridMaven extends CauseOfBlockage {

            @Override
            public String getShortDescription() {
                    return "Blocked by GridMaven plugin";
            }

    }
}
