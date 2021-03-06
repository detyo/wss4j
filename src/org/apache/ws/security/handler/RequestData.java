package org.apache.ws.security.handler;

import org.apache.ws.security.SOAPConstants;
import org.apache.ws.security.WSSConfig;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.message.WSSecHeader;

import java.util.Vector;
import java.security.cert.X509Certificate;

/**
 * This class holds per request data.
 *
 * @author Werner Dittmann (Werner.Dittmann@t-online.de)
 */
public class RequestData {
    private Object msgContext = null;
    private boolean noSerialization = false;
    private SOAPConstants soapConstants = null;
    private String actor = null;
    private String username = null;
    private String pwType = null;
    private String[] utElements = null;
    private Crypto sigCrypto = null;
    private Crypto decCrypto = null;
    private int sigKeyId = 0;
    private String sigAlgorithm = null;
    private Vector signatureParts = new Vector();
    private Crypto encCrypto = null;
    private int encKeyId = 0;
    private String encSymmAlgo = null;
    private String encKeyTransport = null;
    private String encUser = null;
    private Vector encryptParts = new Vector();
    private X509Certificate encCert = null;
    private int timeToLive = 300; 	// Timestamp: time in seconds between creation and expiry
    private WSSConfig wssConfig = null;
    private Vector signatureValues = new Vector();
    private WSSecHeader secHeader = null;

    public void clear() {
        soapConstants = null;
       	actor = username = pwType = sigAlgorithm = encSymmAlgo = encKeyTransport = encUser = null;
       	sigCrypto = decCrypto = encCrypto = null;
       	signatureParts.clear();
       	encryptParts.clear();
       	encCert = null;
       	utElements = null;
        wssConfig = null;
        signatureValues.clear();
    }

    public Object getMsgContext() {
        return msgContext;
    }

    public void setMsgContext(Object msgContext) {
        this.msgContext = msgContext;
    }

    public boolean isNoSerialization() {
        return noSerialization;
    }

    public void setNoSerialization(boolean noSerialization) {
        this.noSerialization = noSerialization;
    }

    public SOAPConstants getSoapConstants() {
        return soapConstants;
    }

    public void setSoapConstants(SOAPConstants soapConstants) {
        this.soapConstants = soapConstants;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPwType() {
        return pwType;
    }

    public void setPwType(String pwType) {
        this.pwType = pwType;
    }

    public String[] getUtElements() {
        return utElements;
    }

    public void setUtElements(String[] utElements) {
        this.utElements = utElements;
    }

    public Crypto getSigCrypto() {
        return sigCrypto;
    }

    public void setSigCrypto(Crypto sigCrypto) {
        this.sigCrypto = sigCrypto;
    }

    public Crypto getDecCrypto() {
        return decCrypto;
    }

    public void setDecCrypto(Crypto decCrypto) {
        this.decCrypto = decCrypto;
    }

    public int getSigKeyId() {
        return sigKeyId;
    }

    public void setSigKeyId(int sigKeyId) {
        this.sigKeyId = sigKeyId;
    }

    public String getSigAlgorithm() {
        return sigAlgorithm;
    }

    public void setSigAlgorithm(String sigAlgorithm) {
        this.sigAlgorithm = sigAlgorithm;
    }

    public Vector getSignatureParts() {
        return signatureParts;
    }

    public Crypto getEncCrypto() {
        return encCrypto;
    }

    public void setEncCrypto(Crypto encCrypto) {
        this.encCrypto = encCrypto;
    }

    public int getEncKeyId() {
        return encKeyId;
    }

    public void setEncKeyId(int encKeyId) {
        this.encKeyId = encKeyId;
    }

    public String getEncSymmAlgo() {
        return encSymmAlgo;
    }

    public void setEncSymmAlgo(String encSymmAlgo) {
        this.encSymmAlgo = encSymmAlgo;
    }

    public String getEncKeyTransport() {
        return encKeyTransport;
    }

    public void setEncKeyTransport(String encKeyTransport) {
        this.encKeyTransport = encKeyTransport;
    }

    public String getEncUser() {
        return encUser;
    }

    public void setEncUser(String encUser) {
        this.encUser = encUser;
    }

    public Vector getEncryptParts() {
        return encryptParts;
    }

    public X509Certificate getEncCert() {
        return encCert;
    }

    public void setEncCert(X509Certificate encCert) {
        this.encCert = encCert;
    }

    public int getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(int timeToLive) {
        this.timeToLive = timeToLive;
    }

    /**
     * @return Returns the wssConfig.
     */
    public WSSConfig getWssConfig() {
        return wssConfig;
    }

    /**
     * @param wssConfig The wssConfig to set.
     */
    public void setWssConfig(WSSConfig wssConfig) {
        this.wssConfig = wssConfig;
    }
    /**
     * @return Returns the vector of stored signature values.
     */
    public Vector getSignatureValues() {
        return signatureValues;
    }

    /**
     * @return Returns the secHeader.
     */
    public WSSecHeader getSecHeader() {
        return secHeader;
    }

    /**
     * @param secHeader The secHeader to set.
     */
    public void setSecHeader(WSSecHeader secHeader) {
        this.secHeader = secHeader;
    }
}
