package jenkins.plugins.ui_samples.ProgressBar;

import lib.JenkinsTagLib

def st=namespace(lib.FormTagLib)

t=namespace(JenkinsTagLib.class)

//namespace("/lib/samples").sample(title:_("Progress Bar")){
    // in this sample, we add extra margin around them
    //style(".progress-bar {margin:1em;}")
    //p("This page shows you how to use the progress bar widget")

    p("The 'pos' parameter controls the degree of progress, 0-100")
    t.progressBar(pos:30)
    t.progressBar(pos:60)
    t.progressBar(pos:90)
    p("-1 will draw the progress bar in the 'indeterminate' state");
    t.progressBar(pos:-1)
//}
