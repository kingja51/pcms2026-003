package com.gonet.common.file.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 썸네일 생성용 스레드풀.
 *
 * <p>{@link FileUploadProperties.ThumbnailAsync} 설정을 바인딩해 전용
 * {@code thumbnailExecutor} 를 등록. {@code @Async("thumbnailExecutor")} 로 참조.
 *
 * <p>거부 정책: {@link ThreadPoolExecutor.CallerRunsPolicy} — 큐가 가득 차면 호출 스레드(업로드
 * 트랜잭션 커밋 후)가 직접 실행. 업로드 응답이 잠시 느려질 뿐 **태스크 유실 없음**.
 */
@Configuration
public class ThumbnailAsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailAsyncConfig.class);

    @Bean("thumbnailExecutor")
    public ThreadPoolTaskExecutor thumbnailExecutor(FileUploadProperties props) {
        FileUploadProperties.ThumbnailAsync cfg = props.getThumbnailAsync();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cfg.getCorePool());
        executor.setMaxPoolSize(cfg.getMaxPool());
        executor.setQueueCapacity(cfg.getQueueCapacity());
        executor.setThreadNamePrefix(cfg.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("===THUMBNAIL_EXECUTOR ready core={} max={} queue={} prefix={}",
            cfg.getCorePool(), cfg.getMaxPool(), cfg.getQueueCapacity(), cfg.getThreadNamePrefix());
        return executor;
    }
}
