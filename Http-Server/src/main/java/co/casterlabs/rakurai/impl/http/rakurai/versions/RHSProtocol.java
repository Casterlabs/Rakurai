package co.casterlabs.rakurai.impl.http.rakurai.versions;

import java.io.BufferedInputStream;
import java.net.Socket;

import co.casterlabs.rakurai.impl.http.rakurai.RakuraiHttpServer;
import co.casterlabs.rakurai.io.http.HttpSession;

public interface RHSProtocol {

    public HttpSession accept(RakuraiHttpServer server, Socket client, BufferedInputStream in);

}
