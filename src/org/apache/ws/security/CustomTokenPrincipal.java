/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ws.security;

import org.w3c.dom.Element;

import java.io.Serializable;
import java.security.Principal;

public class CustomTokenPrincipal implements Principal, Serializable {


    private Element tokenElement;
    private String name;
    private Object tokenObject;
    
    public Object getTokenObject() {
        return tokenObject;
    }

    public void setTokenObject(Object tokenObject) {
        this.tokenObject = tokenObject;
    }

    public CustomTokenPrincipal(String name) {
        this.name = name;
    }
    
    public String getName() {
        return this.name;
    }

    public Element getTokenElement() {
        return tokenElement;
    }

    public void setTokenElement(Element tokenElement) {
        this.tokenElement = tokenElement;
    }

}
