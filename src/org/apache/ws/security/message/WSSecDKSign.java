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

import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ws.security.SOAPConstants;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSDocInfo;
import org.apache.ws.security.WSDocInfoStore;
import org.apache.ws.security.WSEncryptionPart;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.message.token.Reference;
import org.apache.ws.security.message.token.SecurityTokenReference;
import org.apache.ws.security.saml.SAMLUtil;
import org.apache.ws.security.transform.STRTransform;
import org.apache.ws.security.util.WSSecurityUtil;
import org.apache.xml.security.algorithms.SignatureAlgorithm;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.keys.KeyInfo;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.signature.XMLSignatureException;
import org.apache.xml.security.transforms.TransformationException;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.transforms.params.InclusiveNamespaces;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Builder to sign with derived keys
 * 
 * @author Ruchith Fernando (ruchith.fernando@gmail.com)
 * @author Davanum Srinivas (dims@yahoo.com)
 * @author Werner Dittmann (werner@apache.org)
 */
public class WSSecDKSign extends WSSecDerivedKeyBase {

    private static Log log = LogFactory.getLog(WSSecDKSign.class.getName());

    protected String sigAlgo = XMLSignature.ALGO_ID_MAC_HMAC_SHA1;

    protected String canonAlgo = Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS;

    protected byte[] signatureValue = null;
    
    private XMLSignature sig = null;
    
    private KeyInfo keyInfo = null;

    private String keyInfoUri = null;

    private SecurityTokenReference secRef = null;

    private String strUri = null;
    
    private WSDocInfo wsDocInfo;

    public Document build(Document doc, Crypto crypto, WSSecHeader secHeader) throws WSSecurityException  {
        
        this.prepare(doc, crypto, secHeader);
        

        
        SOAPConstants soapConstants = WSSecurityUtil.getSOAPConstants(doc
                .getDocumentElement());

        if (parts == null) {
            parts = new Vector();
            WSEncryptionPart encP = new WSEncryptionPart(soapConstants
                    .getBodyQName().getLocalPart(), soapConstants
                    .getEnvelopeURI(), "Content");
            parts.add(encP);
        }
        
        addReferencesToSign(parts, secHeader);
        
        computeSignature();
        
        this.prependSigToHeader(secHeader);
        /*
         * prepend elements in the right order to the security header
         */
        prependDKElementToHeader(secHeader);

        return doc;
    }
    
    protected void prepare(Document doc, Crypto crypto, WSSecHeader secHeader)
                            throws WSSecurityException {
        super.prepare(doc, crypto);
        
        wsDocInfo = new WSDocInfo(doc.hashCode());
        wsDocInfo.setCrypto(crypto);
        
        /*
         * Get an initialize a XMLSignature element.
         */
        if (canonAlgo.equals(WSConstants.C14N_EXCL_OMIT_COMMENTS)) {
            Element canonElem = XMLUtils.createElementInSignatureSpace(doc,
                    Constants._TAG_CANONICALIZATIONMETHOD);

            canonElem.setAttributeNS(null, Constants._ATT_ALGORITHM, canonAlgo);

            if (wssConfig.isWsiBSPCompliant()) {
                Set prefixes = getInclusivePrefixes(secHeader
                        .getSecurityHeader(), false);

                InclusiveNamespaces inclusiveNamespaces = new InclusiveNamespaces(
                        doc, prefixes);

                canonElem.appendChild(inclusiveNamespaces.getElement());
            }

            try {
                SignatureAlgorithm signatureAlgorithm = new SignatureAlgorithm(
                        doc, sigAlgo);
                sig = new XMLSignature(doc, null, signatureAlgorithm
                        .getElement(), canonElem);
            } catch (XMLSecurityException e) {
                log.error("", e);
                throw new WSSecurityException(
                        WSSecurityException.FAILED_SIGNATURE, "noXMLSig");
            }
        } else {
            try {
                sig = new XMLSignature(doc, null, sigAlgo, canonAlgo);
            } catch (XMLSecurityException e) {
                log.error("", e);
                throw new WSSecurityException(
                        WSSecurityException.FAILED_SIGNATURE, "noXMLSig");
            }
        }
        
        sig.addResourceResolver(EnvelopeIdResolver.getInstance());
        String sigUri = "Signature-" + sig.hashCode();
        sig.setId(sigUri);
        
        keyInfo = sig.getKeyInfo();
        keyInfoUri = "KeyId-" + keyInfo.hashCode();
        keyInfo.setId(keyInfoUri);
        
        secRef = new SecurityTokenReference(doc);
        strUri = "STRId-" + secRef.hashCode();
        secRef.setID(strUri);
        
        Reference refUt = new Reference(document);
        refUt.setURI("#" + this.dktId);
        secRef.setReference(refUt);
        
        keyInfo.addUnknownElement(secRef.getElement());
    }
    
    
    protected Set getInclusivePrefixes(Element target) {
        return getInclusivePrefixes(target, true);
    }

    protected Set getInclusivePrefixes(Element target, boolean excludeVisible) {
        Set result = new HashSet();
        Node parent = target;
        NamedNodeMap attributes;
        Node attribute;
        while (!(parent.getParentNode() instanceof Document)) {
            parent = parent.getParentNode();
            attributes = parent.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                attribute = attributes.item(i);
                if (attribute.getNamespaceURI() != null
                        && attribute.getNamespaceURI().equals(
                                org.apache.ws.security.WSConstants.XMLNS_NS)) {
                    if (attribute.getNodeName().equals("xmlns")) {
                        result.add("#default");
                    } else {
                        result.add(attribute.getLocalName());
                    }
                }
            }
        }

        if (excludeVisible == true) {
            attributes = target.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                attribute = attributes.item(i);
                if (attribute.getNamespaceURI() != null
                        && attribute.getNamespaceURI().equals(
                                org.apache.ws.security.WSConstants.XMLNS_NS)) {
                    if (attribute.getNodeName().equals("xmlns")) {
                        result.remove("#default");
                    } else {
                        result.remove(attribute.getLocalName());
                    }
                }
                if (attribute.getPrefix() != null) {
                    result.remove(attribute.getPrefix());
                }
            }

            if (target.getPrefix() == null) {
                result.remove("#default");
            } else {
                result.remove(target.getPrefix());
            }
        }

        return result;
    }
    
    /**
     * This method adds references to the Signature.
     * 
     * The added references are signed when calling
     * <code>computeSignature()</code>. This method can be called several
     * times to add references as required. <code>addReferencesToSign()</code>
     * can be called anytime after <code>prepare</code>.
     * 
     * @param references
     *            A vector containing <code>WSEncryptionPart</code> objects
     *            that define the parts to sign.
     * @param secHeader
     *            Used to compute namespaces to be inserted by
     *            InclusiveNamespaces to be WSI compliant.
     * @throws WSSecurityException
     */
    public void addReferencesToSign(Vector references, WSSecHeader secHeader)
            throws WSSecurityException {
        Transforms transforms = null;

        Element envel = document.getDocumentElement();

        for (int part = 0; part < references.size(); part++) {
            WSEncryptionPart encPart = (WSEncryptionPart) references.get(part);

            String idToSign = encPart.getId();

            String elemName = encPart.getName();
            String nmSpace = encPart.getNamespace();

            /*
             * Set up the elements to sign. There are two resevered element
             * names: "Token" and "STRTransform" "Token": Setup the Signature to
             * either sign the information that points to the security token or
             * the token itself. If its a direct reference sign the token,
             * otherwise sign the KeyInfo Element. "STRTransform": Setup the
             * ds:Reference to use STR Transform
             * 
             */
            transforms = new Transforms(document);
            try {
                if (idToSign != null) {
                    Element toSignById = WSSecurityUtil.findElementById(
                            document.getDocumentElement(), idToSign,
                            WSConstants.WSU_NS);
                    if (toSignById == null) {
                        toSignById = WSSecurityUtil.findElementById(document
                                .getDocumentElement(), idToSign, null);
                    }
                    transforms
                            .addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
                    if (wssConfig.isWsiBSPCompliant()) {
                        transforms.item(0).getElement().appendChild(
                                new InclusiveNamespaces(document,
                                        getInclusivePrefixes(toSignById))
                                        .getElement());
                    }
                    sig.addDocument("#" + idToSign, transforms);
                } else if (elemName.equals("Token")) {
                    transforms
                            .addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
                    if (wssConfig.isWsiBSPCompliant()) {
                        transforms.item(0).getElement().appendChild(
                                new InclusiveNamespaces(document,
                                        getInclusivePrefixes(keyInfo
                                                .getElement()))
                                        .getElement());
                    }
                    sig.addDocument("#" + keyInfoUri, transforms);
                } else if (elemName.equals("STRTransform")) { // STRTransform
                    Element ctx = createSTRParameter(document);
                    transforms.addTransform(
                            STRTransform.implementedTransformURI, ctx);
                    sig.addDocument("#" + strUri, transforms);
                } else if (elemName.equals("Assertion")) { // Assertion

                    String id = null;
                    id = SAMLUtil.getAssertionId(envel, elemName, nmSpace);

                    Element body = (Element) WSSecurityUtil.findElement(
                            envel, elemName, nmSpace);
                    if (body == null) {
                        throw new WSSecurityException(
                                WSSecurityException.FAILURE, "noEncElement",
                                new Object[] { nmSpace + ", " + elemName });
                    }
                    transforms
                            .addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
                    if (wssConfig.isWsiBSPCompliant()) {
                        transforms.item(0).getElement().appendChild(
                                new InclusiveNamespaces(document,
                                        getInclusivePrefixes(body))
                                        .getElement());
                    }
                    String prefix = WSSecurityUtil.setNamespace(body,
                            WSConstants.WSU_NS, WSConstants.WSU_PREFIX);
                    body.setAttributeNS(WSConstants.WSU_NS, prefix + ":Id", id);
                    sig.addDocument("#" + id, transforms);

                } else {
                    Element body = (Element) WSSecurityUtil.findElement(
                            envel, elemName, nmSpace);
                    if (body == null) {
                        throw new WSSecurityException(
                                WSSecurityException.FAILURE, "noEncElement",
                                new Object[] { nmSpace + ", " + elemName });
                    }
                    transforms
                            .addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
                    if (wssConfig.isWsiBSPCompliant()) {
                        transforms.item(0).getElement().appendChild(
                                new InclusiveNamespaces(document,
                                        getInclusivePrefixes(body))
                                        .getElement());
                    }
                    sig.addDocument("#" + setWsuId(body), transforms);
                }
            } catch (TransformationException e1) {
                throw new WSSecurityException(
                        WSSecurityException.FAILED_SIGNATURE, "noXMLSig", null,
                        e1);
            } catch (XMLSignatureException e1) {
                throw new WSSecurityException(
                        WSSecurityException.FAILED_SIGNATURE, "noXMLSig", null,
                        e1);
            }
        }
    }
    
    protected Element createSTRParameter(Document doc) {
        Element transformParam = doc.createElementNS(WSConstants.WSSE_NS,
                WSConstants.WSSE_PREFIX + ":TransformationParameters");

        WSSecurityUtil.setNamespace(transformParam, WSConstants.WSSE_NS,
                WSConstants.WSSE_PREFIX);

        Element canonElem = doc.createElementNS(WSConstants.SIG_NS,
                WSConstants.SIG_PREFIX + ":CanonicalizationMethod");

        WSSecurityUtil.setNamespace(canonElem, WSConstants.SIG_NS,
                WSConstants.SIG_PREFIX);

        canonElem.setAttributeNS(null, "Algorithm",
                Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        transformParam.appendChild(canonElem);
        return transformParam;
    }
    
    
    /**
     * Prepends the Signature element to the elements already in the Security
     * header.
     * 
     * The method can be called any time after <code>prepare()</code>.
     * This allows to insert the Signature element at any position in the
     * Security header.
     * 
     * @param securityHeader
     *            The secHeader that holds the Signature element.
     */
    private void prependSigToHeader(WSSecHeader secHeader) {
        WSSecurityUtil.prependChildElement(document, secHeader.getSecurityHeader(), sig
                .getElement(), false);
    }
    
    
    /**
     * Compute the Signature over the references.
     * 
     * After references are set this method computes the Signature for them.
     * This method can be called anytime after the references were set. See
     * <code>addReferencesToSign()</code>.
     * 
     * @throws WSSecurityException
     */
    public void computeSignature() throws WSSecurityException {
        WSDocInfoStore.store(wsDocInfo);
        try {
            sig.sign(sig.createSecretKey(derivedKeyBytes));
            signatureValue = sig.getSignatureValue();
        } catch (XMLSignatureException e1) {
            throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE,
                    null, null, e1);
        } catch (Exception e1) {
            throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE,
                    null, null, e1);
        } finally {
            WSDocInfoStore.delete(wsDocInfo);
        }

    }
    
    /**
     * @see org.apache.ws.security.message.WSSecDerivedKeyBase#getDerivedKeyLength()
     */
    protected int getDerivedKeyLength() throws WSSecurityException {
        return WSSecurityUtil.getKeyLength(this.sigAlgo);
    }
    
    
    public void setSignatureAlgorithm(String algo) {
        this.sigAlgo = algo;
    }
    

}