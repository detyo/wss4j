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
import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.utils.resolver.ResourceResolverException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;
import org.apache.xml.utils.URI;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * XML-Security resolver that is used for resolving same-document URI like URI="#id".
 * It is desgined to only work with SOAPEnvelopes.
 * <p/>
 *
 * @author Davanum Srinivas (dims@yahoo.com).
 */
public class EnvelopeIdResolver extends ResourceResolverSpi {
    private static Log log =
            LogFactory.getLog(EnvelopeIdResolver.class.getName());

    private static EnvelopeIdResolver resolver = null;

    private boolean doDebug = false;

    /**
     * Singleton instance of the resolver.
     * <p/>
     *
     * @return TODO
     */
    public synchronized static ResourceResolverSpi getInstance() {
        if (resolver == null) {
            resolver = new EnvelopeIdResolver();
        }
        return resolver;
    }

    private EnvelopeIdResolver() {
    }

    /**
     * This is the workhorse method used to resolve resources.
     * <p/>
     *
     * @param uri
     * @param BaseURI
     * @return TODO
     * @throws ResourceResolverException
     */
    public XMLSignatureInput engineResolve(Attr uri, String BaseURI)
            throws ResourceResolverException {

        doDebug = log.isDebugEnabled();

        String uriNodeValue = uri.getNodeValue();

        if (doDebug) {
            log.debug("enter engineResolve, look for: " + uriNodeValue);
        }

        Document doc = uri.getOwnerDocument();

        /*
         * URI="#chapter1"
         * Identifies a node-set containing the element with ID attribute
         * value 'chapter1' of the XML resource containing the signature.
         * XML Signature (and its applications) modify this node-set to
         * include the element plus all descendents including namespaces and
         * attributes -- but not comments.
         */
         
        /*
         * First lookup the SOAP Body element (processed by default) and
         * check if it contains an Id and if it matches
         */
        String id = uriNodeValue.substring(1);
        SOAPConstants sc = WSSecurityUtil.getSOAPConstants(doc.getDocumentElement());
        Element selectedElem = WSSecurityUtil.findBodyElement(doc, sc);
        if (selectedElem == null) {
            throw new ResourceResolverException("generic.EmptyMessage",
                    new Object[]{"Body element not found"},
                    uri,
                    BaseURI);
        }
        String cId = selectedElem.getAttributeNS(WSConstants.WSU_NS, "Id");

        /*
         * If Body Id match fails, look for a generic Id (without a namespace)
         * that matches the URI. If that lookup fails, try to get a namespace
         * qualified Id that matches the URI.
         */
        if (!id.equals(cId)) {
            cId = null;
            if ((selectedElem = WSSecurityUtil.getElementByWsuId(doc, uriNodeValue)) != null) {
                cId = selectedElem.getAttributeNS(WSConstants.WSU_NS,"Id");
            } else if ((selectedElem = WSSecurityUtil.getElementByGenId(doc, uriNodeValue)) != null) {
                cId = selectedElem.getAttribute("Id");
            }
            if (cId == null) {
                throw new ResourceResolverException("generic.EmptyMessage",
                        new Object[]{"Id not found"},
                        uri,
                        BaseURI);
            }
        }

        XMLSignatureInput result = new XMLSignatureInput(selectedElem);
        result.setMIMEType("text/xml");
        try {
            URI uriNew = new URI(new URI(BaseURI), uri.getNodeValue());
            result.setSourceURI(uriNew.toString());
        } catch (URI.MalformedURIException ex) {
            result.setSourceURI(BaseURI);
        }
        if (doDebug) {
            log.debug("exit engineResolve, result: " + result);
        }
        return result;
    }

    /**
     * This method helps the ResourceResolver to decide whether a
     * ResourceResolverSpi is able to perform the requested action.
     * <p/>
     *
     * @param uri
     * @param BaseURI
     * @return TODO
     */
    public boolean engineCanResolve(Attr uri, String BaseURI) {
        if (uri == null) {
            return false;
        }
        String uriNodeValue = uri.getNodeValue();
        return uriNodeValue.startsWith("#");
    }
}
