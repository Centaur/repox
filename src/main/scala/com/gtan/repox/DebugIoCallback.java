package com.gtan.repox;

import com.typesafe.scalalogging.LazyLogging;
import com.typesafe.scalalogging.Logger;
import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import org.xnio.IoUtils;

import java.io.IOException;

public class DebugIoCallback implements IoCallback {

    @Override
    public void onComplete(final HttpServerExchange exchange, final Sender sender) {
        System.out.println("DebugIoCallback onComplete");
        sender.close(new IoCallback() {
            @Override
            public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                System.out.println("DebugIoCallback onComplete -- sender.close.onComplete");
                exchange.endExchange();
            }

            @Override
            public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                System.out.println("DebugIoCallback onComplete -- sender.close.onException: " + exception.getMessage());
                UndertowLogger.REQUEST_IO_LOGGER.ioException(exception);
                exchange.endExchange();
            }
        });
    }

    @Override
    public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
        System.out.println("DebugIoCallback onException");
        exception.printStackTrace();
        try {
            exchange.endExchange();
        } finally {
            IoUtils.safeClose(exchange.getConnection());
        }
    }

}
