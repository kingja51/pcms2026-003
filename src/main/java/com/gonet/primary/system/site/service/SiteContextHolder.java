package com.gonet.primary.system.site.service;

import com.gonet.primary.system.site.dto.SiteContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class SiteContextHolder {
    private SiteContext context;
    public void set(SiteContext ctx) { this.context = ctx; }
    public SiteContext get() { return context; }
    public String siteId() { return context != null ? context.getSiteId() : null; }
}