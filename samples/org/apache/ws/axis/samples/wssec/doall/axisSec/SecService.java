/*
 * Copyright  2003-2004 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

/**
 * SecService.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2dev Oct 27, 2003 (02:34:09 EST) WSDL2Java emitter.
 */

package org.apache.ws.axis.samples.wssec.doall.axisSec;

public interface SecService extends javax.xml.rpc.Service {
    public java.lang.String getSecHttpAddress();

    public org.apache.ws.axis.samples.wssec.doall.axisSec.SecPort getSecHttp() throws javax.xml.rpc.ServiceException;

    public org.apache.ws.axis.samples.wssec.doall.axisSec.SecPort getSecHttp(java.net.URL portAddress) throws javax.xml.rpc.ServiceException;
}
