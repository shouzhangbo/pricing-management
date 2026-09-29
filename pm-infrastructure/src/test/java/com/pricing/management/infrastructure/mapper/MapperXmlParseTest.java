package com.pricing.management.infrastructure.mapper;

import java.io.Reader;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperXmlParseTest {
    private static final List<String> MAPPERS = List.of(
            "BizLineMapper", "PricingSceneMapper", "PricingFeeGroupMapper", "PricingFeeItemMapper",
            "PricingSchemeMapper", "PricingSchemeDimensionMapper", "PricingSchemeFactorValueMapper",
            "PricingSchemeTemplateMapper", "PricingFactorMapper", "PricingTemplateMapper",
            "ApprovalFlowMapper", "AuditLogMapper");

    @Test
    void allMapperXmlFilesShouldParseAndExposeStatements() throws Exception {
        Configuration configuration = new Configuration();
        for (String mapper : MAPPERS) {
            String resource = "mapper/" + mapper + ".xml";
            try (Reader reader = Resources.getResourceAsReader(resource)) {
                new XMLMapperBuilder(reader, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        assertTrue(configuration.hasStatement(BizLineMapper.class.getName() + ".selectById"));
        assertTrue(configuration.hasStatement(PricingSchemeMapper.class.getName() + ".selectEffectiveByFeeId"));
        assertTrue(configuration.hasStatement(AuditLogMapper.class.getName() + ".selectByBusiness"));
    }
}
