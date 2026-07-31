package com.gonet.primary.system.mail.mapper;

import com.gonet.primary.system.mail.dto.MailTemplate;
import com.gonet.primary.system.mail.dto.MailTemplateSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * tb_mail_template CRUD.
 */
@EgovMapper
public interface MailTemplateMapper {

    List<MailTemplate> findList(@Param("search") MailTemplateSearch search);
    int                countList(@Param("search") MailTemplateSearch search);
    MailTemplate       findById(@Param("mailTemplateId") String id);

    /** 활성 + use_yn='Y' 인 템플릿을 코드로 조회 (로그인 핫패스 — 메일 발송 시). */
    MailTemplate       findActiveByCode(@Param("templateCode") String code);

    int existsByCode(@Param("templateCode") String code,
                      @Param("excludeId") String excludeId);

    /** MailTemplateSeeder 용 — code 로 본문 유무 확인. */
    int existsBodyByCode(@Param("templateCode") String code);

    void insert(MailTemplate row);
    void update(MailTemplate row);
    int  softDelete(@Param("mailTemplateId") String id);

    /** Seeder 전용 — body_html 이 NULL 인 행에 본문 주입. */
    int updateBodyIfNullByCode(@Param("templateCode") String code,
                                @Param("bodyHtml") String bodyHtml);
}
