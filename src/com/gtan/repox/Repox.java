package com.gtan.repox;

import com.ning.http.client.*;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: xf
 * Date: 14/11/19
 * Time: 下午10:47
 */
public class Repox {
    static String upstream = "http://uk.maven.org";
    static AsyncHttpClient client = new AsyncHttpClient();

    public static void handle(HttpServerExchange exchange) throws IOException {
        HttpString method = exchange.getRequestMethod();
        String uri = exchange.getRequestURI();

        switch (method.toString().toUpperCase()) {
            case "GET":
                break;
            case "HEAD":
                client.prepareHead(upstream + uri).execute(new AsyncHandler<Object>() {
                    HttpResponseStatus status = null;

                    @Override
                    public void onThrowable(Throwable t) {
                        t.printStackTrace();
                    }

                    @Override
                    public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
                        System.out.println("onBodyPartReceived");
                        return STATE.CONTINUE;
                    }

                    @Override
                    public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
                        System.out.println("onStatusReceived");
                        System.out.println("response started: "+exchange.isResponseStarted());
                        status = responseStatus;
                        return STATE.CONTINUE;
                    }

                    @Override
                    public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
                        System.out.println("onHeaderReceived");
                        for (Map.Entry<String, List<String>> entry : headers.getHeaders()) {
                            System.out.println(entry.getKey() + ":" + entry.getValue());
                            exchange.getResponseHeaders().addAll(new HttpString(entry.getKey()), entry.getValue());
                        }
                        exchange.setResponseCode(status.getStatusCode());
                        exchange.endExchange();
                        return STATE.ABORT;
                    }

                    @Override
                    public Object onCompleted() throws Exception {
                        return null;
                    }
                });
                break;

        }
    }
}
