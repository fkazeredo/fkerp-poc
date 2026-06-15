package com.fksoft.erp.infra.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.erp.domain.error.DomainException;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/** Presentation-completeness: every {@link DomainException} subtype must have an HTTP status. */
class HttpErrorMappingTest {

    @Test
    void everyDomainExceptionIsMappedToAStatus() throws ClassNotFoundException {
        Set<Class<? extends DomainException>> mapped = new HttpErrorMapping().mappedTypes();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(DomainException.class));

        for (var candidate : scanner.findCandidateComponents("com.fksoft.erp")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (!Modifier.isAbstract(type.getModifiers())) {
                assertThat(mapped).contains(type.asSubclass(DomainException.class));
            }
        }
    }
}
