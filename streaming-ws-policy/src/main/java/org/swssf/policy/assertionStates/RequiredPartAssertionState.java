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
package org.swssf.policy.assertionStates;

import org.swssf.policy.secpolicy.model.AbstractSecurityAssertion;
import org.swssf.wss.securityEvent.RequiredPartSecurityEvent;
import org.swssf.wss.securityEvent.SecurityEvent;

import javax.xml.namespace.QName;

/**
 * @author $Author$
 * @version $Revision$ $Date$
 */
public class RequiredPartAssertionState extends AssertionState {

    private QName element;

    public RequiredPartAssertionState(AbstractSecurityAssertion assertion, boolean asserted, QName element) {
        super(assertion, asserted);
        this.element = element;
    }

    @Override
    public boolean assertEvent(SecurityEvent securityEvent) {
        RequiredPartSecurityEvent requiredPartSecurityEvent = (RequiredPartSecurityEvent) securityEvent;
        if (element.equals(requiredPartSecurityEvent.getElement())
                || (element.getLocalPart().equals("*") && element.getNamespaceURI().equals(requiredPartSecurityEvent.getElement().getNamespaceURI()))) {
            setAsserted(true);
        }
        return true;
    }
}