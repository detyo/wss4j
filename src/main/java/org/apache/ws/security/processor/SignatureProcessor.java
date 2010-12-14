/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ws.security.processor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ws.security.CustomTokenPrincipal;
import org.apache.ws.security.PublicKeyCallback;
import org.apache.ws.security.PublicKeyPrincipal;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSDataRef;
import org.apache.ws.security.WSDocInfo;
import org.apache.ws.security.WSPasswordCallback;
import org.apache.ws.security.WSSConfig;
import org.apache.ws.security.WSSecurityEngine;
import org.apache.ws.security.WSSecurityEngineResult;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.WSUsernameTokenPrincipal;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.message.DOMURIDereferencer;
import org.apache.ws.security.message.token.BinarySecurity;
import org.apache.ws.security.message.token.DerivedKeyToken;
import org.apache.ws.security.message.token.PKIPathSecurity;
import org.apache.ws.security.message.token.SecurityTokenReference;
import org.apache.ws.security.message.token.UsernameToken;
import org.apache.ws.security.message.token.X509Security;
import org.apache.ws.security.saml.SAMLKeyInfo;
import org.apache.ws.security.saml.SAMLUtil;
import org.apache.ws.security.transform.STRTransform;
import org.apache.ws.security.transform.STRTransformUtil;
import org.apache.ws.security.util.WSSecurityUtil;

import org.opensaml.SAMLAssertion;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;

import javax.xml.crypto.MarshalException;
import javax.xml.crypto.NodeSetData;
import javax.xml.crypto.URIDereferencer;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.XMLValidateContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;

import javax.xml.namespace.QName;

import java.math.BigInteger;
import java.security.Key;
import java.security.PublicKey;
import java.security.Principal;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.List;

public class SignatureProcessor implements Processor {
    private static Log log = LogFactory.getLog(SignatureProcessor.class.getName());
    
    private String signatureId;
    
    private X509Certificate[] certs;
    
    private byte[] signatureValue;
    
    private int secretKeyLength = WSConstants.WSE_DERIVED_KEY_LEN;
    
    private KeyInfoFactory keyInfoFactory = KeyInfoFactory.getInstance("DOM");
    private XMLSignatureFactory signatureFactory = XMLSignatureFactory.getInstance("DOM");

    private String signatureMethod;
    
    private String c14nMethod;

    public void handleToken(
        Element elem, 
        Crypto crypto, 
        Crypto decCrypto, 
        CallbackHandler cb, 
        WSDocInfo wsDocInfo, 
        List<WSSecurityEngineResult> returnResults, 
        WSSConfig wsc
    ) throws WSSecurityException {
        if (log.isDebugEnabled()) {
            log.debug("Found signature element");
        }
        List<WSDataRef> protectedRefs = new java.util.ArrayList<WSDataRef>();
        Principal lastPrincipalFound = null;
        certs = null;
        signatureValue = null;
        secretKeyLength = wsc.getSecretKeyLength();
        signatureMethod = c14nMethod = null;
        
        try {
            lastPrincipalFound = 
                verifyXMLSignature(
                    elem, crypto, protectedRefs, cb, wsDocInfo
                );
        } catch (WSSecurityException ex) {
            throw ex;
        }
        int actionPerformed = WSConstants.SIGN;
        if (lastPrincipalFound instanceof WSUsernameTokenPrincipal) {
            actionPerformed = WSConstants.UT_SIGN;
        }

        WSSecurityEngineResult result = new WSSecurityEngineResult(
                actionPerformed, lastPrincipalFound,
                certs, protectedRefs, signatureValue);
        result.put(WSSecurityEngineResult.TAG_SIGNATURE_METHOD, signatureMethod);
        result.put(WSSecurityEngineResult.TAG_CANONICALIZATION_METHOD, c14nMethod);
        returnResults.add(0, result);
        
        signatureId = elem.getAttribute("Id");
    }

    /**
     * Verify the WS-Security signature.
     * 
     * The functions at first checks if then <code>KeyInfo</code> that is
     * contained in the signature contains standard X509 data. If yes then
     * get the certificate data via the standard <code>KeyInfo</code> methods.
     * 
     * Otherwise, if the <code>KeyInfo</code> info does not contain X509 data, check
     * if we can find a <code>wsse:SecurityTokenReference</code> element. If yes, the next
     * step is to check how to get the certificate. Two methods are currently supported
     * here:
     * <ul>
     * <li> A URI reference to a binary security token contained in the <code>wsse:Security
     * </code> header.  If the dereferenced token is
     * of the correct type the contained certificate is extracted.
     * </li>
     * <li> Issuer name an serial number of the certificate. In this case the method
     * looks up the certificate in the keystore via the <code>crypto</code> parameter.
     * </li>
     * </ul>
     * 
     * @param elem        the XMLSignature DOM Element.
     * @param crypto      the object that implements the access to the keystore and the
     *                    handling of certificates.
     * @param protectedRefs A list of (references) to the signed elements
     * @param cb CallbackHandler instance to extract key passwords
     * @return the subject principal of the validated X509 certificate (the
     *         authenticated subject). The calling function may use this
     *         principal for further authentication or authorization.
     * @throws WSSecurityException
     */
    protected Principal verifyXMLSignature(
        Element elem,
        Crypto crypto,
        List<WSDataRef> protectedRefs,
        CallbackHandler cb,
        WSDocInfo wsDocInfo
    ) throws WSSecurityException {
        if (log.isDebugEnabled()) {
            log.debug("Verify XML Signature");
        }
        
        byte[] secretKey = null;
        PublicKey publicKey = null;
        Principal principal = null;
        KeyValue keyValue = null;
        boolean validateCertificateChain = false;
        
        Element keyInfoElement = 
            WSSecurityUtil.getDirectChildElement(
                elem,
                "KeyInfo",
                WSConstants.SIG_NS
            );
        
        if (keyInfoElement != null) {
            Element strElement = 
                WSSecurityUtil.getDirectChildElement(
                    keyInfoElement,
                    SecurityTokenReference.SECURITY_TOKEN_REFERENCE,
                    WSConstants.WSSE_NS
                );
            if (strElement == null) {
                try {
                    //
                    // Look for a KeyValue object
                    //
                    keyValue = getKeyValue(keyInfoElement);
                } catch (javax.xml.crypto.MarshalException ex) {
                    throw new WSSecurityException(WSSecurityException.FAILED_CHECK, null, null, ex);
                } 

                if (keyValue != null) {
                    try {
                        //
                        // Look for a Public Key in Key Value
                        //
                        publicKey = keyValue.getPublicKey();
                        principal = validatePublicKey(cb, publicKey);
                    } catch (java.security.KeyException ex) {
                        log.error(ex.getMessage(), ex);
                        throw new WSSecurityException(WSSecurityException.FAILED_CHECK, null, null, ex);
                    }     
                } else {
                    throw new WSSecurityException(
                        WSSecurityException.INVALID_SECURITY, "unsupportedKeyInfo"
                    );
                }
            } else {
                SecurityTokenReference secRef = new SecurityTokenReference(strElement);
                //
                // Here we get some information about the document that is being
                // processed, in particular the crypto implementation, and already
                // detected BST that may be used later during dereferencing.
                //
                if (secRef.containsReference()) {
                    org.apache.ws.security.message.token.Reference ref = secRef.getReference();
                    
                    String uri = ref.getURI();
                    if (uri.charAt(0) == '#') {
                        uri = uri.substring(1);
                    }
                    Processor processor = wsDocInfo.getProcessor(uri);
                    
                    if (processor == null) {
                        Element token = secRef.getTokenElement(elem.getOwnerDocument(), wsDocInfo, cb);
                        QName el = new QName(token.getNamespaceURI(), token.getLocalName());
                        if (el.equals(WSSecurityEngine.BINARY_TOKEN)) {
                            certs = getCertificatesTokenReference(token, crypto);
                            if (certs != null && certs.length > 1) {
                                validateCertificateChain = true;
                            }
                        } else if (el.equals(WSSecurityEngine.SAML_TOKEN)) {
                            if (crypto == null) {
                                throw new WSSecurityException(
                                    WSSecurityException.FAILURE, "noSigCryptoFile"
                                );
                            }
                            SAMLKeyInfo samlKi = SAMLUtil.getSAMLKeyInfo(token, crypto, cb);
                            certs = samlKi.getCerts();
                            secretKey = samlKi.getSecret();
                            principal = createPrincipalFromSAMLKeyInfo(samlKi);
                        } else if (el.equals(WSSecurityEngine.ENCRYPTED_KEY)){
                            String encryptedKeyID = token.getAttribute("Id");                   
                            EncryptedKeyProcessor encryptKeyProcessor = 
                                new EncryptedKeyProcessor();
                            if (crypto == null) {
                                throw new WSSecurityException(
                                        WSSecurityException.FAILURE, "noSigCryptoFile"
                                );
                            }
                            encryptKeyProcessor.handleEncryptedKey(token, cb, crypto);
                            secretKey = encryptKeyProcessor.getDecryptedBytes();
                            principal = new CustomTokenPrincipal(encryptedKeyID);
                        } else {
                            String id = secRef.getReference().getURI();
                            secretKey = getSecretKeyFromCustomToken(id, cb);
                            principal = new CustomTokenPrincipal(id);
                        }
                    } else if (processor instanceof UsernameTokenProcessor) {
                        UsernameToken ut = ((UsernameTokenProcessor)processor).getUt();
                        if (ut.isDerivedKey()) {
                            secretKey = ut.getDerivedKey();
                        } else {
                            secretKey = ut.getSecretKey(secretKeyLength);
                        }
                        principal = ut.createPrincipal();
                    } else if (processor instanceof BinarySecurityTokenProcessor) {
                        certs = ((BinarySecurityTokenProcessor)processor).getCertificates();
                        if (certs != null && certs.length > 1) {
                            validateCertificateChain = true;
                        }
                    } else if (processor instanceof EncryptedKeyProcessor) {
                        EncryptedKeyProcessor encryptedKeyProcessor = 
                            (EncryptedKeyProcessor)processor;
                        secretKey = encryptedKeyProcessor.getDecryptedBytes();
                        principal = new CustomTokenPrincipal(encryptedKeyProcessor.getId());
                    } else if (processor instanceof SecurityContextTokenProcessor) {
                        SecurityContextTokenProcessor sctProcessor = 
                            (SecurityContextTokenProcessor)processor;
                        secretKey = sctProcessor.getSecret();
                        principal = new CustomTokenPrincipal(sctProcessor.getIdentifier());
                    } else if (processor instanceof DerivedKeyTokenProcessor) {
                        DerivedKeyTokenProcessor dktProcessor = 
                            (DerivedKeyTokenProcessor) processor;
                        DerivedKeyToken dkt = dktProcessor.getDerivedKeyToken();
                        int keyLength = dkt.getLength();
                        if (keyLength <= 0) {
                            String signatureMethodURI = getSignatureMethod(elem);
                            keyLength = WSSecurityUtil.getKeyLength(signatureMethodURI);
                        }
                        secretKey = dktProcessor.getKeyBytes(keyLength);
                        principal = dkt.createPrincipal();
                    } else if (processor instanceof SAMLTokenProcessor) {
                        if (crypto == null) {
                            throw new WSSecurityException(
                                WSSecurityException.FAILURE, "noSigCryptoFile"
                            );
                        }
                        SAMLTokenProcessor samlp = (SAMLTokenProcessor) processor;
                        SAMLKeyInfo samlKi = 
                            SAMLUtil.getSAMLKeyInfo(samlp.getSamlTokenElement(), crypto, cb);
                        certs = samlKi.getCerts();
                        secretKey = samlKi.getSecret();
                        publicKey = samlKi.getPublicKey();
                        principal = createPrincipalFromSAMLKeyInfo(samlKi);
                    }
                } else if (secRef.containsX509Data() || secRef.containsX509IssuerSerial()) {
                    certs = secRef.getX509IssuerSerial(crypto);
                } else if (secRef.containsKeyIdentifier()) {
                    if (secRef.getKeyIdentifierValueType().equals(SecurityTokenReference.ENC_KEY_SHA1_URI)) {
                        String id = secRef.getKeyIdentifierValue();
                        secretKey = getSecretKeyFromEncKeySHA1KI(id, cb);
                        principal = new CustomTokenPrincipal(id);
                    } else if (WSConstants.WSS_SAML_KI_VALUE_TYPE.equals(secRef.getKeyIdentifierValueType())) { 
                        Element token = 
                            secRef.getKeyIdentifierTokenElement(elem.getOwnerDocument(), wsDocInfo, cb);
                        
                        if (crypto == null) {
                            throw new WSSecurityException(
                                WSSecurityException.FAILURE, "noSigCryptoFile"
                            );
                        }
                        SAMLKeyInfo samlKi = SAMLUtil.getSAMLKeyInfo(token, crypto, cb);
                        certs = samlKi.getCerts();
                        secretKey = samlKi.getSecret();
                        publicKey = samlKi.getPublicKey();
                        principal = createPrincipalFromSAMLKeyInfo(samlKi);
                    } else {
                        certs = secRef.getKeyIdentifier(crypto);
                    }
                } else {
                    throw new WSSecurityException(
                        WSSecurityException.INVALID_SECURITY,
                        "unsupportedKeyInfo", 
                        new Object[]{strElement.toString()}
                    );
                }
            }
        } else {
            principal = getDefaultPrincipal(crypto);
        }
        //
        // Check that we have a certificate, a public key or a secret key with which to
        // perform signature verification
        //
        if ((certs == null || certs.length == 0 || certs[0] == null) 
            && secretKey == null
            && publicKey == null) {
            throw new WSSecurityException(WSSecurityException.FAILED_CHECK);
        }
        
        //
        // Validate certificates and verify trust
        //
        validateCertificates(certs);
        if (certs != null) {
            if (principal == null) {
                principal = certs[0].getSubjectX500Principal();
            }
            boolean trust = false;
            if (!validateCertificateChain || certs.length == 1) {
                trust = verifyTrust(certs[0], crypto);
            } else if (validateCertificateChain && certs.length > 1) {
                trust = verifyTrust(certs, crypto);
            }
            if (!trust) {
                throw new WSSecurityException(WSSecurityException.FAILED_CHECK);
            }
        }

        //
        // Perform the signature verification and build up a List of elements that the
        // signature refers to
        //
        Key key = null;
        if (certs != null && certs[0] != null) {
            key = certs[0].getPublicKey();
        } else if (publicKey != null) {
            key = publicKey;
        } else {
            String signatureMethod = getSignatureMethod(elem);
            key = WSSecurityUtil.prepareSecretKey(signatureMethod, secretKey);
        }
        XMLValidateContext context = new DOMValidateContext(key, elem);
        context.setProperty("javax.xml.crypto.dsig.cacheReference", Boolean.TRUE);
        URIDereferencer dereferencer = new DOMURIDereferencer();
        ((DOMURIDereferencer)dereferencer).setWsDocInfo(wsDocInfo);
        context.setURIDereferencer(dereferencer);
        context.setProperty(STRTransform.TRANSFORM_WS_DOC_INFO, wsDocInfo);
        try {
            XMLSignature xmlSignature = signatureFactory.unmarshalXMLSignature(context);
            boolean signatureOk = xmlSignature.validate(context);
            if (signatureOk) {
                signatureValue = xmlSignature.getSignatureValue().getValue();
                protectedRefs = 
                    buildProtectedRefs(
                        elem.getOwnerDocument(), xmlSignature.getSignedInfo(), wsDocInfo, protectedRefs
                    );
                
                return principal;
            } else {
                //
                // Log the exact signature error
                //
                if (log.isDebugEnabled()) {
                    log.debug("XML Signature verification has failed");
                    boolean signatureValidationCheck = 
                        xmlSignature.getSignatureValue().validate(context);
                    log.debug("Signature Validation check: " + signatureValidationCheck);
                    java.util.Iterator<?> referenceIterator = 
                        xmlSignature.getSignedInfo().getReferences().iterator();
                    while (referenceIterator.hasNext()) {
                        Reference reference = (Reference)referenceIterator.next();
                        boolean referenceValidationCheck = reference.validate(context);
                        String id = reference.getId();
                        if (id == null) {
                            id = reference.getURI();
                        }
                        log.debug("Reference " + id + " check: " + referenceValidationCheck);
                    }
                }
                
                throw new WSSecurityException(WSSecurityException.FAILED_CHECK);
            }
        } catch (Exception ex) {
            throw new WSSecurityException(
                WSSecurityException.FAILED_CHECK, null, null, ex
            );
        }
    }
    
    
    /**
     * Validate an array of certificates by checking the validity of each cert
     * @param certsToValidate The array of certificates to validate
     * @throws WSSecurityException
     */
    public static void validateCertificates(
        X509Certificate[] certsToValidate
    ) throws WSSecurityException {
        if (certsToValidate != null && certsToValidate.length > 0) {
            try {
                for (int i = 0; i < certsToValidate.length; i++) {
                    certsToValidate[i].checkValidity();
                }
            } catch (CertificateExpiredException e) {
                throw new WSSecurityException(
                    WSSecurityException.FAILED_CHECK, "invalidCert", null, e
                );
            } catch (CertificateNotYetValidException e) {
                throw new WSSecurityException(
                    WSSecurityException.FAILED_CHECK, "invalidCert", null, e
                );
            }
        }
    }
    
    
    /**
     * Evaluate whether a given certificate should be trusted.
     * 
     * Policy used in this implementation:
     * 1. Search the keystore for the transmitted certificate
     * 2. Search the keystore for a connection to the transmitted certificate
     * (that is, search for certificate(s) of the issuer of the transmitted certificate
     * 3. Verify the trust path for those certificates found because the search for the issuer 
     * might be fooled by a phony DN (String!)
     *
     * @param cert the certificate that should be validated against the keystore
     * @return true if the certificate is trusted, false if not
     * @throws WSSecurityException
     */
    public static boolean verifyTrust(X509Certificate cert, Crypto crypto) 
        throws WSSecurityException {

        // If no certificate was transmitted, do not trust the signature
        if (cert == null) {
            return false;
        }

        String subjectString = cert.getSubjectX500Principal().getName();
        String issuerString = cert.getIssuerX500Principal().getName();
        BigInteger issuerSerial = cert.getSerialNumber();

        if (log.isDebugEnabled()) {
            log.debug("Transmitted certificate has subject " + subjectString);
            log.debug(
                "Transmitted certificate has issuer " + issuerString + " (serial " 
                + issuerSerial + ")"
            );
        }

        //
        // FIRST step - Search the keystore for the transmitted certificate
        //
        if (crypto.isCertificateInKeyStore(cert)) {
            return true;
        }

        //
        // SECOND step - Search for the issuer of the transmitted certificate in the 
        // keystore or the truststore
        //
        String[] aliases = crypto.getAliasesForDN(issuerString);

        // If the alias has not been found, the issuer is not in the keystore/truststore
        // As a direct result, do not trust the transmitted certificate
        if (aliases == null || aliases.length < 1) {
            if (log.isDebugEnabled()) {
                log.debug(
                    "No aliases found in keystore for issuer " + issuerString 
                    + " of certificate for " + subjectString
                );
            }
            return false;
        }

        //
        // THIRD step
        // Check the certificate trust path for every alias of the issuer found in the 
        // keystore/truststore
        //
        for (int i = 0; i < aliases.length; i++) {
            String alias = aliases[i];

            if (log.isDebugEnabled()) {
                log.debug(
                    "Preparing to validate certificate path with alias " + alias 
                    + " for issuer " + issuerString
                );
            }

            // Retrieve the certificate(s) for the alias from the keystore/truststore
            X509Certificate[] certs = crypto.getCertificates(alias);

            // If no certificates have been found, there has to be an error:
            // The keystore/truststore can find an alias but no certificate(s)
            if (certs == null || certs.length < 1) {
                throw new WSSecurityException(
                    "Could not get certificates for alias " + alias
                );
            }

            //
            // Form a certificate chain from the transmitted certificate
            // and the certificate(s) of the issuer from the keystore/truststore
            //
            X509Certificate[] x509certs = new X509Certificate[certs.length + 1];
            x509certs[0] = cert;
            for (int j = 0; j < certs.length; j++) {
                x509certs[j + 1] = certs[j];
            }

            //
            // Use the validation method from the crypto to check whether the subjects' 
            // certificate was really signed by the issuer stated in the certificate
            //
            if (crypto.validateCertPath(x509certs)) {
                if (log.isDebugEnabled()) {
                    log.debug(
                        "Certificate path has been verified for certificate with subject " 
                        + subjectString
                    );
                }
                return true;
            }
        }

        if (log.isDebugEnabled()) {
            log.debug(
                "Certificate path could not be verified for certificate with subject " 
                + subjectString
            );
        }
        return false;
    }
    

    /**
     * Evaluate whether the given certificate chain should be trusted.
     * 
     * @param certificates the certificate chain that should be validated against the keystore
     * @return true if the certificate chain is trusted, false if not
     * @throws WSSecurityException
     */
    public static boolean verifyTrust(X509Certificate[] certificates, Crypto crypto) 
        throws WSSecurityException {
        String subjectString = certificates[0].getSubjectX500Principal().getName();
        //
        // Use the validation method from the crypto to check whether the subjects' 
        // certificate was really signed by the issuer stated in the certificate
        //
        if (certificates != null && certificates.length > 1
            && crypto.validateCertPath(certificates)) {
            if (log.isDebugEnabled()) {
                log.debug(
                    "Certificate path has been verified for certificate with subject " 
                    + subjectString
                );
            }
            return true;
        }
        
        if (log.isDebugEnabled()) {
            log.debug(
                "Certificate path could not be verified for certificate with subject " 
                + subjectString
            );
        }
            
        return false;
    }
    
    
    
    /**
     * Get the default principal from the KeyStore
     * @param crypto The Crypto object containing the default alias
     * @return The default principal
     * @throws WSSecurityException
     */
    private Principal getDefaultPrincipal(
        Crypto crypto
    ) throws WSSecurityException {
        if (crypto == null) {
            throw new WSSecurityException(WSSecurityException.FAILURE, "noSigCryptoFile");
        }
        if (crypto.getDefaultX509Alias() != null) {
            certs = crypto.getCertificates(crypto.getDefaultX509Alias());
            return certs[0].getSubjectX500Principal();
        } else {
            throw new WSSecurityException(
                WSSecurityException.INVALID_SECURITY, "unsupportedKeyInfo"
            );
        }
    }
    
    
    /**
     * Get the Secret Key from a CallbackHandler for a custom token
     * @param id The id of the element
     * @param cb The CallbackHandler object
     * @return A Secret Key
     * @throws WSSecurityException
     */
    private byte[] getSecretKeyFromCustomToken(
        String id,
        CallbackHandler cb
    ) throws WSSecurityException {
        if (id.charAt(0) == '#') {
            id = id.substring(1);
        }
        WSPasswordCallback pwcb = 
            new WSPasswordCallback(id, WSPasswordCallback.CUSTOM_TOKEN);
        try {
            Callback[] callbacks = new Callback[]{pwcb};
            cb.handle(callbacks);
        } catch (Exception e) {
            throw new WSSecurityException(
                WSSecurityException.FAILURE,
                "noPassword", 
                new Object[] {id}, 
                e
            );
        }

        return pwcb.getKey();
    }
    
    
    /**
     * Get the Secret Key from a CallbackHandler for the Encrypted Key SHA1 case.
     * @param id The id of the element
     * @param cb The CallbackHandler object
     * @return A Secret Key
     * @throws WSSecurityException
     */
    private byte[] getSecretKeyFromEncKeySHA1KI(
        String id,
        CallbackHandler cb
    ) throws WSSecurityException {
        WSPasswordCallback pwcb = 
            new WSPasswordCallback(
                id,
                null,
                SecurityTokenReference.ENC_KEY_SHA1_URI,
                WSPasswordCallback.ENCRYPTED_KEY_TOKEN
            );
        try {
            Callback[] callbacks = new Callback[]{pwcb};
            cb.handle(callbacks);
        } catch (Exception e) {
            throw new WSSecurityException(
                WSSecurityException.FAILURE,
                "noPassword", 
                new Object[] {id}, 
                e
            );
        }
        return pwcb.getKey();
    }
    
    
    /**
     * Get the signature method algorithm URI from the associated signature element.
     * @param signatureElement The signature element
     * @return the signature method URI
     */
    private static String getSignatureMethod(
        Element signatureElement
    ) {
        Element signedInfoElement = 
            WSSecurityUtil.getDirectChildElement(
                signatureElement,
                "SignedInfo",
                WSConstants.SIG_NS
            );
        if (signedInfoElement != null) {
            Element signatureMethodElement = 
                WSSecurityUtil.getDirectChildElement(
                    signedInfoElement,
                    "SignatureMethod",
                    WSConstants.SIG_NS
                );
            if (signatureMethodElement != null) {
                return signatureMethodElement.getAttributeNS(null, "Algorithm");
            }
        }
        return null;
    }
    
    
    /**
     * A method to create a Principal from a SAML KeyInfo
     * @param samlKeyInfo The SAML KeyInfo object
     * @return A principal
     */
    private static Principal createPrincipalFromSAMLKeyInfo(
        SAMLKeyInfo samlKeyInfo
    ) {
        X509Certificate[] samlCerts = samlKeyInfo.getCerts();
        Principal principal = null;
        if (samlCerts != null && samlCerts.length > 0) {
            principal = samlCerts[0].getSubjectX500Principal();
        } else {
            final SAMLAssertion assertion = samlKeyInfo.getAssertion();
            principal = new CustomTokenPrincipal(assertion.getId());
            ((CustomTokenPrincipal)principal).setTokenObject(assertion);
        }
        return principal;
    }
    
    
    /**
     * Get the KeyValue object from the KeyInfo DOM element if it exists
     */
    private KeyValue getKeyValue(
        Element keyInfoElement
    ) throws MarshalException {
        XMLStructure keyInfoStructure = new DOMStructure(keyInfoElement);
        KeyInfo keyInfo = keyInfoFactory.unmarshalKeyInfo(keyInfoStructure);
        List<?> list = keyInfo.getContent();

        for (int i = 0; i < list.size(); i++) {
            XMLStructure xmlStructure = (XMLStructure) list.get(i);
            if (xmlStructure instanceof KeyValue) {
                return (KeyValue)xmlStructure;
            }
        }
        return null;
    }
    
    /**
     * Validate a public key via a CallbackHandler
     * @param cb The CallbackHandler object
     * @param publicKey The PublicKey to validate
     * @return A PublicKeyPrincipal object encapsulating the public key after successful 
     *         validation
     * @throws WSSecurityException
     */
    private static Principal validatePublicKey(
        CallbackHandler cb,
        PublicKey publicKey
    ) throws WSSecurityException {
        PublicKeyCallback pwcb = 
            new PublicKeyCallback(publicKey);
        try {
            Callback[] callbacks = new Callback[]{pwcb};
            cb.handle(callbacks);
            if (!pwcb.isVerified()) {
                throw new WSSecurityException(
                    WSSecurityException.FAILED_AUTHENTICATION, null, null, null
                );
            }
        } catch (Exception e) {
            throw new WSSecurityException(
                WSSecurityException.FAILED_AUTHENTICATION, null, null, e
            );
        }
        return new PublicKeyPrincipal(publicKey);
    }
    
    
    /**
     * This method digs into the Signature element to get the elements that
     * this Signature covers. Build the QName of these Elements and return them
     * to caller
     * @param doc The owning document
     * @param signedInfo The SignedInfo object
     * @param protectedRefs A list of protected references
     * @return A list of protected references
     * @throws WSSecurityException
     */
    private List<WSDataRef> buildProtectedRefs(
        Document doc,
        SignedInfo signedInfo,
        WSDocInfo wsDocInfo,
        List<WSDataRef> protectedRefs
    ) throws WSSecurityException {
        List<?> referencesList = signedInfo.getReferences();
        for (int i = 0; i < referencesList.size(); i++) {
            Reference siRef = (Reference)referencesList.get(i);
            String uri = siRef.getURI();
            
            if (!"".equals(uri)) {
                Element se = null;
                
                List<?> transformsList = siRef.getTransforms();
                
                for (int j = 0; j < transformsList.size(); j++) {
                    
                    Transform transform = (Transform)transformsList.get(j);
                    
                    if (STRTransform.TRANSFORM_URI.equals(transform.getAlgorithm())) {
                        NodeSetData data = (NodeSetData)siRef.getDereferencedData();
                        if (data != null) {
                            java.util.Iterator<?> iter = data.iterator();
                            
                            Node securityTokenReference = null;
                            while (iter.hasNext()) {
                                Node node = (Node)iter.next();
                                if ("SecurityTokenReference".equals(node.getLocalName())) {
                                    securityTokenReference = node;
                                    break;
                                }
                            }
                            
                            if (securityTokenReference != null) {
                                SecurityTokenReference secTokenRef = 
                                    new SecurityTokenReference((Element)securityTokenReference);
                                se = STRTransformUtil.dereferenceSTR(doc, secTokenRef, wsDocInfo);
                            }
                        }
                    }
                }
                
                if (se == null) {
                    se = 
                        WSSecurityUtil.findElementById(
                            doc.getDocumentElement(), uri, false
                        );
                }
                if (se == null) {
                    throw new WSSecurityException(WSSecurityException.FAILED_CHECK);
                }
                
                signatureMethod = signedInfo.getSignatureMethod().getAlgorithm();
                c14nMethod = signedInfo.getCanonicalizationMethod().getAlgorithm();
                
                WSDataRef ref = new WSDataRef();
                ref.setWsuId(uri);
                ref.setProtectedElement(se);
                ref.setAlgorithm(signatureMethod);
                ref.setDigestAlgorithm(siRef.getDigestMethod().getAlgorithm());
                ref.setXpath(ReferenceListProcessor.getXPath(se));
                protectedRefs.add(ref);
            }
        }
        return protectedRefs;
    }
    
    
    /**
     * Extracts the certificate(s) from the Binary Security token reference.
     *
     * @param elem The element containing the binary security token. This is
     *             either X509 certificate(s) or a PKIPath.
     * @return an array of X509 certificates
     * @throws WSSecurityException
     */
    public static X509Certificate[] getCertificatesTokenReference(Element elem, Crypto crypto)
        throws WSSecurityException {
        if (crypto == null) {
            throw new WSSecurityException(WSSecurityException.FAILURE, "noSigCryptoFile");
        }
        BinarySecurity token = createSecurityToken(elem);
        if (token instanceof PKIPathSecurity) {
            return ((PKIPathSecurity) token).getX509Certificates(crypto);
        } else {
            X509Certificate cert = ((X509Security) token).getX509Certificate(crypto);
            return new X509Certificate[]{cert};
        }
    }


    /**
     * Checks the <code>element</code> and creates appropriate binary security object.
     *
     * @param element The XML element that contains either a <code>BinarySecurityToken
     *                </code> or a <code>PKIPath</code> element. Other element types a not
     *                supported
     * @return the BinarySecurity object, either a <code>X509Security</code> or a
     *         <code>PKIPathSecurity</code> object.
     * @throws WSSecurityException
     */
    private static BinarySecurity createSecurityToken(Element element) throws WSSecurityException {

        String type = element.getAttribute("ValueType");
        if (X509Security.X509_V3_TYPE.equals(type)) {
            X509Security x509 = new X509Security(element);
            return (BinarySecurity) x509;
        } else if (PKIPathSecurity.getType().equals(type)) {
            PKIPathSecurity pkiPath = new PKIPathSecurity(element);
            return (BinarySecurity) pkiPath;
        }
        throw new WSSecurityException(
            WSSecurityException.UNSUPPORTED_SECURITY_TOKEN,
            "unsupportedBinaryTokenType", 
            new Object[]{type}
        );
    }

    public String getId() {
        return signatureId;
    }

}