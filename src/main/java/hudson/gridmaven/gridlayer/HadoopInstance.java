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

import hudson.gridmaven.MavenModuleSetBuild;
import hudson.util.IOUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

/**
 * This class is wrapper for working with HDFS.
 * @author Filip Hubik
 */
public class HadoopInstance {

    private Configuration conf;
    private FileSystem fs;

    public HadoopInstance(Class c) {
        Configuration conf = new Configuration();
        conf.set("fs.default.name", "hdfs://localhost:9000/");
        conf.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
        conf.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem");

        // Workaround bug with hadoop Configuration classloader, which
        // do not know abou current classloader and replaces it with
        // CurrentThreadContextClassloader, and that cant see hadoop libraries loaded.
        Class a = c;
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
            ex.printStackTrace();
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

    /**
     * List files stored in HDFS for debugging purposes.
     */
    public void listFiles(String path, PrintStream print) {
        Path p = new Path(path);
        try {
            FileStatus[] status = fs.listStatus(p);
            if (status.length < 1) {
                print.println("Zero files stored in HDFS");
            }
            for (int i = 0; i < status.length; i++) {
                print.println("Reading file: " + status[i].getPath());
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

    /**
     * This method opens a path and recursively adds all files into tar, then
     * inserts it to HDFS.
     */
    public void tarAndInsert(String directoryPath, String tarGzPath) throws IOException {
        OutputStream fOut = null;
        //FileOutputStream fOut = null;
        BufferedOutputStream bOut = null;
        TarArchiveOutputStream tOut = null;
        //tarGzPath = "test.tar";
        //File f = new File(tarGzPath);
        Path f = new Path(tarGzPath);
        
        String skipDirectoryPath="";
        File l = new File(directoryPath);
        if (l.isDirectory())
            skipDirectoryPath = directoryPath;
        try {
            fs.delete(f, true);
            fOut = fs.create(f);
            //fOut = new FileOutputStream(f);
            bOut = new BufferedOutputStream(fOut);
            tOut = new TarArchiveOutputStream(bOut);
            
            addFileToTar(tOut, directoryPath, "", skipDirectoryPath);
            
            tOut.finish();
            tOut.close();
            bOut.close();
            fOut.close();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.getLogger(HadoopInstance.class.getName()).log(Level.SEVERE, null, e);
        }
    }

    private void addFileToTar(TarArchiveOutputStream tOut, String path, String base, String root) throws IOException {
        if (!root.equals(path)) {
            File f = new File(path);
            String entryName = base + f.getName();
            TarArchiveEntry tarEntry = new TarArchiveEntry(f, entryName);
            tOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            tOut.putArchiveEntry(tarEntry);
            
            if (f.isFile()) {
                IOUtils.copy(new FileInputStream(f), tOut);
                tOut.closeArchiveEntry();
            } else {
                tOut.closeArchiveEntry();
                File[] children = f.listFiles();
                if (children != null) {
                    for (File child : children) {
                        addFileToTar(tOut, child.getAbsolutePath(), entryName + "/", root);
                    }
                }
            }
        }
        else{
            File f = new File(path);
            File[] children = f.listFiles();
            if (children != null) {
                    for (File child : children) {
                        addFileToTar(tOut, child.getAbsolutePath(), "", root);
                    }
                }
        }
    }
    
    public void untarToLocalFile (){
        
    }

    public FileSystem getFs() {
        return fs;
    }
    
    /**
     * This method decompress filesystem structure from HDFS archive
     */
    public void getAndUntar(String src, String targetPath) throws FileNotFoundException, IOException {
        BufferedOutputStream dest = null;
        InputStream tarArchiveStream = new FSDataInputStream(fs.open(new Path(src)));
        TarArchiveInputStream tis = new TarArchiveInputStream(new BufferedInputStream(tarArchiveStream));
        TarArchiveEntry entry = null;
        try {
            while ((entry = tis.getNextTarEntry()) != null) {
                int count;
                File outputFile = new File(targetPath, entry.getName());

                if (entry.isDirectory()) { // entry is a directory
                    if (!outputFile.exists()) {
                        outputFile.mkdirs();
                    }
                } else { // entry is a file
                    byte[] data = new byte[BUFFER_MAX];
                    FileOutputStream fos = new FileOutputStream(outputFile);
                    dest = new BufferedOutputStream(fos, BUFFER_MAX);
                    while ((count = tis.read(data, 0, BUFFER_MAX)) != -1) {
                        dest.write(data, 0, count);
                    }
                    dest.flush();
                    dest.close();
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            if (dest != null) {
                dest.flush();
                dest.close();
            }
            tis.close();
        }
    }
    public static final int BUFFER_MAX = 2048;
}