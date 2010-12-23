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
package ch.gigerstyle.xmlsec.ext;

import ch.gigerstyle.xmlsec.securityEvent.SecurityEvent;
import ch.gigerstyle.xmlsec.securityEvent.SecurityEventListener;

import java.util.List;

/**
 * @author $Author$
 * @version $Revision$ $Date$
 */
public interface SecurityContext {

    public <T> void put(String key, T value);

    public <T> T get(String key);

    public <T> void putAsList(Class key, T value);

    public <T> List<T> getAsList(Class key);

    public void registerSecurityTokenProvider(String id, SecurityTokenProvider securityTokenProvider);

    public SecurityTokenProvider getSecurityTokenProvider(String id);

    public void setSecurityEventListener(SecurityEventListener securityEventListener);

    public void registerSecurityEvent(SecurityEvent securityEvent) throws XMLSecurityException;
}
