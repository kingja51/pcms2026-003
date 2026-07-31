package com.gonet.primary.system.mail.service;

import com.gonet.primary.system.mail.dto.MailTemplate;
import com.gonet.primary.system.mail.dto.MailTemplateSaveForm;
import com.gonet.primary.system.mail.dto.MailTemplateSearch;

import java.util.List;

public interface MailTemplateService {

    // 조회
    List<MailTemplate> search(MailTemplateSearch search);
    int                count(MailTemplateSearch search);
    MailTemplate       get(String mailTemplateId);

    /** 발송 핫패스 — code 로 활성 템플릿 단건 조회 (캐시). */
    MailTemplate       getByCodeActive(String code);

    // CUD
    String create(MailTemplateSaveForm form);
    void   update(MailTemplateSaveForm form);
    void   softDelete(String mailTemplateId);
}
