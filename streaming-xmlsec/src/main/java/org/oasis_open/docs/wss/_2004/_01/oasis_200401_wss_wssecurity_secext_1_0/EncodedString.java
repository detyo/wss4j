//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 in JDK 6 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2010.05.13 at 04:42:25 PM CEST 
//


package org.oasis_open.docs.wss._2004._01.oasis_200401_wss_wssecurity_secext_1_0;

import ch.gigerstyle.xmlsec.ext.Constants;
import ch.gigerstyle.xmlsec.ext.ParseException;
import ch.gigerstyle.xmlsec.ext.Parseable;
import ch.gigerstyle.xmlsec.ext.Utils;

import javax.xml.bind.annotation.*;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.util.Iterator;


/**
 * This type is used for elements containing stringified binary data.
 * <p/>
 * <p>Java class for EncodedString complex type.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;complexType name="EncodedString">
 *   &lt;simpleContent>
 *     &lt;extension base="&lt;http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd>AttributedString">
 *       &lt;attribute name="EncodingType" type="{http://www.w3.org/2001/XMLSchema}anyURI" />
 *       &lt;anyAttribute processContents='lax' namespace='##other'/>
 *     &lt;/extension>
 *   &lt;/simpleContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EncodedString")
@XmlSeeAlso({
        BinarySecurityTokenType.class,
        KeyIdentifierType.class
})
public class EncodedString
        extends AttributedString implements Parseable {

    @XmlAttribute(name = "EncodingType")
    @XmlSchemaType(name = "anyURI")
    protected String encodingType;

    private QName startElementName;

    public EncodedString() {
        super();
    }

    public EncodedString(StartElement startElement) {
        super(startElement);
        this.startElementName = startElement.getName();
        Iterator<Attribute> attributeIterator = startElement.getAttributes();
        while (attributeIterator.hasNext()) {
            Attribute attribute = attributeIterator.next();
            if (attribute.getName().equals(Constants.ATT_NULL_EncodingType)) {
                this.encodingType = attribute.getValue();
            }
        }
    }

    public boolean parseXMLEvent(XMLEvent xmlEvent) throws ParseException {

        super.parseXMLEvent(xmlEvent);

        switch (xmlEvent.getEventType()) {
            case XMLStreamConstants.START_ELEMENT:
                break;
            case XMLStreamConstants.END_ELEMENT:
                EndElement endElement = xmlEvent.asEndElement();
                if (endElement.getName().equals(startElementName)) {
                    return true;
                }
                break;
            case XMLStreamConstants.CHARACTERS:
                break;
            default:
                throw new ParseException("Unexpected event received " + Utils.getXMLEventAsString(xmlEvent));
        }
        return false;
    }

    public void validate() throws ParseException {
        super.validate();
    }

    /**
     * Gets the value of the encodingType property.
     *
     * @return possible object is
     *         {@link String }
     */
    public String getEncodingType() {
        return encodingType;
    }

    /**
     * Sets the value of the encodingType property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setEncodingType(String value) {
        this.encodingType = value;
    }

}
