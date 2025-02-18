package org.owntracks.android.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import timber.log.Timber;

public class SocketFactory extends javax.net.ssl.SSLSocketFactory{
    private final javax.net.ssl.SSLSocketFactory factory;
    private final String[] protocols=new String[] {"TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"};

    public static class SocketFactoryOptions {

        private InputStream caCrtInputStream;
        private InputStream caClientP12InputStream;
        private String caClientP12Password;

        public SocketFactoryOptions withCaInputStream(InputStream stream) {
            this.caCrtInputStream = stream;
            return this;
        }
        public SocketFactoryOptions withClientP12InputStream(InputStream stream) {
            this.caClientP12InputStream = stream;
            return this;
        }
        public SocketFactoryOptions withClientP12Password(String password) {
            this.caClientP12Password = password;
            return this;
        }

        boolean hasCaCrt() {
            return caCrtInputStream != null;
        }

        boolean hasClientP12Crt() {
            return caClientP12Password != null;
        }

        InputStream getCaCrtInputStream() {
            return caCrtInputStream;
        }

        InputStream getCaClientP12InputStream() {
            return caClientP12InputStream;
        }

        String getCaClientP12Password() {
            return caClientP12Password;
        }

        boolean hasClientP12Password() {
            return (caClientP12Password != null) && !caClientP12Password.equals("");
        }
    }


    private final TrustManagerFactory tmf;

    public SocketFactory(SocketFactoryOptions options) throws KeyStoreException, NoSuchAlgorithmException, IOException, KeyManagementException, java.security.cert.CertificateException, UnrecoverableKeyException {
        Timber.v("initializing CustomSocketFactory");

        tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");

        if(options.hasCaCrt()) {
            Timber.v("options.hasCaCrt(): true");

            KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            caKeyStore.load(null, null);

            CertificateFactory caCF = CertificateFactory.getInstance("X.509");
            X509Certificate ca = (X509Certificate) caCF.generateCertificate(options.getCaCrtInputStream());
            String alias = ca.getSubjectX500Principal().getName();
            // Set propper alias name
            caKeyStore.setCertificateEntry(alias, ca);
            tmf.init(caKeyStore);

            Timber.v("Certificate Owner: %s", ca.getSubjectDN().toString());
            Timber.v("Certificate Issuer: %s", ca.getIssuerDN().toString());
            Timber.v("Certificate Serial Number: %s", ca.getSerialNumber().toString());
            Timber.v("Certificate Algorithm: %s", ca.getSigAlgName());
            Timber.v("Certificate Version: %s", ca.getVersion());
            Timber.v("Certificate OID: %s", ca.getSigAlgOID());
            Enumeration<String> aliasesCA = caKeyStore.aliases();
            while (aliasesCA.hasMoreElements()) {
                String o = aliasesCA.nextElement();
                Timber.v("Alias: %s isKeyEntry:%s isCertificateEntry:%s", o, caKeyStore.isKeyEntry(o), caKeyStore.isCertificateEntry(o));
            }
        } else {
            Timber.v("CA sideload: false, using system keystore");
            KeyStore keyStore = KeyStore.getInstance("AndroidCAStore");
            keyStore.load(null);
            tmf.init(keyStore);
        }

        if (options.hasClientP12Crt()) {
            Timber.v("options.hasClientP12Crt(): true");

            KeyStore clientKeyStore = KeyStore.getInstance("PKCS12", Security.getProvider("BC"));

            clientKeyStore.load(options.getCaClientP12InputStream(), options.hasClientP12Password() ? options.getCaClientP12Password().toCharArray() : new char[0]);
            kmf.init(clientKeyStore, options.hasClientP12Password() ? options.getCaClientP12Password().toCharArray() : new char[0]);

            Timber.v("Client .p12 Keystore content: ");
            Enumeration<String> aliasesClientCert = clientKeyStore.aliases();
            while (aliasesClientCert.hasMoreElements()) {
                String o = aliasesClientCert.nextElement();
                Timber.v("Alias: %s", o);
            }
        } else {
            Timber.v("Client .p12 sideload: false, using null client cert");
            kmf.init(null,null);
        }

        // Create an SSLContext that uses our TrustManager
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), getTrustManagers(), null);
        this.factory= context.getSocketFactory();
    }

    public TrustManager[] getTrustManagers() {
        return tmf.getTrustManagers();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return this.factory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return this.factory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException{
        SSLSocket r = (SSLSocket)this.factory.createSocket();
        r.setEnabledProtocols(protocols);
        return r;
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        SSLSocket r = (SSLSocket)this.factory.createSocket(s, host, port, autoClose);
        r.setEnabledProtocols(protocols);
        return r;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {

        SSLSocket r = (SSLSocket)this.factory.createSocket(host, port);
        r.setEnabledProtocols(protocols);
        return r;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        SSLSocket r = (SSLSocket)this.factory.createSocket(host, port, localHost, localPort);
        r.setEnabledProtocols(protocols);
        return r;
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        SSLSocket r = (SSLSocket)this.factory.createSocket(host, port);
        r.setEnabledProtocols(protocols);
        return r;
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        SSLSocket r = (SSLSocket)this.factory.createSocket(address, port, localAddress,localPort);
        r.setEnabledProtocols(protocols);
        return r;
    }
}
