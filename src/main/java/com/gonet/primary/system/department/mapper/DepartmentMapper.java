package com.gonet.primary.system.department.mapper;

import com.gonet.primary.system.department.dto.Department;
import com.gonet.primary.system.department.dto.DepartmentOption;
import com.gonet.primary.system.department.dto.DepartmentSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * tb_department CRUD — 읽기 옵션 + Mng 전체 (/admin/system/department).
 *
 * <p>트리 계층:
 * <ul>
 *   <li>{@code parent_department_id} self-reference (NULL = 루트)</li>
 *   <li>{@code depth} 는 parent.depth+1 — Service 에서 계산</li>
 *   <li>같은 parent 내 {@code sort_order} 정렬</li>
 * </ul>
 * <p>감사 6컬럼은 AuditInterceptor 자동 주입.
 */
@EgovMapper
public interface DepartmentMapper {

    // ------------------------------------------------------------------
    // 읽기 (직원 폼 공유)
    // ------------------------------------------------------------------

    /** 활성 부서 드롭다운. 트리 순서(depth/sort_order). */
    List<DepartmentOption> findActiveOptions();

    /** 단일 조회. */
    Department findById(@Param("departmentId") String departmentId);

    // ------------------------------------------------------------------
    // Mng 목록·중복확인
    // ------------------------------------------------------------------

    List<Department> findList(@Param("search") DepartmentSearch search);

    int countList(@Param("search") DepartmentSearch search);

    /** department_code 중복확인. excludeDepartmentId 는 수정 시 자기 자신 제외. */
    int existsByCode(@Param("departmentCode") String departmentCode,
                      @Param("excludeDepartmentId") String excludeDepartmentId);

    /** 자식 부서 존재 여부 — 삭제 전 검증 (0 이상이면 삭제 불가). */
    int countChildren(@Param("departmentId") String departmentId);

    /** 직계 자식 부서 목록 — 서브트리 depth 재계산 BFS 순회용. */
    List<Department> findChildDepartments(@Param("parentDepartmentId") String parentDepartmentId);

    /**
     * 조상 부서 체인 — 자기 자신 포함, root 까지 거슬러 올라간 모든 부서.
     *
     * <p>Gemini 문서검색 가시성 필터 용도. 사용자(부서=Y) 가 볼 수 있는 파일:
     * <ul>
     *   <li>{@code file.dept_id IS NULL} (ALL)</li>
     *   <li>{@code file.dept_id IN ancestors_of_user_dept (Y 포함)}</li>
     * </ul>
     *
     * <p>3 벤더 모두 Recursive CTE 사용 — MariaDB 10.2+ / MySQL 8.0+ / PostgreSQL 8.4+.
     * delete_yn='N' 필터, depth 안전장치(&lt; 20) 포함.
     */
    List<Department> findAncestorDepartments(@Param("departmentId") String departmentId);

    /** depth 만 단독 갱신 (updated_at 자동 주입). 서브트리 재계산 전용. */
    int updateDepth(@Param("departmentId") String departmentId,
                     @Param("depth") int depth);

    /** 해당 부서에 속한 활성 직원 수 — 삭제 전 검증. */
    int countEmployees(@Param("departmentId") String departmentId);

    // ------------------------------------------------------------------
    // CUD
    // ------------------------------------------------------------------

    void insert(Department department);

    void update(Department department);

    int softDelete(@Param("departmentId") String departmentId);

    /** 부서장 단독 변경 — 별도 엔드포인트에서 호출. NULL 허용(해임). */
    int updateManager(@Param("departmentId") String departmentId,
                       @Param("managerEmployeeId") String managerEmployeeId);
}
