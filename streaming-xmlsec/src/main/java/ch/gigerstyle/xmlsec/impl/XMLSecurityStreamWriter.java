package ch.gigerstyle.xmlsec.impl;

import ch.gigerstyle.xmlsec.ext.Constants;
import ch.gigerstyle.xmlsec.ext.OutputProcessorChain;
import ch.gigerstyle.xmlsec.ext.XMLSecurityException;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.XMLEvent;
import java.util.*;

/**
 * User: giger
 * Date: May 29, 2010
 * Time: 3:07:35 PM
 * Copyright 2010 Marc Giger gigerstyle@gmx.ch
 * <p/>
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2, or (at your option) any
 * later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
public class XMLSecurityStreamWriter implements XMLStreamWriter {

    XMLEventFactory xmlEventFactory = XMLEventFactory.newFactory();
    XMLEventNSAllocator xmlEventNSAllocator = new XMLEventNSAllocator();

    private OutputProcessorChain outputProcessorChain;

    public XMLSecurityStreamWriter(OutputProcessorChain outputProcessorChain) {
        this.outputProcessorChain = outputProcessorChain;
        this.outputProcessorChain.getSecurityContext().put(Constants.XMLEVENT_NS_ALLOCATOR, xmlEventNSAllocator);
    }

    private void chainProcessEvent(XMLEvent xmlEvent) throws XMLStreamException {
        try {
            outputProcessorChain.reset();
            outputProcessorChain.processEvent(xmlEvent);
        } catch (XMLSecurityException e) {
            throw new XMLStreamException(e);
        }
    }

    private Deque<QName> startElementStack = new ArrayDeque<QName>();
    private QName openStartElement = null;
    private List<Attribute> currentAttributes = new ArrayList<Attribute>();
    private List<Namespace> currentNamespaces = new ArrayList<Namespace>();

    private void outputOpenStartElement() throws XMLStreamException {
        if (openStartElement != null) {
            //extend XMLEventNSAllocator and delegate create* methods in AbstractOutput processors to it.
            //replace all create" methods calls in this class with an delegate to XMLEventNSAllocator
            //chainProcessEvent(xmlEventFactory.createStartElement(openStartElement, currentAttributes.iterator(), currentNamespaces.iterator()));
            //add namespace of current element to the list (important for C14N)
            //currentNamespaces.add(xmlEventNSAllocator.createNamespace(openStartElement.getPrefix(), openStartElement.getNamespaceURI()));
            chainProcessEvent(xmlEventNSAllocator.createStartElement(openStartElement, currentNamespaces, currentAttributes));
            currentAttributes.clear();
            currentNamespaces.clear();
            openStartElement = null;
        }
    }

    public void writeStartElement(String localName) throws XMLStreamException {
        QName qName;
        if (localName.contains(":")) {
            String[] splittedName = localName.split(":");
            qName = new QName(null, splittedName[1], splittedName[0]);
        } else {
            //todo: Writes a start tag to the output. All writeStartElement methods open a new scope in the internal namespace context. Writing the corresponding EndElement causes the scope to be closed.
            qName = new QName(localName);
        }
        startElementStack.push(qName);
        openStartElement = qName;
        //chainProcessEvent(xmlEventFactory.createStartElement(qName, null, null));
    }

    public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
        QName qName;
        if (localName.contains(":")) {
            String[] splittedName = localName.split(":");
            qName = new QName(null, splittedName[1], splittedName[0]);
        } else {
            //todo: Writes a start tag to the output. All writeStartElement methods open a new scope in the internal namespace context. Writing the corresponding EndElement causes the scope to be closed.
            qName = new QName(namespaceURI, localName);
        }
        startElementStack.push(qName);
        openStartElement = qName;
        //chainProcessEvent(xmlEventFactory.createStartElement(qName, null, null));
    }

    public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        QName qName = new QName(namespaceURI, localName, prefix);
        startElementStack.push(qName);
        openStartElement = qName;
        //chainProcessEvent(xmlEventFactory.createStartElement(qName, null, null));
    }

    public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
        outputOpenStartElement();
        chainProcessEvent(xmlEventFactory.createStartElement(null, namespaceURI, localName));
        chainProcessEvent(xmlEventFactory.createEndElement(null, namespaceURI, localName));
    }

    public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        outputOpenStartElement();
        chainProcessEvent(xmlEventFactory.createStartElement(prefix, namespaceURI, localName));
        chainProcessEvent(xmlEventFactory.createEndElement(prefix, namespaceURI, localName));
    }

    public void writeEmptyElement(String localName) throws XMLStreamException {
        outputOpenStartElement();
        chainProcessEvent(xmlEventFactory.createStartElement(null, null, localName));
        chainProcessEvent(xmlEventFactory.createEndElement(null, null, localName));
    }

    public void writeEndElement() throws XMLStreamException {
        outputOpenStartElement();
        //chainProcessEvent(xmlEventFactory.createEndElement(startElementStack.pop(), null));
        chainProcessEvent(xmlEventNSAllocator.createEndElement(startElementStack.pop()));
    }

    public void writeEndDocument() throws XMLStreamException {
        outputOpenStartElement();
        Iterator<QName> startElements = startElementStack.iterator();
        while (startElements.hasNext()) {
            chainProcessEvent(xmlEventNSAllocator.createEndElement(startElementStack.pop()));
        }
    }

    public void close() throws XMLStreamException {
        try {
            outputProcessorChain.reset();
            outputProcessorChain.doFinal();
        } catch (XMLSecurityException e) {
            throw new XMLStreamException(e);
        }
    }

    public void flush() throws XMLStreamException {
        //todo? outputstream flush?
    }

    public void writeAttribute(String localName, String value) throws XMLStreamException {
        //chainProcessEvent();
        currentAttributes.add(xmlEventFactory.createAttribute(localName, value));
    }

    public void writeAttribute(String prefix, String namespaceURI, String localName, String value) throws XMLStreamException {
        currentAttributes.add(xmlEventFactory.createAttribute(prefix, namespaceURI, localName, value));
    }

    public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
        //todo: null is not correct!
        currentAttributes.add(xmlEventFactory.createAttribute(null, namespaceURI, localName, value));
    }

    public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
        currentNamespaces.add(xmlEventFactory.createNamespace(prefix, namespaceURI));
    }

    public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
        currentNamespaces.add(xmlEventFactory.createNamespace(namespaceURI));
    }

    public void writeComment(String data) throws XMLStreamException {
        outputOpenStartElement();
        chainProcessEvent(xmlEventFactory.createComment(data));
    }

    public void writeProcessingInstruction(String target) throws XMLStreamException {
        outputOpenStartElement();
        //todo null correct?
        chainProcessEvent(xmlEventFactory.createProcessingInstruction(target, null));
    }

    public void writeProcessingInstruction(String target, String data) throws XMLStreamException {
        outputOpenStartElement();
        chainProcessEvent(xmlEventFactory.createProcessingInstruction(target, data));
    }

    public void writeCData(String data) throws XMLStreamException {
        outputOpenStartElement();
        chainProcessEvent(xmlEventFactory.createCData(data));
    }

    public void writeDTD(String dtd) throws XMLStreamException {
        outputOpenStartElement();
        chainProcessEvent(xmlEventFactory.createDTD(dtd));
    }

    public void writeEntityRef(String name) throws XMLStreamException {
        outputOpenStartElement();
        //todo null not correct
        chainProcessEvent(xmlEventFactory.createEntityReference(name, null));
    }

    public void writeStartDocument() throws XMLStreamException {
        chainProcessEvent(xmlEventFactory.createStartDocument());
    }

    public void writeStartDocument(String version) throws XMLStreamException {
        chainProcessEvent(xmlEventFactory.createStartDocument("utf-8", version));
    }

    public void writeStartDocument(String encoding, String version) throws XMLStreamException {
        chainProcessEvent(xmlEventFactory.createStartDocument(encoding, version));
    }

    public void writeCharacters(String text) throws XMLStreamException {
        outputOpenStartElement();
        chainProcessEvent(xmlEventFactory.createCharacters(text));
    }

    public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
        outputOpenStartElement();
        chainProcessEvent(xmlEventFactory.createCharacters(new String(text, start, len)));
    }

    public String getPrefix(String uri) throws XMLStreamException {
        //todo
        return null;
    }

    public void setPrefix(String prefix, String uri) throws XMLStreamException {
        //todo
    }

    public void setDefaultNamespace(String uri) throws XMLStreamException {
        //todo
    }

    public void setNamespaceContext(NamespaceContext context) throws XMLStreamException {
        //todo
    }

    public NamespaceContext getNamespaceContext() {
        return null;
    }

    public Object getProperty(String name) throws IllegalArgumentException {
        return null;
    }
}