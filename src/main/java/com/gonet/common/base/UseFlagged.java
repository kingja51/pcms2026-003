package com.gonet.common.base;

/**
 * 사용 여부 플래그(use_yn) 를 가진 엔티티 인터페이스.
 * use_yn CHAR(1) — CHECK('Y','N'), 기본값 'Y'.
 */
public interface UseFlagged {

    String getUseYn();
    void   setUseYn(String useYn);

    default boolean isActive() {
        return "Y".equalsIgnoreCase(getUseYn());
    }
}
