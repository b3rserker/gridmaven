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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

/**
 *
 * @author berserker
 */
public class HadoopInstance {

    private Configuration conf;
    private FileSystem fs;

    public HadoopInstance() {
        Configuration conf = new Configuration();
        conf.set("fs.default.name", "hdfs://localhost:9000/");
        conf.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
        conf.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem");

        // Workaround bug with hadoop Configuration classloader, which
        // do not know abou current classloader and replaces it with
        // CurrentThreadContextClassloader, and that cant see hadoop libraries loaded.
        Class a = this.getClass();
        ClassLoader loader = a.getClassLoader();
        conf.setClassLoader(loader);

        this.conf = conf;
        // Retrieve fs if it can be done
        try {
            this.fs = FileSystem.get(conf);
        } catch (IOException ex) {
            ex.printStackTrace();
            Logger.getLogger(HadoopInstance.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void add(String src, String dest) {
        Path s = new Path(src);
        Path d = new Path(dest);
        if (fileExists(src)) {
            return;
        }
        try {
            fs.copyFromLocalFile(s, d);
        } catch (IOException ex) {
            Logger.getLogger(HadoopInstance.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void quickAdd(String src) {
        Path s = new Path(src);
        Path d = new Path("/");

        if (fileExists(src)) {
            return;
        }
        try {

            fs.copyFromLocalFile(s, d);
        } catch (IOException ex) {
            Logger.getLogger(HadoopInstance.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void get(String src, String dest) {
        Path s = new Path(src);
        Path d = new Path(dest);
        try {
            fs.copyToLocalFile(s, d);
        } catch (IOException ex) {
            Logger.getLogger(HadoopInstance.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void listFiles(String path) {
        Path p = new Path(path);
        try {
            FileStatus[] status = fs.listStatus(p);
            if (status.length < 1) {
                System.out.println("Zero files stored in HDFS");
            }
            for (int i = 0; i < status.length; i++) {
                System.out.println("Reading file: " + status[i].getPath());
            }
        } catch (IOException ex) {
            Logger.getLogger(HadoopInstance.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private boolean fileExists(String f) {
        FileStatus[] status;
        String objectName = f.substring(f.lastIndexOf('/'),f.length());
        Path p = new Path(objectName);
        try {
            status = fs.listStatus(p);
            if (status == null)
                return false;
        } catch (NullPointerException ex) {
            //Logger.getLogger(HadoopInstance.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        } catch (IOException ex) {
            Logger.getLogger(HadoopInstance.class.getName()).log(Level.SEVERE, null, ex);
        }
        return true;
    }
}