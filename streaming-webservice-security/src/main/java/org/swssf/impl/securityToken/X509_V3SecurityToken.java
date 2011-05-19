/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.swssf.impl.securityToken;

import org.swssf.crypto.Crypto;
import org.swssf.ext.Constants;
import org.swssf.ext.WSSecurityException;

import javax.security.auth.callback.CallbackHandler;
import java.io.ByteArrayInputStream;
import java.security.cert.X509Certificate;

/**
 * @author $Author: $
 * @version $Revision: $ $Date: $
 */
public class X509_V3SecurityToken extends X509SecurityToken {
    private String alias = null;
    private X509Certificate[] x509Certificates;

    X509_V3SecurityToken(Crypto crypto, CallbackHandler callbackHandler, byte[] binaryContent, String id, Object processor) throws WSSecurityException {
        super(crypto, callbackHandler, id, processor);
        this.x509Certificates = new X509Certificate[]{getCrypto().loadCertificate(new ByteArrayInputStream(binaryContent))};
    }

    protected String getAlias() throws WSSecurityException {
        if (this.alias == null) {
            this.alias = getCrypto().getAliasForX509Cert(this.x509Certificates[0]);
        }
        return this.alias;
    }

    @Override
    public X509Certificate[] getX509Certificates() throws WSSecurityException {
        return this.x509Certificates;
    }

    public Constants.KeyIdentifierType getKeyIdentifierType() {
        return Constants.KeyIdentifierType.BST_EMBEDDED;
    }
}
