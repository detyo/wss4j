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
package org.swssf.wss.impl.processor.input;

import org.swssf.binding.wss10.SecurityTokenReferenceType;
import org.swssf.binding.xmldsig.KeyInfoType;
import org.swssf.binding.xmlenc.EncryptedDataType;
import org.swssf.binding.xmlenc.ReferenceList;
import org.swssf.binding.xmlenc.ReferenceType;
import org.swssf.wss.ext.*;
import org.swssf.wss.securityEvent.ContentEncryptedElementSecurityEvent;
import org.swssf.wss.securityEvent.EncryptedElementSecurityEvent;
import org.swssf.wss.securityEvent.EncryptedPartSecurityEvent;
import org.swssf.wss.securityEvent.TokenSecurityEvent;
import org.swssf.xmlsec.ext.*;
import org.swssf.xmlsec.impl.processor.input.AbstractDecryptInputProcessor;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import javax.xml.stream.events.XMLEvent;
import java.util.Iterator;
import java.util.List;

/**
 * Processor for decryption of EncryptedData XML structures
 *
 * @author $Author$
 * @version $Revision$ $Date$
 */
public class DecryptInputProcessor extends AbstractDecryptInputProcessor {

    public DecryptInputProcessor(KeyInfoType keyInfoType, ReferenceList referenceList,
                                 WSSSecurityProperties securityProperties, WSSecurityContext securityContext)
            throws XMLSecurityException {

        super(keyInfoType, referenceList, securityProperties);
        checkBSPCompliance(keyInfoType, referenceList, securityContext, WSSConstants.BSPRule.R3006);
    }

    private void checkBSPCompliance(KeyInfoType keyInfoType, ReferenceList referenceList, WSSecurityContext securityContext,
                                    WSSConstants.BSPRule bspRule) throws WSSecurityException {
        if (keyInfoType != null) {
            if (keyInfoType.getContent().size() != 1) {
                securityContext.handleBSPRule(WSSConstants.BSPRule.R5424);
            }
            SecurityTokenReferenceType securityTokenReferenceType = XMLSecurityUtils.getQNameType(keyInfoType.getContent(),
                    WSSConstants.TAG_wsse_SecurityTokenReference);
            if (securityTokenReferenceType == null) {
                securityContext.handleBSPRule(WSSConstants.BSPRule.R5426);
            }
        }

        if (referenceList != null) {
            List<JAXBElement<ReferenceType>> references = referenceList.getDataReferenceOrKeyReference();
            Iterator<JAXBElement<ReferenceType>> referenceTypeIterator = references.iterator();
            while (referenceTypeIterator.hasNext()) {
                ReferenceType referenceType = referenceTypeIterator.next().getValue();
                if (!referenceType.getURI().startsWith("#")) {
                    securityContext.handleBSPRule(bspRule);
                }
            }
        }
    }

    protected void handleEncryptedContent(InputProcessorChain inputProcessorChain, XMLEvent parentXMLEvent, XMLEvent xmlEvent,
                                          SecurityToken securityToken) throws XMLSecurityException {

        final WSSDocumentContext documentContext = (WSSDocumentContext) inputProcessorChain.getDocumentContext();
        List<QName> parentElementPath = documentContext.getParentElementPath(xmlEvent.getEventType());
        if (documentContext.getDocumentLevel() == 3
                && documentContext.isInSOAPBody()) {
            //soap:body content encryption counts as EncryptedPart
            EncryptedPartSecurityEvent encryptedPartSecurityEvent =
                    new EncryptedPartSecurityEvent(securityToken, true, documentContext.getProtectionOrder());
            encryptedPartSecurityEvent.setElementPath(parentElementPath);
            encryptedPartSecurityEvent.setXmlEvent(parentXMLEvent);
            ((WSSecurityContext) inputProcessorChain.getSecurityContext()).registerSecurityEvent(encryptedPartSecurityEvent);
        } else {
            ContentEncryptedElementSecurityEvent contentEncryptedElementSecurityEvent =
                    new ContentEncryptedElementSecurityEvent(securityToken, true, documentContext.getProtectionOrder());
            contentEncryptedElementSecurityEvent.setElementPath(parentElementPath);
            contentEncryptedElementSecurityEvent.setXmlEvent(parentXMLEvent);
            ((WSSecurityContext) inputProcessorChain.getSecurityContext()).registerSecurityEvent(contentEncryptedElementSecurityEvent);
        }
    }

    @Override
    protected AbstractDecryptedEventReaderInputProcessor newDecryptedEventReaderInputProccessor(
            boolean encryptedHeader, List<ComparableNamespace>[] comparableNamespaceList,
            List<ComparableAttribute>[] comparableAttributeList, EncryptedDataType currentEncryptedDataType,
            SecurityToken securityToken, SecurityContext securityContext) throws WSSecurityException {

        String encryptionAlgorithm = currentEncryptedDataType.getEncryptionMethod().getAlgorithm();
        if (!WSSConstants.NS_XENC_TRIBLE_DES.equals(encryptionAlgorithm)
                && !WSSConstants.NS_XENC_AES128.equals(encryptionAlgorithm)
                && !WSSConstants.NS_XENC_AES256.equals(encryptionAlgorithm)) {
            ((WSSecurityContext) securityContext).handleBSPRule(WSSConstants.BSPRule.R5620);
        }

        return new DecryptedEventReaderInputProcessor(getSecurityProperties(),
                SecurePart.Modifier.getModifier(currentEncryptedDataType.getType()),
                encryptedHeader, comparableNamespaceList, comparableAttributeList,
                this,
                securityToken);
    }

    @Override
    protected void handleSecurityToken(SecurityToken securityToken, SecurityContext securityContext,
                                       EncryptedDataType encryptedDataType) throws XMLSecurityException {
        securityToken.addTokenUsage(SecurityToken.TokenUsage.Encryption);
        TokenSecurityEvent tokenSecurityEvent = WSSUtils.createTokenSecurityEvent(securityToken);
        ((WSSecurityContext) securityContext).registerSecurityEvent(tokenSecurityEvent);
    }

    /*
   <xenc:EncryptedData xmlns:xenc="http://www.w3.org/2001/04/xmlenc#" Id="EncDataId-1612925417" Type="http://www.w3.org/2001/04/xmlenc#Content">
       <xenc:EncryptionMethod xmlns:xenc="http://www.w3.org/2001/04/xmlenc#" Algorithm="http://www.w3.org/2001/04/xmlenc#aes256-cbc" />
       <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
           <wsse:SecurityTokenReference xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
               <wsse:Reference xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" URI="#EncKeyId-1483925398" />
           </wsse:SecurityTokenReference>
       </ds:KeyInfo>
       <xenc:CipherData xmlns:xenc="http://www.w3.org/2001/04/xmlenc#">
           <xenc:CipherValue xmlns:xenc="http://www.w3.org/2001/04/xmlenc#">
           ...
           </xenc:CipherValue>
       </xenc:CipherData>
   </xenc:EncryptedData>
    */

    /**
     * The DecryptedEventReaderInputProcessor reads the decrypted stream with a StAX reader and
     * forwards the generated XMLEvents
     */
    class DecryptedEventReaderInputProcessor extends AbstractDecryptedEventReaderInputProcessor {

        DecryptedEventReaderInputProcessor(
                XMLSecurityProperties securityProperties, SecurePart.Modifier encryptionModifier,
                boolean encryptedHeader, List<ComparableNamespace>[] namespaceList,
                List<ComparableAttribute>[] attributeList,
                DecryptInputProcessor decryptInputProcessor,
                SecurityToken securityToken
        ) {
            super(securityProperties, encryptionModifier, encryptedHeader, namespaceList, attributeList, decryptInputProcessor, securityToken);
        }

        protected void handleEncryptedElement(InputProcessorChain inputProcessorChain, XMLEvent xmlEvent, SecurityToken securityToken) throws XMLSecurityException {
            //fire a SecurityEvent:
            final WSSDocumentContext documentContext = (WSSDocumentContext) inputProcessorChain.getDocumentContext();
            if (documentContext.getDocumentLevel() == 3
                    && documentContext.isInSOAPHeader()) {
                EncryptedPartSecurityEvent encryptedPartSecurityEvent =
                        new EncryptedPartSecurityEvent(securityToken, true, documentContext.getProtectionOrder());
                encryptedPartSecurityEvent.setElementPath(documentContext.getPath());
                encryptedPartSecurityEvent.setXmlEvent(xmlEvent);
                ((WSSecurityContext) inputProcessorChain.getSecurityContext()).registerSecurityEvent(encryptedPartSecurityEvent);
            } else {
                EncryptedElementSecurityEvent encryptedElementSecurityEvent =
                        new EncryptedElementSecurityEvent(securityToken, true, documentContext.getProtectionOrder());
                encryptedElementSecurityEvent.setElementPath(documentContext.getPath());
                encryptedElementSecurityEvent.setXmlEvent(xmlEvent);
                ((WSSecurityContext) inputProcessorChain.getSecurityContext()).registerSecurityEvent(encryptedElementSecurityEvent);
            }
        }
    }
}