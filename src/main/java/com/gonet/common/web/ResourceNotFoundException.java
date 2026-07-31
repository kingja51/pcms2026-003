package com.gonet.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 요청한 자원이 없다 — HTTP <b>404</b>.
 *
 * <h2>왜 필요한가</h2>
 * 사용자 화면의 조회 경로는 대부분 {@code try-catch} 없이 서비스를 호출한다
 * (실측 19개 GET 메서드). 그런데 서비스는 대상이 없을 때
 * {@code IllegalArgumentException("…을 찾을 수 없습니다")} 를 던진다.
 * 이 예외를 4xx 로 매핑하는 핸들러가 없어서 <b>없는 게시글·게시판을 열면 500</b> 이 났다.
 *
 * <p>사용자 입력으로 도달 가능한 경로에서 5xx 가 나면 오류 로그가 오염되고
 * <b>진짜 장애가 그 안에 묻힌다</b>. 존재하지 않는 자원은 404 여야 한다.
 *
 * <h2>왜 IllegalArgumentException 을 상속하나</h2>
 * 기존 코드에 {@code catch (IllegalArgumentException)} 가 <b>105곳</b> 있다.
 * 관리자 화면의 CUD 규약(try-catch → flash → HX-Redirect)이 전부 이 catch 에 기대고 있다.
 * 별개 계층으로 만들면 그 105곳이 조용히 예외를 놓쳐 <b>관리자 화면이 500 으로 바뀐다</b>.
 *
 * <p>상속으로 두면 두 동작이 모두 성립한다:
 * <ul>
 *   <li>잡는 쪽(관리자 CUD) — 기존 {@code catch} 가 그대로 잡아 flash 로 처리</li>
 *   <li>안 잡는 쪽(사용자 조회) — {@link ResponseStatus} 가 404 로 응답</li>
 * </ul>
 *
 * <h2>쓰지 말아야 할 곳</h2>
 * <b>검증 실패에는 쓰지 않는다.</b> "비밀번호는 필수입니다", "이미 사용 중인 아이디"
 * 같은 것은 자원 부재가 아니라 입력 오류이므로 {@code IllegalArgumentException} 그대로 둔다.
 * 실측상 {@code IllegalArgumentException} 267건 중 <b>179건이 검증 오류</b>다 —
 * 통째로 404 매핑을 걸었다면 "비밀번호는 필수입니다" 가 404 가 됐을 것이다.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends IllegalArgumentException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
