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

package org.apache.ws.security.action;

import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.handler.RequestData;
import org.apache.ws.security.handler.WSHandler;
import org.apache.ws.security.handler.WSHandlerConstants;
import org.apache.ws.security.message.WSSecEncrypt;
import org.w3c.dom.Document;

public class EncryptionAction implements Action {
    public void execute(WSHandler handler, int actionToDo, Document doc, RequestData reqData)
            throws WSSecurityException {
        WSSecEncrypt wsEncrypt = new WSSecEncrypt();
        wsEncrypt.setWsConfig(reqData.getWssConfig());

        if (reqData.getEncKeyId() != 0) {
            wsEncrypt.setKeyIdentifierType(reqData.getEncKeyId());
        }
        if (reqData.getEncKeyId() == WSConstants.EMBEDDED_KEYNAME) {
            String encKeyName 
		= handler.getString(WSHandlerConstants.ENC_KEY_NAME,
				    reqData.getMsgContext());
            wsEncrypt.setEmbeddedKeyName(encKeyName);
            byte[] embeddedKey =
                    handler.getPassword(reqData.getEncUser(),
                            actionToDo,
                            WSHandlerConstants.ENC_CALLBACK_CLASS,
                            WSHandlerConstants.ENC_CALLBACK_REF, reqData)
                            .getKey();
            wsEncrypt.setKey(embeddedKey);
        }
        if (reqData.getEncSymmAlgo() != null) {
            wsEncrypt.setSymmetricEncAlgorithm(reqData.getEncSymmAlgo());
        }
        if (reqData.getEncKeyTransport() != null) {
            wsEncrypt.setKeyEnc(reqData.getEncKeyTransport());
        }
        wsEncrypt.setUserInfo(reqData.getEncUser());
        wsEncrypt.setUseThisCert(reqData.getEncCert());
        if (reqData.getEncryptParts().size() > 0) {
            wsEncrypt.setParts(reqData.getEncryptParts());
        }
        try {
            wsEncrypt.build(doc, reqData.getEncCrypto(), reqData.getSecHeader());
        } catch (WSSecurityException e) {
            throw new WSSecurityException("WSHandler: Encryption: error during message processing"
                    + e);
        }
    }
}
