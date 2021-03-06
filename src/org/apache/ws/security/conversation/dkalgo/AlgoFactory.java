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

package org.apache.ws.security.conversation.dkalgo;

import org.apache.ws.security.conversation.ConversationConstants;
import org.apache.ws.security.conversation.ConversationException;

/**
 * @author Ruchith Fernando
 */

public class AlgoFactory {

    /**
     * This gives a DerivationAlgorithm instance from the default set of algorithms provided
     *
     * @param algorithm The algo identifier @see DeivationAlgorithm
     * @return A derivation algorithm
     * @throws ConversationException If the specified algorithm is not available
     *                               in default implementations
     */
    public static DerivationAlgorithm getInstance(String algorithm) throws
            ConversationException {
        if (algorithm.equals(ConversationConstants.DerivationAlgorithm.P_SHA_1)) {
            return new P_SHA1();
        } else {
            throw new ConversationException("No such algorithm");
        }
    }

}
