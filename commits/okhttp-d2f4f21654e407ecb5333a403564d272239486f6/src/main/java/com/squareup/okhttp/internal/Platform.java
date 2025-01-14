/*
 * Copyright (C) 2012 Square, Inc.
 * Copyright (C) 2012 The Android Open Source Project
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
package com.squareup.okhttp.internal;

import com.squareup.okhttp.OkHttpClient;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import javax.net.ssl.SSLSocket;

/**
 * Access to Platform-specific features necessary for SPDY and advanced TLS.
 *
 * <h3>SPDY</h3>
 * SPDY requires a TLS extension called NPN (Next Protocol Negotiation) that's
 * available in Android 4.1+ and OpenJDK 7+ (with the npn-boot extension). It
 * also requires a recent version of {@code DeflaterOutputStream} that is
 * public API in Java 7 and callable via reflection in Android 4.1+.
 */
public class Platform {
    private static final Platform PLATFORM = findPlatform();

    private Constructor<DeflaterOutputStream> deflaterConstructor;

    public static Platform get() {
        return PLATFORM;
    }

    public void makeTlsTolerant(SSLSocket socket, String uriHost, boolean tlsTolerant) {
        if (!tlsTolerant) {
            socket.setEnabledProtocols(new String[]{"SSLv3"});
        }
    }

    /**
     * Returns the negotiated protocol, or null if no protocol was negotiated.
     */
    public byte[] getNpnSelectedProtocol(SSLSocket socket) {
        return null;
    }

    /**
     * Sets client-supported protocols on a socket to send to a server. The
     * protocols are only sent if the socket implementation supports NPN.
     */
    public void setNpnProtocols(SSLSocket socket, byte[] npnProtocols) {
    }

    /**
     * Returns a deflater output stream that supports SYNC_FLUSH for SPDY name
     * value blocks. This throws an {@link UnsupportedOperationException} on
     * Java 6 and earlier where there is no built-in API to do SYNC_FLUSH.
     */
    public OutputStream newDeflaterOutputStream(
            OutputStream out, Deflater deflater, boolean syncFlush) {
        try {
            Constructor<DeflaterOutputStream> constructor = deflaterConstructor;
            if (constructor == null) {
                constructor = deflaterConstructor = DeflaterOutputStream.class.getConstructor(
                        OutputStream.class, Deflater.class, boolean.class);
            }
            return constructor.newInstance(out, deflater, syncFlush);
        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException("Cannot SPDY; no SYNC_FLUSH available");
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof RuntimeException
                    ? (RuntimeException) e.getCause()
                    : new RuntimeException(e.getCause());
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new AssertionError();
        }
    }

    /**
     * Attempt to match the host runtime to a capable Platform implementation.
     */
    private static Platform findPlatform() {
        // Attempt to find Android 2.3+ APIs.
        Class<?> openSslSocketClass;
        Method setUseSessionTickets;
        Method setHostname;
        try {
            openSslSocketClass = Class.forName(
                    "org.apache.harmony.xnet.provider.jsse.OpenSSLSocketImpl");
            setUseSessionTickets = openSslSocketClass.getMethod(
                    "setUseSessionTickets", boolean.class);
            setHostname = openSslSocketClass.getMethod("setHostname", String.class);

            // Attempt to find Android 4.1+ APIs.
            try {
                Method setNpnProtocols = openSslSocketClass.getMethod(
                        "setNpnProtocols", byte[].class);
                Method getNpnSelectedProtocol = openSslSocketClass.getMethod(
                        "getNpnSelectedProtocol");
                return new Android41(openSslSocketClass, setUseSessionTickets, setHostname,
                        setNpnProtocols, getNpnSelectedProtocol);
            } catch (NoSuchMethodException ignored) {
                return new Android23(openSslSocketClass, setUseSessionTickets, setHostname);
            }
        } catch (ClassNotFoundException ignored) {
            // This isn't an Android runtime.
        } catch (NoSuchMethodException ignored) {
            // This isn't Android 2.3 or better.
        }

        // Attempt to find the Jetty's NPN extension for OpenJDK.
        try {
            String npnClassName = "org.eclipse.jetty.npn.NextProtoNego";
            Class<?> nextProtoNegoClass = Class.forName(npnClassName);
            Class<?> providerClass = Class.forName(npnClassName + "$Provider");
            Class<?> clientProviderClass = Class.forName(npnClassName + "$ClientProvider");
            Method putMethod = nextProtoNegoClass.getMethod("put", SSLSocket.class, providerClass);
            Method getMethod = nextProtoNegoClass.getMethod("get", SSLSocket.class);
            return new JdkWithJettyNpnPlatform(putMethod, getMethod, clientProviderClass);
        } catch (ClassNotFoundException ignored) {
            return new Platform(); // NPN isn't on the classpath.
        } catch (NoSuchMethodException ignored) {
            return new Platform(); // The NPN version isn't what we expect.
        }
    }

    /**
     * Android version 2.3 and newer support TLS session tickets and server name
     * indication (SNI).
     */
    private static class Android23 extends Platform {
        protected final Class<?> openSslSocketClass;
        private final Method setUseSessionTickets;
        private final Method setHostname;

        private Android23(Class<?> openSslSocketClass, Method setUseSessionTickets,
                Method setHostname) {
            this.openSslSocketClass = openSslSocketClass;
            this.setUseSessionTickets = setUseSessionTickets;
            this.setHostname = setHostname;
        }

        @Override public void makeTlsTolerant(
                SSLSocket socket, String uriHost, boolean tlsTolerant) {
            super.makeTlsTolerant(socket, uriHost, tlsTolerant);
            if (tlsTolerant && openSslSocketClass.isInstance(socket)) {
                // This is Android: use reflection on OpenSslSocketImpl.
                try {
                    setUseSessionTickets.invoke(socket, true);
                    setHostname.invoke(socket, uriHost);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
    }

    /**
     * Android version 4.1 and newer support NPN.
     */
    private static class Android41 extends Android23 {
        private final Method setNpnProtocols;
        private final Method getNpnSelectedProtocol;

        private Android41(Class<?> openSslSocketClass, Method setUseSessionTickets,
                Method setHostname, Method setNpnProtocols, Method getNpnSelectedProtocol) {
            super(openSslSocketClass, setUseSessionTickets, setHostname);
            this.setNpnProtocols = setNpnProtocols;
            this.getNpnSelectedProtocol = getNpnSelectedProtocol;
        }

        @Override public void setNpnProtocols(SSLSocket socket, byte[] npnProtocols) {
            if (!openSslSocketClass.isInstance(socket)) {
                return;
            }
            try {
                setNpnProtocols.invoke(socket, new Object[] {npnProtocols});
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        @Override public byte[] getNpnSelectedProtocol(SSLSocket socket) {
            if (!openSslSocketClass.isInstance(socket)) {
                return null;
            }
            try {
                return (byte[]) getNpnSelectedProtocol.invoke(socket);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
    }

    /**
     * OpenJDK 7 plus {@code org.mortbay.jetty.npn/npn-boot} on the boot class
     * path.
     */
    private static class JdkWithJettyNpnPlatform extends Platform {
        private final Method getMethod;
        private final Method putMethod;
        private final Class<?> clientProviderClass;

        public JdkWithJettyNpnPlatform(
                Method putMethod, Method getMethod, Class<?> clientProviderClass) {
            this.putMethod = putMethod;
            this.getMethod = getMethod;
            this.clientProviderClass = clientProviderClass;
        }

        @Override public void setNpnProtocols(SSLSocket socket, byte[] npnProtocols) {
            try {
                List<String> strings = new ArrayList<String>();
                for (int i = 0; i < npnProtocols.length;) {
                    int length = npnProtocols[i++];
                    strings.add(new String(npnProtocols, i, length, "US-ASCII"));
                    i += length;
                }
                Object provider = Proxy.newProxyInstance(Platform.class.getClassLoader(),
                        new Class[] {clientProviderClass}, new JettyNpnProvider(strings));
                putMethod.invoke(null, socket, provider);
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError(e);
            } catch (InvocationTargetException e) {
                throw new AssertionError(e);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }

        @Override public byte[] getNpnSelectedProtocol(SSLSocket socket) {
            try {
                JettyNpnProvider provider = (JettyNpnProvider) Proxy.getInvocationHandler(
                        getMethod.invoke(null, socket));
                if (!provider.unsupported && provider.selected == null) {
                    Logger logger = Logger.getLogger(OkHttpClient.class.getName());
                    logger.log(Level.INFO, "NPN callback dropped so SPDY is disabled. "
                            + "Is npn-boot on the boot class path?");
                    return null;
                }
                return provider.unsupported
                        ? null
                        : provider.selected.getBytes("US-ASCII");
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError();
            } catch (InvocationTargetException e) {
                throw new AssertionError();
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            }
        }
    }

    /**
     * Handle the methods of NextProtoNego's ClientProvider and ServerProvider
     * without a compile-time dependency on those interfaces.
     */
    private static class JettyNpnProvider implements InvocationHandler {
        private final List<String> clientProtocols;
        private boolean unsupported;
        private String selected;

        public JettyNpnProvider(List<String> clientProtocols) {
            this.clientProtocols = clientProtocols;
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            String methodName = method.getName();
            Class<?> returnType = method.getReturnType();
            if (methodName.equals("supports") && boolean.class == returnType) {
                return true;
            } else if (methodName.equals("unsupported") && void.class == returnType) {
                this.unsupported = true;
                return null;
            } else if (methodName.equals("selectProtocol") && String.class == returnType
                    && args.length == 1 && (args[0] == null || args[0] instanceof List)) {
                // TODO: use OpenSSL's algorithm which uses both lists
                List<?> serverProtocols = (List) args[0];
                System.out.println("CLIENT PROTOCOLS: " + clientProtocols + " SERVER PROTOCOLS: " + serverProtocols);
                this.selected = clientProtocols.get(0);
                return selected;
            } else {
                return method.invoke(this, args);
            }
        }
    }
}
