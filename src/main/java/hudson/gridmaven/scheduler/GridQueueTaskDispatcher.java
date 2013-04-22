package hudson.gridmaven.scheduler;

import hudson.Extension;
import hudson.gridmaven.MavenModule;
import hudson.gridmaven.MavenModuleSet;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.BuildableItem;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.model.queue.CauseOfBlockage;
import jenkins.model.Jenkins;

@Extension
public class GridQueueTaskDispatcher extends QueueTaskDispatcher {
	//private static final SmartJenkinsComputerManager COM_MANAGER = SmartJenkinsComputerManager.getInstance();

        @Override
	public CauseOfBlockage canTake(Node node, BuildableItem item) {
            Queue.Task t = item.task;
            String n = node.getNodeName();
            Label l = item.getAssignedLabel();
            
            // If it's gridproject, we will schedule
            if (t instanceof hudson.gridmaven.MavenModuleSet || t instanceof hudson.gridmaven.MavenModule){
                if (item.task instanceof MavenModuleSet && (node instanceof Jenkins))
                    return null;
                else if (item.task instanceof MavenModule && node.getNodeName().contains("suse")) {
                    return null;
                }
                return new BecauseOfSmartJenkinsSchedule();
            }
            
            // Not a grid project
            // But we override all jobs to master, slaves are slower for devel
            if (node instanceof Jenkins)
                return null;
            else
               return new BecauseOfSmartJenkinsSchedule(); 
	}

	private static class BecauseOfSmartJenkinsSchedule extends CauseOfBlockage {

		@Override
		public String getShortDescription() {
			return "Blocked by Smart-Jenkins";
		}
                
	}
}
