/*
 * Copyright Splunk Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.splunk.rum;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.singleton;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SplunkSpanDataModifierTest {

    @Mock private SpanExporter delegate;
    @Captor private ArgumentCaptor<Collection<SpanData>> exportedSpansCaptor;

    private SplunkSpanDataModifier underTest;

    @Before
    public void setUp() {
        underTest = new SplunkSpanDataModifier(delegate);
    }

    @Test
    public void shouldConvertExceptionEventsToSpanAttributes() {
        SpanData original =
                TestSpanData.builder()
                        .setName("test")
                        .setKind(SpanKind.CLIENT)
                        .setStatus(StatusData.unset())
                        .setStartEpochNanos(12345)
                        .setEndEpochNanos(67890)
                        .setHasEnded(true)
                        .setEvents(
                                Arrays.asList(
                                        EventData.create(
                                                123,
                                                "test",
                                                Attributes.of(stringKey("attribute"), "value")),
                                        EventData.create(
                                                456,
                                                SemanticAttributes.EXCEPTION_EVENT_NAME,
                                                Attributes.builder()
                                                        .put(
                                                                SemanticAttributes.EXCEPTION_TYPE,
                                                                "com.example.Error")
                                                        .put(
                                                                SemanticAttributes
                                                                        .EXCEPTION_MESSAGE,
                                                                "failed")
                                                        .put(
                                                                SemanticAttributes
                                                                        .EXCEPTION_STACKTRACE,
                                                                "<stacktrace>")
                                                        .build())))
                        .setAttributes(Attributes.of(stringKey("attribute"), "value"))
                        .build();

        CompletableResultCode exportResult = CompletableResultCode.ofSuccess();
        when(delegate.export(exportedSpansCaptor.capture())).thenReturn(exportResult);

        CompletableResultCode actual = underTest.export(singleton(original));

        assertThat(actual).isSameAs(exportResult);

        Collection<SpanData> exportedSpans = exportedSpansCaptor.getValue();
        assertThat(exportedSpans).hasSize(1);
        assertThat(exportedSpans.iterator().next())
                .hasName("test")
                .hasKind(SpanKind.CLIENT)
                .hasEvents(
                        EventData.create(
                                123, "test", Attributes.of(stringKey("attribute"), "value")))
                .hasTotalRecordedEvents(1)
                .hasAttributesSatisfyingExactly(
                        equalTo(SplunkSpanDataModifier.SPLUNK_OPERATION_KEY, "test"),
                        equalTo(stringKey("attribute"), "value"),
                        equalTo(SemanticAttributes.EXCEPTION_TYPE, "Error"),
                        equalTo(SplunkRum.ERROR_TYPE_KEY, "Error"),
                        equalTo(SemanticAttributes.EXCEPTION_MESSAGE, "failed"),
                        equalTo(SplunkRum.ERROR_MESSAGE_KEY, "failed"),
                        equalTo(SemanticAttributes.EXCEPTION_STACKTRACE, "<stacktrace>"))
                .hasTotalAttributeCount(7);
    }

    @Test
    public void shouldSetCaseSensitiveSpanNameToAttribute() {
        SpanData original =
                TestSpanData.builder()
                        .setName("SplunkRumSpan")
                        .setKind(SpanKind.CLIENT)
                        .setStatus(StatusData.unset())
                        .setStartEpochNanos(12345)
                        .setEndEpochNanos(67890)
                        .setHasEnded(true)
                        .build();

        CompletableResultCode exportResult = CompletableResultCode.ofSuccess();
        when(delegate.export(exportedSpansCaptor.capture())).thenReturn(exportResult);

        CompletableResultCode actual = underTest.export(singleton(original));

        assertThat(actual).isSameAs(exportResult);

        Collection<SpanData> exportedSpans = exportedSpansCaptor.getValue();
        assertThat(exportedSpans).hasSize(1);
        assertThat(exportedSpans.iterator().next())
                .hasName("SplunkRumSpan")
                .hasAttributesSatisfyingExactly(
                        equalTo(SplunkSpanDataModifier.SPLUNK_OPERATION_KEY, "SplunkRumSpan"));
    }

    @Test
    public void shouldCopySelectedResourceAttributes() {
        Resource resource =
                Resource.create(
                        Attributes.builder()
                                .put("custom.ignored.attribute", "something")
                                .put(ResourceAttributes.SERVICE_NAME, "myApp")
                                .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "dev")
                                .put(ResourceAttributes.DEVICE_MODEL_NAME, "phone")
                                .put(ResourceAttributes.DEVICE_MODEL_IDENTIFIER, "phone 12345")
                                .put(ResourceAttributes.OS_NAME, "Android")
                                .put(ResourceAttributes.OS_TYPE, "linux")
                                .put(ResourceAttributes.OS_VERSION, "13")
                                .put(SplunkRum.APP_NAME_KEY, "myApp")
                                .put(SplunkRum.RUM_VERSION_KEY, "1.0.0")
                                .build());

        SpanData original =
                TestSpanData.builder()
                        .setName("SplunkRumSpan")
                        .setKind(SpanKind.CLIENT)
                        .setStatus(StatusData.unset())
                        .setStartEpochNanos(12345)
                        .setEndEpochNanos(67890)
                        .setHasEnded(true)
                        .setResource(resource)
                        .build();

        CompletableResultCode exportResult = CompletableResultCode.ofSuccess();
        when(delegate.export(exportedSpansCaptor.capture())).thenReturn(exportResult);

        CompletableResultCode actual = underTest.export(singleton(original));

        assertThat(actual).isSameAs(exportResult);

        Collection<SpanData> exportedSpans = exportedSpansCaptor.getValue();
        assertThat(exportedSpans).hasSize(1);
        assertThat(exportedSpans.iterator().next())
                .hasName("SplunkRumSpan")
                .hasAttributesSatisfyingExactly(
                        equalTo(SplunkSpanDataModifier.SPLUNK_OPERATION_KEY, "SplunkRumSpan"),
                        equalTo(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "dev"),
                        equalTo(ResourceAttributes.DEVICE_MODEL_NAME, "phone"),
                        equalTo(ResourceAttributes.DEVICE_MODEL_IDENTIFIER, "phone 12345"),
                        equalTo(ResourceAttributes.OS_NAME, "Android"),
                        equalTo(ResourceAttributes.OS_TYPE, "linux"),
                        equalTo(ResourceAttributes.OS_VERSION, "13"),
                        equalTo(SplunkRum.APP_NAME_KEY, "myApp"),
                        equalTo(SplunkRum.RUM_VERSION_KEY, "1.0.0"));
    }
}