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

package org.apache.ws.security.message;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ws.security.SOAPConstants;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.util.WSSecurityUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * This class implements WS Security header.
 * 
 * Setup a Security header with a specified actor and mustunderstand flag.
 * 
 * <p/>
 * 
 * The defaults for actor and mustunderstand are: empty <code>actor</code> and
 * <code>mustunderstand</code> is true.
 * 
 * @author Werner Dittmann (Werner.Dittmann@apache.org)
 */
public class WSSecHeader {
    private static Log log = LogFactory.getLog(WSSecHeader.class.getName());

    protected String actor = null;

    protected boolean mustunderstand = true;

    protected boolean doDebug = false;

    private Element securityHeader = null;

    /**
     * Constructor.
     */
    public WSSecHeader() {
    }

    /**
     * Constructor.
     * 
     * @param actor
     *            The actor name of the <code>wsse:Security</code> header
     */
    public WSSecHeader(String actor) {
        this(actor, true);
    }

    /**
     * Constructor.
     * 
     * @param actor
     *            The actor name of the <code>wsse:Security</code> header
     * @param mu
     *            Set <code>mustUnderstand</code> to true or false
     */
    public WSSecHeader(String act, boolean mu) {
        actor = act;
        mustunderstand = mu;
    }

    /**
     * set actor name.
     * 
     * @param act
     *            The actor name of the <code>wsse:Security</code> header
     */
    public void setActor(String act) {
        actor = act;
    }

    /**
     * Set the <code>mustUnderstand</code> flag for the
     * <code>wsse:Security</code> header.
     * 
     * @param mu
     *            Set <code>mustUnderstand</code> to true or false
     */
    public void setMustUnderstand(boolean mu) {
        mustunderstand = mu;
    }

    /**
     * Get the security header element of this instance.
     * 
     * @return The security header element.
     */
    public Element getSecurityHeader() {
        return securityHeader;
    }

    /**
     * Creates a security header and inserts it as child into the SOAP Envelope.
     * 
     * <p/>
     * 
     * Check if a WS Security header block for an actor is already available in
     * the document. If a header block is found return it, otherwise a new
     * wsse:Security header block is created and the attributes set
     * 
     * @param doc
     *            A SOAP envelope as <code>Document</code>
     * @return A <code>wsse:Security</code> element
     */
    public Element insertSecurityHeader(Document doc) {

        /*
         * If there is already a security header in this instance just return it
         */
        if (securityHeader != null) {
            return securityHeader;
        }
        SOAPConstants soapConstants = WSSecurityUtil.getSOAPConstants(doc
                .getDocumentElement());

        securityHeader = WSSecurityUtil.findWsseSecurityHeaderBlock(doc, doc
                .getDocumentElement(), actor, true);

        String soapPrefix = WSSecurityUtil.getPrefixNS(soapConstants
                .getEnvelopeURI(), securityHeader);
        if (actor != null && actor.length() > 0) {
            securityHeader.setAttributeNS(soapConstants.getEnvelopeURI(),
                    soapPrefix
                            + ":"
                            + soapConstants.getRoleAttributeQName()
                                    .getLocalPart(), actor);
        }
        if (mustunderstand) {
            securityHeader.setAttributeNS(soapConstants.getEnvelopeURI(),
                    soapPrefix + ":" + WSConstants.ATTR_MUST_UNDERSTAND,
                    soapConstants.getMustunderstand());
        }
        return securityHeader;
    }
}
