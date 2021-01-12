package de.qtc.rmg.utils;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.rmi.ssl.SslRMIClientSocketFactory;

import de.qtc.rmg.internal.ExceptionHandler;
import de.qtc.rmg.io.Logger;
import de.qtc.rmg.networking.DummyTrustManager;
import de.qtc.rmg.networking.LoopbackSocketFactory;
import de.qtc.rmg.networking.LoopbackSslSocketFactory;
import sun.rmi.transport.tcp.TCPEndpoint;

@SuppressWarnings("restriction")
public final class RMIWhisperer {

    public int port;
    public String host;

    private Registry rmiRegistry;
    private RMIClientSocketFactory csf;

    public RMIWhisperer(String host, int port, boolean ssl, boolean followRedirects)
    {
         this.host = host;
         this.port = port;

         RMISocketFactory fac = RMISocketFactory.getDefaultSocketFactory();
         RMISocketFactory my = new LoopbackSocketFactory(host, fac, followRedirects);

         try {
             RMISocketFactory.setSocketFactory(my);
         } catch (IOException e) {
             Logger.eprintlnMixedBlue("Unable to set custom", "RMISocketFactory.", "Host redirection will probably not work.");
             RMGUtils.showStackTrace(e);
             Logger.eprintln("");
         }

         try {
             SSLContext ctx = SSLContext.getInstance("TLS");
             ctx.init(null, new TrustManager[] { new DummyTrustManager() }, null);
             SSLContext.setDefault(ctx);

             LoopbackSslSocketFactory.host = host;
             LoopbackSslSocketFactory.fac = ctx.getSocketFactory();
             LoopbackSslSocketFactory.followRedirect = followRedirects;
             java.security.Security.setProperty("ssl.SocketFactory.provider", "de.qtc.rmg.networking.LoopbackSslSocketFactory");

         } catch (NoSuchAlgorithmException | KeyManagementException e) {
             Logger.eprintlnMixedBlue("Unable to set", "TrustManager", "for SSL connections.");
             Logger.eprintln("SSL connections to untrusted hosts might fail.");
             RMGUtils.showStackTrace(e);
         }

         if( ssl )
             csf = new SslRMIClientSocketFactory();
         else
             csf = my;
    }

    public void locateRegistry()
    {
        Logger.print("Creating RMI Registry object... ");

        try {
            this.rmiRegistry = LocateRegistry.getRegistry(host, port, csf);
            Logger.printlnPlain("done.");

        } catch( RemoteException e ) {
            Logger.printlnPlain("failed.");
            RMGUtils.stackTrace(e);
            RMGUtils.exit();
        }
    }

    public String[] getBoundNames()
    {
        String[] boundNames = null;
        Logger.print("Obtaining list of bound names... ");

        try {
            boundNames = rmiRegistry.list();
            Logger.printlnPlain("done.");
            Logger.printlnMixedBlueFirst(String.valueOf(boundNames.length), "names are bound to the registry.");

        } catch( java.rmi.NoSuchObjectException e) {
            Logger.printlnPlain("failed.");
            Logger.eprintlnMixedYellow("Caugth", "NoSuchObjectException", "while listing bound names.");
            Logger.eprintlnMixedYellow("Remote endpoint is probably", "not an RMI registry.");
            RMGUtils.showStackTrace(e);
            RMGUtils.exit();

        } catch( java.rmi.ConnectIOException e ) {

            Logger.printlnPlain("failed.");
            Logger.eprintln("");
            Throwable t = RMGUtils.getCause(e);

            if( t instanceof java.io.EOFException ) {
                ExceptionHandler.eofException(e, "list", "call");

            } else if( t instanceof java.net.NoRouteToHostException) {
                ExceptionHandler.noRouteToHost(e, "list", "call");

            } else {
                ExceptionHandler.unexpectedException(e, "list", "call", true);
            }

        } catch( RemoteException e ) {

            Logger.printlnPlain("failed.");
            Logger.eprintln("");
            Throwable t = RMGUtils.getCause(e);

            if( t instanceof java.net.NoRouteToHostException ) {
                ExceptionHandler.noRouteToHost(e, "list", "call");

            } else if( t instanceof java.net.ConnectException ) {
                ExceptionHandler.connectionRefused(e, "list", "call");

            } else if( t instanceof java.rmi.ConnectIOException && t.getMessage().equals("non-JRMP server at remote endpoint")) {
                ExceptionHandler.noJRMPServer(e, "list", "call");

            } else if( t instanceof javax.net.ssl.SSLException && t.getMessage().contains("Unsupported or unrecognized SSL message") ) {
                ExceptionHandler.sslError(e, "list", "call");

            } else {
                ExceptionHandler.unexpectedException(e, "list", "call", true);
            }
        }

        return boundNames;
    }

    public String[] getBoundNames(String boundName)
    {
        if( boundName == null )
            return getBoundNames();

        return new String[] {boundName};
    }

    public ArrayList<HashMap<String, String>> getClassNames(String[] boundNames)
    {
        ArrayList<HashMap<String, String>> returnList = new ArrayList<HashMap<String, String>>();

        HashMap<String, String> knownClasses = new HashMap<String,String>();
        HashMap<String, String> unknownClasses = new HashMap<String,String>();

        Object object = null;

        for( String className : boundNames ) {

          try {

              object = rmiRegistry.lookup(className);
              knownClasses.put(className, object.getClass().getName());

          } catch( RemoteException e ) {

              Throwable cause = RMGUtils.getCause(e);
              if( cause instanceof ClassNotFoundException ) {

                  /*
                   * Expected exception message is: <CLASSNAME> (no security manager: RMI class loader disabled).
                   * This is always true, as long as no security manager is used when starting rmg. As the exception
                   * is thrown on the client-side, server-side security managers are not important here.
                   * Since classnames cannot contain spaces, cutting on the first space should be sufficient.
                   */
                  String message = cause.getMessage();
                  int end = message.indexOf(" ");

                  message = message.substring(0, end);
                  unknownClasses.put(className, message);

              } else {
                  ExceptionHandler.unexpectedException(e, "lookup", "call", false);
              }

          } catch( NotBoundException e) {
              ExceptionHandler.unexpectedException(e, "lookup", "call", false);
          }
        }

        returnList.add(knownClasses);
        returnList.add(unknownClasses);
        return returnList;
    }

    public TCPEndpoint getEndpoint()
    {
        return new TCPEndpoint(host, port, csf, null);
    }

    public Registry getRegistry()
    {
        return this.rmiRegistry;
    }
}
