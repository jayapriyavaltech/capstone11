package com.coremedia.blueprint.sfmc.p13n.cae;

import com.coremedia.blueprint.base.sfmc.p13n.cae.journey.email.JourneyEMailResolverConfiguration;
import com.coremedia.blueprint.base.sfmc.p13n.cae.journey.segments.JourneySegmentsConfiguration;
import com.coremedia.personalization.context.collector.ContextSource;
import com.coremedia.springframework.customizer.Customize;
import com.coremedia.springframework.xml.ResourceAwareXmlBeanDefinitionReader;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;

@Configuration(proxyBeanMethods = false)
@Import({JourneySegmentsConfiguration.class,
         JourneyEMailResolverConfiguration.class})
@ImportResource(value = {
        "classpath:/framework/spring/personalization-plugin/personalization-context.xml",
}, reader = ResourceAwareXmlBeanDefinitionReader.class)
public class SFMCP13NCAEConfiguration {
  @Bean
  @Customize("contextSources")
  @NonNull
  public ContextSource journeySegmentSourceCustomizer(@NonNull ContextSource journeySegmentSource) {
    return journeySegmentSource;
  }
}
