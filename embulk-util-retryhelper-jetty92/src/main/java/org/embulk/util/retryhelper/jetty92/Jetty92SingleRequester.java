/*
 * Copyright 2020 The Embulk project
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

package org.embulk.util.retryhelper.jetty92;

/**
 * Jetty92SingleRequester is to define a single request to the target REST service to be ready for retries.
 *
 * It is expected to use with {@link Jetty92RetryHelper} as follows.
 *
 * <pre>{@code
 * InputStream inputStream = jetty92RetryHelper.requestWithRetry(
 *     new InputStreamJetty92ResponseEntityReader(),
 *     new Jetty92SingleRequester() {
 *         public void requestOnce(org.eclipse.jetty.client.HttpClient client,
 *                                 org.eclipse.jetty.client.api.Response.Listener listener)
 *         {
 *             client.newRequest("https://example.com/api/resource").method(HttpMethod.GET).send(listener);
 *         }
 *
 *         public boolean isResponseStatusToRetry(org.eclipse.jetty.client.api.Response response)
 *         {
 *             return (response.getStatus() / 100) == 4;
 *         }
 *     });
 * }</pre>
 *
 * @see Jetty92ResponseReader
 * @see InputStreamJetty92ResponseEntityReader
 */
public abstract class Jetty92SingleRequester
{
    /**
     * Requests to the target service with the given {@code org.eclipse.jetty.client.HttpClient}.
     *
     * The response is provided to the specified {@code org.eclipse.jetty.client.api.Response.Listener} by Jetty.
     * The method is {@code abstract} that must be overridden.
     */
    public abstract void requestOnce(org.eclipse.jetty.client.HttpClient client,
                                     org.eclipse.jetty.client.api.Response.Listener responseListener);

    /**
     * Returns {@code true} if the given {@code Exception} from {@link Jetty92RetryHelper} is to retry.
     *
     * This method cannot be overridden. Override {@code isResponseStatusRetryable} and {@code isExceptionRetryable}
     * instead. {@code isResponseStatusRetryable} is called for {@code org.eclipse.jetty.client.HttpResponseException}.
     * {@code isExceptionRetryable} is called for other exceptions.
     */
    public final boolean toRetry(Exception exception) {
        // Expects |org.eclipse.jetty.client.HttpResponseException| is throws in case of HTTP error status
        // such as implemented in |Jetty92RetryHelper|.
        if (exception instanceof org.eclipse.jetty.client.HttpResponseException) {
            return isResponseStatusToRetry(((org.eclipse.jetty.client.HttpResponseException) exception).getResponse());
        }
        else {
            return isExceptionToRetry(exception);
        }
    }

    /**
     * Returns {@code true} if the given {@code org.eclipse.jetty.client.api.Response} is to retry.
     *
     * This method is {@code abstract} to be overridden, and {@code protected} to be called from {@code isRetryable}.
     */
    protected abstract boolean isResponseStatusToRetry(org.eclipse.jetty.client.api.Response response);

    /**
     * Returns true if the given {@code Exception} is to retry.
     *
     * This method available to override, and {@code protected} to be called from {@code isRetryable}.
     */
    protected boolean isExceptionToRetry(Exception exception) {
        return false;
    }
}
