/*
 * Copyright 1996-2011 itServe AG. All rights reserved.
 *
 * This software is the proprietary information of itServe AG
 * Bern Switzerland. Use is subject to license terms.
 *
 */
package org.swssf.test;

import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSSConfig;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.components.crypto.CryptoFactory;
import org.apache.ws.security.handler.WSHandlerConstants;
import org.apache.ws.security.message.*;
import org.apache.ws.security.util.WSSecurityUtil;
import org.swssf.WSSec;
import org.swssf.ext.Constants;
import org.swssf.ext.InboundWSSec;
import org.swssf.ext.OutboundWSSec;
import org.swssf.ext.SecurityProperties;
import org.swssf.securityEvent.SecurityEvent;
import org.swssf.test.utils.SOAPUtil;
import org.swssf.test.utils.SecretKeyCallbackHandler;
import org.swssf.test.utils.StAX2DOM;
import org.swssf.test.utils.XmlReaderToWriter;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.soap.SOAPConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Properties;

/**
 * @author $Author: $
 * @version $Revision: $ $Date: $
 */
public class SecurityContextTokenTest extends AbstractTestBase {

    @BeforeClass
    public void setUp() throws Exception {
        WSSConfig.init();
    }

    @Test
    public void testSCTDKTEncryptOutbound() throws Exception {
        byte[] secret = new byte[128 / 8];
        Constants.secureRandom.nextBytes(secret);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            SecurityProperties securityProperties = new SecurityProperties();
            Constants.Action[] actions = new Constants.Action[]{Constants.Action.ENCRYPT_WITH_DERIVED_KEY};
            securityProperties.setOutAction(actions);
            CallbackHandlerImpl callbackHandler = new CallbackHandlerImpl(secret);
            securityProperties.setCallbackHandler(callbackHandler);
            securityProperties.loadEncryptionKeystore(this.getClass().getClassLoader().getResource("transmitter.jks"), "default".toCharArray());
            securityProperties.setEncryptionUser("receiver");
            securityProperties.setEncryptionSymAlgorithm("http://www.w3.org/2001/04/xmlenc#aes128-cbc");
            securityProperties.setDerivedKeyTokenReference(Constants.DerivedKeyTokenReference.SecurityContextToken);

            OutboundWSSec wsSecOut = WSSec.getOutboundWSSec(securityProperties);
            XMLStreamWriter xmlStreamWriter = wsSecOut.processOutMessage(baos, "UTF-8", new ArrayList<SecurityEvent>());
            XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml"));
            XmlReaderToWriter.writeAll(xmlStreamReader, xmlStreamWriter);
            xmlStreamWriter.close();

            Document document = documentBuilderFactory.newDocumentBuilder().parse(new ByteArrayInputStream(baos.toByteArray()));
            NodeList nodeList = document.getElementsByTagNameNS(Constants.TAG_xenc_EncryptedData.getNamespaceURI(), Constants.TAG_xenc_EncryptedData.getLocalPart());
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), Constants.TAG_soap11_Body.getLocalPart());
            nodeList = document.getElementsByTagNameNS(Constants.TAG_wsc0502_SecurityContextToken.getNamespaceURI(), Constants.TAG_wsc0502_SecurityContextToken.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 1);
            nodeList = document.getElementsByTagNameNS(Constants.TAG_wsc0502_DerivedKeyToken.getNamespaceURI(), Constants.TAG_wsc0502_DerivedKeyToken.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 1);
            nodeList = document.getElementsByTagNameNS(Constants.TAG_xenc_ReferenceList.getNamespaceURI(), Constants.TAG_xenc_ReferenceList.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 1);
            nodeList = document.getElementsByTagNameNS(Constants.TAG_xenc_EncryptedKey.getNamespaceURI(), Constants.TAG_xenc_EncryptedKey.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 0);
        }
        {
            String action = WSHandlerConstants.ENCRYPT;
            Properties properties = new Properties();
            WSS4JCallbackHandlerImpl callbackHandler = new WSS4JCallbackHandlerImpl(secret);
            properties.put(WSHandlerConstants.PW_CALLBACK_REF, callbackHandler);
            doInboundSecurityWithWSS4J_1(documentBuilderFactory.newDocumentBuilder().parse(new ByteArrayInputStream(baos.toByteArray())), action, SOAPConstants.SOAP_1_1_PROTOCOL, properties, false);
        }
    }

    @Test
    public void testSCTDKTEncryptInbound() throws Exception {

        byte[] tempSecret = WSSecurityUtil.generateNonce(16);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            Document doc = SOAPUtil.toSOAPPart(SOAPUtil.SAMPLE_SOAP_MSG);
            WSSecHeader secHeader = new WSSecHeader();
            secHeader.insertSecurityHeader(doc);

            WSSecSecurityContextToken sctBuilder = new WSSecSecurityContextToken();
            Crypto crypto = CryptoFactory.getInstance("transmitter-crypto.properties");
            sctBuilder.prepare(doc, crypto);

            // Store the secret
            SecretKeyCallbackHandler callbackHandler = new SecretKeyCallbackHandler();
            callbackHandler.addSecretKey(sctBuilder.getIdentifier(), tempSecret);

            String tokenId = sctBuilder.getSctId();

            // Derived key encryption
            WSSecDKEncrypt encrBuilder = new WSSecDKEncrypt();
            encrBuilder.setSymmetricEncAlgorithm(WSConstants.AES_128);
            encrBuilder.setExternalKey(tempSecret, tokenId);
            encrBuilder.build(doc, secHeader);

            sctBuilder.prependSCTElementToHeader(doc, secHeader);

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(doc), new StreamResult(baos));
        }

        {
            SecurityProperties securityProperties = new SecurityProperties();
            securityProperties.loadDecryptionKeystore(this.getClass().getClassLoader().getResource("receiver.jks"), "default".toCharArray());
            CallbackHandlerImpl callbackHandler = new CallbackHandlerImpl(tempSecret);
            securityProperties.setCallbackHandler(callbackHandler);
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            Document document = StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);

            NodeList nodeList = document.getElementsByTagNameNS(Constants.TAG_xenc_EncryptedData.getNamespaceURI(), Constants.TAG_xenc_EncryptedData.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 0);
        }
    }

    @Test
    public void testSCTKDKTSignOutbound() throws Exception {
        byte[] secret = new byte[128 / 8];
        Constants.secureRandom.nextBytes(secret);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            SecurityProperties securityProperties = new SecurityProperties();
            Constants.Action[] actions = new Constants.Action[]{Constants.Action.SIGNATURE_WITH_DERIVED_KEY};
            securityProperties.setOutAction(actions);
            CallbackHandlerImpl callbackHandler = new CallbackHandlerImpl(secret);
            securityProperties.setCallbackHandler(callbackHandler);
            securityProperties.loadSignatureKeyStore(this.getClass().getClassLoader().getResource("transmitter.jks"), "default".toCharArray());
            securityProperties.setSignatureUser("transmitter");
            securityProperties.setDerivedKeyTokenReference(Constants.DerivedKeyTokenReference.SecurityContextToken);

            OutboundWSSec wsSecOut = WSSec.getOutboundWSSec(securityProperties);
            XMLStreamWriter xmlStreamWriter = wsSecOut.processOutMessage(baos, "UTF-8", new ArrayList<SecurityEvent>());
            XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml"));
            XmlReaderToWriter.writeAll(xmlStreamReader, xmlStreamWriter);
            xmlStreamWriter.close();

            System.out.println(new String(baos.toByteArray()));

            Document document = documentBuilderFactory.newDocumentBuilder().parse(new ByteArrayInputStream(baos.toByteArray()));
            NodeList nodeList = document.getElementsByTagNameNS(Constants.TAG_dsig_Signature.getNamespaceURI(), Constants.TAG_dsig_Signature.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 1);
            nodeList = document.getElementsByTagNameNS(Constants.TAG_wsc0502_SecurityContextToken.getNamespaceURI(), Constants.TAG_wsc0502_SecurityContextToken.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 1);
            nodeList = document.getElementsByTagNameNS(Constants.TAG_wsc0502_DerivedKeyToken.getNamespaceURI(), Constants.TAG_wsc0502_DerivedKeyToken.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 1);
            nodeList = document.getElementsByTagNameNS(Constants.TAG_xenc_ReferenceList.getNamespaceURI(), Constants.TAG_xenc_ReferenceList.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 0);
            nodeList = document.getElementsByTagNameNS(Constants.TAG_xenc_EncryptedKey.getNamespaceURI(), Constants.TAG_xenc_EncryptedKey.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 0);
        }
        {
            String action = WSHandlerConstants.SIGNATURE;
            Properties properties = new Properties();
            WSS4JCallbackHandlerImpl callbackHandler = new WSS4JCallbackHandlerImpl(secret);
            properties.put(WSHandlerConstants.PW_CALLBACK_REF, callbackHandler);
            doInboundSecurityWithWSS4J_1(documentBuilderFactory.newDocumentBuilder().parse(new ByteArrayInputStream(baos.toByteArray())), action, SOAPConstants.SOAP_1_1_PROTOCOL, properties, false);
        }
    }

    @Test
    public void testSCTKDKTSignInbound() throws Exception {

        byte[] tempSecret = WSSecurityUtil.generateNonce(16);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            Document doc = SOAPUtil.toSOAPPart(SOAPUtil.SAMPLE_SOAP_MSG);
            WSSecHeader secHeader = new WSSecHeader();
            secHeader.insertSecurityHeader(doc);

            WSSecSecurityContextToken sctBuilder = new WSSecSecurityContextToken();
            Crypto crypto = CryptoFactory.getInstance("transmitter-crypto.properties");
            sctBuilder.prepare(doc, crypto);

            // Store the secret
            SecretKeyCallbackHandler callbackHandler = new SecretKeyCallbackHandler();
            callbackHandler.addSecretKey(sctBuilder.getIdentifier(), tempSecret);

            String tokenId = sctBuilder.getSctId();

            // Derived key signature
            WSSecDKSign sigBuilder = new WSSecDKSign();
            sigBuilder.setExternalKey(tempSecret, tokenId);
            sigBuilder.setSignatureAlgorithm(WSConstants.HMAC_SHA1);
            sigBuilder.build(doc, secHeader);

            sctBuilder.prependSCTElementToHeader(doc, secHeader);

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(doc), new StreamResult(baos));
            System.out.println(new String(baos.toByteArray()));
        }

        {
            SecurityProperties securityProperties = new SecurityProperties();
            securityProperties.loadSignatureVerificationKeystore(this.getClass().getClassLoader().getResource("receiver.jks"), "default".toCharArray());
            securityProperties.loadDecryptionKeystore(this.getClass().getClassLoader().getResource("receiver.jks"), "default".toCharArray());
            CallbackHandlerImpl callbackHandler = new CallbackHandlerImpl(tempSecret);
            securityProperties.setCallbackHandler(callbackHandler);
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            Document document = StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);
        }
    }

    @Test
    public void testSCTKDKTSignAbsoluteInbound() throws Exception {

        byte[] tempSecret = WSSecurityUtil.generateNonce(16);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            Document doc = SOAPUtil.toSOAPPart(SOAPUtil.SAMPLE_SOAP_MSG);
            WSSecHeader secHeader = new WSSecHeader();
            secHeader.insertSecurityHeader(doc);

            WSSecSecurityContextToken sctBuilder = new WSSecSecurityContextToken();
            Crypto crypto = CryptoFactory.getInstance("transmitter-crypto.properties");
            sctBuilder.prepare(doc, crypto);

            // Store the secret
            SecretKeyCallbackHandler callbackHandler = new SecretKeyCallbackHandler();
            callbackHandler.addSecretKey(sctBuilder.getIdentifier(), tempSecret);

            // Derived key signature
            WSSecDKSign sigBuilder = new WSSecDKSign();
            sigBuilder.setExternalKey(tempSecret, sctBuilder.getIdentifier());
            sigBuilder.setTokenIdDirectId(true);
            sigBuilder.setSignatureAlgorithm(WSConstants.HMAC_SHA1);
            sigBuilder.build(doc, secHeader);

            sctBuilder.prependSCTElementToHeader(doc, secHeader);

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(doc), new StreamResult(baos));

            System.out.println(new String(baos.toByteArray()));
        }

        {
            SecurityProperties securityProperties = new SecurityProperties();
            securityProperties.loadSignatureVerificationKeystore(this.getClass().getClassLoader().getResource("receiver.jks"), "default".toCharArray());
            securityProperties.loadDecryptionKeystore(this.getClass().getClassLoader().getResource("receiver.jks"), "default".toCharArray());
            CallbackHandlerImpl callbackHandler = new CallbackHandlerImpl(tempSecret);
            securityProperties.setCallbackHandler(callbackHandler);
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            Document document = StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);
        }
    }

    @Test
    public void testSCTKDKTSignEncrypt() throws Exception {

        byte[] tempSecret = WSSecurityUtil.generateNonce(16);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            Document doc = SOAPUtil.toSOAPPart(SOAPUtil.SAMPLE_SOAP_MSG);
            WSSecHeader secHeader = new WSSecHeader();
            secHeader.insertSecurityHeader(doc);

            WSSecSecurityContextToken sctBuilder = new WSSecSecurityContextToken();
            Crypto crypto = CryptoFactory.getInstance("transmitter-crypto.properties");
            sctBuilder.prepare(doc, crypto);

            // Store the secret
            SecretKeyCallbackHandler callbackHandler = new SecretKeyCallbackHandler();
            callbackHandler.addSecretKey(sctBuilder.getIdentifier(), tempSecret);

            String tokenId = sctBuilder.getSctId();

            // Derived key signature
            WSSecDKSign sigBuilder = new WSSecDKSign();
            sigBuilder.setExternalKey(tempSecret, tokenId);
            sigBuilder.setSignatureAlgorithm(WSConstants.HMAC_SHA1);
            sigBuilder.build(doc, secHeader);

            // Derived key encryption
            WSSecDKEncrypt encrBuilder = new WSSecDKEncrypt();
            encrBuilder.setSymmetricEncAlgorithm(WSConstants.AES_128);
            encrBuilder.setExternalKey(tempSecret, tokenId);
            encrBuilder.build(doc, secHeader);

            sctBuilder.prependSCTElementToHeader(doc, secHeader);

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(doc), new StreamResult(baos));
        }

        {
            SecurityProperties securityProperties = new SecurityProperties();
            securityProperties.loadDecryptionKeystore(this.getClass().getClassLoader().getResource("receiver.jks"), "default".toCharArray());
            securityProperties.loadSignatureVerificationKeystore(this.getClass().getClassLoader().getResource("receiver.jks"), "default".toCharArray());
            CallbackHandlerImpl callbackHandler = new CallbackHandlerImpl(tempSecret);
            securityProperties.setCallbackHandler(callbackHandler);
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            Document document = StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);

            NodeList nodeList = document.getElementsByTagNameNS(Constants.TAG_xenc_EncryptedData.getNamespaceURI(), Constants.TAG_xenc_EncryptedData.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 0);
        }
    }

    @Test
    public void testSCTKDKTEncryptSign() throws Exception {

        byte[] tempSecret = WSSecurityUtil.generateNonce(16);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            Document doc = SOAPUtil.toSOAPPart(SOAPUtil.SAMPLE_SOAP_MSG);
            WSSecHeader secHeader = new WSSecHeader();
            secHeader.insertSecurityHeader(doc);

            WSSecSecurityContextToken sctBuilder = new WSSecSecurityContextToken();
            Crypto crypto = CryptoFactory.getInstance("transmitter-crypto.properties");
            sctBuilder.prepare(doc, crypto);

            // Store the secret
            SecretKeyCallbackHandler callbackHandler = new SecretKeyCallbackHandler();
            callbackHandler.addSecretKey(sctBuilder.getIdentifier(), tempSecret);

            String tokenId = sctBuilder.getSctId();

            // Derived key encryption
            WSSecDKEncrypt encrBuilder = new WSSecDKEncrypt();
            encrBuilder.setSymmetricEncAlgorithm(WSConstants.AES_128);
            encrBuilder.setExternalKey(tempSecret, tokenId);
            encrBuilder.build(doc, secHeader);

            // Derived key signature
            WSSecDKSign sigBuilder = new WSSecDKSign();
            sigBuilder.setExternalKey(tempSecret, tokenId);
            sigBuilder.setSignatureAlgorithm(WSConstants.HMAC_SHA1);
            sigBuilder.build(doc, secHeader);

            sctBuilder.prependSCTElementToHeader(doc, secHeader);

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(doc), new StreamResult(baos));
        }

        {
            SecurityProperties securityProperties = new SecurityProperties();
            securityProperties.loadDecryptionKeystore(this.getClass().getClassLoader().getResource("receiver.jks"), "default".toCharArray());
            securityProperties.loadSignatureVerificationKeystore(this.getClass().getClassLoader().getResource("receiver.jks"), "default".toCharArray());
            CallbackHandlerImpl callbackHandler = new CallbackHandlerImpl(tempSecret);
            securityProperties.setCallbackHandler(callbackHandler);
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            Document document = StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);

            NodeList nodeList = document.getElementsByTagNameNS(Constants.TAG_xenc_EncryptedData.getNamespaceURI(), Constants.TAG_xenc_EncryptedData.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 0);
        }
    }

    @Test
    public void testSCTSign() throws Exception {

        byte[] tempSecret = WSSecurityUtil.generateNonce(16);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        {
            Document doc = SOAPUtil.toSOAPPart(SOAPUtil.SAMPLE_SOAP_MSG);
            WSSecHeader secHeader = new WSSecHeader();
            secHeader.insertSecurityHeader(doc);

            WSSecSecurityContextToken sctBuilder = new WSSecSecurityContextToken();
            Crypto crypto = CryptoFactory.getInstance("transmitter-crypto.properties");
            sctBuilder.prepare(doc, crypto);

            // Store the secret
            SecretKeyCallbackHandler callbackHandler = new SecretKeyCallbackHandler();
            callbackHandler.addSecretKey(sctBuilder.getIdentifier(), tempSecret);

            String tokenId = sctBuilder.getSctId();

            WSSecSignature builder = new WSSecSignature();
            builder.setSecretKey(tempSecret);
            builder.setKeyIdentifierType(WSConstants.CUSTOM_SYMM_SIGNING);
            builder.setCustomTokenValueType(WSConstants.WSC_SCT);
            builder.setCustomTokenId(tokenId);
            builder.setSignatureAlgorithm(SignatureMethod.HMAC_SHA1);
            builder.build(doc, crypto, secHeader);

            sctBuilder.prependSCTElementToHeader(doc, secHeader);

            javax.xml.transform.Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.transform(new DOMSource(doc), new StreamResult(baos));
        }

        {
            SecurityProperties securityProperties = new SecurityProperties();
            securityProperties.loadSignatureVerificationKeystore(this.getClass().getClassLoader().getResource("receiver.jks"), "default".toCharArray());
            CallbackHandlerImpl callbackHandler = new CallbackHandlerImpl(tempSecret);
            securityProperties.setCallbackHandler(callbackHandler);
            InboundWSSec wsSecIn = WSSec.getInboundWSSec(securityProperties);
            XMLStreamReader xmlStreamReader = wsSecIn.processInMessage(xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(baos.toByteArray())));

            Document document = StAX2DOM.readDoc(documentBuilderFactory.newDocumentBuilder(), xmlStreamReader);
        }
    }
}
