package com.gonet.common.base;

/**
 * soft delete 컬럼을 가진 엔티티 인터페이스.
 * delete_yn CHAR(1) — CHECK('Y','N'), 기본값 'N'.
 */
public interface SoftDeletable {

    String getDeleteYn();
    void   setDeleteYn(String deleteYn);

    default boolean isDeleted() {
        return "Y".equalsIgnoreCase(getDeleteYn());
    }

    default void markDeleted() {
        setDeleteYn("Y");
    }
}
