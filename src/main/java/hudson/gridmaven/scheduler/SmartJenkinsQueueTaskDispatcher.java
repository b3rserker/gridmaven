package hudson.gridmaven.scheduler;

import hudson.Extension;
import hudson.gridmaven.MavenModuleSet;
import hudson.model.Node;
import hudson.model.Queue.BuildableItem;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.model.queue.CauseOfBlockage;

@Extension
public class SmartJenkinsQueueTaskDispatcher extends QueueTaskDispatcher {
	//private static final SmartJenkinsComputerManager COM_MANAGER = SmartJenkinsComputerManager.getInstance();

	@Override
	public CauseOfBlockage canTake(Node node, BuildableItem item) {
		//final SmartJenkinsComputer slave = COM_MANAGER.getSlave(node.getNodeName());
//		if (slave != null && slave.getConfiguration().enable) {
//			return new BecauseOfSmartJenkinsSchedule();
//		}
//		return null;
            if (item.task.getClass() == MavenModuleSet.class)
                return null;
            return new BecauseOfSmartJenkinsSchedule();
	}

	private static class BecauseOfSmartJenkinsSchedule extends CauseOfBlockage {

		@Override
		public String getShortDescription() {
			return "Blocked by Smart-Jenkins";
		}
                
	}
}
