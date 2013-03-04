package org.jenkinsci.plugins.berserkers;

import hudson.model.AbstractBuild;
import hudson.model.Build;
import hudson.model.Job;
import hudson.model.Run;
import java.io.IOException;

/**
 *
 * @author berserker
 */
public class HelloWorldRun extends AbstractBuild<HelloWorldJob,HelloWorldRun>{

    public HelloWorldRun(HelloWorldJob job) throws IOException {
        super(job);
    }

    @Override
    public void run() {
        throw new UnsupportedOperationException("3Not supported yet.");
    }
    
}
