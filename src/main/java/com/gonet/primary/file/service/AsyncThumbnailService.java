package com.gonet.primary.file.service;

import com.gonet.common.file.security.FileStorage;
import com.gonet.common.file.security.ThumbnailGenerator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.file.mapper.FileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

/**
 * 비동기 썸네일 생성 서비스 — 업로드 응답을 블로킹하지 않기 위해 별도 스레드풀에서 처리.
 *
 * <p>호출 흐름:
 * <ol>
 *   <li>{@code FileServiceImpl.commit()} 이 tb_file INSERT 완료 후 fire-and-forget 호출</li>
 *   <li>{@code thumbnailExecutor} ({@link com.gonet.common.file.config.ThumbnailAsyncConfig}) 에서 실행</li>
 *   <li>{@link ThumbnailGenerator#generate} 로 400px JPG 생성</li>
 *   <li>성공 시 {@link FileMapper#updateThumbnailPath} 로 DB UPDATE (새 트랜잭션)</li>
 *   <li>실패는 로그만 — 원본 업로드는 이미 커밋됨, UX 에 영향 없음</li>
 * </ol>
 *
 * <p>서버 재시작으로 큐 내 태스크가 유실된 경우, {@code is_image_yn='Y'
 * AND thumbnail_path IS NULL} 파일을 대상으로 한 재생성 배치를 추후 추가해 복구 가능.
 *
 * <p>주의: {@code @Async} 는 프록시 호출에만 작동. 반드시 외부에서 본 서비스의 public 메서드를 호출할 것.
 */
@Service
public class AsyncThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(AsyncThumbnailService.class);

    private final FileStorage         storage;
    private final ThumbnailGenerator  generator;
    private final FileMapper          fileMapper;

    public AsyncThumbnailService(FileStorage storage,
                                  ThumbnailGenerator generator,
                                  FileMapper fileMapper) {
        this.storage    = storage;
        this.generator  = generator;
        this.fileMapper = fileMapper;
    }

    /**
     * 썸네일 생성 + DB UPDATE 를 비동기 실행. fire-and-forget.
     *
     * @param fileId         tb_file.file_id
     * @param storedPath     tb_file.stored_path (정식 저장소 기준 상대경로)
     * @param extension      정규화된 확장자 (jpg/jpeg/png/gif)
     * @param directory      엔티티 디렉터리 ({@link FileStorage#thumbnailTarget} 파라미터)
     * @param storedName     stored_name (uuid.ext 형식)
     */
    @Async("thumbnailExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void generateAndSave(String fileId,
                                 String storedPath,
                                 String extension,
                                 String directory,
                                 String storedName) {
        try {
            Path source = storage.resolveStoredPath(storedPath);
            FileStorage.ThumbnailTarget tt = storage.thumbnailTarget(directory, storedName);

            boolean ok = generator.generate(source, extension, tt.absolute());
            if (ok) {
                fileMapper.updateThumbnailPath(fileId, tt.relative());
                log.info("===THUMBNAIL_ASYNC_OK fileId={} path={}", fileId, tt.relative());
            } else {
                log.info("===THUMBNAIL_ASYNC_SKIP fileId={} ext={}", fileId, extension);
            }
        } catch (Exception ex) {
            // 업로드는 이미 커밋됐으므로 썸네일 실패가 사용자에게 미치는 영향 없음.
            // 재생성 배치로 추후 복구 가능.
            log.warn("THUMBNAIL_ASYNC_FAIL fileId={} reason={}", fileId, ex.getMessage(), ex);
        }
    }
}
