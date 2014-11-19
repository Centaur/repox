package com.gtan.repox;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/19
 * Time: 下午9:25
 */
public class Main {
    public static void main(String[] args) {
       Undertow server = Undertow.builder()
               .addHttpListener(8078, "localhost")
               .setHandler(new HttpHandler() {
                   @Override
                   public void handleRequest(HttpServerExchange exchange) throws Exception {
                       if (exchange.isInIoThread()) {
                           System.out.println("in Io Thread");
                           exchange.dispatch(this);
                       } else {
                           System.out.println("in Worker Thread");
                           Repox.handle(exchange);
                       }
                   }
               }).build();
        server.start();
    }
}
