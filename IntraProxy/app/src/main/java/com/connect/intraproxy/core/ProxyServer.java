/**
 * Copyright (c) 2013, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.connect.intraproxy.core;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @hide
 */
public class ProxyServer extends Thread {

    private static final String CONNECT = "CONNECT";
    private static final String HTTP_OK = "HTTP/1.1 200 OK\n";

    private static final String TAG = "ProxyServer";

    // HTTP Headers
    private static final String HEADER_CONNECTION = "connection";
    private static final String HEADER_PROXY_CONNECTION = "proxy-connection";

    private static final String INTERFACE_MOBILE = "rmnet0";
    private static final String INTERFACE_WLAN = "wlan0";

    private ExecutorService threadExecutor;

    public boolean mIsRunning = false;

    private ServerSocket serverSocket;
    private int mPort;
    //private IProxyPortListener mCallback;

    public static final int PROXY_PORT = 22222;

    public interface ServerStatusListener {
        void onStarted();
        void onStopped();
    }

    private ServerStatusListener serverStatusListener;

    private static class ProxyConnection implements Runnable {
        private final Socket connection;

        private ProxyConnection(Socket connection) {
            this.connection = connection;
        }

        @Override
        public void run() {
            try {
                String requestLine = getLine(connection.getInputStream());
                String[] splitLine = requestLine.split(" ");
                if (splitLine.length < 3) {
                    connection.close();
                    return;
                }
                String requestType = splitLine[0];
                String urlString = splitLine[1];
                String httpVersion = splitLine[2];

                //Log.d("TAG", requestLine);

                URI url = null;
                String host;
                int port;

                if (requestType.equals(CONNECT)) {
                    String[] hostPortSplit = urlString.split(":");
                    host = hostPortSplit[0];
                    // Use default SSL port if not specified. Parse it otherwise
                    if (hostPortSplit.length < 2) {
                        port = 443;
                    } else {
                        try {
                            port = Integer.parseInt(hostPortSplit[1]);
                        } catch (NumberFormatException nfe) {
                            connection.close();
                            return;
                        }
                    }
                    urlString = "Https://" + host + ":" + port;
                } else {
                    try {
                        url = new URI(urlString);
                        host = url.getHost();
                        port = url.getPort();
                        if (port < 0) {
                            port = 80;
                        }
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                        connection.close();
                        return;
                    }
                }

                //Log.d("TAG", "host: " + host + ", port: " + port);
//
//                List<Proxy> list = Collections.emptyList();
//                try {
//                    list = ProxySelector.getDefault().select(new URI(urlString));
//                } catch (URISyntaxException e) {
//                    e.printStackTrace();
//                }
                Socket server = null;
//                for (Proxy proxy : list) {
//                    try {
//                        if (!proxy.equals(Proxy.NO_PROXY)) {
//                            // Only Inets created by PacProxySelector.
//                            InetSocketAddress inetSocketAddress =
//                                    (InetSocketAddress)proxy.address();
//                            server = new Socket(inetSocketAddress.getHostName(),
//                                    inetSocketAddress.getPort());
//                            sendLine(server, requestLine);
//                        } else {
//                            server = new Socket(host, port);
//                            if (requestType.equals(CONNECT)) {
//                                skipToRequestBody(connection);
//                                // No proxy to respond so we must.
//                                sendLine(connection, HTTP_OK);
//                            } else {
//                                // Proxying the request directly to the origin server.
//                                sendAugmentedRequestToHost(connection, server,
//                                        requestType, url, httpVersion);
//                            }
//                        }
//                    } catch (IOException ioe) {
//                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
//                            Log.v(TAG, "Unable to connect to proxy " + proxy, ioe);
//                        }
//                    }
//                    if (server != null) {
//                        break;
//                    }
//                }
//                if (list.isEmpty()) {
                    server = new Socket(host, port);
                    if (requestType.equals(CONNECT)) {
                        skipToRequestBody(connection);
                        // No proxy to respond so we must.
                        sendLine(connection, HTTP_OK);
                    } else {
                        // Proxying the request directly to the origin server.
                        sendAugmentedRequestToHost(connection, server,
                                requestType, url, httpVersion);
                    }
//                }

                //server = new Socket("10.75.98.193", 443);
                //server = new Socket("103.255.172.201", 443);
                //server = new Socket("192.168.0.1", 80);

                // Pass data back and forth until complete.
                if (server != null) {
                    SocketConnect.connect(connection, server);
                }
            } catch (Exception e) {
                Log.d(TAG, "Problem Proxying", e);
            }
            try {
                connection.close();
            } catch (IOException ioe) {
                // Do nothing
            }
        }

        /**
         * Sends HTTP request-line (i.e. the first line in the request)
         * that contains absolute path of a given absolute URI.
         *
         * @param server server to send the request to.
         * @param requestType type of the request, a.k.a. HTTP method.
         * @param absoluteUri absolute URI which absolute path should be extracted.
         * @param httpVersion version of HTTP, e.g. HTTP/1.1.
         * @throws IOException if the request-line cannot be sent.
         */
        private void sendRequestLineWithPath(Socket server, String requestType,
                URI absoluteUri, String httpVersion) throws IOException {

            String absolutePath = getAbsolutePathFromAbsoluteURI(absoluteUri);
            String outgoingRequestLine = String.format("%s %s %s",
                    requestType, absolutePath, httpVersion);
            sendLine(server, outgoingRequestLine);
        }

        /**
         * Extracts absolute path form a given URI. E.g., passing
         * <code>http://google.com:80/execute?query=cat#top</code>
         * will result in <code>/execute?query=cat#top</code>.
         *
         * @param uri URI which absolute path has to be extracted,
         * @return the absolute path of the URI,
         */
        private String getAbsolutePathFromAbsoluteURI(URI uri) {
            String rawPath = uri.getRawPath();
            String rawQuery = uri.getRawQuery();
            String rawFragment = uri.getRawFragment();
            StringBuilder absolutePath = new StringBuilder();

            if (rawPath != null) {
                absolutePath.append(rawPath);
            } else {
                absolutePath.append("/");
            }
            if (rawQuery != null) {
                absolutePath.append("?").append(rawQuery);
            }
            if (rawFragment != null) {
                absolutePath.append("#").append(rawFragment);
            }
            return absolutePath.toString();
        }

        private String getLine(InputStream inputStream) throws IOException {
            StringBuilder buffer = new StringBuilder();
            int byteBuffer = inputStream.read();
            if (byteBuffer < 0) return "";
            do {
                if (byteBuffer != '\r') {
                    buffer.append((char)byteBuffer);
                }
                byteBuffer = inputStream.read();
            } while ((byteBuffer != '\n') && (byteBuffer >= 0));

            return buffer.toString();
        }

        private void sendLine(Socket socket, String line) throws IOException {
            OutputStream os = socket.getOutputStream();
            os.write(line.getBytes());
            os.write('\r');
            os.write('\n');
            os.flush();
        }

        /**
         * Reads from socket until an empty line is read which indicates the end of HTTP headers.
         *
         * @param socket socket to read from.
         * @throws IOException if an exception took place during the socket read.
         */
        private void skipToRequestBody(Socket socket) throws IOException {
            while (getLine(socket.getInputStream()).length() != 0);
        }

        /**
         * Sends an augmented request to the final host (DIRECT connection).
         *
         * @param src socket to read HTTP headers from.The socket current position should point
         *            to the beginning of the HTTP header section.
         * @param dst socket to write the augmented request to.
         * @param httpMethod original request http method.
         * @param uri original request absolute URI.
         * @param httpVersion original request http version.
         * @throws IOException if an exception took place during socket reads or writes.
         */
        private void sendAugmentedRequestToHost(Socket src, Socket dst,
                String httpMethod, URI uri, String httpVersion) throws IOException {

            sendRequestLineWithPath(dst, httpMethod, uri, httpVersion);
            filterAndForwardRequestHeaders(src, dst);

            // Currently the proxy does not support keep-alive connections; therefore,
            // the proxy has to request the destination server to close the connection
            // after the destination server sent the response.
            sendLine(dst, "Connection: close");

            // Sends and empty line that indicates termination of the header section.
            sendLine(dst, "");
        }

        /**
         * Forwards original request headers filtering out the ones that have to be removed.
         *
         * @param src source socket that contains original request headers.
         * @param dst destination socket to send the filtered headers to.
         * @throws IOException if the data cannot be read from or written to the sockets.
         */
        private void filterAndForwardRequestHeaders(Socket src, Socket dst) throws IOException {
            String line;
            do {
                line = getLine(src.getInputStream());
                if (line.length() > 0 && !shouldRemoveHeaderLine(line)) {
                    sendLine(dst, line);
                }
            } while (line.length() > 0);
        }

        /**
         * Returns true if a given header line has to be removed from the original request.
         *
         * @param line header line that should be analysed.
         * @return true if the header line should be removed and not forwarded to the destination.
         */
        private boolean shouldRemoveHeaderLine(String line) {
            int colIndex = line.indexOf(":");
            if (colIndex != -1) {
                String headerName = line.substring(0, colIndex).trim();
                if (headerName.regionMatches(true, 0, HEADER_CONNECTION, 0,
                                                      HEADER_CONNECTION.length())
                        || headerName.regionMatches(true, 0, HEADER_PROXY_CONNECTION,
                                                          0, HEADER_PROXY_CONNECTION.length())) {
                    return true;
                }
            }
            return false;
        }
    }

    public ProxyServer() {
        threadExecutor = Executors.newCachedThreadPool();
        mPort = -1;
        //mCallback = null;
    }

    @Override
    public void run() {
        try {
//            NetworkInterface nf = NetworkInterface.getByName(INTERFACE_WLAN);
//            Enumeration<InetAddress> addresses = nf.getInetAddresses();
//
//            while (addresses.hasMoreElements()) {
//                InetAddress address = addresses.nextElement();
//                if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
//                    serverSocket = new ServerSocket(22222);
//
//                    if (serverStatusListener != null) {
//                        serverStatusListener.onStarted();
//                    }
//
//                    while (mIsRunning) {
//                        try {
//                            Socket socket = serverSocket.accept();
//
//                            Log.d("TAG", socket.getInetAddress().getHostAddress());
//
//                            ProxyConnection parser = new ProxyConnection(socket);
//
//                            threadExecutor.execute(parser);
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    }
//
//                    break;
//                }
//            }

            serverSocket = new ServerSocket(PROXY_PORT);

            //setPort(serverSocket.getLocalPort());

            if (serverStatusListener != null) {
                serverStatusListener.onStarted();
            }

            while (mIsRunning) {
                try {
                    Socket socket = serverSocket.accept();
                    InetAddress address = socket.getInetAddress();

                    //Log.d("TAG", address.getHostAddress());
                    // Only receive local connections.
                    if (!address.isLoopbackAddress()) {
                        ProxyConnection parser = new ProxyConnection(socket);

                        threadExecutor.execute(parser);
                    } else {
                        socket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start proxy server", e);
        }

        mIsRunning = false;

        if (serverStatusListener != null) {
            serverStatusListener.onStopped();
            serverStatusListener = null;
        }

    }

//    public synchronized void setPort(int port) {
//        if (mCallback != null) {
//            try {
//                mCallback.setProxyPort(port);
//            } catch (RemoteException e) {
//                Log.w(TAG, "Proxy failed to report port to PacManager", e);
//            }
//        }
//        mPort = port;
//    }
//
//    public synchronized void setCallback(IProxyPortListener callback) {
//        if (mPort != -1) {
//            try {
//                callback.setProxyPort(mPort);
//            } catch (RemoteException e) {
//                Log.w(TAG, "Proxy failed to report port to PacManager", e);
//            }
//        }
//        mCallback = callback;
//    }

    public synchronized void startServer() {
        mIsRunning = true;
        start();
    }

    public synchronized void stopServer() {
        mIsRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
                serverSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (serverStatusListener != null) {
            serverStatusListener.onStopped();
            serverStatusListener = null;
        }
    }

    public boolean isBound() {
        return (mPort != -1);
    }

    public int getPort() {
        return mPort;
    }

    public void setServerStatusListener(ServerStatusListener serverStatusListener) {
        this.serverStatusListener = serverStatusListener;
    }
}
