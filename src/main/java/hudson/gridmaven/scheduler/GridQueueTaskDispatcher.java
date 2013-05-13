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

@Extension
public class GridQueueTaskDispatcher extends QueueTaskDispatcher {
	private static final Maven.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class);

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
