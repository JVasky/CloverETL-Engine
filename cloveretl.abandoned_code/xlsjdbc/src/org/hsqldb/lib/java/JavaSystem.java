/* Copyright (c) 2001-2004, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG, 
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package org.hsqldb.lib.java;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.DriverManager;
import java.util.Properties;

// fredt@users 20020320 - patch 1.7.0 - JDBC 2 support and error trapping
// fredt@users 20021030 - patch 1.7.2 - updates

/**
 * Handles the differences between JDK 1.1.x and 1.2.x and above
 *
 * @author fredt@users
 * @version 1.7.2
 */
public class JavaSystem {

    public static BigInteger getUnscaledValue(BigDecimal o) {

//#ifdef JAVA1TARGET
/*
        int scale = o.scale();
        return o.movePointRight(scale).toBigInteger();
*/

//#else
        return o.unscaledValue();

//#endif
    }

    public static void setLogToSystem(boolean value) {

//#ifdef JAVA1TARGET
/*
        try {
            PrintStream newOutStream = (value) ? System.out
                                               : null;
            DriverManager.setLogStream(newOutStream);
        } catch (Exception e){}
*/

//#else
        try {
            PrintWriter newPrintWriter = (value) ? new PrintWriter(System.out)
                                                 : null;

            DriverManager.setLogWriter(newPrintWriter);
        } catch (Exception e) {}

//#endif
    }

    public static void deleteOnExit(File f) {

//#ifdef JAVA1TARGET
/*
*/

//#else
        f.deleteOnExit();

//#endif
    }

    public static void saveProperties(Properties props, String name,
                                      File file)
                                      throws FileNotFoundException,
                                          IOException {

        FileOutputStream fos = null;

        fos = new FileOutputStream(file);

        saveProperties(props, name, fos);
        fos.close();
    }

    public static void saveProperties(Properties props, String name,
                                      OutputStream os) throws IOException {

//#ifdef JAVA1TARGET
/*
    props.save(os, name);
*/

//#else
        props.store(os, name);

//#endif
    }

    public static void runFinalizers() {

//#ifdef JAVA1TARGET
/*
        System.runFinalizersOnExit(true);
*/

//#endif
    }

    public static void createNewFile(File file) {

//#ifdef JAVA1TARGET
/*
*/

//#else
        try {
            file.createNewFile();
        } catch (IOException e) {}

//#endif
    }
}
