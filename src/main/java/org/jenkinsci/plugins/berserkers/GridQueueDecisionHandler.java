/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.berserkers;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.QueueDecisionHandler;
import java.util.List;
import jenkins.model.Jenkins;

/**
 *
 * @author berserker
 */
@Extension
public class GridQueueDecisionHandler extends QueueDecisionHandler {

    @Override
    public boolean shouldSchedule(Queue.Task p, List<Action> actions) {
        Jenkins jenkins = Jenkins.getInstance();
        List<Node> allNodes = jenkins.getNodes();
        for (Node n : allNodes) {
            System.out.println("Nody: "+n.getDisplayName());
        }
        System.out.println("Task assigned to:"+p.getAssignedLabel());
   
        for (Action action : actions) {
            System.out.println("Vypis akci: "+action.getDisplayName()+" "+action.getUrlName()
                    +" "+action.toString());
//			if (CauseAction.class.isAssignableFrom(action.getClass())) {
				List<Cause> causes = ((CauseAction) action).getCauses();
				for (Cause cause : causes) {
                                    System.out.println("Vypis causes:" +cause.getClass()
                                            +" "+cause.getShortDescription());
				}
//			}
	}

        return true;
    }

}
