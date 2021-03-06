/*
 * Copyright  2003-2006 The Apache Software Foundation, or their licensors, as
 * appropriate.
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

package org.apache.ws.security.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ws.security.SOAP11Constants;
import org.apache.ws.security.SOAP12Constants;
import org.apache.ws.security.SOAPConstants;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSSecurityEngineResult;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.handler.WSHandlerConstants;
import org.apache.ws.security.handler.WSHandlerResult;
import org.apache.ws.security.message.token.BinarySecurity;
import org.apache.ws.security.message.token.X509Security;
import org.apache.xml.security.algorithms.JCEMapper;
import org.apache.xml.security.signature.XMLSignature;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.namespace.QName;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

/**
 * WS-Security Utility methods. <p/>
 * 
 * @author Davanum Srinivas (dims@yahoo.com).
 */
public class WSSecurityUtil {
    private static Log log = LogFactory.getLog(WSSecurityUtil.class);

    private static boolean doDebug = false;

    static {
        doDebug = log.isDebugEnabled();
    }

    /**
     * Returns the first WS-Security header element for a given actor. Only one
     * WS-Security header is allowed for an actor.
     * 
     * @param doc
     * @param actor
     * @return the <code>wsse:Security</code> element or <code>null</code>
     *         if not such element found
     */
    public static Element getSecurityHeader(Document doc, String actor,
            SOAPConstants sc) {
        Element soapHeaderElement = (Element) getDirectChild(doc
                .getDocumentElement(), sc.getHeaderQName().getLocalPart(), sc
                .getEnvelopeURI());

        if (soapHeaderElement == null) { // no SOAP header at all
            return null;
        }

        // get all wsse:Security nodes
        NodeList list = null;
        int len = 0;
        list = soapHeaderElement.getElementsByTagNameNS(WSConstants.WSSE_NS,
                WSConstants.WSSE_LN);
        if (list == null) {
            return null;
        } else {
            len = list.getLength();
        }
        Element elem;
        Attr attr;
        String hActor;
        for (int i = 0; i < len; i++) {
            elem = (Element) list.item(i);
            attr = elem.getAttributeNodeNS(sc.getEnvelopeURI(), sc
                    .getRoleAttributeQName().getLocalPart());
            hActor = (attr != null) ? attr.getValue() : null;
            if (WSSecurityUtil.isActorEqual(actor, hActor)) {
                return elem;
            }
        }
        return null;
    }

    /**
     * Compares two actor strings and returns true if these are equal. Takes
     * care of the null length strings and uses ignore case.
     * 
     * @param actor
     * @param hActor
     * @return TODO
     */
    public static boolean isActorEqual(String actor, String hActor) {
        if ((((hActor == null) || (hActor.length() == 0)) && ((actor == null) || (actor
                .length() == 0)))
                || ((hActor != null) && (actor != null) && hActor
                        .equalsIgnoreCase(actor))) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Gets a direct child with specified localname and namespace. <p/>
     * 
     * @param fNode
     *            the node where to start the search
     * @param localName
     *            local name of the child to get
     * @param namespace
     *            the namespace of the child to get
     * @return the node or <code>null</code> if not such node found
     */
    public static Node getDirectChild(Node fNode, String localName,
            String namespace) {
        for (Node currentChild = fNode.getFirstChild(); currentChild != null; currentChild = currentChild
                .getNextSibling()) {
            if (localName.equals(currentChild.getLocalName())
                    && namespace.equals(currentChild.getNamespaceURI())) {
                return currentChild;
            }
        }
        return null;
    }

    /**
     * return the first soap "Body" element. <p/>
     * 
     * @param doc
     * @return the body element or <code>null</code> if document does not
     *         contain a SOAP body
     */
    public static Element findBodyElement(Document doc, SOAPConstants sc) {
        Element soapBodyElement = (Element) WSSecurityUtil.getDirectChild(doc
                .getFirstChild(), sc.getBodyQName().getLocalPart(), sc
                .getEnvelopeURI());
        return soapBodyElement;
    }

    /**
     * Returns the first element that matches <code>name</code> and
     * <code>namespace</code>. <p/> This is a replacement for a XPath lookup
     * <code>//name</code> with the given namespace. It's somewhat faster than
     * XPath, and we do not deal with prefixes, just with the real namespace URI
     * 
     * @param startNode
     *            Where to start the search
     * @param name
     *            Local name of the element
     * @param namespace
     *            Namespace URI of the element
     * @return The found element or <code>null</code>
     */
    public static Node findElement(Node startNode, String name, String namespace) {

        /*
         * Replace the formely recursive implementation with a depth-first-loop
         * lookup
         */
        if (startNode == null) {
            return null;
        }
        Node startParent = startNode.getParentNode();
        Node processedNode = null;

        while (startNode != null) {
            // start node processing at this point
            if (startNode.getNodeType() == Node.ELEMENT_NODE
                    && startNode.getLocalName().equals(name)) {
                String ns = startNode.getNamespaceURI();
                if (ns != null && ns.equals(namespace)) {
                    return startNode;
                }

                if ((namespace == null || namespace.length() == 0)
                        && (ns == null || ns.length() == 0)) {
                    return startNode;
                }
            }
            processedNode = startNode;
            startNode = startNode.getFirstChild();

            // no child, this node is done.
            if (startNode == null) {
                // close node processing, get sibling
                startNode = processedNode.getNextSibling();
            }
            // no more siblings, get parent, all children
            // of parent are processed.
            while (startNode == null) {
                processedNode = processedNode.getParentNode();
                if (processedNode == startParent) {
                    return null;
                }
                // close parent node processing (processed node now)
                startNode = processedNode.getNextSibling();
            }
        }
        return null;
    }

    /**
     * Returns the single element that containes an Id with value
     * <code>uri</code> and <code>namespace</code>. <p/> This is a
     * replacement for a XPath Id lookup with the given namespace. It's somewhat
     * faster than XPath, and we do not deal with prefixes, just with the real
     * namespace URI
     * 
     * If there are multiple elements, we log a warning and return null as this
     * can be used to get around the signature checking.
     * 
     * @param startNode
     *            Where to start the search
     * @param value
     *            Value of the Id attribute
     * @param namespace
     *            Namespace URI of the Id
     * @return The found element if there was exactly one match, or
     *         <code>null</code> otherwise
     */
    public static Element findElementById(Node startNode, String value,
            String namespace) {
        Element foundElement = null;

        /*
         * Replace the formely recursive implementation with a depth-first-loop
         * lookup
         */
        if (startNode == null) {
            return null;
        }
        Node startParent = startNode.getParentNode();
        Node processedNode = null;

        while (startNode != null) {
            // start node processing at this point
            if (startNode.getNodeType() == Node.ELEMENT_NODE) {
                Element se = (Element) startNode;
                if (se.hasAttributeNS(namespace, "Id")
                        && value.equals(se.getAttributeNS(namespace, "Id"))) {
                    if (foundElement == null) {
                        foundElement = se; // Continue searching to find
                        // duplicates
                    } else {
                        log
                                .warn("Multiple elements with the same 'Id' attribute value!");
                        return null;
                    }
                }
            }

            processedNode = startNode;
            startNode = startNode.getFirstChild();

            // no child, this node is done.
            if (startNode == null) {
                // close node processing, get sibling
                startNode = processedNode.getNextSibling();
            }
            // no more siblings, get parent, all children
            // of parent are processed.
            while (startNode == null) {
                processedNode = processedNode.getParentNode();
                if (processedNode == startParent) {
                    return foundElement;
                }
                // close parent node processing (processed node now)
                startNode = processedNode.getNextSibling();
            }
        }
        return foundElement;
    }

    /**
     * set the namespace if it is not set already. <p/>
     * 
     * @param element
     * @param namespace
     * @param prefix
     * @return TODO
     */
    public static String setNamespace(Element element, String namespace,
            String prefix) {
        String pre = getPrefixNS(namespace, element);
        if (pre != null) {
            return pre;
        }
        element.setAttributeNS(WSConstants.XMLNS_NS, "xmlns:" + prefix,
                namespace);
        return prefix;
    }

    /*
     * ** The following methods were copied over from aixs.utils.XMLUtils and
     * adapted
     */

    public static String getPrefixNS(String uri, Node e) {
        while (e != null && (e.getNodeType() == Element.ELEMENT_NODE)) {
            NamedNodeMap attrs = e.getAttributes();
            for (int n = 0; n < attrs.getLength(); n++) {
                Attr a = (Attr) attrs.item(n);
                String name;
                if ((name = a.getName()).startsWith("xmlns:")
                        && a.getNodeValue().equals(uri)) {
                    return name.substring(6);
                }
            }
            e = e.getParentNode();
        }
        return null;
    }

    public static String getNamespace(String prefix, Node e) {
        while (e != null && (e.getNodeType() == Node.ELEMENT_NODE)) {
            Attr attr = null;
            if (prefix == null) {
                attr = ((Element) e).getAttributeNode("xmlns");
            } else {
                attr = ((Element) e).getAttributeNodeNS(WSConstants.XMLNS_NS,
                        prefix);
            }
            if (attr != null)
                return attr.getValue();
            e = e.getParentNode();
        }
        return null;
    }

    /**
     * Return a QName when passed a string like "foo:bar" by mapping the "foo"
     * prefix to a namespace in the context of the given Node.
     * 
     * @return a QName generated from the given string representation
     */
    public static QName getQNameFromString(String str, Node e) {
        return getQNameFromString(str, e, false);
    }

    /**
     * Return a QName when passed a string like "foo:bar" by mapping the "foo"
     * prefix to a namespace in the context of the given Node. If default
     * namespace is found it is returned as part of the QName.
     * 
     * @return a QName generated from the given string representation
     */
    public static QName getFullQNameFromString(String str, Node e) {
        return getQNameFromString(str, e, true);
    }

    private static QName getQNameFromString(String str, Node e,
            boolean defaultNS) {
        if (str == null || e == null)
            return null;
        int idx = str.indexOf(':');
        if (idx > -1) {
            String prefix = str.substring(0, idx);
            String ns = getNamespace(prefix, e);
            if (ns == null)
                return null;
            return new QName(ns, str.substring(idx + 1));
        } else {
            if (defaultNS) {
                String ns = getNamespace(null, e);
                if (ns != null)
                    return new QName(ns, str);
            }
            return new QName("", str);
        }
    }

    /**
     * Return a string for a particular QName, mapping a new prefix if
     * necessary.
     */
    public static String getStringForQName(QName qname, Element e) {
        String uri = qname.getNamespaceURI();
        String prefix = getPrefixNS(uri, e);
        if (prefix == null) {
            int i = 1;
            prefix = "ns" + i;
            while (getNamespace(prefix, e) != null) {
                i++;
                prefix = "ns" + i;
            }
            e.setAttributeNS(WSConstants.XMLNS_NS, "xmlns:" + prefix, uri);
        }
        return prefix + ":" + qname.getLocalPart();
    }

    /* ** up to here */

    /**
     * Search for an element given its wsu:id. <p/>
     * 
     * @param doc
     *            the DOM document (SOAP request)
     * @param id
     *            the Id of the element
     * @return the found element or null if no element with the Id exists
     */
    public static Element getElementByWsuId(Document doc, String id) {

        if (id == null) {
            return null;
        }
        id = getIDfromReference(id);
        return WSSecurityUtil.findElementById(doc.getDocumentElement(), id,
                WSConstants.WSU_NS);
    }

    /**
     * Turn a reference (eg "#5") into an ID (eg "5").
     * 
     * @param ref
     * @return ref trimmed and with the leading "#" removed, or null if not
     *         correctly formed
     */
    public static String getIDfromReference(String ref) {
        String id = ref.trim();
        if ((id.length() == 0) || (id.charAt(0) != '#')) {
            return null;
        }
        return id.substring(1);
    }

    /**
     * Search for an element given its generic id. <p/>
     * 
     * @param doc
     *            the DOM document (SOAP request)
     * @param id
     *            the Id of the element
     * @return the found element or null if no element with the Id exists
     */
    public static Element getElementByGenId(Document doc, String id) {
        if (id == null) {
            return null;
        }
        id = id.trim();
        if ((id.length() == 0) || (id.charAt(0) != '#')) {
            return null;
        }
        id = id.substring(1);
        return WSSecurityUtil.findElementById(doc.getDocumentElement(), id,
                null);
    }

    /**
     * Create a BinarySecurityToken element <p/>
     * 
     * @param doc
     *            the DOM document (SOAP request)
     * @param wsuIdVal
     *            the value for the wsu:Id
     * @return then BST element (DOM element)
     */
    public static Element createBinarySecurityToken(Document doc,
            String wsuIdVal) {
        Element retVal = doc.createElementNS(WSConstants.WSSE_NS,
                "wsse:BinarySecurityToken");
        retVal.setAttributeNS(WSConstants.XMLNS_NS, "xmlns:wsu",
                WSConstants.WSU_NS);
        retVal.setAttributeNS(WSConstants.WSU_NS, "wsu:Id", wsuIdVal);
        retVal.setAttributeNS(null, "ValueType", X509Security.getType());
        retVal.setAttributeNS(null, "EncodingType",
                BinarySecurity.BASE64_ENCODING);
        return retVal;
    }

    /**
     * create a new element in the same namespace <p/>
     * 
     * @param parent
     *            for the new element
     * @param localName
     *            of the new element
     * @return the new element
     */
    private static Element createElementInSameNamespace(Element parent,
            String localName) {
        
        String qName = localName;
        
        String prefix = parent.getPrefix();
        if (prefix != null && prefix.length() > 0) {
            qName = prefix + ":" + localName;
        }
         
        String nsUri = parent.getNamespaceURI();
        return parent.getOwnerDocument().createElementNS(nsUri, qName);
    }

    /**
     * find a child element with given namespace and local name <p/>
     * 
     * @param parent
     *            the node to start the search
     * @param namespaceUri
     *            of the element
     * @param localName
     *            of the eleme
     * @return the found element or null if the element does not exist
     */
    private static Element findChildElement(Element parent,
            String namespaceUri, String localName) {
        NodeList children = parent.getChildNodes();
        int len = children.getLength();
        for (int i = 0; i < len; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elementChild = (Element) child;
                if (namespaceUri.equals(elementChild.getNamespaceURI())
                        && localName.equals(elementChild.getLocalName())) {
                    return elementChild;
                }
            }
        }
        return null;
    }

    /**
     * append a child element <p/>
     * 
     * @param doc
     *            the DOM document (SOAP request)
     * @param parent
     *            element of this child element
     * @param child
     *            the element to append
     * @return the child element
     */
    public static Element appendChildElement(Document doc, Element parent,
            Element child) {
        Node whitespaceText = doc.createTextNode("\n");
        parent.appendChild(whitespaceText);
        parent.appendChild(child);
        return child;
    }

    /**
     * prepend a child element <p/>
     * 
     * @param doc
     *            the DOM document (SOAP request)
     * @param parent
     *            element of this child element
     * @param child
     *            the element to append
     * @param addWhitespace
     *            if true prepend a newline before child
     * @return the child element
     */
    public static Element prependChildElement(Document doc, Element parent,
            Element child, boolean addWhitespace) {
        Node firstChild = parent.getFirstChild();
        if (firstChild == null) {
            parent.appendChild(child);
        } else {
            parent.insertBefore(child, firstChild);
        }
        if (addWhitespace) {
            Node whitespaceText = doc.createTextNode("\n");
            parent.insertBefore(whitespaceText, child);
        }
        return child;
    }

    /**
     * find the first ws-security header block <p/>
     * 
     * @param doc
     *            the DOM document (SOAP request)
     * @param envelope
     *            the SOAP envelope
     * @param doCreate
     *            if true create a new WSS header block if none exists
     * @return the WSS header or null if none found and doCreate is false
     */
    public static Element findWsseSecurityHeaderBlock(Document doc,
            Element envelope, boolean doCreate) {
        return findWsseSecurityHeaderBlock(doc, envelope, null, doCreate);
    }

    /**
     * find a ws-security header block for a given actor <p/>
     * 
     * @param doc
     *            the DOM document (SOAP request)
     * @param envelope
     *            the SOAP envelope
     * @param actor
     *            the acttoer (role) name of the WSS header
     * @param doCreate
     *            if true create a new WSS header block if none exists
     * @return the WSS header or null if none found and doCreate is false
     */
    public static Element findWsseSecurityHeaderBlock(Document doc,
            Element envelope, String actor, boolean doCreate) {
        SOAPConstants sc = getSOAPConstants(envelope);
        Element wsseSecurity = getSecurityHeader(doc, actor, sc);
        if (wsseSecurity != null) {
            return wsseSecurity;
        }
        Element header = findChildElement(envelope, sc.getEnvelopeURI(), sc
                .getHeaderQName().getLocalPart());
        if (header == null) {
            if (doCreate) {
                header = createElementInSameNamespace(envelope, sc
                        .getHeaderQName().getLocalPart());
                header = prependChildElement(doc, envelope, header, true);
            }
        }
        if (doCreate) {
            wsseSecurity = header.getOwnerDocument().createElementNS(
                    WSConstants.WSSE_NS, "wsse:Security");
            wsseSecurity.setAttributeNS(WSConstants.XMLNS_NS, "xmlns:wsse",
                    WSConstants.WSSE_NS);
            return prependChildElement(doc, header, wsseSecurity, true);
        }
        return null;
    }

    /**
     * create a base64 test node <p/>
     * 
     * @param doc
     *            the DOM document (SOAP request)
     * @param data
     *            to encode
     * @return a Text node containing the base64 encoded data
     */
    public static Text createBase64EncodedTextNode(Document doc, byte data[]) {
        return doc.createTextNode(Base64.encode(data));
    }

    public static SecretKey prepareSecretKey(String symEncAlgo, byte[] rawKey) {
        SecretKeySpec keySpec = new SecretKeySpec(rawKey, JCEMapper
                .getJCEKeyAlgorithmFromURI(symEncAlgo));
        return (SecretKey) keySpec;
    }

    public static SOAPConstants getSOAPConstants(Element startElement) {
        Document doc = startElement.getOwnerDocument();
        String ns = doc.getDocumentElement().getNamespaceURI();
        if (WSConstants.URI_SOAP12_ENV.equals(ns)) {
            return new SOAP12Constants();
        } else {
            return new SOAP11Constants();
        }
    }

    public static Cipher getCipherInstance(String cipherAlgo)
            throws WSSecurityException {
        Cipher cipher = null;
        try {
            if (cipherAlgo.equalsIgnoreCase(WSConstants.KEYTRANSPORT_RSA15)) {
                cipher = Cipher.getInstance("RSA/NONE/PKCS1PADDING");
            } else if (cipherAlgo
                    .equalsIgnoreCase(WSConstants.KEYTRANSPORT_RSAOEP)) {
                cipher = Cipher.getInstance("RSA/NONE/OAEPPADDING");
            } else {
                throw new WSSecurityException(
                        WSSecurityException.UNSUPPORTED_ALGORITHM,
                        "unsupportedKeyTransp", new Object[] { cipherAlgo });
            }
        } catch (NoSuchPaddingException ex) {
            throw new WSSecurityException(
                    WSSecurityException.UNSUPPORTED_ALGORITHM,
                    "unsupportedKeyTransp", new Object[] { "No such padding: "
                            + cipherAlgo });
        } catch (NoSuchAlgorithmException ex) {
            throw new WSSecurityException(
                    WSSecurityException.UNSUPPORTED_ALGORITHM,
                    "unsupportedKeyTransp",
                    new Object[] { "No such algorithm: " + cipherAlgo });
        }
        return cipher;
    }

    /**
     * Fetch the result of a given action from a given result vector <p/>
     * 
     * @param wsResultVector
     *            The result vector to fetch an action from
     * @param action
     *            The action to fetch
     * @return The result fetched from the result vector, null if the result
     *         could not be found
     */
    public static WSSecurityEngineResult fetchActionResult(
            Vector wsResultVector, int action) {
        WSSecurityEngineResult wsResult = null;

        // Find the part of the security result that matches the given action

        for (int i = 0; i < wsResultVector.size(); i++) {
            // Check the result of every action whether it matches the given
            // action
            if (((WSSecurityEngineResult) wsResultVector.get(i)).getAction() == action) {
                wsResult = (WSSecurityEngineResult) wsResultVector.get(i);
            }
        }

        return wsResult;
    }

    /**
     * Fetch the result of a given action from a given result vector <p/>
     * 
     * @param wsResultVector
     *            The result vector to fetch an action from
     * @param action
     *            The action to fetch
     * @param results
     *            where to store the found results data for the action
     * @return The result fetched from the result vector, null if the result
     *         could not be found
     */
    public static Vector fetchAllActionResults(Vector wsResultVector,
            int action, Vector results) {

        // Find the parts of the security result that matches the given action
        for (int i = 0; i < wsResultVector.size(); i++) {
            // Check the result of every action whether it matches the given
            // action
            if (((WSSecurityEngineResult) wsResultVector.get(i)).getAction() == action) {
                results.add(wsResultVector.get(i));
            }
        }
        return results;
    }

    static public int decodeAction(String action, Vector actions)
            throws WSSecurityException {

        int doAction = 0;

        if (action == null) {
            return doAction;
        }
        String single[] = StringUtil.split(action, ' ');
        for (int i = 0; i < single.length; i++) {
            if (single[i].equals(WSHandlerConstants.NO_SECURITY)) {
                doAction = WSConstants.NO_SECURITY;
                return doAction;
            } else if (single[i].equals(WSHandlerConstants.USERNAME_TOKEN)) {
                doAction |= WSConstants.UT;
                actions.add(new Integer(WSConstants.UT));
            } else if (single[i].equals(WSHandlerConstants.SIGNATURE)) {
                doAction |= WSConstants.SIGN;
                actions.add(new Integer(WSConstants.SIGN));
            } else if (single[i].equals(WSHandlerConstants.ENCRYPT)) {
                doAction |= WSConstants.ENCR;
                actions.add(new Integer(WSConstants.ENCR));
            } else if (single[i].equals(WSHandlerConstants.SAML_TOKEN_UNSIGNED)) {
                doAction |= WSConstants.ST_UNSIGNED;
                actions.add(new Integer(WSConstants.ST_UNSIGNED));
            } else if (single[i].equals(WSHandlerConstants.SAML_TOKEN_SIGNED)) {
                doAction |= WSConstants.ST_SIGNED;
                actions.add(new Integer(WSConstants.ST_SIGNED));
            } else if (single[i].equals(WSHandlerConstants.TIMESTAMP)) {
                doAction |= WSConstants.TS;
                actions.add(new Integer(WSConstants.TS));
            } else if (single[i].equals(WSHandlerConstants.NO_SERIALIZATION)) {
                doAction |= WSConstants.NO_SERIALIZE;
                actions.add(new Integer(WSConstants.NO_SERIALIZE));
            } else if (single[i].equals(WSHandlerConstants.SIGN_WITH_UT_KEY)) {
                doAction |= WSConstants.UT_SIGN;
                actions.add(new Integer(WSConstants.UT_SIGN));
            } else {
                throw new WSSecurityException(
                        "WSDoAllSender: Unknown action defined" + single[i]);
            }
        }
        return doAction;
    }

    /**
     * Returns the length of the key in # of bytes
     * 
     * @param algorithm
     * @return
     */
    public static int getKeyLength(String algorithm) throws WSSecurityException {
        if (algorithm.equals(WSConstants.TRIPLE_DES)) {
            return 24;
        } else if (algorithm.equals(WSConstants.AES_128)) {
            return 16;
        } else if (algorithm.equals(WSConstants.AES_192)) {
            return 24;
        } else if (algorithm.equals(WSConstants.AES_256)) {
            return 32;
        } else if (XMLSignature.ALGO_ID_MAC_HMAC_SHA1.equals(algorithm)) {
            return 20;
        } else if (XMLSignature.ALGO_ID_MAC_HMAC_SHA256.equals(algorithm)) {
            return 32;
        } else if (XMLSignature.ALGO_ID_MAC_HMAC_SHA384.equals(algorithm)) {
            return 48;
        } else if (XMLSignature.ALGO_ID_MAC_HMAC_SHA512.equals(algorithm)) {
            return 64;
        } else if (XMLSignature.ALGO_ID_MAC_HMAC_NOT_RECOMMENDED_MD5
                .equals(algorithm)) {
            return 16;
        } else {
            throw new WSSecurityException(
                    WSSecurityException.UNSUPPORTED_ALGORITHM, null, null, null);
        }
    }

    /**
     * Generate a nonce of the given length
     * 
     * @return
     * @throws Exception
     */
    public static byte[] generateNonce(int length) throws WSSecurityException {
        try {
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            byte[] temp = new byte[length];
            random.nextBytes(temp);
            return temp;
        } catch (Exception e) {
            throw new WSSecurityException(
                    "Error in generating nonce of length " + length, e);
        }
    }

    /**
     * Search through a WSS4J results vector for a single signature covering all
     * these elements.
     * 
     * NOTE: it is important that the given elements are those that are 
     * referenced using wsu:Id. When the signed element is referenced using a
     * transformation such as XPath filtering the validation is carried out 
     * in signature verification itself.
     * 
     * @param results
     *            results (e.g., as stored as WSHandlerConstants.RECV_RESULTS on
     *            an Axis MessageContext)
     * @param elements
     *            the elements to check
     * @return the identity of the signer
     * @throws WSSecurityException
     *             if no suitable signature could be found or if any element
     *             didn't have a wsu:Id attribute
     */
    public static X509Certificate ensureSignedTogether(Iterator results,
            Element[] elements) throws WSSecurityException {
        log.debug("ensureSignedTogether()");

        if (results == null)
            throw new IllegalArgumentException("No results vector");
        if (elements == null || elements.length == 0)
            throw new IllegalArgumentException("No elements to check!");

        // Turn the list of required elements into a list of required wsu:Id
        // strings
        String[] requiredIDs = new String[elements.length];
        for (int i = 0; i < elements.length; i++) {
            Element e = (Element) elements[i];
            if (e == null) {
                throw new IllegalArgumentException("elements[" + i
                        + "] is null!");
            }
            requiredIDs[i] = e.getAttributeNS(WSConstants.WSU_NS, "Id");
            if (requiredIDs[i] == null) {
                throw new WSSecurityException(WSSecurityException.FAILED_CHECK,
                        "requiredElementNoID", new Object[] { e.getNodeName() });
            }
            log.debug("Required element " + e.getNodeName() + " has wsu:Id "
                    + requiredIDs[i]);
        }

        WSSecurityException fault = null;

        // Search through the results for a SIGN result
        while (results.hasNext()) {
            WSHandlerResult result = (WSHandlerResult) results.next();
            Iterator actions = result.getResults().iterator();

            while (actions.hasNext()) {
                WSSecurityEngineResult resultItem = (WSSecurityEngineResult) actions
                        .next();
                if (resultItem.getAction() == WSConstants.SIGN) {
                    try {
                        checkSignsAllElements(resultItem, requiredIDs);
                        return resultItem.getCertificate();
                    } catch (WSSecurityException ex) {
                        // Store the exception but keep going... there may be a
                        // better signature later
                        log
                                .debug(
                                        "SIGN result does not sign all required elements",
                                        ex);
                        fault = ex;
                    }
                }
            }
        }

        if (fault != null)
            throw fault;

        throw new WSSecurityException(WSSecurityException.FAILED_CHECK,
                "noSignResult");
    }

    /**
     * Ensure that this signature covers all required elements (identified by
     * their wsu:Id attributes).
     * 
     * @param resultItem
     *            the signature to check
     * @param requiredIDs
     *            the list of wsu:Id values that must be covered
     * @throws WSSecurityException
     *             if any required element is not included
     */
    private static void checkSignsAllElements(
            WSSecurityEngineResult resultItem, String[] requiredIDs)
            throws WSSecurityException {
        if (resultItem.getAction() != WSConstants.SIGN)
            throw new IllegalArgumentException("Not a SIGN result");

        Set sigElems = resultItem.getSignedElements();
        if (sigElems == null)
            throw new RuntimeException(
                    "Missing signedElements set in WSSecurityEngineResult!");

        log.debug("Found SIGN result...");
        for (Iterator i = sigElems.iterator(); i.hasNext();) {
            Object sigElement = i.next();
            if(sigElement instanceof String) {
                log.debug("Signature includes element with ID " + sigElement);
            } else {
                log.debug("Signature includes element with null uri " + 
                        sigElement.toString());
            }
        }

        log.debug("Checking required elements are in the signature...");
        for (int i = 0; i < requiredIDs.length; i++) {
            if (!sigElems.contains(requiredIDs[i])) {
                throw new WSSecurityException(WSSecurityException.FAILED_CHECK,
                        "requiredElementNotSigned",
                        new Object[] { requiredIDs[i] });
            }
            log.debug("Element with ID " + requiredIDs[i]
                    + " was correctly signed");
        }
        log.debug("All required elements are signed");
    }
}
