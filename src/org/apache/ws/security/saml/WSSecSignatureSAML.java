package org.apache.ws.security.saml;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ws.security.SOAPConstants;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSDocInfo;
import org.apache.ws.security.WSDocInfoStore;
import org.apache.ws.security.WSEncryptionPart;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.message.EnvelopeIdResolver;
import org.apache.ws.security.message.WSSecHeader;
import org.apache.ws.security.message.WSSecSignature;
import org.apache.ws.security.message.token.Reference;
import org.apache.ws.security.message.token.SecurityTokenReference;
import org.apache.ws.security.message.token.X509Security;
import org.apache.ws.security.transform.STRTransform;
import org.apache.ws.security.util.WSSecurityUtil;
import org.apache.xml.security.algorithms.SignatureAlgorithm;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.keys.KeyInfo;
import org.apache.xml.security.keys.content.X509Data;
import org.apache.xml.security.keys.content.x509.XMLX509Certificate;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.signature.XMLSignatureException;
import org.apache.xml.security.transforms.TransformationException;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.transforms.params.InclusiveNamespaces;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.XMLUtils;
import org.opensaml.SAMLAssertion;
import org.opensaml.SAMLException;
import org.opensaml.SAMLObject;
import org.opensaml.SAMLSubject;
import org.opensaml.SAMLSubjectStatement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

public class WSSecSignatureSAML extends WSSecSignature {

    private static Log log = LogFactory.getLog(WSSecSignatureSAML.class
            .getName());

    private static Log tlog = LogFactory.getLog("org.apache.ws.security.TIME");

    private boolean senderVouches = false;

    private SecurityTokenReference secRefSaml = null;

    private Element samlToken = null;

    private Crypto userCrypto = null;

    private Crypto issuerCrypto = null;

    private String issuerKeyName = null;

    private String issuerKeyPW = null;

    /**
     * Constructor.
     */
    public WSSecSignatureSAML() {
    }

    /**
     * Builds a signed soap envelope with SAML token.
     * 
     * <p/>
     * 
     * The method first gets an appropriate security header. According to the
     * defined parameters for certificate handling the signature elements are
     * constructed and inserted into the <code>wsse:Signature</code>
     * 
     * @param doc
     *            The unsigned SOAP envelope as <code>Document</code>
     * @param assertion
     *            the complete SAML assertion
     * @param issuerCrypto
     *            An instance of the Crypto API to handle keystore SAML token
     *            issuer and to generate certificates
     * @param issuerKeyName
     *            Private key to use in case of "sender-Vouches"
     * @param issuerKeyPW
     *            Password for issuer private key
     * @return A signed SOAP envelope as <code>Document</code>
     * @throws org.apache.ws.security.WSSecurityException
     */
    public Document build(Document doc, Crypto uCrypto,
            SAMLAssertion assertion, Crypto iCrypto, String iKeyName,
            String iKeyPW, WSSecHeader secHeader) throws WSSecurityException {

        Element securityHeader = secHeader.getSecurityHeader();

        prepare(doc, uCrypto, assertion, iCrypto, iKeyName, iKeyPW, secHeader);

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

        /*
         * The order to prepend is: - signature Element - BinarySecurityToken
         * (depends on mode) - SecurityTokenRefrence (depends on mode) - SAML
         * token
         */

        prependToHeader(secHeader);

        /*
         * if we have a BST prepend it in front of the Signature according to
         * strict layout rules.
         */
        if (bstToken != null) {
            prependBSTElementToHeader(secHeader);
        }

        prependSAMLElementsToHeader(secHeader);

        computeSignature();

        return doc;
    }

    /**
     * Initialize a WSSec SAML Signature.
     * 
     * The method sets up and initializes a WSSec SAML Signature structure after
     * the relevant information was set. After setup of the references to
     * elements to sign may be added. After all references are added they can be
     * signed.
     * 
     * <p/>
     * 
     * This method does not add the Signature element to the security header.
     * See <code>prependSignatureElementToHeader()</code> method.
     * 
     * @param doc
     *            The SOAP envelope as <code>Document</code>
     * @param cr
     *            An instance of the Crypto API to handle keystore and
     *            certificates
     * @param secHeader
     *            The security header that will hold the Signature. This ise use
     *            to construct namespace prefixes for Signature. This method
     * @throws WSSecurityException
     */
    public void prepare(Document doc, Crypto uCrypto, SAMLAssertion assertion,
            Crypto iCrypto, String iKeyName, String iKeyPW,
            WSSecHeader secHeader) throws WSSecurityException {

        doDebug = log.isDebugEnabled();
        if (doDebug) {
            log.debug("Beginning ST signing...");
        }

        userCrypto = uCrypto;
        issuerCrypto = iCrypto;
        document = doc;
        issuerKeyName = iKeyName;
        issuerKeyPW = iKeyPW;

        /*
         * Get some information about the SAML token content. This controls how
         * to deal with the whole stuff. First get the Authentication statement
         * (includes Subject), then get the _first_ confirmation method only
         * thats if "senderVouches" is true.
         */
        SAMLSubjectStatement samlSubjS = null;
        Iterator it = assertion.getStatements();
        while (it.hasNext()) {
            SAMLObject so = (SAMLObject) it.next();
            if (so instanceof SAMLSubjectStatement) {
                samlSubjS = (SAMLSubjectStatement) so;
                break;
            }
        }
        SAMLSubject samlSubj = null;
        if (samlSubjS != null) {
            samlSubj = samlSubjS.getSubject();
        }
        if (samlSubj == null) {
            throw new WSSecurityException(WSSecurityException.FAILURE,
                    "invalidSAMLToken", new Object[] { "for Signature" });
        }

        String confirmMethod = null;
        it = samlSubj.getConfirmationMethods();
        if (it.hasNext()) {
            confirmMethod = (String) it.next();
        }
        if (SAMLSubject.CONF_SENDER_VOUCHES.equals(confirmMethod)) {
            senderVouches = true;
        }
        /*
         * Gather some info about the document to process and store it for
         * retrival
         */
        wsDocInfo = new WSDocInfo(doc.hashCode());

        X509Certificate[] certs = null;

        if (senderVouches) {
            certs = issuerCrypto.getCertificates(issuerKeyName);
            wsDocInfo.setCrypto(issuerCrypto);
        }
        /*
         * in case of key holder: - get the user's certificate that _must_ be
         * included in the SAML token. To ensure the cert integrity the SAML
         * token must be signed (by the issuer). Just check if its signed, but
         * don't verify this SAML token's signature here (maybe later).
         */
        else {
            if (userCrypto == null || assertion.isSigned() == false) {
                throw new WSSecurityException(WSSecurityException.FAILURE,
                        "invalidSAMLsecurity",
                        new Object[] { "for SAML Signature (Key Holder)" });
            }
            Element e = samlSubj.getKeyInfo();
            try {
                KeyInfo ki = new KeyInfo(e, null);

                if (ki.containsX509Data()) {
                    X509Data data = ki.itemX509Data(0);
                    XMLX509Certificate certElem = null;
                    if (data != null && data.containsCertificate()) {
                        certElem = data.itemCertificate(0);
                    }
                    if (certElem != null) {
                        X509Certificate cert = certElem.getX509Certificate();
                        certs = new X509Certificate[1];
                        certs[0] = cert;
                    }
                }
                // TODO: get alias name for cert, check against username set by
                // caller
            } catch (XMLSecurityException e3) {
                throw new WSSecurityException(WSSecurityException.FAILURE,
                        "invalidSAMLsecurity",
                        new Object[] { "cannot get certificate (key holder)" },
                        e3);
            }
            wsDocInfo.setCrypto(userCrypto);
        }
        if (certs == null || certs.length <= 0) {
            throw new WSSecurityException(WSSecurityException.FAILURE,
                    "invalidX509Data", new Object[] { "for Signature" });
        }
        if (sigAlgo == null) {
            String pubKeyAlgo = certs[0].getPublicKey().getAlgorithm();
            log.debug("automatic sig algo detection: " + pubKeyAlgo);
            if (pubKeyAlgo.equalsIgnoreCase("DSA")) {
                sigAlgo = XMLSignature.ALGO_ID_SIGNATURE_DSA;
            } else if (pubKeyAlgo.equalsIgnoreCase("RSA")) {
                sigAlgo = XMLSignature.ALGO_ID_SIGNATURE_RSA;
            } else {
                throw new WSSecurityException(
                        WSSecurityException.FAILURE,
                        "invalidX509Data",
                        new Object[] { "for Signature - unkown public key Algo" });
            }
        }
        sig = null;
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

        certUri = "CertId-" + certs[0].hashCode();

        /*
         * If the sender vouches, then we must sign the SAML token _and_ at
         * least one part of the message (usually the SOAP body). To do so we
         * need to - put in a reference to the SAML token. Thus we create a STR
         * and insert it into the wsse:Security header - set a reference of the
         * created STR to the signature and use STR Transfrom during the
         * signature
         */
        Transforms transforms = null;

        try {
            if (senderVouches) {
                secRefSaml = new SecurityTokenReference(doc);
                String strSamlUri = "STRSAMLId-" + secRefSaml.hashCode();
                secRefSaml.setID(strSamlUri);

                // Decouple Reference/KeyInfo setup - quick shot here
                Reference ref = new Reference(doc);
                ref.setURI("#" + assertion.getId());
                ref.setValueType(WSConstants.WSS_SAML_NS
                        + WSConstants.WSS_SAML_ASSERTION);
                secRefSaml.setReference(ref);
                // up to here

                Element ctx = createSTRParameter(doc);
                transforms = new Transforms(doc);
                transforms.addTransform(STRTransform.implementedTransformURI,
                        ctx);
                sig.addDocument("#" + strSamlUri, transforms);
            }
        } catch (TransformationException e1) {
            throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE,
                    "noXMLSig", null, e1);
        } catch (XMLSignatureException e1) {
            throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE,
                    "noXMLSig", null, e1);
        }

        switch (keyIdentifierType) {
        case WSConstants.BST_DIRECT_REFERENCE:
            Reference ref = new Reference(doc);
            if (senderVouches) {
                ref.setURI("#" + certUri);
                bstToken = new X509Security(doc);
                ((X509Security) bstToken).setX509Certificate(certs[0]);
                bstToken.setID(certUri);
                wsDocInfo.setBst(bstToken.getElement());
                ref.setValueType(bstToken.getValueType());
            } else {
                ref.setURI("#" + assertion.getId());
                ref.setValueType(WSConstants.WSS_SAML_NS
                        + WSConstants.WSS_SAML_ASSERTION);
            }
            secRef.setReference(ref);
            break;
        //
        // case WSConstants.ISSUER_SERIAL :
        // XMLX509IssuerSerial data =
        // new XMLX509IssuerSerial(doc, certs[0]);
        // secRef.setX509IssuerSerial(data);
        // break;
        //
        // case WSConstants.X509_KEY_IDENTIFIER :
        // secRef.setKeyIdentifier(certs[0]);
        // break;
        //
        // case WSConstants.SKI_KEY_IDENTIFIER :
        // secRef.setKeyIdentifierSKI(certs[0], crypto);
        // break;
        //
        default:
            throw new WSSecurityException(WSSecurityException.FAILURE,
                    "unsupportedKeyId");
        }

        keyInfo.addUnknownElement(secRef.getElement());

        try {
            samlToken = (Element) assertion.toDOM(doc);
        } catch (SAMLException e2) {
            throw new WSSecurityException(WSSecurityException.FAILED_SIGNATURE,
                    "noSAMLdoc", null, e2);
        }
        wsDocInfo.setAssertion(samlToken);
    }

    /**
     * Prepend the SAML elements to the elements already in the Security header.
     * 
     * The method can be called any time after <code>prepare()</code>. This
     * allows to insert the SAML elements at any position in the Security
     * header.
     * 
     * <p/>
     * 
     * This methods first prepends the SAML security reference if mode is
     * <code>senderVouches</code>, then the SAML token itself,
     * 
     * @param secHeader
     *            The security header that holds the BST element.
     */
    public void prependSAMLElementsToHeader(WSSecHeader secHeader) {
        if (senderVouches) {
            WSSecurityUtil.prependChildElement(document, secHeader
                    .getSecurityHeader(), secRefSaml.getElement(), true);
        }

        WSSecurityUtil.prependChildElement(document, secHeader
                .getSecurityHeader(), samlToken, true);
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

        Element envelope = document.getDocumentElement();
        for (int part = 0; part < parts.size(); part++) {
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
                    if (keyIdentifierType == WSConstants.BST_DIRECT_REFERENCE) {
                        if (wssConfig.isWsiBSPCompliant()) {
                            transforms.item(0).getElement().appendChild(
                                    new InclusiveNamespaces(document,
                                            getInclusivePrefixes(secHeader
                                                    .getSecurityHeader()))
                                            .getElement());
                        }
                        sig.addDocument("#" + certUri, transforms);
                    } else {
                        if (wssConfig.isWsiBSPCompliant()) {
                            transforms.item(0).getElement().appendChild(
                                    new InclusiveNamespaces(document,
                                            getInclusivePrefixes(keyInfo
                                                    .getElement()))
                                            .getElement());
                        }
                        sig.addDocument("#" + keyInfoUri, transforms);
                    }
                } else if (elemName.equals("STRTransform")) { // STRTransform
                    Element ctx = createSTRParameter(document);
                    transforms.addTransform(
                            STRTransform.implementedTransformURI, ctx);
                    sig.addDocument("#" + strUri, transforms);
                } else {
                    Element body = (Element) WSSecurityUtil.findElement(
                            envelope, elemName, nmSpace);
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
            if (senderVouches) {
                sig
                        .sign(issuerCrypto.getPrivateKey(issuerKeyName,
                                issuerKeyPW));
            } else {
                sig.sign(userCrypto.getPrivateKey(user, password));
            }
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
}
