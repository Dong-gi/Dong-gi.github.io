package io.github.donggi.mvc.interceptor;

import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeferredResultInterceptor implements DeferredResultProcessingInterceptor {
    @Override
    public <T> void beforeConcurrentHandling(NativeWebRequest request, DeferredResult<T> deferredResult)
            throws Exception {
        log.debug("beforeConcurrentHandling");
    }
    @Override
    public <T> void afterCompletion(NativeWebRequest request, DeferredResult<T> deferredResult) throws Exception {
        log.debug("afterCompletion");
    }
    @Override
    public <T> boolean handleTimeout(NativeWebRequest request, DeferredResult<T> deferredResult) throws Exception {
        log.debug("handleTimeout");
        deferredResult.setErrorResult("timeout");
        return true;
    }
}