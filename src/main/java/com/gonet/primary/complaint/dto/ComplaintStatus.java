package com.gonet.primary.complaint.dto;

public enum ComplaintStatus {
    /** 접수 */
    RECEIVED,
    /** 처리 중 */
    IN_PROGRESS,
    /** 답변 완료 */
    ANSWERED,
    /** 종결 */
    CLOSED,
    /** 반려 */
    REJECTED;

    public String label() {
        return switch (this) {
            case RECEIVED    -> "접수";
            case IN_PROGRESS -> "처리중";
            case ANSWERED    -> "답변완료";
            case CLOSED      -> "종결";
            case REJECTED    -> "반려";
        };
    }

    public String badgeCss() {
        return switch (this) {
            case RECEIVED    -> "bg-blue-50 text-blue-700";
            case IN_PROGRESS -> "bg-amber-50 text-amber-700";
            case ANSWERED    -> "bg-green-50 text-green-700";
            case CLOSED      -> "bg-slate-100 text-slate-600";
            case REJECTED    -> "bg-red-50 text-red-700";
        };
    }
}
