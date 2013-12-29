/*
 * The MIT License
 *
 * Copyright 2013 berserker.
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Filip Hubik
 * This class delivers information about module
 * and HDFS preferences to slave as one entity.
 */
public class HadoopSlaveRequestInfo implements Serializable{

    private static final long serialVersionUID = 0L;
    
    public String mArtifact;
    public String mVersion;
    public String mPackaging;
    public String hdfsUrl;
    public String rArtifact;
    public String rGroupId;
    public String rVersion;
    public String rName;
    public String jobName;    
    public Map<String, String> entrySet;
    public List<UpStreamDep> upStreamDeps = new ArrayList<UpStreamDep>();
    public String mavenExePath;
    
    public HadoopSlaveRequestInfo() {
    }

    public UpStreamDep addUpStreamDep(String a, String g, String v, String p) {
        UpStreamDep u = new UpStreamDep(a, g, v, p);
        upStreamDeps.add(u);
        return u;
    }

    public class UpStreamDep implements Serializable {

        private static final long serialVersionUID = 1L;
        public String art;
        public String group;
        public String ver;
        public String pkg;

        public UpStreamDep(String a, String g, String v, String p) {
            art = a;
            group = g;
            ver = v;
            pkg = p;

        }
    }

    
}
