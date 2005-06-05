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

package org.apache.ws.security.trust.message.token;

import javax.xml.namespace.QName;

import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.message.token.SecurityTokenReference;
import org.apache.ws.security.trust.TrustConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * @author Ruchith Fernando (ruchith.fernando@gmail.com)
 */
public class RenewTarget extends AbstractToken {
	
    public static final QName TOKEN = new QName(TrustConstants.WST_NS, TrustConstants.RENEW_TARGET_LN, TrustConstants.WST_PREFIX);

    private Element tokenToBeRenewed = null;
    private SecurityTokenReference securityTokenReference = null;
    
    public RenewTarget(Element elem) throws WSSecurityException {
    	super(elem);
    }
    
    public RenewTarget(Document doc) {
        super(doc);
    }
    
	/**
	 * Returns the <code>wsse:SecurityTokenReference</code>
	 * @return
	 */    
	public SecurityTokenReference getSecurityTokenReference() {
		return securityTokenReference;
	}
	
	/**
	 * Sets a <code>wsse:SecurityTokenReference</code>
	 * @param securityTokenReference
	 */
	public void setSecurityTokenReference(SecurityTokenReference securityTokenReference) {
		//If there's another token remove it
		if(this.tokenToBeRenewed != null)
			this.element.removeChild(this.tokenToBeRenewed);
		this.securityTokenReference = securityTokenReference;
		this.element.appendChild(this.securityTokenReference.getElement());
	}
	
	/**
	 * Returns the token to be renewed
	 * @return
	 */
	public Element getTokenToBeRenewed() {
		return tokenToBeRenewed;
	}
	
	/**
	 * Sets the token to be renewed
	 * @param tokenToBeRenewed
	 */
	public void setTokenToBeRenewed(Element tokenToBeRenewed) {
		//if there's wsse:SecurityTokenReference remove it
		if(this.securityTokenReference != null)
			this.element.removeChild(this.securityTokenReference.getElement());
		this.tokenToBeRenewed = tokenToBeRenewed;
		this.element.appendChild(this.tokenToBeRenewed);
	}
	

	/**
	 * Returns the QName of this type
	 * @see org.apache.ws.security.trust.message.token.AbstractToken#getToken()
	 */
	protected QName getToken() {
		return TOKEN;
	}
}