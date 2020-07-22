package org.embulk.util.retryhelper.jetty92;

import java.util.Locale;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.embulk.util.retryhelper.Retryable;
import org.embulk.util.retryhelper.RetryExecutor;
import org.embulk.util.retryhelper.RetryGiveupException;
import org.slf4j.LoggerFactory;

public class Jetty92RetryHelper
        implements AutoCloseable
{
    public Jetty92RetryHelper(int maximumRetries,
                              int initialRetryIntervalMillis,
                              int maximumRetryIntervalMillis,
                              Jetty92ClientCreator clientCreator)
    {
        this.maximumRetries = maximumRetries;
        this.initialRetryIntervalMillis = initialRetryIntervalMillis;
        this.maximumRetryIntervalMillis = maximumRetryIntervalMillis;
        try {
            this.clientStarted = clientCreator.createAndStart();
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        this.closeAutomatically = true;
        this.logger = LoggerFactory.getLogger(Jetty92RetryHelper.class);
    }

    public Jetty92RetryHelper(int maximumRetries,
                              int initialRetryIntervalMillis,
                              int maximumRetryIntervalMillis,
                              Jetty92ClientCreator clientCreator,
                              final org.slf4j.Logger logger)
    {
        this.maximumRetries = maximumRetries;
        this.initialRetryIntervalMillis = initialRetryIntervalMillis;
        this.maximumRetryIntervalMillis = maximumRetryIntervalMillis;
        try {
            this.clientStarted = clientCreator.createAndStart();
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        this.closeAutomatically = true;
        this.logger = logger;
    }

    /**
     * Creates a {@code Jetty92RetryHelper} instance with a ready-made Jetty 9.3 {@code HttpClient} instance.
     *
     * Note that the {@code HttpClient} instance is not automatically closed.
     */
    public static Jetty92RetryHelper createWithReadyMadeClient(int maximumRetries,
                                                               int initialRetryIntervalMillis,
                                                               int maximumRetryIntervalMillis,
                                                               final org.eclipse.jetty.client.HttpClient clientStarted,
                                                               final org.slf4j.Logger logger)
    {
        return new Jetty92RetryHelper(maximumRetries,
                                      initialRetryIntervalMillis,
                                      maximumRetryIntervalMillis,
                                      clientStarted,
                                      false,
                                      logger);
    }

    private Jetty92RetryHelper(int maximumRetries,
                               int initialRetryIntervalMillis,
                               int maximumRetryIntervalMillis,
                               final org.eclipse.jetty.client.HttpClient clientStarted,
                               boolean closeAutomatically,
                               final org.slf4j.Logger logger)
    {
        this.maximumRetries = maximumRetries;
        this.initialRetryIntervalMillis = initialRetryIntervalMillis;
        this.maximumRetryIntervalMillis = maximumRetryIntervalMillis;
        this.logger = logger;
        this.clientStarted = clientStarted;
        this.closeAutomatically = closeAutomatically;
    }

    public <T> T requestWithRetry(final Jetty92ResponseReader<T> responseReader,
                                  final Jetty92SingleRequester singleRequester)
    {
        try {
            return RetryExecutor
                .retryExecutor()
                .withRetryLimit(this.maximumRetries)
                .withInitialRetryWait(this.initialRetryIntervalMillis)
                .withMaxRetryWait(this.maximumRetryIntervalMillis)
                .runInterruptible(new Retryable<T>() {
                        @Override
                        public T call()
                                throws Exception
                        {
                            Response.Listener listener = responseReader.getListener();
                            singleRequester.requestOnce(clientStarted, listener);
                            Response response = responseReader.getResponse();
                            if (response.getStatus() / 100 != 2) {
                                final String errorResponseBody;
                                try {
                                    errorResponseBody = responseReader.readResponseContentInString();
                                }
                                catch (Exception ex) {
                                    throw new HttpResponseException(
                                        "Response not 2xx: "
                                        + response.getStatus() + " "
                                        + response.getReason() + " "
                                        + "Response body not available by: " + Util.getStackTraceAsString(ex),
                                        response);
                                }
                                throw new HttpResponseException(
                                    "Response not 2xx: "
                                    + response.getStatus() + " "
                                    + response.getReason() + " "
                                    + errorResponseBody,
                                    response);
                            }
                            return responseReader.readResponseContent();
                        }

                        @Override
                        public boolean isRetryableException(Exception exception)
                        {
                            return singleRequester.toRetry(exception);
                        }

                        @Override
                        public void onRetry(Exception exception, int retryCount, int retryLimit, int retryWait)
                                throws RetryGiveupException
                        {
                            String message = String.format(
                                Locale.ENGLISH, "Retrying %d/%d after %d seconds. Message: %s",
                                retryCount, retryLimit, retryWait / 1000, exception.getMessage());
                            if (retryCount % 3 == 0) {
                                logger.warn(message, exception);
                            }
                            else {
                                logger.warn(message);
                            }
                        }

                        @Override
                        public void onGiveup(Exception first, Exception last)
                                throws RetryGiveupException
                        {
                        }
                    });
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            // InterruptedException must not be RuntimeException.
            throw new RuntimeException(ex);
        }
        catch (RetryGiveupException ex) {
            // RetryGiveupException is ExecutionException, which must not be RuntimeException.
            throw new RuntimeException(ex.getCause());
        }
    }

    @Override
    public void close()
    {
        if (this.closeAutomatically && this.clientStarted != null) {
            try {
                if (this.clientStarted.isStarted()) {
                    this.clientStarted.stop();
                }
            }
            catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            finally {
                this.clientStarted.destroy();
            }
        }
    }

    private final int maximumRetries;
    private final int initialRetryIntervalMillis;
    private final int maximumRetryIntervalMillis;
    private final org.eclipse.jetty.client.HttpClient clientStarted;
    private final org.slf4j.Logger logger;
    private final boolean closeAutomatically;
}
